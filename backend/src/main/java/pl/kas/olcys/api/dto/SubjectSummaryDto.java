package pl.kas.olcys.api.dto;

import java.time.Instant;
import java.util.List;

public record SubjectSummaryDto(
        String id,
        String displayName,
        List<IdentifierDto> identifiers,
        Instant createdAt,
        List<RunSummaryDto> runs
) {
}
