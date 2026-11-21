package pl.kas.olcys.api.dto;

import java.util.List;

public record AddressFindingDto(
        String id,
        String address,
        String network,
        String checksumFormat,
        boolean checksumValid,
        int confidenceScore,
        String confidenceLevelPl,
        boolean falsePositive,
        String rejectionReason,
        String rejectionCategory,
        String entityTag,
        boolean interactionRecorded,
        List<ScoreBreakdownItemDto> scoreBreakdown,
        List<String> evidenceIds,
        OnChainMetricsDto onChainMetrics
) {
}
