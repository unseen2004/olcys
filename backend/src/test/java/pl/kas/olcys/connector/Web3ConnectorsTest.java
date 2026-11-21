package pl.kas.olcys.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.kas.olcys.api.dto.AnalysisMode;
import pl.kas.olcys.api.dto.IdentifierDto;
import pl.kas.olcys.connector.web2.EnsConnector;
import pl.kas.olcys.connector.web2.GithubConnector;
import pl.kas.olcys.connector.web2.KeybaseConnector;
import pl.kas.olcys.connector.web2.RawEvidence;
import pl.kas.olcys.connector.web2.RedditConnector;
import pl.kas.olcys.connector.web2.WaybackConnector;
import pl.kas.olcys.connector.web3.BitcoinConnector;
import pl.kas.olcys.connector.web3.EntityTagRegistry;
import pl.kas.olcys.connector.web3.EthereumConnector;
import pl.kas.olcys.connector.web3.RawOnChainProfile;
import pl.kas.olcys.connector.web3.RawTransaction;
import pl.kas.olcys.connector.web3.TronConnector;
import pl.kas.olcys.crypto.CryptoAddressValidator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class Web3ConnectorsTest {

    private EntityTagRegistry tagRegistry;
    private EthereumConnector ethereumConnector;
    private BitcoinConnector bitcoinConnector;
    private TronConnector tronConnector;
    private CryptoAddressValidator validator;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        tagRegistry = new EntityTagRegistry(mapper);
        tagRegistry.init();

        RestClient.Builder builder = RestClient.builder();
        ethereumConnector = new EthereumConnector(tagRegistry, builder, mapper);
        bitcoinConnector = new BitcoinConnector(tagRegistry, builder, mapper);
        tronConnector = new TronConnector(tagRegistry, builder, mapper);
        validator = new CryptoAddressValidator();
    }

    @Test
    void shouldDetectSmartContractsAndKnownExchangesInEntityRegistry() {
        assertThat(ethereumConnector.network()).isEqualTo("ETH");
        assertThat(bitcoinConnector.network()).isEqualTo("BTC");
        assertThat(tronConnector.network()).isEqualTo("TRON");

        // Smart contracts must return non-0x bytecode and high txCount
        RawOnChainProfile uniswapV3 = ethereumConnector.inspectAddress(
                "0xE592427A0AEce92De3Edee1F18E0157C05861564", AnalysisMode.HYBRID, 1);
        assertThat(uniswapV3.bytecode()).isNotEqualTo("0x").startsWith("0x60");
        assertThat(uniswapV3.txCount()).isGreaterThan(10_000);
        assertThat(uniswapV3.explorerTag()).contains("Uniswap");

        RawOnChainProfile tornado = ethereumConnector.inspectAddress(
                "0xd90e2f925DA726b50C4Ed8D0Fb90Ad053324F31b", AnalysisMode.SYNTHETIC, 1);
        assertThat(tornado.bytecode()).isNotEqualTo("0x");
        assertThat(tornado.txCount()).isGreaterThan(10_000);

        RawOnChainProfile usdtTron = tronConnector.inspectAddress(
                "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", AnalysisMode.SYNTHETIC, 1);
        assertThat(usdtTron.bytecode()).isNotEqualTo("0x");
        assertThat(usdtTron.txCount()).isGreaterThan(10_000);

        // CEX hot wallets must have EOA bytecode "0x", txCount > 100000 and explorerTag
        RawOnChainProfile binanceEth = ethereumConnector.inspectAddress(
                "0x28c6c06298d514db089934071355e5743bf21d60", AnalysisMode.SYNTHETIC, 1);
        assertThat(binanceEth.bytecode()).isEqualTo("0x");
        assertThat(binanceEth.txCount()).isGreaterThan(100_000);
        assertThat(binanceEth.explorerTag()).contains("Binance");

        RawOnChainProfile binanceBtc = bitcoinConnector.inspectAddress(
                "bc1qm34lsc65zpw79lxes69zkqmk6ee3ewf0j77s3h", AnalysisMode.SYNTHETIC, 1);
        assertThat(binanceBtc.bytecode()).isEqualTo("0x");
        assertThat(binanceBtc.txCount()).isGreaterThan(100_000);
        assertThat(binanceBtc.explorerTag()).contains("Binance");
    }

    @Test
    void shouldProvideRichScenarioWalletsWithValidCounterpartyAddresses() {
        // Scenario 1 v1 ETH
        RawOnChainProfile janekV1 = ethereumConnector.inspectAddress(
                "0x71C7656EC7ab88b098defB751B7401B5f6d8976F", AnalysisMode.HYBRID, 1);
        assertThat(janekV1.bytecode()).isEqualTo("0x");
        assertThat(janekV1.txCount()).isLessThan(10_000);
        assertThat(janekV1.stablecoinVolumeUsd()).isGreaterThan(0);
        assertThat(janekV1.transactions()).extracting(RawTransaction::toAddress).contains(
                "0x28C6c06298d514Db089934071355E5743bf21d60",
                "0xE592427A0AEce92De3Edee1F18E0157C05861564",
                "0xdAC17F958D2ee523a2206206994597C13D831ec7"
        );
        assertAllTransactionAddressesValid(janekV1);

        // Scenario 1 v2 ETH (higher volume than v1 for positive RunDiff)
        RawOnChainProfile janekV2 = ethereumConnector.inspectAddress(
                "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045", AnalysisMode.HYBRID, 2);
        assertThat(janekV2.bytecode()).isEqualTo("0x");
        assertThat(janekV2.estimatedTotalVolumePln()).isGreaterThan(janekV1.estimatedTotalVolumePln());
        assertAllTransactionAddressesValid(janekV2);

        // Scenario 1 BTC
        RawOnChainProfile janekBtc = bitcoinConnector.inspectAddress(
                "bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq", AnalysisMode.HYBRID, 1);
        assertThat(janekBtc.bytecode()).isEqualTo("0x");
        assertThat(janekBtc.transactions()).extracting(RawTransaction::toAddress).contains(
                "bc1qm34lsc65zpw79lxes69zkqmk6ee3ewf0j77s3h",
                "1CE9RRmFnhrXfG7ULF5YYkgDybqKd9pVV2"
        );
        assertAllTransactionAddressesValid(janekBtc);

        // Scenario 2 ETH & TRON
        RawOnChainProfile marekEth = ethereumConnector.inspectAddress(
                "0xAb5801a7D398351b8bE11C439e05C5B3259aeC9B", AnalysisMode.SYNTHETIC, 1);
        assertThat(marekEth.transactions().size()).isGreaterThanOrEqualTo(8);
        assertAllTransactionAddressesValid(marekEth);

        RawOnChainProfile marekTron = tronConnector.inspectAddress(
                "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb", AnalysisMode.SYNTHETIC, 1);
        assertThat(marekTron.bytecode()).isEqualTo("0x");
        assertThat(marekTron.stablecoinVolumeUsd()).isGreaterThan(0);
        assertThat(marekTron.transactions()).extracting(RawTransaction::toAddress).contains(
                "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t",
                "TNXoiAJ3dct8Fjg4M9fkLFh9S2v9TXc32G"
        );
        assertAllTransactionAddressesValid(marekTron);
    }

    @Test
    void shouldHandleLiveHttpResponsesAndGracefullyFallbackOnErrors() {
        RestClient.Builder ethBuilder = RestClient.builder();
        MockRestServiceServer ethServer = MockRestServiceServer.bindTo(ethBuilder).build();
        ethServer.expect(requestTo("https://ethereum-rpc.publicnode.com"))
                .andRespond(withSuccess("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0x6080604052\"}", MediaType.APPLICATION_JSON));
        ethServer.expect(requestTo("https://eth.blockscout.com/api/v2/addresses/0x1111111111111111111111111111111111111111"))
                .andRespond(withServerError());

        EthereumConnector liveEth = new EthereumConnector(tagRegistry, ethBuilder, new ObjectMapper());
        RawOnChainProfile profile = liveEth.inspectAddress(
                "0x1111111111111111111111111111111111111111", AnalysisMode.LIVE, 1);
        assertThat(profile.bytecode()).isEqualTo("0x6080604052");
        assertThat(profile.transactions()).isEmpty();
        ethServer.verify();

        // Live Bitcoin mempool fallback to empty EOA on HTTP 500
        RestClient.Builder btcBuilder = RestClient.builder();
        MockRestServiceServer btcServer = MockRestServiceServer.bindTo(btcBuilder).build();
        btcServer.expect(requestTo("https://mempool.space/api/address/1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"))
                .andRespond(withSuccess("{\"chain_stats\":{\"tx_count\":1,\"funded_txo_sum\":500000000,\"spent_txo_sum\":0}}", MediaType.APPLICATION_JSON));
        btcServer.expect(requestTo("https://mempool.space/api/address/1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa/txs"))
                .andRespond(withServerError());

        BitcoinConnector liveBtc = new BitcoinConnector(tagRegistry, btcBuilder, new ObjectMapper());
        RawOnChainProfile btcProfile = liveBtc.inspectAddress(
                "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa", AnalysisMode.LIVE, 1);
        assertThat(btcProfile.bytecode()).isEqualTo("0x");
        assertThat(btcProfile.txCount()).isEqualTo(1);
        assertThat(btcProfile.totalInflowNative()).isEqualTo(5.0);
        btcServer.verify();

        // Live TRON resilient fallback from TronScan 401/500 to TronGrid v1 + TRC-20 parsing
        RestClient.Builder tronBuilder = RestClient.builder();
        MockRestServiceServer tronServer = MockRestServiceServer.bindTo(tronBuilder).build();
        tronServer.expect(requestTo("https://apilist.tronscanapi.com/api/accountv2?address=T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb"))
                .andRespond(withServerError());
        tronServer.expect(requestTo("https://api.trongrid.io/v1/accounts/T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb"))
                .andRespond(withSuccess("{\"data\":[{\"balance\":2500000,\"address\":\"T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb\"}]}", MediaType.APPLICATION_JSON));
        tronServer.expect(requestTo("https://api.trongrid.io/v1/accounts/T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb/transactions/trc20?limit=10"))
                .andRespond(withSuccess("{\"data\":[{\"transaction_id\":\"abc123\",\"block_timestamp\":1771500000000,\"from\":\"T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb\",\"to\":\"TNXoiAJ3dct8Fjg4M9fkLFh9S2v9TXc32G\",\"value\":\"5000000000\",\"token_info\":{\"symbol\":\"USDT\",\"decimals\":6}}]}", MediaType.APPLICATION_JSON));

        TronConnector liveTron = new TronConnector(tagRegistry, tronBuilder, new ObjectMapper());
        RawOnChainProfile tronProfile = liveTron.inspectAddress(
                "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb", AnalysisMode.LIVE, 1);
        assertThat(tronProfile.bytecode()).isEqualTo("0x");
        assertThat(tronProfile.txCount()).isEqualTo(1);
        assertThat(tronProfile.stablecoinVolumeUsd()).isEqualTo(5000.0);
        assertThat(tronProfile.transactions()).hasSize(1);
        tronServer.verify();

        // Live GitHub & ENS graceful failure on HTTP 500
        RestClient.Builder ghBuilder = RestClient.builder();
        MockRestServiceServer ghServer = MockRestServiceServer.bindTo(ghBuilder).build();
        ghServer.expect(requestTo("https://api.github.com/users/testuser"))
                .andRespond(withServerError());
        ghServer.expect(requestTo("https://api.github.com/repos/testuser/testuser/readme"))
                .andRespond(withServerError());
        ghServer.expect(requestTo("https://api.github.com/users/testuser/events/public?per_page=10"))
                .andRespond(withServerError());

        GithubConnector gh = new GithubConnector(ghBuilder, new ObjectMapper());
        List<RawEvidence> ghResult = gh.search(List.of(new IdentifierDto("NICK", "testuser")), 1);
        assertThat(ghResult).isEmpty();
        ghServer.verify();

        // Live Reddit parsing + deduplication
        RestClient.Builder redditBuilder = RestClient.builder();
        MockRestServiceServer redditServer = MockRestServiceServer.bindTo(redditBuilder).build();
        String redditPayload = "{\"data\":{\"children\":[{\"data\":{\"subreddit\":\"kryptowaluty\",\"title\":\"P2P BLIK\",\"selftext\":\"0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045\",\"permalink\":\"/r/kryptowaluty/comments/1\"}}]}}";
        redditServer.expect(requestTo("https://www.reddit.com/user/tester/submitted.json?limit=10"))
                .andRespond(withSuccess(redditPayload, MediaType.APPLICATION_JSON));
        redditServer.expect(requestTo("https://www.reddit.com/search.json?q=tester&limit=5"))
                .andRespond(withSuccess(redditPayload, MediaType.APPLICATION_JSON));

        RedditConnector reddit = new RedditConnector(redditBuilder, new ObjectMapper());
        List<RawEvidence> redditResult = reddit.search(List.of(new IdentifierDto("NICK", "tester")), 1);
        assertThat(redditResult).hasSize(1);
        assertThat(redditResult.get(0).contextHint()).isEqualTo("TRANSACTIONAL_CONTEXT");
        assertThat(redditResult.get(0).sha256())
                .isEqualTo(CryptoAddressValidator.sha256Hex(redditResult.get(0).snippet()));
        redditServer.verify();

        // Live Wayback CDX parsing + archived HTML snapshot text extraction
        RestClient.Builder wbBuilder = RestClient.builder();
        MockRestServiceServer wbServer = MockRestServiceServer.bindTo(wbBuilder).build();
        wbServer.expect(requestTo("https://web.archive.org/cdx/search/cdx?url=janek.pl&output=json&limit=3"))
                .andRespond(withSuccess("[[\"urlkey\",\"timestamp\",\"original\",\"mimetype\",\"statuscode\"],[\"pl,janek)/kontakt\",\"20260210120000\",\"https://janek.pl/kontakt\",\"text/html\",\"200\"]]", MediaType.APPLICATION_JSON));
        wbServer.expect(requestTo("https://web.archive.org/web/20260210120000id_/https://janek.pl/kontakt"))
                .andRespond(withSuccess(
                        "<html><head><style>.h{display:none}</style><script>const x=1;</script></head>"
                                + "<body><h1>Kontakt</h1><p>Rozliczenia ETH: 0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045</p></body></html>",
                        MediaType.TEXT_HTML));

        WaybackConnector wb = new WaybackConnector(wbBuilder, new ObjectMapper());
        List<RawEvidence> wbResult = wb.search(List.of(new IdentifierDto("EMAIL", "a@janek.pl")), 1);
        assertThat(wbResult).hasSize(1);
        assertThat(wbResult.get(0).url()).isEqualTo("https://web.archive.org/web/20260210120000/https://janek.pl/kontakt");
        assertThat(wbResult.get(0).contextHint()).isEqualTo("TRANSACTIONAL_CONTEXT");
        assertThat(wbResult.get(0).snippet())
                .contains("Treść archiwalna:")
                .contains("0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045")
                .doesNotContain("<script>")
                .doesNotContain("const x=1");
        assertThat(wbResult.get(0).sha256())
                .isEqualTo(CryptoAddressValidator.sha256Hex(wbResult.get(0).snippet()));
        wbServer.verify();

        // Live ENS positive resolution
        RestClient.Builder ensBuilder = RestClient.builder();
        MockRestServiceServer ensServer = MockRestServiceServer.bindTo(ensBuilder).build();
        ensServer.expect(requestTo("https://api.ensideas.com/ens/resolve/vitalik.eth"))
                .andRespond(withSuccess(
                        "{\"address\":\"0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045\",\"name\":\"vitalik.eth\",\"displayName\":\"vitalik.eth\"}",
                        MediaType.APPLICATION_JSON));

        EnsConnector ens = new EnsConnector(ensBuilder, new ObjectMapper());
        List<RawEvidence> ensResult = ens.search(List.of(new IdentifierDto("ENS", "vitalik.eth")), 1);
        assertThat(ensResult).hasSize(1);
        assertThat(ensResult.get(0).snippet()).contains("0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045");
        assertThat(ensResult.get(0).sha256())
                .isEqualTo(CryptoAddressValidator.sha256Hex(ensResult.get(0).snippet()));
        ensServer.verify();

        // Live ENS unregistered address returns name: null -> must NOT emit evidence
        RestClient.Builder ensNullBuilder = RestClient.builder();
        MockRestServiceServer ensNullServer = MockRestServiceServer.bindTo(ensNullBuilder).build();
        ensNullServer.expect(requestTo("https://api.ensideas.com/ens/resolve/0x1234567890123456789012345678901234567890"))
                .andRespond(withSuccess(
                        "{\"address\":\"0x1234567890123456789012345678901234567890\",\"name\":null,\"displayName\":\"0x1234...7890\"}",
                        MediaType.APPLICATION_JSON));
        EnsConnector ensNull = new EnsConnector(ensNullBuilder, new ObjectMapper());
        List<RawEvidence> ensNullResult = ensNull.search(
                List.of(new IdentifierDto("CRYPTO_ADDRESS", "0x1234567890123456789012345678901234567890")), 1);
        assertThat(ensNullResult).isEmpty();
        ensNullServer.verify();

        // Live GitHub search by FULL_NAME resolves login and fetches profile + README.md + events
        RestClient.Builder ghSearchBuilder = RestClient.builder();
        MockRestServiceServer ghSearchServer = MockRestServiceServer.bindTo(ghSearchBuilder).build();
        ghSearchServer.expect(requestTo("https://api.github.com/search/users?q=Linus+Torvalds&per_page=1"))
                .andRespond(withSuccess("{\"total_count\":1,\"items\":[{\"login\":\"torvalds\"}]}", MediaType.APPLICATION_JSON));
        ghSearchServer.expect(requestTo("https://api.github.com/users/torvalds"))
                .andRespond(withSuccess("{\"login\":\"torvalds\",\"name\":\"Linus Torvalds\",\"bio\":\"Linux creator\",\"html_url\":\"https://github.com/torvalds\"}", MediaType.APPLICATION_JSON));
        ghSearchServer.expect(requestTo("https://api.github.com/repos/torvalds/torvalds/readme"))
                .andRespond(withSuccess("Wsparcie BTC: bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq", MediaType.TEXT_PLAIN));
        ghSearchServer.expect(requestTo("https://api.github.com/users/torvalds/events/public?per_page=10"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        GithubConnector ghSearch = new GithubConnector(ghSearchBuilder, new ObjectMapper());
        List<RawEvidence> ghSearchResult = ghSearch.search(List.of(new IdentifierDto("FULL_NAME", "Linus Torvalds")), 1);
        assertThat(ghSearchResult).hasSize(2);
        assertThat(ghSearchResult.get(0).url()).isEqualTo("https://github.com/torvalds");
        assertThat(ghSearchResult.get(0).matchedIdentifier()).isEqualTo("Linus Torvalds");
        assertThat(ghSearchResult.get(0).contextHint()).isEqualTo("FORUM_DISCUSSION");
        assertThat(ghSearchResult.get(1).snippet()).contains("bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq");
        ghSearchServer.verify();

        // Live GitHub FULL_NAME fuzzy search mismatch (e.g. "Jan Kowalski" -> @johnykov "Jan K.") must be rejected without fetching README/events
        RestClient.Builder ghMismatchBuilder = RestClient.builder();
        MockRestServiceServer ghMismatchServer = MockRestServiceServer.bindTo(ghMismatchBuilder).build();
        ghMismatchServer.expect(requestTo("https://api.github.com/search/users?q=Jan+Kowalski&per_page=1"))
                .andRespond(withSuccess("{\"total_count\":1,\"items\":[{\"login\":\"johnykov\"}]}", MediaType.APPLICATION_JSON));
        ghMismatchServer.expect(requestTo("https://api.github.com/users/johnykov"))
                .andRespond(withSuccess("{\"login\":\"johnykov\",\"name\":\"Jan K.\",\"bio\":\"Backend dev\",\"html_url\":\"https://github.com/johnykov\"}", MediaType.APPLICATION_JSON));

        GithubConnector ghMismatch = new GithubConnector(ghMismatchBuilder, new ObjectMapper());
        List<RawEvidence> ghMismatchResult = ghMismatch.search(List.of(new IdentifierDto("FULL_NAME", "Jan Kowalski")), 1);
        assertThat(ghMismatchResult).isEmpty();
        ghMismatchServer.verify();

        // Live Reddit HTTP 403 -> PullPush fallback parsing & deduplication
        RestClient.Builder reddit403Builder = RestClient.builder();
        MockRestServiceServer reddit403Server = MockRestServiceServer.bindTo(reddit403Builder).build();
        String pullPushPayload = "{\"data\":[{\"subreddit\":\"kryptowaluty\",\"title\":\"Sprzedam ETH P2P\",\"selftext\":\"Adres: 0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045\",\"full_link\":\"https://www.reddit.com/r/kryptowaluty/comments/abc123/sprzedam_eth_p2p/\"}]}";
        reddit403Server.expect(requestTo("https://www.reddit.com/user/tester403/submitted.json?limit=10"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        reddit403Server.expect(requestTo("https://api.pullpush.io/reddit/search/submission/?author=tester403&size=5"))
                .andRespond(withSuccess(pullPushPayload, MediaType.APPLICATION_JSON));
        reddit403Server.expect(requestTo("https://www.reddit.com/search.json?q=tester403&limit=5"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        reddit403Server.expect(requestTo("https://api.pullpush.io/reddit/search/submission/?q=tester403&size=5"))
                .andRespond(withSuccess(pullPushPayload, MediaType.APPLICATION_JSON));

        RedditConnector reddit403 = new RedditConnector(reddit403Builder, new ObjectMapper());
        List<RawEvidence> reddit403Result = reddit403.search(List.of(new IdentifierDto("NICK", "tester403")), 1);
        assertThat(reddit403Result).hasSize(1);
        assertThat(reddit403Result.get(0).url())
                .isEqualTo("https://www.reddit.com/r/kryptowaluty/comments/abc123/sprzedam_eth_p2p/");
        assertThat(reddit403Result.get(0).contextHint()).isEqualTo("TRANSACTIONAL_CONTEXT");
        assertThat(reddit403Result.get(0).snippet()).contains("0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045");
        assertThat(reddit403Result.get(0).sha256())
                .isEqualTo(CryptoAddressValidator.sha256Hex(reddit403Result.get(0).snippet()));
        reddit403Server.verify();

        // Live Keybase user lookup parsing (proofs + cryptocurrency_addresses)
        RestClient.Builder kbBuilder = RestClient.builder();
        MockRestServiceServer kbServer = MockRestServiceServer.bindTo(kbBuilder).build();
        String kbJson = """
                {
                  "status": {"code": 0, "name": "OK"},
                  "them": [{
                    "basics": {"username": "janek_kb"},
                    "profile": {"full_name": "Jan Kowalski", "bio": "Web3 dev"},
                    "proofs_summary": {"all": [{"proof_type": "github", "nametag": "krypto_janek"}]},
                    "cryptocurrency_addresses": {"bitcoin": [{"address": "bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq"}]}
                  }]
                }
                """;
        kbServer.expect(requestTo("https://keybase.io/_/api/1.0/user/lookup.json?usernames=janek_kb"))
                .andRespond(withSuccess(kbJson, MediaType.APPLICATION_JSON));
        KeybaseConnector kb = new KeybaseConnector(kbBuilder, new ObjectMapper());
        List<RawEvidence> kbResult = kb.search(List.of(new IdentifierDto("NICK", "janek_kb")), 1);
        assertThat(kbResult).hasSize(1);
        assertThat(kbResult.get(0).url()).isEqualTo("https://keybase.io/janek_kb");
        assertThat(kbResult.get(0).snippet()).contains("bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq");
        assertThat(kbResult.get(0).sha256())
                .isEqualTo(CryptoAddressValidator.sha256Hex(kbResult.get(0).snippet()));
        kbServer.verify();
    }

    private void assertAllTransactionAddressesValid(RawOnChainProfile profile) {
        for (RawTransaction tx : profile.transactions()) {
            assertThat(validator.validateAddress(tx.fromAddress()))
                    .as("Valid fromAddress %s in %s", tx.fromAddress(), tx.txHash())
                    .isPresent()
                    .get()
                    .matches(CryptoAddressValidator.ValidatedAddress::checksumValid);
            assertThat(validator.validateAddress(tx.toAddress()))
                    .as("Valid toAddress %s in %s", tx.toAddress(), tx.txHash())
                    .isPresent()
                    .get()
                    .matches(CryptoAddressValidator.ValidatedAddress::checksumValid);
        }
    }
}
