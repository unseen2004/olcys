package pl.kas.olcys.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.kas.olcys.api.dto.AnalysisRunDto;
import pl.kas.olcys.api.dto.RunDiffDto;
import pl.kas.olcys.domain.AnalysisRunRepository;
import pl.kas.olcys.domain.MonitoringScheduleEntity;
import pl.kas.olcys.domain.MonitoringScheduleRepository;
import pl.kas.olcys.domain.SubjectEntity;
import pl.kas.olcys.domain.SubjectRepository;
import pl.kas.olcys.engine.AnalysisOrchestrator;
import pl.kas.olcys.engine.RunDiffService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@EnableScheduling
@RequestMapping("/api")
@Tag(name = "Monitoring Cykliczny (CRON)", description = "Zarządzanie cyklicznym harmonogramem kontroli spraw KAS")
public class CyclicMonitoringController {

    private static final Logger log = LoggerFactory.getLogger(CyclicMonitoringController.class);

    public record MonitoringRequest(Boolean enabled, Integer intervalHours) {
    }

    public record MonitoringStatusDto(
            String subjectId,
            String subjectDisplayName,
            boolean enabled,
            int intervalHours,
            Instant updatedAt,
            Instant lastHeartbeatAt
    ) {
    }

    private final SubjectRepository subjectRepository;
    private final MonitoringScheduleRepository monitoringScheduleRepository;
    private final AnalysisRunRepository analysisRunRepository;
    private final AnalysisOrchestrator analysisOrchestrator;
    private final RunDiffService runDiffService;

    @Autowired
    public CyclicMonitoringController(
            SubjectRepository subjectRepository,
            MonitoringScheduleRepository monitoringScheduleRepository,
            AnalysisRunRepository analysisRunRepository,
            AnalysisOrchestrator analysisOrchestrator,
            RunDiffService runDiffService
    ) {
        this.subjectRepository = subjectRepository;
        this.monitoringScheduleRepository = monitoringScheduleRepository;
        this.analysisRunRepository = analysisRunRepository;
        this.analysisOrchestrator = analysisOrchestrator;
        this.runDiffService = runDiffService;
    }

    @GetMapping("/monitoring")
    @Operation(summary = "Zwraca listę spraw objętych cyklicznym harmonogramem monitoringu KAS")
    public List<MonitoringStatusDto> listMonitoringSchedules() {
        return monitoringScheduleRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(CyclicMonitoringController::toDto)
                .toList();
    }

    @PostMapping("/subjects/{id}/monitoring")
    @Operation(summary = "Włącza lub wyłącza cykliczny monitoring zmian portfeli (CRON) dla sprawy")
    public MonitoringStatusDto configureMonitoring(
            @PathVariable String id,
            @RequestBody(required = false) MonitoringRequest request
    ) {
        SubjectEntity subject = subjectRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Nie znaleziono podmiotu o ID: " + id));

        MonitoringScheduleEntity current = monitoringScheduleRepository.findById(id).orElse(null);
        boolean nextEnabled = (request != null && request.enabled() != null)
                ? request.enabled()
                : (current == null || !current.isEnabled());
        int interval = (request != null && request.intervalHours() != null && request.intervalHours() > 0)
                ? request.intervalHours()
                : (current != null && current.getIntervalHours() > 0 ? current.getIntervalHours() : 24);

        MonitoringScheduleEntity entity = current != null ? current : new MonitoringScheduleEntity();
        entity.setSubjectId(subject.getId());
        entity.setSubjectDisplayName(subject.getDisplayName());
        entity.setEnabled(nextEnabled);
        entity.setIntervalHours(interval);
        entity.setUpdatedAt(Instant.now());
        if (current == null) {
            entity.setLastHeartbeatAt(null);
        }

        MonitoringScheduleEntity saved = monitoringScheduleRepository.save(entity);
        log.info("Updated cyclic monitoring in DB for subject {}: enabled={}, interval={}h", id, nextEnabled, interval);
        return toDto(saved);
    }

    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 3_600_000L)
    public void monitoringHeartbeat() {
        Instant now = Instant.now();
        List<MonitoringScheduleEntity> activeSchedules = monitoringScheduleRepository.findByEnabledTrue();
        for (MonitoringScheduleEntity schedule : activeSchedules) {
            if (!schedule.isEnabled()) {
                continue;
            }
            String subjectId = schedule.getSubjectId();
            Instant lastHeartbeat = schedule.getLastHeartbeatAt();
            if (lastHeartbeat != null
                    && Duration.between(lastHeartbeat, now).toHours() < schedule.getIntervalHours()) {
                continue;
            }
            try {
                AnalysisRunDto triggered = analysisOrchestrator.triggerRun(subjectId, null);
                log.info("Cyclic monitoring executed scheduled follow-up analysis for subject {} (run #{})",
                        subjectId, triggered.runNumber());

                if (analysisRunRepository.findBySubjectIdOrderByRunNumberAsc(subjectId).size() >= 2) {
                    RunDiffDto diff = runDiffService.compareRuns(subjectId, null, null);
                    if (diff.walletRotationDetected()) {
                        log.warn("Cyclic monitoring detected WALLET ROTATION for subject {} (run #{} -> #{}): {}",
                                subjectId, diff.fromRunNumber(), diff.toRunNumber(), diff.summaryPl());
                    } else {
                        log.info("Cyclic monitoring diff for subject {}: no wallet rotation (volumeDelta={} PLN)",
                                subjectId, diff.volumeDeltaPln());
                    }
                }

                schedule.setLastHeartbeatAt(now);
                monitoringScheduleRepository.save(schedule);
            } catch (Exception ex) {
                log.warn("Cyclic monitoring failed for subject {}: {}", subjectId, ex.getMessage());
            }
        }
    }

    private static MonitoringStatusDto toDto(MonitoringScheduleEntity entity) {
        return new MonitoringStatusDto(
                entity.getSubjectId(),
                entity.getSubjectDisplayName(),
                entity.isEnabled(),
                entity.getIntervalHours(),
                entity.getUpdatedAt(),
                entity.getLastHeartbeatAt()
        );
    }
}
