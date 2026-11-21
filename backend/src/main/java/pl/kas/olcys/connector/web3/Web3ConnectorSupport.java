package pl.kas.olcys.connector.web3;

import org.slf4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

/**
 * Shared utilities for Web3 blockchain connectors (tagged profiles, empty EOA fallbacks, safe HTTP calls).
 */
final class Web3ConnectorSupport {

    static final String SAMPLE_CONTRACT_BYTECODE =
            "0x608060405234801561001057600080fd5b50600436106100a95760003560e01c8063";

    private Web3ConnectorSupport() {
    }

    static RawOnChainProfile profileFromEntityTag(String address, String network, String nativeSymbol, EntityTag tag) {
        boolean smartContract = switch (tag.role()) {
            case DEX_ROUTER, STABLECOIN, MIXER -> true;
            case CEX, CASP_P2P, CHARITY, BURN, PERSONAL_UNHOSTED -> false;
        };
        int txCount = switch (tag.role()) {
            case CEX -> 450_000;
            case STABLECOIN -> 950_000;
            case DEX_ROUTER -> 620_000;
            case MIXER -> 145_000;
            case CASP_P2P -> 48_000;
            case BURN -> 820_000;
            case CHARITY -> 14_500;
            case PERSONAL_UNHOSTED -> 25;
        };
        String bytecode = smartContract ? SAMPLE_CONTRACT_BYTECODE : "0x";
        return new RawOnChainProfile(
                address,
                network,
                bytecode,
                txCount,
                0.0,
                0.0,
                nativeSymbol,
                0.0,
                0.0,
                tag.name(),
                List.of()
        );
    }

    static RawOnChainProfile emptyEoaProfile(String address, String network, String nativeSymbol, String explorerTag) {
        return new RawOnChainProfile(
                address,
                network,
                "0x",
                0,
                0.0,
                0.0,
                nativeSymbol,
                0.0,
                0.0,
                explorerTag,
                List.of()
        );
    }

    static Optional<String> safeGet(RestClient restClient, String url, Logger log) {
        try {
            String body = restClient.get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(body);
        } catch (Exception ex) {
            log.debug("Web3 GET request failed for {}: {}", url, ex.getMessage());
            return Optional.empty();
        }
    }

    static Optional<String> safePostJson(RestClient restClient, String url, String jsonBody, Logger log) {
        try {
            String body = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(body);
        } catch (Exception ex) {
            log.debug("Web3 POST request failed for {}: {}", url, ex.getMessage());
            return Optional.empty();
        }
    }
}
