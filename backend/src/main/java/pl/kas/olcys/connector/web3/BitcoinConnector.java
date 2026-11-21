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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Web3 connector for Bitcoin (BTC), providing deterministic KAS synthetic profiles
 * and live Mempool.space / Blockstream inspection with resilient fallback.
 */
@Component
public class BitcoinConnector implements Web3Connector {

    private static final Logger log = LoggerFactory.getLogger(BitcoinConnector.class);
    private static final String NETWORK = "BTC";
    private static final String NATIVE_SYMBOL = "BTC";
    private static final double SATOSHIS_PER_BTC = 100_000_000.0;
    private static final double APPROX_BTC_PLN = 360_000.0;

    private final EntityTagProvider entityTagProvider;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Map<String, RawOnChainProfile> syntheticProfiles;

    @Autowired
    public BitcoinConnector(
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
        String mempoolUrl = "https://mempool.space/api/address/" + address;
        String blockstreamUrl = "https://blockstream.info/api/address/" + address;

        Optional<String> statsBody = Web3ConnectorSupport.safeGet(restClient, mempoolUrl, log);
        boolean usedMempool = statsBody.isPresent();
        if (statsBody.isEmpty()) {
            statsBody = Web3ConnectorSupport.safeGet(restClient, blockstreamUrl, log);
        }
        if (statsBody.isEmpty()) {
            return Web3ConnectorSupport.emptyEoaProfile(address, NETWORK, NATIVE_SYMBOL, explorerTag);
        }

        try {
            JsonNode stats = objectMapper.readTree(statsBody.get()).path("chain_stats");
            int txCount = stats.path("tx_count").asInt(0);
            double fundedBtc = stats.path("funded_txo_sum").asDouble(0.0) / SATOSHIS_PER_BTC;
            double spentBtc = stats.path("spent_txo_sum").asDouble(0.0) / SATOSHIS_PER_BTC;
            double volumePln = Math.round((fundedBtc + spentBtc) * APPROX_BTC_PLN * 100.0) / 100.0;

            String txsUrl = (usedMempool ? mempoolUrl : blockstreamUrl) + "/txs";
            List<RawTransaction> txs = txCount > 0 ? fetchLiveTransactions(address, txsUrl) : List.of();

            return new RawOnChainProfile(
                    address,
                    NETWORK,
                    "0x",
                    txCount,
                    fundedBtc,
                    spentBtc,
                    NATIVE_SYMBOL,
                    volumePln,
                    0.0,
                    explorerTag,
                    txs
            );
        } catch (Exception ex) {
            log.debug("Failed to parse Bitcoin address stats for {}: {}", address, ex.getMessage());
            return Web3ConnectorSupport.emptyEoaProfile(address, NETWORK, NATIVE_SYMBOL, explorerTag);
        }
    }

    private List<RawTransaction> fetchLiveTransactions(String address, String txsUrl) {
        Optional<String> body = Web3ConnectorSupport.safeGet(restClient, txsUrl, log);
        if (body.isEmpty()) {
            return List.of();
        }
        List<RawTransaction> list = new ArrayList<>();
        try {
            JsonNode txs = objectMapper.readTree(body.get());
            if (!txs.isArray()) {
                return List.of();
            }
            for (JsonNode tx : txs) {
                if (list.size() >= 10) {
                    break;
                }
                String txid = tx.path("txid").asText("");
                long blockTime = tx.path("status").path("block_time").asLong(0L);
                Instant ts = blockTime > 0 ? Instant.ofEpochSecond(blockTime) : Instant.now();

                double receivedSats = 0.0;
                String targetAddr = address;
                for (JsonNode vout : tx.path("vout")) {
                    String outAddr = vout.path("scriptpubkey_address").asText("");
                    if (address.equals(outAddr)) {
                        receivedSats += vout.path("value").asDouble(0.0);
                    } else if (!outAddr.isBlank() && targetAddr.equals(address)) {
                        targetAddr = outAddr;
                    }
                }
                double sentSats = 0.0;
                String sourceAddr = address;
                for (JsonNode vin : tx.path("vin")) {
                    String prevAddr = vin.path("prevout").path("scriptpubkey_address").asText("");
                    if (address.equals(prevAddr)) {
                        sentSats += vin.path("prevout").path("value").asDouble(0.0);
                    } else if (!prevAddr.isBlank() && sourceAddr.equals(address)) {
                        sourceAddr = prevAddr;
                    }
                }

                boolean isOut = sentSats > 0;
                double btcAmount = (isOut ? sentSats : receivedSats) / SATOSHIS_PER_BTC;
                String counterparty = isOut ? targetAddr : sourceAddr;
                String hint = entityTagProvider.findTag(counterparty, NETWORK)
                        .map(t -> t.name() + " (" + t.role() + ")")
                        .orElse("Live Mempool.space");

                list.add(new RawTransaction(
                        txid,
                        ts,
                        NETWORK,
                        isOut ? "OUT" : "IN",
                        isOut ? address : sourceAddr,
                        isOut ? targetAddr : address,
                        btcAmount,
                        NATIVE_SYMBOL,
                        Math.round(btcAmount * APPROX_BTC_PLN * 100.0) / 100.0,
                        hint
                ));
            }
        } catch (Exception ex) {
            log.debug("Failed to parse Bitcoin transactions for {}: {}", address, ex.getMessage());
        }
        return List.copyOf(list);
    }

    private Map<String, RawOnChainProfile> buildSyntheticProfiles() {
        Map<String, RawOnChainProfile> profiles = new HashMap<>();

        // Scenario 1: Jan Kowalski (@krypto_janek) BTC SegWit wallet from WaybackArchive
        String janekBtc = "bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq";
        List<RawTransaction> txs = List.of(
                new RawTransaction(
                        "b10011223344556677889900aabbccddeeff00112233445566778899aabb0001",
                        Instant.parse("2026-01-22T10:30:00Z"),
                        NETWORK,
                        "IN",
                        "1NCMJD3uNz4wumbNfoezEjcJLf3JazARP7",
                        janekBtc,
                        0.120,
                        NATIVE_SYMBOL,
                        43200.0,
                        "Zlecenie B2B / faktura IT"
                ),
                new RawTransaction(
                        "b20011223344556677889900aabbccddeeff00112233445566778899aabb0002",
                        Instant.parse("2026-02-08T14:10:00Z"),
                        NETWORK,
                        "IN",
                        "15DmTTWRspsNAzhnWraexH4eTmyyFjHdB2",
                        janekBtc,
                        0.090,
                        NATIVE_SYMBOL,
                        32400.0,
                        "Rozliczenie programistyczne B2B"
                ),
                new RawTransaction(
                        "b30011223344556677889900aabbccddeeff00112233445566778899aabb0003",
                        Instant.parse("2026-02-15T16:45:00Z"),
                        NETWORK,
                        "OUT",
                        janekBtc,
                        "bc1qm34lsc65zpw79lxes69zkqmk6ee3ewf0j77s3h",
                        0.110,
                        NATIVE_SYMBOL,
                        39600.0,
                        "Binance BTC Hot Wallet (CEX)"
                ),
                new RawTransaction(
                        "b40011223344556677889900aabbccddeeff00112233445566778899aabb0004",
                        Instant.parse("2026-03-04T11:20:00Z"),
                        NETWORK,
                        "OUT",
                        janekBtc,
                        "1CE9RRmFnhrXfG7ULF5YYkgDybqKd9pVV2",
                        0.045,
                        NATIVE_SYMBOL,
                        16200.0,
                        "Zondacrypto / BitBay BTC (CASP_P2P)"
                ),
                new RawTransaction(
                        "b50011223344556677889900aabbccddeeff00112233445566778899aabb0005",
                        Instant.parse("2026-03-19T13:50:00Z"),
                        NETWORK,
                        "OUT",
                        janekBtc,
                        "1Hwi8WuSRK5sDAWS2iU2u5g8fWNoLB52tW",
                        0.023,
                        NATIVE_SYMBOL,
                        8400.0,
                        "Bitomat.com Settlement BTC (CASP_P2P)"
                )
        );
        profiles.put(janekBtc, new RawOnChainProfile(
                janekBtc, NETWORK, "0x", 16, 0.210, 0.178, NATIVE_SYMBOL,
                64200.0, 0.0, null, txs
        ));

        return Map.copyOf(profiles);
    }
}
