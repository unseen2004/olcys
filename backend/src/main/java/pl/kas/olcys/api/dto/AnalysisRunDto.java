package pl.kas.olcys.api.dto;

import java.time.Instant;
import java.util.List;

public record AnalysisRunDto(
        String runId,
        String subjectId,
        String subjectDisplayName,
        int runNumber,
        int snapshotStep,
        AnalysisMode mode,
        Instant createdAt,
        long durationMs,
        List<IdentifierDto> queriedIdentifiers,
        List<String> activeSources,
        List<String> activeNetworks,
        List<EvidenceArtifactDto> evidences,
        List<AddressFindingDto> confirmedAddresses,
        List<AddressFindingDto> rejectedAddresses,
        List<CashOutSignalDto> cashOutSignals,
        EvidenceGraphDto graph,
        OsintReportDto report
) {
}
