package pl.kas.olcys.api.dto;

import java.util.List;

public record TriggerRunRequest(
        AnalysisMode mode,
        List<String> sources,
        List<String> networks,
        Integer snapshotStep
) {
}
