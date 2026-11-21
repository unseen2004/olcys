package pl.kas.olcys.api.dto;

import java.time.Instant;

public record WalletRotationEventDto(
        String sourceName,
        String sourceUrl,
        String previousAddress,
        String newAddress,
        String network,
        Instant previousSeenAt,
        Instant newSeenAt,
        String alertPl
) {
}
