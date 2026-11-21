package pl.kas.olcys.api.dto;

import java.time.Instant;

public record RunSummaryDto(
        String runId,
        int runNumber,
        int snapshotStep,
        AnalysisMode mode,
        Instant createdAt,
        int validatedAddressCount,
        int rejectedFpCount,
        int maxConfidenceScore,
        int cashOutSignalCount,
        double totalVolumePln
) {
}
