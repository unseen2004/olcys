package pl.kas.olcys.api.dto;

import java.time.Instant;
import java.util.List;

public record ReportSourceFactDto(
        String evidenceId,
        String sourceName,
        String url,
        Instant timestampUtc,
        String sha256,
        String matchedIdentifier,
        String quoteSnippet,
        List<String> detectedAddresses
) {
}
