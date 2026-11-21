package pl.kas.olcys.api.dto;

import java.util.List;

public record ReportComputationZoneDto(
        String methodologySummaryPl,
        List<AddressFindingDto> confirmedWallets,
        List<AddressFindingDto> rejectedFalsePositives,
        double totalEstimatedVolumePln,
        double totalStablecoinVolumeUsd,
        int totalTransactionsAnalyzed,
        List<TimelineEventDto> chronologicalFlow
) {
}
