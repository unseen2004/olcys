package pl.kas.olcys.api.dto;

import java.util.List;

public record ScoreBreakdownItemDto(
        String ruleId,
        String label,
        int delta,
        List<String> evidenceIds,
        String explanationPl
) {
}
