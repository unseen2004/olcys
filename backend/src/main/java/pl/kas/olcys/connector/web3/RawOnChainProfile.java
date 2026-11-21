package pl.kas.olcys.connector.web3;

import java.util.List;

public record RawOnChainProfile(
        String address,
        String network,
        String bytecode,
        int txCount,
        double totalInflowNative,
        double totalOutflowNative,
        String nativeSymbol,
        double estimatedTotalVolumePln,
        double stablecoinVolumeUsd,
        String explorerTag,
        List<RawTransaction> transactions
) {
}
