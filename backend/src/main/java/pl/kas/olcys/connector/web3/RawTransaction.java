package pl.kas.olcys.connector.web3;

import java.time.Instant;

public record RawTransaction(
        String txHash,
        Instant timestamp,
        String network,
        String direction,
        String fromAddress,
        String toAddress,
        double amount,
        String asset,
        double estimatedPln,
        String counterpartyHint
) {
}
