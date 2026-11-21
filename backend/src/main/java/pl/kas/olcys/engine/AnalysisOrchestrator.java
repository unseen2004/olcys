package pl.kas.olcys.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.kas.olcys.api.dto.AddressFindingDto;
import pl.kas.olcys.api.dto.AnalysisMode;
import pl.kas.olcys.api.dto.AnalysisRunDto;
import pl.kas.olcys.api.dto.CashOutSignalDto;
import pl.kas.olcys.api.dto.CreateSubjectRequest;
import pl.kas.olcys.api.dto.EvidenceArtifactDto;
import pl.kas.olcys.api.dto.EvidenceGraphDto;
import pl.kas.olcys.api.dto.GraphEdgeDto;
import pl.kas.olcys.api.dto.GraphNodeDto;
import pl.kas.olcys.api.dto.IdentifierDto;
import pl.kas.olcys.api.dto.OnChainMetricsDto;
import pl.kas.olcys.api.dto.OsintReportDto;
import pl.kas.olcys.api.dto.RunDiffDto;
import pl.kas.olcys.api.dto.RunSummaryDto;
import pl.kas.olcys.api.dto.SubjectSummaryDto;
import pl.kas.olcys.api.dto.TriggerRunRequest;
import pl.kas.olcys.connector.web2.RawEvidence;
import pl.kas.olcys.connector.web2.Web2Connector;
import pl.kas.olcys.connector.web3.RawOnChainProfile;
import pl.kas.olcys.connector.web3.Web3Connector;
import pl.kas.olcys.crypto.CryptoAddressValidator;
import pl.kas.olcys.crypto.CryptoAddressValidator.ValidatedAddress;
import pl.kas.olcys.domain.AddressFindingEntity;
import pl.kas.olcys.domain.AddressFindingRepository;
import pl.kas.olcys.domain.AnalysisRunEntity;
import pl.kas.olcys.domain.AnalysisRunRepository;
import pl.kas.olcys.domain.EvidenceArtifactEntity;
import pl.kas.olcys.domain.EvidenceArtifactRepository;
import pl.kas.olcys.domain.SubjectEntity;
import pl.kas.olcys.domain.SubjectRepository;
import pl.kas.olcys.engine.CashOutAnalyzer.WalletAnalysisResult;
import pl.kas.olcys.engine.FalsePositiveFirewall.FirewallDecision;
import pl.kas.olcys.engine.ai.AiClientRouter;
import pl.kas.olcys.report.ReportBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Parallel Java 21 Virtual Threads orchestrator executing the complete KAS OSINT & Blockchain pipeline:
 * Web2 evidence collection -> address extraction & checksum validation -> Web3 inspection ->
 * 3-layer FP Firewall -> Confidence Scoring -> Cash-Out analysis -> Graph & 3-zone Report -> Persistence.
 */
@Service
public class AnalysisOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AnalysisOrchestrator.class);
    private static final long CONNECTOR_TIMEOUT_SECONDS = 5L;

    private final List<Web2Connector> web2Connectors;
    private final List<Web3Connector> web3Connectors;
    private final CryptoAddressValidator addressValidator;
    private final FalsePositiveFirewall falsePositiveFirewall;
    private final ConfidenceScorer confidenceScorer;
    private final CashOutAnalyzer cashOutAnalyzer;
    private final RunDiffService runDiffService;
    private final AiClientRouter aiClientRouter;
    private final ReportBuilder reportBuilder;
    private final SubjectRepository subjectRepository;
    private final AnalysisRunRepository analysisRunRepository;
    private final EvidenceArtifactRepository evidenceArtifactRepository;
    private final AddressFindingRepository addressFindingRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public AnalysisOrchestrator(
            List<Web2Connector> web2Connectors,
            List<Web3Connector> web3Connectors,
            CryptoAddressValidator addressValidator,
            FalsePositiveFirewall falsePositiveFirewall,
            ConfidenceScorer confidenceScorer,
            CashOutAnalyzer cashOutAnalyzer,
            RunDiffService runDiffService,
            AiClientRouter aiClientRouter,
            ReportBuilder reportBuilder,
            SubjectRepository subjectRepository,
            AnalysisRunRepository analysisRunRepository,
            EvidenceArtifactRepository evidenceArtifactRepository,
            AddressFindingRepository addressFindingRepository,
            ObjectMapper objectMapper
    ) {
        this.web2Connectors = web2Connectors;
        this.web3Connectors = web3Connectors;
        this.addressValidator = addressValidator;
        this.falsePositiveFirewall = falsePositiveFirewall;
        this.confidenceScorer = confidenceScorer;
        this.cashOutAnalyzer = cashOutAnalyzer;
        this.runDiffService = runDiffService;
        this.aiClientRouter = aiClientRouter;
        this.reportBuilder = reportBuilder;
        this.subjectRepository = subjectRepository;
        this.analysisRunRepository = analysisRunRepository;
        this.evidenceArtifactRepository = evidenceArtifactRepository;
        this.addressFindingRepository = addressFindingRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AnalysisRunDto createSubjectAndRun(CreateSubjectRequest request) {
        String subjectId = "subj-" + UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        List<IdentifierDto> cleanIdentifiers = sanitizeIdentifiers(request.identifiers());

        SubjectEntity subject = new SubjectEntity(
                subjectId,
                request.displayName().trim(),
                writeJson(cleanIdentifiers),
                now
        );
        subjectRepository.save(subject);

        int snapshotStep = request.snapshotStep() != null && request.snapshotStep() > 0
                ? request.snapshotStep()
                : 1;
        AnalysisMode mode = request.mode() != null ? request.mode() : AnalysisMode.HYBRID;

        return executeAndPersistRun(
                subject,
                cleanIdentifiers,
                1,
                snapshotStep,
                mode,
                request.sources(),
                request.networks()
        );
    }

    @Transactional
    public AnalysisRunDto triggerRun(String subjectId, TriggerRunRequest request) {
        SubjectEntity subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new NoSuchElementException("Nie znaleziono podmiotu o ID: " + subjectId));

        List<AnalysisRunEntity> existingRuns = analysisRunRepository.findBySubjectIdOrderByRunNumberAsc(subjectId);
        int nextRunNumber = existingRuns.isEmpty()
                ? 1
                : existingRuns.get(existingRuns.size() - 1).getRunNumber() + 1;

        AnalysisMode fallbackMode = existingRuns.isEmpty()
                ? AnalysisMode.HYBRID
                : AnalysisMode.valueOf(existingRuns.get(existingRuns.size() - 1).getMode());

        AnalysisMode mode = (request != null && request.mode() != null) ? request.mode() : fallbackMode;
        int snapshotStep = (request != null && request.snapshotStep() != null && request.snapshotStep() > 0)
                ? request.snapshotStep()
                : nextRunNumber;
        List<String> sources = request != null ? request.sources() : List.of();
        List<String> networks = request != null ? request.networks() : List.of();
        List<IdentifierDto> identifiers = readIdentifiers(subject.getIdentifiersJson());

        return executeAndPersistRun(
                subject,
                identifiers,
                nextRunNumber,
                snapshotStep,
                mode,
                sources,
                networks
        );
    }

    @Transactional(readOnly = true)
    public List<SubjectSummaryDto> listSubjects() {
        List<SubjectEntity> subjects = subjectRepository.findAllByOrderByCreatedAtDesc();
        List<SubjectSummaryDto> result = new ArrayList<>();
        for (SubjectEntity subject : subjects) {
            List<IdentifierDto> identifiers = readIdentifiers(subject.getIdentifiersJson());
            List<RunSummaryDto> runs = analysisRunRepository.findBySubjectIdOrderByRunNumberAsc(subject.getId())
                    .stream()
                    .map(r -> new RunSummaryDto(
                            r.getId(),
                            r.getRunNumber(),
                            r.getSnapshotStep(),
                            AnalysisMode.valueOf(r.getMode()),
                            r.getCreatedAt(),
                            r.getValidatedAddressCount(),
                            r.getRejectedFpCount(),
                            r.getMaxConfidenceScore(),
                            r.getCashOutSignalCount(),
                            r.getTotalVolumePln()
                    ))
                    .toList();
            result.add(new SubjectSummaryDto(
                    subject.getId(),
                    subject.getDisplayName(),
                    identifiers,
                    subject.getCreatedAt(),
                    runs
            ));
        }
        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public AnalysisRunDto getRun(String runId) {
        AnalysisRunEntity entity = analysisRunRepository.findById(runId)
                .orElseThrow(() -> new NoSuchElementException("Nie znaleziono uruchomienia analizy o ID: " + runId));
        return readRunDto(entity.getResultJson());
    }

    @Transactional(readOnly = true)
    public RunDiffDto diffRuns(String subjectId, String fromRunId, String toRunId) {
        SubjectEntity subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new NoSuchElementException("Nie znaleziono podmiotu o ID: " + subjectId));

        List<AnalysisRunEntity> orderedRuns = analysisRunRepository.findBySubjectIdOrderByRunNumberAsc(subject.getId());
        if (orderedRuns.size() < 2 && (fromRunId == null || fromRunId.isBlank() || toRunId == null || toRunId.isBlank())) {
            throw new IllegalArgumentException(
                    "Porównanie zmian w czasie wymaga co najmniej 2 uruchomień analizy dla podmiotu " + subject.getDisplayName()
            );
        }

        AnalysisRunEntity fromEntity;
        AnalysisRunEntity toEntity;

        if (fromRunId == null || fromRunId.isBlank()) {
            fromEntity = orderedRuns.get(orderedRuns.size() - 2);
        } else {
            fromEntity = findSubjectRunOrThrow(subject.getId(), fromRunId);
        }

        if (toRunId == null || toRunId.isBlank()) {
            toEntity = orderedRuns.get(orderedRuns.size() - 1);
        } else {
            toEntity = findSubjectRunOrThrow(subject.getId(), toRunId);
        }

        return runDiffService.compareRuns(readRunDto(fromEntity.getResultJson()), readRunDto(toEntity.getResultJson()));
    }

    private AnalysisRunEntity findSubjectRunOrThrow(String subjectId, String runId) {
        AnalysisRunEntity entity = analysisRunRepository.findById(runId)
                .orElseThrow(() -> new NoSuchElementException("Nie znaleziono uruchomienia analizy o ID: " + runId));
        if (!subjectId.equals(entity.getSubjectId())) {
            throw new IllegalArgumentException("Uruchomienie " + runId + " nie należy do podmiotu " + subjectId);
        }
        return entity;
    }

    private AnalysisRunDto executeAndPersistRun(
            SubjectEntity subject,
            List<IdentifierDto> identifiers,
            int runNumber,
            int snapshotStep,
            AnalysisMode mode,
            List<String> requestedSources,
            List<String> requestedNetworks
    ) {
        long startNanos = System.nanoTime();
        String runId = "run-" + UUID.randomUUID().toString().substring(0, 8);
        Instant createdAt = Instant.now();

        List<String> activeSources = requestedSources == null
                ? List.of()
                : requestedSources.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();
        List<String> activeNetworks = (requestedNetworks == null || requestedNetworks.isEmpty())
                ? List.of("ETH", "BTC", "TRON")
                : requestedNetworks.stream().filter(n -> n != null && !n.isBlank())
                .map(n -> n.trim().toUpperCase(Locale.ROOT)).distinct().toList();

        // 1. Parallel Web2 evidence gathering on Java 21 Virtual Threads
        List<RawEvidence> rawEvidences = collectWeb2EvidencesParallel(identifiers, mode, snapshotStep, activeSources);

        // 2. Context classification & crypto address extraction
        List<EvidenceArtifactDto> evidenceDtos = new ArrayList<>();
        Map<String, ValidatedAddress> candidateAddresses = new LinkedHashMap<>();
        Map<String, List<EvidenceArtifactDto>> evidencesByAddressKey = new LinkedHashMap<>();
        Set<String> directInputAddressKeys = new LinkedHashSet<>();

        for (RawEvidence raw : rawEvidences) {
            String evId = "ev-" + UUID.randomUUID().toString().substring(0, 8);
            String classifiedContext = aiClientRouter.classifyContext(raw.snippet(), raw.contextHint());
            List<ValidatedAddress> extracted = addressValidator.extractValidatedAddresses(raw.snippet());

            List<String> extractedStrings = extracted.stream()
                    .map(ValidatedAddress::normalizedAddress)
                    .toList();

            EvidenceArtifactDto evDto = new EvidenceArtifactDto(
                    evId,
                    raw.sourceName(),
                    raw.sourceCategory(),
                    raw.url(),
                    raw.fetchedAt() != null ? raw.fetchedAt() : createdAt,
                    raw.sha256(),
                    raw.snippet(),
                    raw.matchedIdentifier(),
                    extractedStrings,
                    classifiedContext
            );
            evidenceDtos.add(evDto);

            for (ValidatedAddress addr : extracted) {
                if (!activeNetworks.contains(addr.network().toUpperCase(Locale.ROOT))) {
                    continue;
                }
                String key = addressKey(addr.network(), addr.normalizedAddress());
                candidateAddresses.putIfAbsent(key, addr);
                evidencesByAddressKey.computeIfAbsent(key, k -> new ArrayList<>()).add(evDto);
            }
        }

        // Direct CRYPTO_ADDRESS identifiers supplied by the analyst
        for (IdentifierDto id : identifiers) {
            if (id.value() == null || id.value().isBlank()) {
                continue;
            }
            if ("CRYPTO_ADDRESS".equalsIgnoreCase(id.type()) || addressValidator.validateAddress(id.value()).isPresent()) {
                Optional<ValidatedAddress> validatedOpt = addressValidator.validateAddress(id.value());
                if (validatedOpt.isPresent() && validatedOpt.get().checksumValid()) {
                    ValidatedAddress addr = validatedOpt.get();
                    if (activeNetworks.contains(addr.network().toUpperCase(Locale.ROOT))) {
                        String key = addressKey(addr.network(), addr.normalizedAddress());
                        candidateAddresses.putIfAbsent(key, addr);
                        directInputAddressKeys.add(key);
                    }
                }
            }
        }

        // 3. Parallel Web3 on-chain inspection on Java 21 Virtual Threads
        Map<String, RawOnChainProfile> profilesByKey = inspectWeb3Parallel(candidateAddresses, mode, snapshotStep);

        // 4. False Positive Firewall (Pass 1: classify each address)
        Map<String, FirewallDecision> decisionsByKey = new LinkedHashMap<>();
        List<RawOnChainProfile> confirmedRawProfiles = new ArrayList<>();
        for (Map.Entry<String, ValidatedAddress> entry : candidateAddresses.entrySet()) {
            String key = entry.getKey();
            ValidatedAddress addr = entry.getValue();
            RawOnChainProfile profile = profilesByKey.get(key);
            FirewallDecision decision = falsePositiveFirewall.evaluate(addr.normalizedAddress(), addr.network(), profile);
            decisionsByKey.put(key, decision);
            if (!decision.falsePositive() && profile != null) {
                confirmedRawProfiles.add(profile);
            }
        }

        // 5. Pass 2 of Firewall (interactionRecorded) + Confidence Scoring + Cash-Out Analysis
        List<AddressFindingDto> confirmedAddresses = new ArrayList<>();
        List<AddressFindingDto> rejectedAddresses = new ArrayList<>();
        List<CashOutSignalDto> allCashOutSignals = new ArrayList<>();

        for (Map.Entry<String, ValidatedAddress> entry : candidateAddresses.entrySet()) {
            String key = entry.getKey();
            ValidatedAddress addr = entry.getValue();
            RawOnChainProfile profile = profilesByKey.get(key);
            FirewallDecision decision = decisionsByKey.get(key);
            List<EvidenceArtifactDto> matchingEvidences = evidencesByAddressKey.getOrDefault(key, List.of());
            boolean isDirectInput = directInputAddressKeys.contains(key);
            String findingId = "addr-" + UUID.randomUUID().toString().substring(0, 8);

            if (decision.falsePositive()) {
                boolean interacted = falsePositiveFirewall.detectInteraction(
                        addr.normalizedAddress(),
                        addr.network(),
                        decision.entityTag(),
                        confirmedRawProfiles
                );
                OnChainMetricsDto fpMetrics = toBasicMetrics(addr, profile);
                AddressFindingDto rejectedDto = confidenceScorer.scoreCandidate(
                        findingId, addr, matchingEvidences, isDirectInput, decision, interacted, fpMetrics
                );
                rejectedAddresses.add(rejectedDto);
            } else {
                WalletAnalysisResult walletAnalysis = cashOutAnalyzer.analyzeWallet(profile, evidenceDtos);
                allCashOutSignals.addAll(walletAnalysis.signals());
                OnChainMetricsDto metrics = walletAnalysis.metrics() != null
                        ? walletAnalysis.metrics()
                        : toBasicMetrics(addr, profile);

                AddressFindingDto confirmedDto = confidenceScorer.scoreCandidate(
                        findingId, addr, matchingEvidences, isDirectInput, decision, false, metrics
                );
                confirmedAddresses.add(confirmedDto);
            }
        }

        confirmedAddresses.sort(Comparator.comparingInt(AddressFindingDto::confidenceScore).reversed());
        allCashOutSignals.sort(Comparator.comparing(
                CashOutSignalDto::timestamp,
                Comparator.nullsLast(Comparator.naturalOrder())
        ));

        // 6. Build Evidence Graph
        EvidenceGraphDto graph = buildEvidenceGraph(
                subject.getId(),
                subject.getDisplayName(),
                identifiers,
                evidenceDtos,
                confirmedAddresses,
                rejectedAddresses,
                allCashOutSignals
        );

        // 7. Build 3-zone OSINT Report (including AI executive summary)
        OsintReportDto report = reportBuilder.buildReport(
                subject.getDisplayName(),
                evidenceDtos,
                confirmedAddresses,
                rejectedAddresses,
                allCashOutSignals
        );

        long durationMs = Math.max(1L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
        List<String> effectiveSources = activeSources.isEmpty()
                ? evidenceDtos.stream().map(EvidenceArtifactDto::sourceName).distinct().toList()
                : activeSources;

        AnalysisRunDto runDto = new AnalysisRunDto(
                runId,
                subject.getId(),
                subject.getDisplayName(),
                runNumber,
                snapshotStep,
                mode,
                createdAt,
                durationMs,
                identifiers,
                effectiveSources,
                activeNetworks,
                List.copyOf(evidenceDtos),
                List.copyOf(confirmedAddresses),
                List.copyOf(rejectedAddresses),
                List.copyOf(allCashOutSignals),
                graph,
                report
        );

        // 8. Persist relational audit rows + full JSON snapshot
        persistRunEntities(runDto);

        return runDto;
    }

    private List<RawEvidence> collectWeb2EvidencesParallel(
            List<IdentifierDto> identifiers,
            AnalysisMode mode,
            int snapshotStep,
            List<String> activeSources
    ) {
        List<Web2Connector> selectedConnectors = new ArrayList<>();
        for (Web2Connector connector : web2Connectors) {
            if (!connector.supports(mode)) {
                continue;
            }
            if ("Synthetic".equalsIgnoreCase(connector.name())) {
                selectedConnectors.add(connector);
            } else if (activeSources.isEmpty()
                    || containsIgnoreCase(activeSources, connector.name())
                    || containsIgnoreCase(activeSources, "ALL")) {
                selectedConnectors.add(connector);
            }
        }

        List<RawEvidence> collected = new ArrayList<>();
        if (!selectedConnectors.isEmpty()) {
            long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(CONNECTOR_TIMEOUT_SECONDS);
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                Map<String, Future<List<RawEvidence>>> futures = new LinkedHashMap<>();
                for (Web2Connector connector : selectedConnectors) {
                    futures.put(connector.name(), executor.submit(() -> connector.search(identifiers, snapshotStep)));
                }
                for (Map.Entry<String, Future<List<RawEvidence>>> entry : futures.entrySet()) {
                    long remainingMs = Math.max(50L, TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()));
                    try {
                        List<RawEvidence> part = entry.getValue().get(remainingMs, TimeUnit.MILLISECONDS);
                        if (part != null) {
                            collected.addAll(part);
                        }
                    } catch (Exception ex) {
                        entry.getValue().cancel(true);
                        log.warn("Web2 connector '{}' timed out or failed: {}", entry.getKey(), ex.getMessage());
                    }
                }
            }
        }

        // Filter collected evidences by requested sourceName unless "Synthetic" or "ALL" is selected
        boolean filterBySourceName = !activeSources.isEmpty()
                && !containsIgnoreCase(activeSources, "Synthetic")
                && !containsIgnoreCase(activeSources, "ALL");

        Map<String, RawEvidence> deduplicated = new LinkedHashMap<>();
        for (RawEvidence ev : collected) {
            if (ev == null) {
                continue;
            }
            if (filterBySourceName && !containsIgnoreCase(activeSources, ev.sourceName())) {
                continue;
            }
            String dedupKey = (ev.sourceName() + "|" + ev.url() + "|" + ev.sha256()).toLowerCase(Locale.ROOT);
            deduplicated.putIfAbsent(dedupKey, ev);
        }
        return List.copyOf(deduplicated.values());
    }

    private Map<String, RawOnChainProfile> inspectWeb3Parallel(
            Map<String, ValidatedAddress> candidates,
            AnalysisMode mode,
            int snapshotStep
    ) {
        if (candidates.isEmpty()) {
            return Map.of();
        }
        Map<String, Web3Connector> connectorsByNet = new HashMap<>();
        for (Web3Connector connector : web3Connectors) {
            connectorsByNet.put(connector.network().toUpperCase(Locale.ROOT), connector);
        }

        Map<String, RawOnChainProfile> profiles = new LinkedHashMap<>();
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(CONNECTOR_TIMEOUT_SECONDS);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Map<String, Future<RawOnChainProfile>> futures = new LinkedHashMap<>();
            for (Map.Entry<String, ValidatedAddress> entry : candidates.entrySet()) {
                ValidatedAddress addr = entry.getValue();
                Web3Connector connector = connectorsByNet.get(addr.network().toUpperCase(Locale.ROOT));
                if (connector != null) {
                    futures.put(entry.getKey(), executor.submit(
                            () -> connector.inspectAddress(addr.normalizedAddress(), mode, snapshotStep)
                    ));
                }
            }

            for (Map.Entry<String, Future<RawOnChainProfile>> entry : futures.entrySet()) {
                long remainingMs = Math.max(50L, TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()));
                try {
                    RawOnChainProfile profile = entry.getValue().get(remainingMs, TimeUnit.MILLISECONDS);
                    if (profile != null) {
                        profiles.put(entry.getKey(), profile);
                    }
                } catch (Exception ex) {
                    entry.getValue().cancel(true);
                    log.warn("Web3 inspection for '{}' timed out or failed: {}", entry.getKey(), ex.getMessage());
                }
            }
        }
        return profiles;
    }

    private EvidenceGraphDto buildEvidenceGraph(
            String subjectId,
            String subjectDisplayName,
            List<IdentifierDto> identifiers,
            List<EvidenceArtifactDto> evidences,
            List<AddressFindingDto> confirmed,
            List<AddressFindingDto> rejected,
            List<CashOutSignalDto> cashOutSignals
    ) {
        List<GraphNodeDto> nodes = new ArrayList<>();
        List<GraphEdgeDto> edges = new ArrayList<>();

        String subjectNodeId = "subject:" + subjectId;
        nodes.add(new GraphNodeDto(
                subjectNodeId,
                "SUBJECT",
                subjectDisplayName,
                identifiers.stream().map(i -> i.type() + ": " + i.value()).reduce((a, b) -> a + " | " + b).orElse(""),
                null,
                null,
                Map.of("subjectId", subjectId, "identifiersCount", identifiers.size())
        ));

        Map<String, String> identifierNodeByValue = new LinkedHashMap<>();
        for (int i = 0; i < identifiers.size(); i++) {
            IdentifierDto id = identifiers.get(i);
            String idNodeId = "id:" + i;
            nodes.add(new GraphNodeDto(
                    idNodeId,
                    "IDENTIFIER",
                    id.value(),
                    id.type(),
                    null,
                    null,
                    Map.of("identifierType", id.type(), "identifierValue", id.value())
            ));
            edges.add(new GraphEdgeDto(
                    "edge-subj-" + i,
                    subjectNodeId,
                    idNodeId,
                    id.type(),
                    false
            ));
            identifierNodeByValue.put(normalizeHandle(id.value()), idNodeId);
        }

        for (EvidenceArtifactDto ev : evidences) {
            String evNodeId = "ev:" + ev.id();
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("evidenceId", ev.id());
            meta.put("sourceName", ev.sourceName());
            meta.put("sourceCategory", ev.sourceCategory());
            meta.put("url", ev.url());
            meta.put("fetchedAt", ev.fetchedAt().toString());
            meta.put("sha256", ev.sha256());
            meta.put("snippet", ev.snippet());
            meta.put("matchedIdentifier", ev.matchedIdentifier());
            meta.put("contextClassification", ev.contextClassification());

            nodes.add(new GraphNodeDto(
                    evNodeId,
                    "EVIDENCE",
                    ev.sourceName(),
                    ev.url(),
                    null,
                    null,
                    meta
            ));

            String sourceIdNode = identifierNodeByValue.getOrDefault(
                    normalizeHandle(ev.matchedIdentifier()),
                    subjectNodeId
            );
            edges.add(new GraphEdgeDto(
                    "edge-id-" + ev.id(),
                    sourceIdNode,
                    evNodeId,
                    ev.matchedIdentifier() != null ? ev.matchedIdentifier() : "OSINT",
                    false
            ));
        }

        for (AddressFindingDto f : confirmed) {
            String walletNodeId = "addr:" + f.network() + ":" + f.address();
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("findingId", f.id());
            meta.put("address", f.address());
            meta.put("network", f.network());
            meta.put("checksumFormat", f.checksumFormat());
            meta.put("confidenceScore", f.confidenceScore());
            meta.put("confidenceLevelPl", f.confidenceLevelPl());
            if (f.onChainMetrics() != null) {
                meta.put("txCount", f.onChainMetrics().txCount());
                meta.put("estimatedTotalVolumePln", f.onChainMetrics().estimatedTotalVolumePln());
                meta.put("stablecoinVolumeUsd", f.onChainMetrics().stablecoinVolumeUsd());
            }

            nodes.add(new GraphNodeDto(
                    walletNodeId,
                    "WALLET_CONFIRMED",
                    f.address(),
                    f.network() + " • " + f.confidenceLevelPl(),
                    f.confidenceScore(),
                    false,
                    meta
            ));

            if (f.evidenceIds() != null && !f.evidenceIds().isEmpty()) {
                for (String evId : f.evidenceIds()) {
                    edges.add(new GraphEdgeDto(
                            "edge-ev-" + evId + "-" + f.id(),
                            "ev:" + evId,
                            walletNodeId,
                            f.confidenceScore() + "%",
                            false
                    ));
                }
            } else {
                String sourceIdNode = identifierNodeByValue.getOrDefault(
                        normalizeHandle(f.address()),
                        subjectNodeId
                );
                edges.add(new GraphEdgeDto(
                        "edge-direct-" + f.id(),
                        sourceIdNode,
                        walletNodeId,
                        f.confidenceScore() + "%",
                        false
                ));
            }
        }

        for (AddressFindingDto fp : rejected) {
            String fpNodeId = "fp:" + fp.network() + ":" + fp.address();
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("findingId", fp.id());
            meta.put("address", fp.address());
            meta.put("network", fp.network());
            meta.put("checksumFormat", fp.checksumFormat());
            meta.put("confidenceScore", 0);
            meta.put("rejectionCategory", fp.rejectionCategory() != null ? fp.rejectionCategory() : "");
            meta.put("rejectionReason", fp.rejectionReason() != null ? fp.rejectionReason() : "");
            meta.put("entityTag", fp.entityTag() != null ? fp.entityTag() : "");

            String sublabel = (fp.entityTag() != null ? fp.entityTag() : fp.rejectionCategory())
                    + (fp.interactionRecorded() ? " • Odnotowano interakcję" : "");

            nodes.add(new GraphNodeDto(
                    fpNodeId,
                    "WALLET_REJECTED",
                    fp.address(),
                    sublabel,
                    0,
                    true,
                    meta
            ));

            if (fp.evidenceIds() != null) {
                for (String evId : fp.evidenceIds()) {
                    edges.add(new GraphEdgeDto(
                            "edge-fp-" + evId + "-" + fp.id(),
                            "ev:" + evId,
                            fpNodeId,
                            "Odrzucono FP (0%)",
                            true
                    ));
                }
            }
        }

        Map<String, CashOutSignalDto> cashOutGrouped = new LinkedHashMap<>();
        Map<String, Double> cashOutSumPln = new LinkedHashMap<>();
        for (CashOutSignalDto sig : cashOutSignals) {
            String cpKey = sig.counterpartyName() == null ? "CASP" : sig.counterpartyName();
            String nodeId = "cashout:" + sig.signalType() + ":" + cpKey;
            cashOutGrouped.putIfAbsent(nodeId, sig);
            cashOutSumPln.merge(nodeId, sig.estimatedPln(), Double::sum);
        }

        for (Map.Entry<String, CashOutSignalDto> entry : cashOutGrouped.entrySet()) {
            String nodeId = entry.getKey();
            CashOutSignalDto sig = entry.getValue();
            double sumPln = Math.round(cashOutSumPln.getOrDefault(nodeId, sig.estimatedPln()) * 100.0) / 100.0;

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("signalId", sig.id());
            meta.put("signalType", sig.signalType());
            meta.put("severity", sig.severity());
            meta.put("address", sig.address());
            meta.put("network", sig.network());
            meta.put("counterpartyName", sig.counterpartyName());
            meta.put("estimatedPln", sumPln);
            meta.put("verificationHintPl", sig.verificationHintPl());

            nodes.add(new GraphNodeDto(
                    nodeId,
                    "CASHOUT",
                    sig.counterpartyName(),
                    sig.signalType() + " • " + String.format(Locale.forLanguageTag("pl-PL"), "%,.2f PLN", sumPln),
                    null,
                    null,
                    meta
            ));

            String sourceWalletNodeId = "addr:" + sig.network() + ":" + sig.address();
            edges.add(new GraphEdgeDto(
                    "edge-cashout-" + sig.id(),
                    sourceWalletNodeId,
                    nodeId,
                    sig.signalType(),
                    false
            ));
        }

        return new EvidenceGraphDto(List.copyOf(nodes), List.copyOf(edges));
    }

    private void persistRunEntities(AnalysisRunDto run) {
        double totalVolumePln = run.report() != null && run.report().zone2Computations() != null
                ? run.report().zone2Computations().totalEstimatedVolumePln()
                : 0.0;
        int maxScore = run.confirmedAddresses().stream()
                .mapToInt(AddressFindingDto::confidenceScore)
                .max()
                .orElse(0);

        AnalysisRunEntity runEntity = new AnalysisRunEntity(
                run.runId(),
                run.subjectId(),
                run.runNumber(),
                run.snapshotStep(),
                run.mode().name(),
                run.createdAt(),
                run.durationMs(),
                run.confirmedAddresses().size(),
                run.rejectedAddresses().size(),
                maxScore,
                run.cashOutSignals().size(),
                totalVolumePln,
                writeJson(run)
        );
        analysisRunRepository.save(runEntity);

        List<EvidenceArtifactEntity> evEntities = run.evidences().stream()
                .map(ev -> new EvidenceArtifactEntity(
                        ev.id(),
                        run.runId(),
                        truncate(ev.sourceName(), 128),
                        truncate(ev.sourceCategory(), 64),
                        ev.url(),
                        ev.fetchedAt(),
                        ev.sha256(),
                        ev.snippet(),
                        truncate(ev.matchedIdentifier(), 255),
                        truncate(ev.contextClassification(), 64)
                ))
                .toList();
        evidenceArtifactRepository.saveAll(evEntities);

        List<AddressFindingEntity> findingEntities = new ArrayList<>();
        for (AddressFindingDto f : run.confirmedAddresses()) {
            findingEntities.add(toFindingEntity(run.runId(), f));
        }
        for (AddressFindingDto fp : run.rejectedAddresses()) {
            findingEntities.add(toFindingEntity(run.runId(), fp));
        }
        addressFindingRepository.saveAll(findingEntities);
    }

    private static AddressFindingEntity toFindingEntity(String runId, AddressFindingDto f) {
        return new AddressFindingEntity(
                f.id(),
                runId,
                truncate(f.address(), 128),
                truncate(f.network(), 32),
                f.confidenceScore(),
                f.falsePositive(),
                f.rejectionReason(),
                truncate(f.entityTag(), 255),
                f.interactionRecorded()
        );
    }

    private static OnChainMetricsDto toBasicMetrics(ValidatedAddress addr, RawOnChainProfile profile) {
        if (profile == null) {
            return new OnChainMetricsDto(
                    addr.normalizedAddress(),
                    addr.network(),
                    false,
                    "0x",
                    0,
                    0.0,
                    0.0,
                    addr.network(),
                    0.0,
                    0.0,
                    List.of(),
                    List.of()
            );
        }
        return new OnChainMetricsDto(
                addr.normalizedAddress(),
                addr.network(),
                FalsePositiveFirewall.isSmartContractBytecode(profile.bytecode()),
                FalsePositiveFirewall.formatBytecodePrefix(profile.bytecode()),
                profile.txCount(),
                profile.totalInflowNative(),
                profile.totalOutflowNative(),
                profile.nativeSymbol(),
                profile.estimatedTotalVolumePln(),
                profile.stablecoinVolumeUsd(),
                List.of(),
                List.of()
        );
    }

    private List<IdentifierDto> sanitizeIdentifiers(List<IdentifierDto> raw) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream()
                .filter(id -> id != null && id.value() != null && !id.value().isBlank())
                .map(id -> new IdentifierDto(
                        id.type() == null || id.type().isBlank() ? "NICK" : id.type().trim().toUpperCase(Locale.ROOT),
                        id.value().trim()
                ))
                .toList();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Błąd serializacji danych JSON analizy", ex);
        }
    }

    private List<IdentifierDto> readIdentifiers(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("Błąd deserializacji identyfikatorów podmiotu", ex);
        }
    }

    private AnalysisRunDto readRunDto(String json) {
        try {
            return objectMapper.readValue(json, AnalysisRunDto.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Błąd deserializacji zapisanego uruchomienia analizy", ex);
        }
    }

    private static String addressKey(String network, String normalizedAddress) {
        String net = network == null ? "" : network.trim().toUpperCase(Locale.ROOT);
        String addr = normalizedAddress == null ? "" : normalizedAddress.trim();
        if ("ETH".equals(net)) {
            return net + ":" + addr.toLowerCase(Locale.ROOT);
        }
        return net + ":" + addr;
    }

    private static String normalizeHandle(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("@")) {
            trimmed = trimmed.substring(1).trim();
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static boolean containsIgnoreCase(List<String> list, String candidate) {
        if (list == null || candidate == null) {
            return false;
        }
        for (String item : list) {
            if (item != null && item.trim().equalsIgnoreCase(candidate.trim())) {
                return true;
            }
        }
        return false;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen);
    }
}
