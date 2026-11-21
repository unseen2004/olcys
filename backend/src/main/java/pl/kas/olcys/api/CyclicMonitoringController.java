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
import pl.kas.olcys.domain.SubjectEntity;
import pl.kas.olcys.domain.SubjectRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Map<String, MonitoringStatusDto> schedules = new ConcurrentHashMap<>();

    @Autowired
    public CyclicMonitoringController(SubjectRepository subjectRepository) {
        this.subjectRepository = subjectRepository;
    }

    @GetMapping("/monitoring")
    @Operation(summary = "Zwraca listę spraw objętych cyklicznym harmonogramem monitoringu KAS")
    public List<MonitoringStatusDto> listMonitoringSchedules() {
        return schedules.values().stream()
                .sorted((a, b) -> b.updatedAt().compareTo(a.updatedAt()))
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

        MonitoringStatusDto current = schedules.get(id);
        boolean nextEnabled = (request != null && request.enabled() != null)
                ? request.enabled()
                : (current == null || !current.enabled());
        int interval = (request != null && request.intervalHours() != null && request.intervalHours() > 0)
                ? request.intervalHours()
                : 24;

        MonitoringStatusDto updated = new MonitoringStatusDto(
                subject.getId(),
                subject.getDisplayName(),
                nextEnabled,
                interval,
                Instant.now(),
                current != null ? current.lastHeartbeatAt() : null
        );
        schedules.put(id, updated);
        log.info("Updated cyclic monitoring for subject {}: enabled={}, interval={}h", id, nextEnabled, interval);
        return updated;
    }

    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 3_600_000L)
    public void monitoringHeartbeat() {
        Instant now = Instant.now();
        schedules.replaceAll((id, dto) -> dto.enabled()
                ? new MonitoringStatusDto(dto.subjectId(), dto.subjectDisplayName(), true, dto.intervalHours(), dto.updatedAt(), now)
                : dto);
    }
}
