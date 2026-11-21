package pl.kas.olcys.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.kas.olcys.api.dto.AddressFindingDto;
import pl.kas.olcys.api.dto.AddressScoreChangeDto;
import pl.kas.olcys.api.dto.AnalysisRunDto;
import pl.kas.olcys.api.dto.CashOutSignalDto;
import pl.kas.olcys.api.dto.EvidenceArtifactDto;
import pl.kas.olcys.api.dto.RunDiffDto;
import pl.kas.olcys.api.dto.WalletRotationEventDto;
import pl.kas.olcys.domain.AnalysisRunEntity;
import pl.kas.olcys.domain.AnalysisRunRepository;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compares two persisted {@link AnalysisRunDto} snapshots for the same subject to detect
 * wallet rotations on identical Web2 profiles/URLs, score changes, volume deltas, and new Cash-Out signals.
 */
@Component
public class RunDiffService {

    private final AnalysisRunRepository analysisRunRepository;
    private final ObjectMapper objectMapper;

    public RunDiffService() {
        this.analysisRunRepository = null;
        this.objectMapper = null;
    }

    @Autowired
    public RunDiffService(AnalysisRunRepository analysisRunRepository, ObjectMapper objectMapper) {
        this.analysisRunRepository = analysisRunRepository;
        this.objectMapper = objectMapper;
    }

    public RunDiffDto compareRuns(String subjectId, String fromRunId, String toRunId) {
        if (analysisRunRepository == null || objectMapper == null) {
            throw new IllegalStateException("RunDiffService wymaga AnalysisRunRepository i ObjectMapper do porównywania po ID.");
        }
        List<AnalysisRunEntity> orderedRuns = analysisRunRepository.findBySubjectIdOrderByRunNumberAsc(subjectId);
        if (orderedRuns.size() < 2 && (fromRunId == null || fromRunId.isBlank() || toRunId == null || toRunId.isBlank())) {
            throw new IllegalArgumentException(
                    "Porównanie zmian w czasie wymaga co najmniej 2 uruchomień analizy dla podmiotu " + subjectId
            );
        }
        AnalysisRunEntity fromEntity = (fromRunId == null || fromRunId.isBlank())
                ? orderedRuns.get(orderedRuns.size() - 2)
                : findRunForSubject(subjectId, fromRunId);
        AnalysisRunEntity toEntity = (toRunId == null || toRunId.isBlank())
                ? orderedRuns.get(orderedRuns.size() - 1)
                : findRunForSubject(subjectId, toRunId);

        try {
            AnalysisRunDto fromDto = objectMapper.readValue(fromEntity.getResultJson(), AnalysisRunDto.class);
            AnalysisRunDto toDto = objectMapper.readValue(toEntity.getResultJson(), AnalysisRunDto.class);
            return compareRuns(fromDto, toDto);
        } catch (Exception ex) {
            throw new IllegalStateException("Nie udało się zdeserializować analizy do porównania diff: " + ex.getMessage(), ex);
        }
    }

    private AnalysisRunEntity findRunForSubject(String subjectId, String runId) {
        AnalysisRunEntity entity = analysisRunRepository.findById(runId)
                .orElseThrow(() -> new NoSuchElementException("Nie znaleziono uruchomienia analizy o ID: " + runId));
        if (!subjectId.equals(entity.getSubjectId())) {
            throw new IllegalArgumentException("Uruchomienie " + runId + " nie należy do podmiotu " + subjectId);
        }
        return entity;
    }

    public RunDiffDto compareRuns(AnalysisRunDto fromRun, AnalysisRunDto toRun) {
        Map<String, AddressFindingDto> fromConfirmed = indexConfirmedByAddress(fromRun);
        Map<String, AddressFindingDto> toConfirmed = indexConfirmedByAddress(toRun);

        List<WalletRotationEventDto> rotations = detectWalletRotations(fromRun, toRun, fromConfirmed, toConfirmed);
        boolean walletRotationDetected = !rotations.isEmpty();

        List<String> addedAddresses = new ArrayList<>();
        for (Map.Entry<String, AddressFindingDto> entry : toConfirmed.entrySet()) {
            if (!fromConfirmed.containsKey(entry.getKey())) {
                addedAddresses.add(entry.getValue().address());
            }
        }

        List<String> removedAddresses = new ArrayList<>();
        for (Map.Entry<String, AddressFindingDto> entry : fromConfirmed.entrySet()) {
            if (!toConfirmed.containsKey(entry.getKey())) {
                removedAddresses.add(entry.getValue().address());
            }
        }

        List<AddressScoreChangeDto> scoreChanges = computeScoreChanges(fromConfirmed, toConfirmed);

        double fromVolumePln = totalVolumePln(fromRun);
        double toVolumePln = totalVolumePln(toRun);
        double volumeDeltaPln = Math.round((toVolumePln - fromVolumePln) * 100.0) / 100.0;

        List<CashOutSignalDto> newCashOutSignals = findNewCashOutSignals(fromRun, toRun);

        String summaryPl = buildSummaryPl(
                fromRun, toRun, walletRotationDetected, rotations,
                addedAddresses, removedAddresses, volumeDeltaPln, newCashOutSignals
        );

        return new RunDiffDto(
                toRun.subjectId(),
                toRun.subjectDisplayName(),
                fromRun.runId(),
                toRun.runId(),
                fromRun.runNumber(),
                toRun.runNumber(),
                fromRun.createdAt(),
                toRun.createdAt(),
                walletRotationDetected,
                List.copyOf(rotations),
                List.copyOf(addedAddresses),
                List.copyOf(removedAddresses),
                List.copyOf(scoreChanges),
                volumeDeltaPln,
                List.copyOf(newCashOutSignals),
                summaryPl
        );
    }

    private List<WalletRotationEventDto> detectWalletRotations(
            AnalysisRunDto fromRun,
            AnalysisRunDto toRun,
            Map<String, AddressFindingDto> fromConfirmed,
            Map<String, AddressFindingDto> toConfirmed
    ) {
        List<EvidenceArtifactDto> fromEvidences = fromRun.evidences() == null ? List.of() : fromRun.evidences();
        List<EvidenceArtifactDto> toEvidences = toRun.evidences() == null ? List.of() : toRun.evidences();

        List<WalletRotationEventDto> rotations = new ArrayList<>();
        Set<String> dedupKeys = new LinkedHashSet<>();

        for (EvidenceArtifactDto e1 : fromEvidences) {
            for (EvidenceArtifactDto e2 : toEvidences) {
                if (!sameWeb2ProfileOrUrl(e1, e2)) {
                    continue;
                }

                List<AddressFindingDto> addrs1 = confirmedFindingsInEvidence(e1, fromConfirmed);
                List<AddressFindingDto> addrs2 = confirmedFindingsInEvidence(e2, toConfirmed);
                if (addrs1.isEmpty() || addrs2.isEmpty()) {
                    continue;
                }

                Set<String> allProfileKeysInFrom = new LinkedHashSet<>();
                for (EvidenceArtifactDto ef : fromEvidences) {
                    if (sameWeb2ProfileOrUrl(e1, ef)) {
                        confirmedFindingsInEvidence(ef, fromConfirmed).forEach(f -> allProfileKeysInFrom.add(addressKey(f)));
                    }
                }
                Set<String> allProfileKeysInTo = new LinkedHashSet<>();
                for (EvidenceArtifactDto et : toEvidences) {
                    if (sameWeb2ProfileOrUrl(e2, et)) {
                        confirmedFindingsInEvidence(et, toConfirmed).forEach(f -> allProfileKeysInTo.add(addressKey(f)));
                    }
                }

                for (AddressFindingDto oldFinding : addrs1) {
                    if (allProfileKeysInTo.contains(addressKey(oldFinding))) {
                        continue;
                    }
                    for (AddressFindingDto newFinding : addrs2) {
                        if (allProfileKeysInFrom.contains(addressKey(newFinding))) {
                            continue;
                        }
                        if (!oldFinding.network().equalsIgnoreCase(newFinding.network())) {
                            continue;
                        }

                        String dedup = e2.sourceName() + "|" + oldFinding.address() + "|" + newFinding.address();
                        if (!dedupKeys.add(dedup)) {
                            continue;
                        }

                        String alertPl = String.format(
                                "ALERT KAS: Na profilu %s (%s) nastąpiła podmiana adresu portfela %s z %s na nowy adres %s. Może to świadczyć o próbie ukrycia bieżących rozliczeń lub rotacji portfela operacyjnego.",
                                e2.sourceName(),
                                e2.url(),
                                newFinding.network(),
                                oldFinding.address(),
                                newFinding.address()
                        );
                        rotations.add(new WalletRotationEventDto(
                                e2.sourceName(),
                                e2.url(),
                                oldFinding.address(),
                                newFinding.address(),
                                newFinding.network(),
                                e1.fetchedAt(),
                                e2.fetchedAt(),
                                alertPl
                        ));
                    }
                }
            }
        }
        return rotations;
    }

    private static boolean sameWeb2ProfileOrUrl(EvidenceArtifactDto e1, EvidenceArtifactDto e2) {
        if (e1 == null || e2 == null) {
            return false;
        }
        String src1 = e1.sourceName() == null ? "" : e1.sourceName().trim();
        String src2 = e2.sourceName() == null ? "" : e2.sourceName().trim();
        if (!src1.equalsIgnoreCase(src2)) {
            return false;
        }
        String url1 = normalizeUrl(e1.url());
        String url2 = normalizeUrl(e2.url());
        if (!url1.isEmpty() && !url2.isEmpty()) {
            return url1.equalsIgnoreCase(url2);
        }
        return "OWN_PROFILE_VERIFIED".equalsIgnoreCase(e1.contextClassification())
                && "OWN_PROFILE_VERIFIED".equalsIgnoreCase(e2.contextClassification());
    }

    private static List<AddressFindingDto> confirmedFindingsInEvidence(
            EvidenceArtifactDto ev,
            Map<String, AddressFindingDto> confirmedMap
    ) {
        if (ev.extractedAddresses() == null) {
            return List.of();
        }
        List<AddressFindingDto> matches = new ArrayList<>();
        for (String addr : ev.extractedAddresses()) {
            if (addr == null) {
                continue;
            }
            AddressFindingDto direct = confirmedMap.get(normalizeLookupKey(addr));
            if (direct != null) {
                matches.add(direct);
            }
        }
        return matches;
    }

    private static List<AddressScoreChangeDto> computeScoreChanges(
            Map<String, AddressFindingDto> fromConfirmed,
            Map<String, AddressFindingDto> toConfirmed
    ) {
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(fromConfirmed.keySet());
        allKeys.addAll(toConfirmed.keySet());

        List<AddressScoreChangeDto> changes = new ArrayList<>();
        for (String key : allKeys) {
            AddressFindingDto oldF = fromConfirmed.get(key);
            AddressFindingDto newF = toConfirmed.get(key);
            if (oldF == null && newF != null) {
                changes.add(new AddressScoreChangeDto(
                        newF.address(), newF.network(), null, newF.confidenceScore(), "ADDED"));
            } else if (oldF != null && newF == null) {
                changes.add(new AddressScoreChangeDto(
                        oldF.address(), oldF.network(), oldF.confidenceScore(), null, "REMOVED"));
            } else if (oldF != null) {
                String changeType;
                if (newF.confidenceScore() > oldF.confidenceScore()) {
                    changeType = "INCREASED";
                } else if (newF.confidenceScore() < oldF.confidenceScore()) {
                    changeType = "DECREASED";
                } else {
                    changeType = "UNCHANGED";
                }
                changes.add(new AddressScoreChangeDto(
                        newF.address(), newF.network(), oldF.confidenceScore(), newF.confidenceScore(), changeType));
            }
        }
        return changes;
    }

    private static List<CashOutSignalDto> findNewCashOutSignals(AnalysisRunDto fromRun, AnalysisRunDto toRun) {
        Set<String> oldKeys = new LinkedHashSet<>();
        if (fromRun.cashOutSignals() != null) {
            for (CashOutSignalDto sig : fromRun.cashOutSignals()) {
                oldKeys.add(signalKey(sig));
            }
        }
        List<CashOutSignalDto> newSignals = new ArrayList<>();
        if (toRun.cashOutSignals() != null) {
            for (CashOutSignalDto sig : toRun.cashOutSignals()) {
                if (!oldKeys.contains(signalKey(sig))) {
                    newSignals.add(sig);
                }
            }
        }
        return newSignals;
    }

    private static String signalKey(CashOutSignalDto sig) {
        return sig.signalType() + "|" + sig.address() + "|" + sig.txHash();
    }

    private static Map<String, AddressFindingDto> indexConfirmedByAddress(AnalysisRunDto run) {
        Map<String, AddressFindingDto> map = new LinkedHashMap<>();
        if (run.confirmedAddresses() != null) {
            for (AddressFindingDto f : run.confirmedAddresses()) {
                map.putIfAbsent(addressKey(f), f);
            }
        }
        return map;
    }

    private static String addressKey(AddressFindingDto f) {
        return normalizeLookupKey(f.address());
    }

    private static String normalizeLookupKey(String addr) {
        if (addr == null) {
            return "";
        }
        String trimmed = addr.trim();
        return trimmed.startsWith("0x") || trimmed.startsWith("0X")
                ? trimmed.toLowerCase(Locale.ROOT)
                : trimmed;
    }

    private static double totalVolumePln(AnalysisRunDto run) {
        if (run.report() != null && run.report().zone2Computations() != null) {
            return run.report().zone2Computations().totalEstimatedVolumePln();
        }
        if (run.confirmedAddresses() == null) {
            return 0.0;
        }
        return run.confirmedAddresses().stream()
                .mapToDouble(f -> f.onChainMetrics() != null ? f.onChainMetrics().estimatedTotalVolumePln() : 0.0)
                .sum();
    }

    private static String normalizeUrl(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String buildSummaryPl(
            AnalysisRunDto fromRun,
            AnalysisRunDto toRun,
            boolean walletRotationDetected,
            List<WalletRotationEventDto> rotations,
            List<String> added,
            List<String> removed,
            double volumeDeltaPln,
            List<CashOutSignalDto> newCashOutSignals
    ) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.forLanguageTag("pl-PL"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        String deltaPrefix = volumeDeltaPln > 0 ? "+" : "";
        String deltaText = deltaPrefix + nf.format(volumeDeltaPln) + " PLN";

        StringBuilder sb = new StringBuilder();
        sb.append("Porównanie Analizy #").append(fromRun.runNumber())
                .append(" (v").append(fromRun.snapshotStep()).append(") z Analizą #")
                .append(toRun.runNumber()).append(" (v").append(toRun.snapshotStep()).append("): ");

        if (walletRotationDetected) {
            WalletRotationEventDto first = rotations.get(0);
            sb.append("WYKRYTO PODMIANĘ PORTFELA na profilu ").append(first.sourceName())
                    .append(" (").append(first.previousAddress()).append(" -> ").append(first.newAddress()).append("). ");
        } else {
            sb.append("nie odnotowano podmiany adresu na monitorowanych profilach Web2. ");
        }

        sb.append("Nowe adresy: ").append(added.size())
                .append(", usunięte adresy: ").append(removed.size())
                .append(", zmiana wolumenu: ").append(deltaText)
                .append(", nowe sygnały Cash-Out: ").append(newCashOutSignals.size()).append(".");

        return sb.toString();
    }
}
