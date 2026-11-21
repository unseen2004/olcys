package pl.kas.olcys.api.dto;

import java.util.Map;

public record GraphNodeDto(
        String id,
        String type,
        String label,
        String sublabel,
        Integer confidenceScore,
        Boolean falsePositive,
        Map<String, Object> metadata
) {
}
