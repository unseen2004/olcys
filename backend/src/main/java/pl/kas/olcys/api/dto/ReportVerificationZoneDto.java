package pl.kas.olcys.api.dto;

import java.util.List;

public record ReportVerificationZoneDto(
        List<CashOutSignalDto> cashOutSignals,
        List<String> pit38RiskIndicatorsPl,
        List<String> investigativeRecommendationsPl,
        List<String> limitationsAndLegalDisclaimerPl
) {
}
