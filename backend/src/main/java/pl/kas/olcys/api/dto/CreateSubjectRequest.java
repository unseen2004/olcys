package pl.kas.olcys.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateSubjectRequest(
        @NotBlank String displayName,
        @NotEmpty @Valid List<IdentifierDto> identifiers,
        AnalysisMode mode,
        List<String> sources,
        List<String> networks,
        Integer snapshotStep
) {
}
