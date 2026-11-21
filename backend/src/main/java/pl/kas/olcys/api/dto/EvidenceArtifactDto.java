package pl.kas.olcys.api.dto;

import java.time.Instant;
import java.util.List;

public record EvidenceArtifactDto(
        String id,
        String sourceName,
        String sourceCategory,
        String url,
        Instant fetchedAt,
        String sha256,
        String snippet,
        String matchedIdentifier,
        List<String> extractedAddresses,
        String contextClassification
) {
}
