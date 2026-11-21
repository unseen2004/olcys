package pl.kas.olcys.connector.web3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pl.kas.olcys.api.dto.AnalysisMode;
import pl.kas.olcys.engine.FalsePositiveFirewall;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Web3 connector for Ethereum (EVM), providing deterministic KAS synthetic profiles
 * and live RPC / Blockscout inspection with resilient fallback.
 */
@Component
public class EthereumConnector implements Web3Connector {

    private static final Logger log = LoggerFactory.getLogger(EthereumConnector.class);
    private static final String NETWORK = "ETH";
    private static final String NATIVE_SYMBOL = "ETH";
    private static final String PUBLIC_RPC_URL = "https://ethereum-rpc.publicnode.com";
    private static final String BLOCKSCOUT_ADDR_URL = "https://eth.blockscout.com/api/v2/addresses/";

    private final EntityTagProvider entityTagProvider;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Map<String, RawOnChainProfile> syntheticProfiles;

    @Autowired
    public EthereumConnector(
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
        String lower = trimmed.toLowerCase(Locale.ROOT);
        Optional<EntityTag> knownTag = entityTagProvider.findTag(trimmed, NETWORK);

        if (mode == AnalysisMode.SYNTHETIC || mode == AnalysisMode.HYBRID) {
            RawOnChainProfile synthetic = syntheticProfiles.get(lower);
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

    private RawOnChainProfile inspectLive(String address, String fallbackTag) {
        String bytecode = fetchEthCode(address);
        String explorerTag = fallbackTag;
        int txCount = 0;

        boolean knownSmartContract = FalsePositiveFirewall.isSmartContractBytecode(bytecode);
        if (knownSmartContract && hasText(explorerTag)) {
            return new RawOnChainProfile(
                    address,
                    NETWORK,
                    bytecode,
                    0,
                    0.0,
                    0.0,
                    NATIVE_SYMBOL,
                    0.0,
                    0.0,
                    explorerTag,
                    List.of()
            );
        }

        Optional<String> blockscoutBody = Web3ConnectorSupport.safeGet(
                restClient, BLOCKSCOUT_ADDR_URL + address, log);
        List<RawTransaction> txs = List.of();
        if (blockscoutBody.isPresent()) {
            try {
                JsonNode root = objectMapper.readTree(blockscoutBody.get());
                if (!hasText(explorerTag)) {
                    String name = root.path("name").asText("");
                    if (hasText(name)) {
                        explorerTag = name;
                    } else {
                        explorerTag = extractFirstBlockscoutTag(root);
                    }
                }
                boolean isEip7702 = "eip7702".equalsIgnoreCase(root.path("proxy_type").asText(""))
                        || FalsePositiveFirewall.isEip7702DelegatedEoa(bytecode);
                if (root.path("is_contract").asBoolean(false) && !isEip7702 && "0x".equals(bytecode)) {
                    bytecode = Web3ConnectorSupport.SAMPLE_CONTRACT_BYTECODE;
                }
                int blockscoutTx = parseSafeTxCount(root.path("transactions_count"));
                if (blockscoutTx > 0) {
                    txCount = blockscoutTx;
                }
            } catch (Exception ex) {
                log.debug("Failed to parse Blockscout address JSON for {}: {}", address, ex.getMessage());
            }

            if (!FalsePositiveFirewall.isSmartContractBytecode(bytecode)) {
                if (txCount == 0) {
                    txCount = fetchBlockscoutCountersTxCount(address);
                }
                if (txCount <= FalsePositiveFirewall.CUSTODIAL_TX_THRESHOLD) {
                    txs = fetchBlockscoutTransactions(address);
                }
            }
        }

        if (txCount == 0 && !txs.isEmpty()) {
            txCount = txs.size();
        }
        double inflow = txs.stream().filter(t -> "IN".equals(t.direction())).mapToDouble(RawTransaction::amount).sum();
        double outflow = txs.stream().filter(t -> "OUT".equals(t.direction())).mapToDouble(RawTransaction::amount).sum();
        double volumePln = txs.stream().mapToDouble(RawTransaction::estimatedPln).sum();

        return new RawOnChainProfile(
                address,
                NETWORK,
                bytecode,
                txCount,
                inflow,
                outflow,
                NATIVE_SYMBOL,
                volumePln,
                0.0,
                explorerTag,
                txs
        );
    }

    private int fetchBlockscoutCountersTxCount(String address) {
        Optional<String> countersBody = Web3ConnectorSupport.safeGet(
                restClient, BLOCKSCOUT_ADDR_URL + address + "/counters", log);
        if (countersBody.isEmpty()) {
            return 0;
        }
        try {
            JsonNode root = objectMapper.readTree(countersBody.get());
            return parseSafeTxCount(root.path("transactions_count"));
        } catch (Exception ex) {
            log.debug("Failed to parse Blockscout counters JSON for {}: {}", address, ex.getMessage());
            return 0;
        }
    }

    private static int parseSafeTxCount(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return 0;
        }
        try {
            long val = node.isNumber() ? node.asLong(0L) : Long.parseLong(node.asText("0").trim());
            if (val <= 0L) {
                return 0;
            }
            return val > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) val;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String extractFirstBlockscoutTag(JsonNode root) {
        for (JsonNode tagNode : root.path("public_tags")) {
            String displayName = tagNode.path("display_name").asText("");
            if (hasText(displayName)) {
                return displayName.trim();
            }
            String label = tagNode.path("label").asText("");
            if (hasText(label)) {
                return label.trim();
            }
        }
        for (JsonNode tagNode : root.path("metadata").path("tags")) {
            String name = tagNode.path("name").asText("");
            if (hasText(name)) {
                return name.trim();
            }
        }
        return null;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private String fetchEthCode(String address) {
        String payload = String.format(
                "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getCode\",\"params\":[\"%s\",\"latest\"],\"id\":1}",
                address);
        Optional<String> rpcResp = Web3ConnectorSupport.safePostJson(restClient, PUBLIC_RPC_URL, payload, log);
        if (rpcResp.isPresent()) {
            try {
                String result = objectMapper.readTree(rpcResp.get()).path("result").asText("0x");
                if (!result.isBlank()) {
                    return result;
                }
            } catch (Exception ex) {
                log.debug("Failed to parse eth_getCode JSON for {}: {}", address, ex.getMessage());
            }
        }
        return "0x";
    }

    private List<RawTransaction> fetchBlockscoutTransactions(String address) {
        Optional<String> txBody = Web3ConnectorSupport.safeGet(
                restClient, BLOCKSCOUT_ADDR_URL + address + "/transactions", log);
        if (txBody.isEmpty()) {
            return List.of();
        }
        List<RawTransaction> list = new ArrayList<>();
        try {
            JsonNode items = objectMapper.readTree(txBody.get()).path("items");
            if (!items.isArray()) {
                return List.of();
            }
            for (JsonNode item : items) {
                if (list.size() >= 15) {
                    break;
                }
                String hash = item.path("hash").asText("");
                String from = item.path("from").path("hash").asText("");
                String to = item.path("to").path("hash").asText("");
                String tsRaw = item.path("timestamp").asText("");
                String valueWei = item.path("value").asText("0");
                double ethAmount = parseWeiToEth(valueWei);
                Instant ts = parseInstantOrNow(tsRaw);
                String direction = from.equalsIgnoreCase(address) ? "OUT" : "IN";
                String counterparty = "OUT".equals(direction) ? to : from;
                String hint = entityTagProvider.findTag(counterparty, NETWORK)
                        .map(t -> t.name() + " (" + t.role() + ")")
                        .orElse("Live RPC / Blockscout");

                list.add(new RawTransaction(
                        hash,
                        ts,
                        NETWORK,
                        direction,
                        from,
                        to,
                        ethAmount,
                        NATIVE_SYMBOL,
                        Math.round(ethAmount * 11000.0 * 100.0) / 100.0,
                        hint
                ));
            }
        } catch (Exception ex) {
            log.debug("Failed to parse Blockscout transactions for {}: {}", address, ex.getMessage());
        }
        return List.copyOf(list);
    }

    private static double parseWeiToEth(String wei) {
        try {
            return Double.parseDouble(wei) / 1_000_000_000_000_000_000.0;
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private static Instant parseInstantOrNow(String raw) {
        try {
            return raw == null || raw.isBlank() ? Instant.now() : Instant.parse(raw);
        } catch (Exception ex) {
            return Instant.now();
        }
    }

    private Map<String, RawOnChainProfile> buildSyntheticProfiles() {
        Map<String, RawOnChainProfile> profiles = new HashMap<>();

        // Scenario 1 v1: Jan Kowalski (@krypto_janek) primary ETH wallet
        String janekV1 = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F";
        List<RawTransaction> janekV1Txs = List.of(
                new RawTransaction(
                        "0x1a8f4c92e71b3301d8f4e22a99c04e71b1029384a5b6c7d8e9f0112233445501",
                        Instant.parse("2026-01-18T11:20:00Z"),
                        NETWORK,
                        "IN",
                        "0xD1FF60281Ac1735b3D59Ab0864b3c96421Ca931b",
                        janekV1,
                        4.20,
                        "ETH",
                        46200.0,
                        "Kontrahent B2B / zlecenie IT (niehostowany)"
                ),
                new RawTransaction(
                        "0x2b9e5d83f82c4412e9a5f33b88d15f82c2130495b6c7d8e9f011223344556602",
                        Instant.parse("2026-02-12T09:45:00Z"),
                        NETWORK,
                        "IN",
                        "0x77a7C1643B4332DA572f181E67d1E67F1d7dc0a4",
                        janekV1,
                        3.60,
                        "ETH",
                        39600.0,
                        "Wpłata P2P / klient prywatny"
                ),
                new RawTransaction(
                        "0x3c0f6e94093d5523f0b6044c77e26093d32415a6c7d8e9f01122334455667703",
                        Instant.parse("2026-03-10T13:10:00Z"),
                        NETWORK,
                        "OUT",
                        janekV1,
                        "0xE592427A0AEce92De3Edee1F18E0157C05861564",
                        2.40,
                        "ETH",
                        26400.0,
                        "Uniswap V3: SwapRouter (DEX_ROUTER)"
                ),
                new RawTransaction(
                        "0x4d107fa51a4e663401c7155d66f371a4e43526b7d8e9f0112233445566778804",
                        Instant.parse("2026-03-10T13:14:00Z"),
                        NETWORK,
                        "OUT",
                        janekV1,
                        "0xdAC17F958D2ee523a2206206994597C13D831ec7",
                        6600.0,
                        "USDT",
                        26400.0,
                        "Tether: USDT ERC-20 — wywołanie kontraktu tokena (STABLECOIN)"
                ),
                new RawTransaction(
                        "0x5e2180b62b5f774512d8266e550482b5f54637c8e9f011223344556677889905",
                        Instant.parse("2026-03-12T17:40:00Z"),
                        NETWORK,
                        "OUT",
                        janekV1,
                        "0x28C6c06298d514Db089934071355E5743bf21d60",
                        1.85,
                        "ETH",
                        20350.0,
                        "Binance 14 Hot Wallet (CEX)"
                ),
                new RawTransaction(
                        "0x6f3291c73c60885623e9377f441593c6065748d9f0112233445566778899aa06",
                        Instant.parse("2026-03-13T10:05:00Z"),
                        NETWORK,
                        "OUT",
                        janekV1,
                        "0x28C6c06298d514Db089934071355E5743bf21d60",
                        5500.0,
                        "USDT",
                        22000.0,
                        "Binance 14 Hot Wallet (CEX)"
                ),
                new RawTransaction(
                        "0x7043a2d84d71996734fa48803326a4d7176859ea0112233445566778899aabb07",
                        Instant.parse("2026-03-14T16:22:00Z"),
                        NETWORK,
                        "OUT",
                        janekV1,
                        "0x51A19d7BB5fb31c9b81Ed8Be3350698F362C7B2C",
                        1.40,
                        "ETH",
                        15400.0,
                        "Kantor OTC Warszawa P2P (CASP_P2P) — korelacja z ogłoszeniem #8841"
                ),
                new RawTransaction(
                        "0x8154b3e95e82aa78450b59912237b5e828796afb112233445566778899aabbcc08",
                        Instant.parse("2026-03-18T14:55:00Z"),
                        NETWORK,
                        "OUT",
                        janekV1,
                        "0x2910543Af39abA0Cd09dBb2D50200b3E800A63D2",
                        1.15,
                        "ETH",
                        12650.0,
                        "Kraken Hot Wallet (CEX)"
                )
        );
        profiles.put(janekV1.toLowerCase(Locale.ROOT), new RawOnChainProfile(
                janekV1, NETWORK, "0x", 34, 7.80, 6.80, NATIVE_SYMBOL,
                112400.0, 12100.0, null, janekV1Txs
        ));

        // Scenario 1 v2: Jan Kowalski (@krypto_janek) rotated ETH wallet
        String janekV2 = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045";
        List<RawTransaction> janekV2Txs = List.of(
                new RawTransaction(
                        "0x9265c4fa6f93bb89561c6aa21148c6f9398a7b0c2233445566778899aabbccdd09",
                        Instant.parse("2026-04-08T10:15:00Z"),
                        NETWORK,
                        "IN",
                        "0xA3A65517e8dfF495458f42ED11D0590192A0cdF4",
                        janekV2,
                        5.20,
                        "ETH",
                        57200.0,
                        "Bitomat.com Settlement (CASP_P2P)"
                ),
                new RawTransaction(
                        "0xa376d50b70a4cc9a672d7bb30059d70a4a9b8c1d33445566778899aabbccddee10",
                        Instant.parse("2026-04-09T12:00:00Z"),
                        NETWORK,
                        "IN",
                        "0x0c590c2A7aDbe8eF4bc241f02e85B391a19ADe22",
                        janekV2,
                        3.10,
                        "ETH",
                        34100.0,
                        "Kontrahent prywatny P2P"
                ),
                new RawTransaction(
                        "0xb487e61c81b5ddab783e8cc4ff6ae81b5bac9d2e445566778899aabbccddeeff11",
                        Instant.parse("2026-04-09T18:30:00Z"),
                        NETWORK,
                        "OUT",
                        janekV2,
                        "0xE592427A0AEce92De3Edee1F18E0157C05861564",
                        3.00,
                        "ETH",
                        33000.0,
                        "Uniswap V3: SwapRouter (DEX_ROUTER)"
                ),
                new RawTransaction(
                        "0xc598f72d92c6eebc894f9dd5ee7bf92c6cbd0e3f5566778899aabbccddeeff0012",
                        Instant.parse("2026-04-09T18:33:00Z"),
                        NETWORK,
                        "OUT",
                        janekV2,
                        "0xdAC17F958D2ee523a2206206994597C13D831ec7",
                        8250.0,
                        "USDT",
                        33000.0,
                        "Tether: USDT ERC-20 — wywołanie kontraktu tokena (STABLECOIN)"
                ),
                new RawTransaction(
                        "0xd6a9083ea3d7ffcd9a50aee6dd8ca03d7dce1f4066778899aabbccddeeff001113",
                        Instant.parse("2026-04-10T14:15:00Z"),
                        NETWORK,
                        "OUT",
                        janekV2,
                        "0x9bF4980E11ed803b12740d861E36C3E8733187aD",
                        8250.0,
                        "USDT",
                        33000.0,
                        "Zondacrypto / BitBay PL (CASP_P2P) — korelacja z ogłoszeniem #9120"
                ),
                new RawTransaction(
                        "0xe7ba194fb4e800deab61bff7cc9db14e8edf2051778899aabbccddeeff00112214",
                        Instant.parse("2026-04-11T09:20:00Z"),
                        NETWORK,
                        "OUT",
                        janekV2,
                        "0x28C6c06298d514Db089934071355E5743bf21d60",
                        4.10,
                        "ETH",
                        45100.0,
                        "Binance 14 Hot Wallet (CEX)"
                )
        );
        profiles.put(janekV2.toLowerCase(Locale.ROOT), new RawOnChainProfile(
                janekV2, NETWORK, "0x", 19, 8.30, 7.10, NATIVE_SYMBOL,
                169400.0, 16500.0, null, janekV2Txs
        ));

        // Scenario 2: Marek OTC Reverse-OSINT ETH wallet (triggers all 4 Cash-Out signal types)
        String marekEth = "0xAb5801a7D398351b8bE11C439e05C5B3259aeC9B";
        List<RawTransaction> marekEthTxs = List.of(
                new RawTransaction(
                        "0xf10011223344556677889900aabbccddeeff00112233445566778899aabbcc01",
                        Instant.parse("2026-02-15T10:00:00Z"),
                        NETWORK,
                        "IN",
                        "0xA5f4dE4Fb6AA893aC22A897fd0D13408A9420Cc1",
                        marekEth,
                        12.50,
                        "ETH",
                        137500.0,
                        "Klient OTC Warszawa (niehostowany)"
                ),
                new RawTransaction(
                        "0xf20011223344556677889900aabbccddeeff00112233445566778899aabbcc02",
                        Instant.parse("2026-02-18T14:20:00Z"),
                        NETWORK,
                        "IN",
                        "0xD1FF60281Ac1735b3D59Ab0864b3c96421Ca931b",
                        marekEth,
                        10.00,
                        "ETH",
                        110000.0,
                        "Wpłata OTC (niehostowany)"
                ),
                new RawTransaction(
                        "0xf30011223344556677889900aabbccddeeff00112233445566778899aabbcc03",
                        Instant.parse("2026-02-19T11:05:00Z"),
                        NETWORK,
                        "OUT",
                        marekEth,
                        "0xE592427A0AEce92De3Edee1F18E0157C05861564",
                        8.00,
                        "ETH",
                        88000.0,
                        "Uniswap V3: SwapRouter (DEX_ROUTER)"
                ),
                new RawTransaction(
                        "0xf40011223344556677889900aabbccddeeff00112233445566778899aabbcc04",
                        Instant.parse("2026-02-19T11:08:00Z"),
                        NETWORK,
                        "OUT",
                        marekEth,
                        "0xdAC17F958D2ee523a2206206994597C13D831ec7",
                        22000.0,
                        "USDT",
                        88000.0,
                        "Tether: USDT ERC-20 — wywołanie kontraktu tokena (STABLECOIN)"
                ),
                new RawTransaction(
                        "0xf50011223344556677889900aabbccddeeff00112233445566778899aabbcc05",
                        Instant.parse("2026-02-20T16:00:00Z"),
                        NETWORK,
                        "OUT",
                        marekEth,
                        "0x9bF4980E11ed803b12740d861E36C3E8733187aD",
                        5000.0,
                        "USDT",
                        20000.0,
                        "Zondacrypto / BitBay PL (CASP_P2P) — wypłata cykliczna #1"
                ),
                new RawTransaction(
                        "0xf60011223344556677889900aabbccddeeff00112233445566778899aabbcc06",
                        Instant.parse("2026-02-27T16:15:00Z"),
                        NETWORK,
                        "OUT",
                        marekEth,
                        "0x9bF4980E11ed803b12740d861E36C3E8733187aD",
                        5050.0,
                        "USDT",
                        20200.0,
                        "Zondacrypto / BitBay PL (CASP_P2P) — wypłata cykliczna #2"
                ),
                new RawTransaction(
                        "0xf70011223344556677889900aabbccddeeff00112233445566778899aabbcc07",
                        Instant.parse("2026-03-06T15:50:00Z"),
                        NETWORK,
                        "OUT",
                        marekEth,
                        "0x2910543Af39abA0Cd09dBb2D50200b3E800A63D2",
                        4950.0,
                        "USDT",
                        19800.0,
                        "Kraken Hot Wallet (CEX) — wypłata cykliczna #3"
                ),
                new RawTransaction(
                        "0xf80011223344556677889900aabbccddeeff00112233445566778899aabbcc08",
                        Instant.parse("2026-03-13T16:30:00Z"),
                        NETWORK,
                        "OUT",
                        marekEth,
                        "0x2910543Af39abA0Cd09dBb2D50200b3E800A63D2",
                        5000.0,
                        "USDT",
                        20000.0,
                        "Kraken Hot Wallet (CEX) — wypłata cykliczna #4"
                ),
                new RawTransaction(
                        "0xf90011223344556677889900aabbccddeeff00112233445566778899aabbcc09",
                        Instant.parse("2026-03-20T18:30:00Z"),
                        NETWORK,
                        "OUT",
                        marekEth,
                        "0x63b94e6bFe075e82f4cbd4347484386ddC382E6c",
                        3.50,
                        "ETH",
                        38500.0,
                        "Ramp Network (CASP_P2P) — korelacja Telegram OTC 2026-03-20"
                )
        );
        profiles.put(marekEth.toLowerCase(Locale.ROOT), new RawOnChainProfile(
                marekEth, NETWORK, "0x", 64, 22.50, 19.80, NATIVE_SYMBOL,
                312000.0, 42000.0, null, marekEthTxs
        ));

        // Scenario 3: @lucky_drop_2026 giveaway noise address (minimal non-cashout EOA)
        String noiseEth = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";
        List<RawTransaction> noiseTxs = List.of(
                new RawTransaction(
                        "0x0a11223344556677889900aabbccddeeff11223344556677889900aabbccdd01",
                        Instant.parse("2026-02-01T12:00:00Z"),
                        NETWORK,
                        "IN",
                        "0x77a7C1643B4332DA572f181E67d1E67F1d7dc0a4",
                        noiseEth,
                        0.015,
                        "ETH",
                        165.0,
                        "Faucet / Giveaway airdrop"
                )
        );
        profiles.put(noiseEth.toLowerCase(Locale.ROOT), new RawOnChainProfile(
                noiseEth, NETWORK, "0x", 4, 0.015, 0.0, NATIVE_SYMBOL,
                165.0, 0.0, null, noiseTxs
        ));

        return Map.copyOf(profiles);
    }
}
