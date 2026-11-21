package pl.kas.olcys.api.dto;

import java.time.Instant;

public record CashOutSignalDto(
        String id,
        String signalType,
        String severity,
        String address,
        String network,
        String txHash,
        Instant timestamp,
        double estimatedPln,
        String counterpartyName,
        String descriptionPl,
        String verificationHintPl
) {
}
