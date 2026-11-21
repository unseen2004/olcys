package pl.kas.olcys.connector.web2;

import java.time.Instant;

public record RawEvidence(
        String sourceName,
        String sourceCategory,
        String url,
        Instant fetchedAt,
        String sha256,
        String snippet,
        String matchedIdentifier,
        String contextHint
) {
}
