package pl.kas.olcys.api.dto;

import java.util.List;

public record ScenarioPresetDto(
        String id,
        String titlePl,
        String subtitlePl,
        String descriptionPl,
        String expectedOutcomePl,
        String displayName,
        List<IdentifierDto> identifiers,
        AnalysisMode recommendedMode,
        List<String> sources,
        List<String> networks,
        boolean supportsWalletRotationDemo
) {
}
