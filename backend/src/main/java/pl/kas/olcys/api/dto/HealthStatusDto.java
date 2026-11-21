package pl.kas.olcys.api.dto;

import java.util.List;

public record HealthStatusDto(
        String status,
        String service,
        String javaVersion,
        boolean virtualThreadsEnabled,
        String databaseStatus,
        List<String> web2Connectors,
        List<String> web3Connectors,
        String aiProvider,
        String aiModel,
        boolean externalDataTransfer
) {
}
