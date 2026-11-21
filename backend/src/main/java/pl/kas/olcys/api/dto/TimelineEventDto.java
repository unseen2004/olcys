package pl.kas.olcys.api.dto;

import java.time.Instant;

public record TimelineEventDto(
        String txHash,
        Instant timestamp,
        String network,
        String direction,
        String fromAddress,
        String toAddress,
        double amount,
        String asset,
        double estimatedPln,
        String counterpartyTag,
        String counterpartyRole,
        String cashOutSignalType,
        String descriptionPl
) {
}
