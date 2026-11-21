package pl.kas.olcys.connector.web3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pl.kas.olcys.api.dto.AnalysisMode;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Web3 connector for TRON (TRX / TRC-20), providing deterministic KAS synthetic profiles
 * and safe live TronScan inspection fallback.
 */
@Component
public class TronConnector implements Web3Connector {

    private static final Logger log = LoggerFactory.getLogger(TronConnector.class);
    private static final String NETWORK = "TRON";
    private static final String NATIVE_SYMBOL = "TRX";
    private static final String TRONSCAN_ACCOUNT_URL = "https://apilist.tronscanapi.com/api/accountv2?address=";

    private final EntityTagProvider entityTagProvider;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Map<String, RawOnChainProfile> syntheticProfiles;

    @Autowired
    public TronConnector(
            EntityTagProvider entityTagProvider,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.entityTagProvider = entityTagProvider;
        this.restClient = restClientBuilder.clone().build();
        this.objectMapper = objectMapper;
        this.syntheticProfiles = buildSyntheticProfiles();
    }

    @Override
    public String network() {
        return NETWORK;
    }

    @Override
    public RawOnChainProfile inspectAddress(String address, AnalysisMode mode, int snapshotStep) {
        if (address == null || address.isBlank()) {
            return Web3ConnectorSupport.emptyEoaProfile("", NETWORK, NATIVE_SYMBOL, null);
        }
        String trimmed = address.trim();
        Optional<EntityTag> knownTag = entityTagProvider.findTag(trimmed, NETWORK);

        if (mode == AnalysisMode.SYNTHETIC || mode == AnalysisMode.HYBRID) {
            RawOnChainProfile synthetic = syntheticProfiles.get(trimmed);
            if (synthetic != null) {
                return synthetic;
            }
            if (knownTag.isPresent()) {
                return Web3ConnectorSupport.profileFromEntityTag(trimmed, NETWORK, NATIVE_SYMBOL, knownTag.get());
            }
            if (mode == AnalysisMode.SYNTHETIC) {
                return Web3ConnectorSupport.emptyEoaProfile(trimmed, NETWORK, NATIVE_SYMBOL, null);
            }
        }

        return inspectLive(trimmed, knownTag.map(EntityTag::name).orElse(null));
    }

    private RawOnChainProfile inspectLive(String address, String explorerTag) {
        Optional<String> accountBody = Web3ConnectorSupport.safeGet(
                restClient, TRONSCAN_ACCOUNT_URL + address, log);
        if (accountBody.isEmpty()) {
            return Web3ConnectorSupport.emptyEoaProfile(address, NETWORK, NATIVE_SYMBOL, explorerTag);
        }

        try {
            JsonNode root = objectMapper.readTree(accountBody.get());
            int txCount = root.path("totalTransactionCount").asInt(0);
            int accountType = root.path("accountType").asInt(0);
            String bytecode = accountType == 2 ? Web3ConnectorSupport.SAMPLE_CONTRACT_BYTECODE : "0x";
            double balanceTrx = root.path("balance").asDouble(0.0) / 1_000_000.0;
            String tag = explorerTag;
            String accountTag = root.path("publicTag").asText("");
            if (!accountTag.isBlank() && (tag == null || tag.isBlank())) {
                tag = accountTag;
            }

            return new RawOnChainProfile(
                    address,
                    NETWORK,
                    bytecode,
                    txCount,
                    balanceTrx,
                    0.0,
                    NATIVE_SYMBOL,
                    Math.round(balanceTrx * 0.9 * 100.0) / 100.0,
                    0.0,
                    tag,
                    List.of()
            );
        } catch (Exception ex) {
            log.debug("Failed to parse TronScan account JSON for {}: {}", address, ex.getMessage());
            return Web3ConnectorSupport.emptyEoaProfile(address, NETWORK, NATIVE_SYMBOL, explorerTag);
        }
    }

    private Map<String, RawOnChainProfile> buildSyntheticProfiles() {
        Map<String, RawOnChainProfile> profiles = new HashMap<>();

        // Scenario 2: Marek OTC private TRON wallet (USDT TRC-20 settlements + recurring Binance/Zondacrypto off-ramp)
        String marekTron = "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb";
        List<RawTransaction> txs = List.of(
                new RawTransaction(
                        "t10011223344556677889900aabbccddeeff00112233445566778899aabb0001",
                        Instant.parse("2026-02-20T09:15:00Z"),
                        NETWORK,
                        "IN",
                        "TRhavTDKTDkXU9nj3tNG3MP21wQcphCJEF",
                        marekTron,
                        18500.0,
                        "USDT",
                        74000.0,
                        "Klient OTC — wpłata USDT TRC-20"
                ),
                new RawTransaction(
                        "t20011223344556677889900aabbccddeeff00112233445566778899aabb0002",
                        Instant.parse("2026-02-22T14:40:00Z"),
                        NETWORK,
                        "IN",
                        "TSnbbn9gxvXWk324ETHu2eBQK5im1NtQnT",
                        marekTron,
                        9200.0,
                        "USDT",
                        36800.0,
                        "Rozliczenie OTC USDT TRC-20"
                ),
                new RawTransaction(
                        "t30011223344556677889900aabbccddeeff00112233445566778899aabb0003",
                        Instant.parse("2026-02-23T11:05:00Z"),
                        NETWORK,
                        "OUT",
                        marekTron,
                        "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t",
                        14500.0,
                        "USDT",
                        58000.0,
                        "Tether: USDT TRC-20 (STABLECOIN)"
                ),
                new RawTransaction(
                        "t40011223344556677889900aabbccddeeff00112233445566778899aabb0004",
                        Instant.parse("2026-02-24T17:10:00Z"),
                        NETWORK,
                        "OUT",
                        marekTron,
                        "TNXoiAJ3dct8Fjg4M9fkLFh9S2v9TXc32G",
                        4900.0,
                        "USDT",
                        19600.0,
                        "Binance TRON Hot Wallet (CEX) — wypłata cykliczna #1"
                ),
                new RawTransaction(
                        "t50011223344556677889900aabbccddeeff00112233445566778899aabb0005",
                        Instant.parse("2026-03-03T17:20:00Z"),
                        NETWORK,
                        "OUT",
                        marekTron,
                        "TNXoiAJ3dct8Fjg4M9fkLFh9S2v9TXc32G",
                        4950.0,
                        "USDT",
                        19800.0,
                        "Binance TRON Hot Wallet (CEX) — wypłata cykliczna #2"
                ),
                new RawTransaction(
                        "t60011223344556677889900aabbccddeeff00112233445566778899aabb0006",
                        Instant.parse("2026-03-10T17:05:00Z"),
                        NETWORK,
                        "OUT",
                        marekTron,
                        "TNXoiAJ3dct8Fjg4M9fkLFh9S2v9TXc32G",
                        4850.0,
                        "USDT",
                        19400.0,
                        "Binance TRON Hot Wallet (CEX) — wypłata cykliczna #3"
                ),
                new RawTransaction(
                        "t70011223344556677889900aabbccddeeff00112233445566778899aabb0007",
                        Instant.parse("2026-03-17T16:40:00Z"),
                        NETWORK,
                        "OUT",
                        marekTron,
                        "TTzV657uYDkNohUj6sz1gAhKUMc2ENRhBk",
                        4800.0,
                        "USDT",
                        19200.0,
                        "Zondacrypto / BitBay TRON (CASP_P2P)"
                ),
                new RawTransaction(
                        "t80011223344556677889900aabbccddeeff00112233445566778899aabb0008",
                        Instant.parse("2026-03-20T19:10:00Z"),
                        NETWORK,
                        "OUT",
                        marekTron,
                        "TN8i1KPpSP3PBY7CnwhENxDqbQ35M8gxsh",
                        3200.0,
                        "USDT",
                        12800.0,
                        "Kantor OTC Warszawa P2P TRON (CASP_P2P) — korelacja Telegram OTC"
                )
        );
        profiles.put(marekTron, new RawOnChainProfile(
                marekTron, NETWORK, "0x", 42, 18500.0, 14200.0, NATIVE_SYMBOL,
                168400.0, 42100.0, null, txs
        ));

        return Map.copyOf(profiles);
    }
}
