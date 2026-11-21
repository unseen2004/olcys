package pl.kas.olcys.api.dto;

import java.time.Instant;
import java.util.List;

public record OsintReportDto(
        String executiveSummaryPl,
        String aiProviderUsed,
        Instant generatedAt,
        List<ReportSourceFactDto> zone1SourceFacts,
        ReportComputationZoneDto zone2Computations,
        ReportVerificationZoneDto zone3VerificationConclusions
) {
}
