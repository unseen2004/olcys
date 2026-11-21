package pl.kas.olcys.api.dto;

import java.util.List;

public record OnChainMetricsDto(
        String address,
        String network,
        boolean isSmartContract,
        String bytecodePrefix,
        int txCount,
        double totalInflowNative,
        double totalOutflowNative,
        String nativeSymbol,
        double estimatedTotalVolumePln,
        double stablecoinVolumeUsd,
        List<String> interactedEntities,
        List<TimelineEventDto> timeline
) {
}
