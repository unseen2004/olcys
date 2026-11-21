package pl.kas.olcys.engine.ai;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pl.kas.olcys.api.dto.AddressFindingDto;
import pl.kas.olcys.api.dto.AnalysisMode;
import pl.kas.olcys.api.dto.AnalysisRunDto;
import pl.kas.olcys.api.dto.CashOutSignalDto;
import pl.kas.olcys.api.dto.CreateSubjectRequest;
import pl.kas.olcys.api.dto.IdentifierDto;
import pl.kas.olcys.engine.AnalysisOrchestrator;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live integration tests exercising a real local Ollama server (http://localhost:11434/v1)
 * with model {@code llama3.2:latest} without mocks or fallback to local-rules.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:olcys_ollama_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "olcys.ai.provider=ollama",
        "olcys.ai.base-url=http://localhost:11434/v1",
        "olcys.ai.model=llama3.2:latest",
        "olcys.ai.allow-external=false",
        "olcys.ai.timeout-seconds=45"
})
@ActiveProfiles("test")
class LocalOllamaLiveIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(LocalOllamaLiveIntegrationTest.class);
    private static final String EXPECTED_MODEL = "llama3.2:latest";

    @Autowired
    private OpenAiCompatibleAiClient openAiClient;

    @Autowired
    private AiClientRouter aiClientRouter;

    @Autowired
    private AnalysisOrchestrator analysisOrchestrator;

    @BeforeAll
    static void checkLocalOllamaReady() {
        Assumptions.assumeTrue(
                isLocalOllamaModelAvailable(EXPECTED_MODEL),
                "Lokalny serwer Ollama z modelem " + EXPECTED_MODEL + " nie jest dostępny pod http://localhost:11434"
        );
    }

    @Test
    void shouldClassifyPolishOsintContextsUsingRealLocalLlamaModel() {
        assertThat(aiClientRouter.providerName()).isEqualTo("ollama");
        assertThat(aiClientRouter.modelName()).isEqualTo(EXPECTED_MODEL);

        String ownProfile = openAiClient.classifyContext(
                "Oficjalny profil GitHub autora: mój portfel ETH do dotacji 0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
                null
        );
        assertThat(openAiClient.wasLastCallFallback()).isFalse();
        assertThat(ownProfile).isEqualTo(LocalRulesAiClient.RULE_OWN_PROFILE);

        String transactional = openAiClient.classifyContext(
                "Sprzedam USDT i ETH za PLN gotówka lub BLIK, kantor P2P Warszawa",
                null
        );
        assertThat(openAiClient.wasLastCallFallback()).isFalse();
        assertThat(transactional).isEqualTo(LocalRulesAiClient.RULE_TRANSACTIONAL);

        String noise = openAiClient.classifyContext(
                "Wielki konkurs giveaway airdrop! Wklej swój adres ETH w komentarzu żeby wygrać darmowe tokeny",
                null
        );
        assertThat(openAiClient.wasLastCallFallback()).isFalse();
        assertThat(noise).isEqualTo(LocalRulesAiClient.RULE_NOISE);
    }

    @Test
    void shouldGeneratePolishExecutiveSummaryUsingRealLocalLlamaModel() {
        AddressFindingDto confirmedWallet = new AddressFindingDto(
                "addr-1",
                "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
                "ETH",
                "EIP-55",
                true,
                100,
                "Bardzo wysoka",
                false,
                null,
                null,
                null,
                false,
                List.of(),
                List.of("ev-1"),
                null
        );
        AddressFindingDto rejectedFp = new AddressFindingDto(
                "addr-2",
                "0xE592427A0AEce92De3Edee1F18E0157C05861564",
                "ETH",
                "EIP-55",
                true,
                0,
                "Wykluczony (FP)",
                true,
                "Smart kontrakt DEX",
                "DEX_ROUTER",
                "Uniswap V3 Router",
                true,
                List.of(),
                List.of("ev-1"),
                null
        );
        CashOutSignalDto cashOut = new CashOutSignalDto(
                "sig-1",
                "CEX_OFFRAMP",
                "HIGH",
                "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
                "ETH",
                "0xabc123",
                Instant.parse("2026-10-15T12:00:00Z"),
                48500.0,
                "Binance",
                "Transfer wychodzący na gorący portfel CEX Binance",
                "Wystąpić do Binance o dane KYC"
        );

        String summary = aiClientRouter.generateExecutiveSummaryPl(
                "Jan Kowalski (@krypto_janek)",
                List.of(confirmedWallet),
                List.of(rejectedFp),
                List.of(cashOut),
                48500.0
        );

        log.info("Ollama [{}] executive summary: {}", EXPECTED_MODEL, summary);

        assertThat(openAiClient.wasLastCallFallback()).isFalse();
        assertThat(aiClientRouter.lastEffectiveProviderName()).isEqualTo("ollama");
        assertThat(summary).isNotBlank();
        assertThat(summary).containsIgnoringCase("Jan Kowalski");
        assertThat(summary.toUpperCase()).contains("PIT-38");
    }

    @Test
    void shouldRunFullAnalysisPipelineWithRealLocalLlamaInThreeZoneReport() {
        CreateSubjectRequest req = new CreateSubjectRequest(
                "Test Ollama LLM — Jan Kowalski (@krypto_janek)",
                List.of(
                        new IdentifierDto("NICK", "krypto_janek"),
                        new IdentifierDto("FULL_NAME", "Jan Kowalski")
                ),
                AnalysisMode.SYNTHETIC,
                List.of("GitHub", "KantorP2P.pl", "WaybackArchive", "ForumKrypto.pl"),
                List.of("ETH", "BTC"),
                1
        );

        AnalysisRunDto run = analysisOrchestrator.createSubjectAndRun(req);

        log.info("Full E2E Ollama report provider={}, summary={}",
                run.report().aiProviderUsed(), run.report().executiveSummaryPl());

        assertThat(run.confirmedAddresses()).hasSize(2);
        assertThat(run.rejectedAddresses()).hasSize(2);
        assertThat(run.report()).isNotNull();
        assertThat(run.report().aiProviderUsed()).isEqualTo("ollama");
        assertThat(openAiClient.wasLastCallFallback()).isFalse();
        assertThat(run.report().executiveSummaryPl()).isNotBlank();
        assertThat(run.report().executiveSummaryPl().toUpperCase()).contains("PIT-38");
    }

    private static boolean isLocalOllamaModelAvailable(String modelName) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:11434/api/tags"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && response.body().contains(modelName);
        } catch (Exception ex) {
            return false;
        }
    }
}
