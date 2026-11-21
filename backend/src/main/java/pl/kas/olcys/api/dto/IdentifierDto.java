package pl.kas.olcys.api.dto;

import jakarta.validation.constraints.NotBlank;

public record IdentifierDto(
        @NotBlank String type,
        @NotBlank String value
) {
}
