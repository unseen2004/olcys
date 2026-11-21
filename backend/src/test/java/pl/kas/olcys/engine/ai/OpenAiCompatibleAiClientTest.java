package pl.kas.olcys.engine.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.kas.olcys.config.AiProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleAiClientTest {

    private final LocalRulesAiClient localRules = new LocalRulesAiClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldThrowSecurityExceptionForExternalHostsWhenAllowExternalIsFalse() {
        List<String> forbiddenExternalUrls = List.of(
                "https://api.openai.com/v1",
                "https://generativelanguage.googleapis.com/v1beta",
                "https://api.anthropic.com/v1",
                "https://openrouter.ai/api/v1"
        );

        for (String externalUrl : forbiddenExternalUrls) {
            AiProperties props = new AiProperties("openai-compatible", externalUrl, "gpt-4o", false, 5);
            OpenAiCompatibleAiClient client = new OpenAiCompatibleAiClient(
                    props, localRules, RestClient.builder(), objectMapper
            );

            assertThatThrownBy(client::validateEndpointSecurity)
                    .as("Must block external endpoint %s", externalUrl)
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("BLOKADA BEZPIECZEŃSTWA");

            assertThatThrownBy(() -> client.classifyContext("Sprzedam ETH BLIK", null))
                    .isInstanceOf(SecurityException.class);

            assertThatThrownBy(() -> client.generateExecutiveSummaryPl("Jan Kowalski", List.of(), List.of(), List.of(), 0.0))
                    .isInstanceOf(SecurityException.class);
        }
    }

    @Test
    void shouldAllowLocalAndInternalDockerHosts() {
        List<String> allowedUrls = List.of(
                "http://localhost:11434/v1",
                "http://127.0.0.1:11434/v1",
                "http://[::1]:11434/v1",
                "http://ollama:11434/v1",
                "http://host.docker.internal:11434/v1"
        );

        for (String url : allowedUrls) {
            AiProperties props = new AiProperties("ollama", url, "llama3.1:8b", false, 5);
            OpenAiCompatibleAiClient client = new OpenAiCompatibleAiClient(
                    props, localRules, RestClient.builder(), objectMapper
            );
            assertThatCode(client::validateEndpointSecurity)
                    .as("Must allow local/internal endpoint %s", url)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void shouldFallbackToLocalRulesWhenLocalOllamaEndpointFailsAndUseOllamaWhenAvailable() {
        // Fallback on HTTP 500 from local Ollama
        RestClient.Builder failingBuilder = RestClient.builder();
        MockRestServiceServer failingServer = MockRestServiceServer.bindTo(failingBuilder).build();
        failingServer.expect(requestTo("http://ollama:11434/v1/chat/completions"))
                .andRespond(withServerError());

        AiProperties props = new AiProperties("ollama", "http://ollama:11434/v1", "llama3.1:8b", false, 5);
        OpenAiCompatibleAiClient clientWithFailure = new OpenAiCompatibleAiClient(
                props, localRules, failingBuilder, objectMapper
        );

        String fallbackSummary = clientWithFailure.generateExecutiveSummaryPl(
                "Jan Kowalski", List.of(), List.of(), List.of(), 0.0
        );
        assertThat(clientWithFailure.wasLastCallFallback()).isTrue();
        assertThat(fallbackSummary).contains("Jan Kowalski").contains("PIT-38");
        failingServer.verify();

        // Successful call to local Ollama
        RestClient.Builder okBuilder = RestClient.builder();
        MockRestServiceServer okServer = MockRestServiceServer.bindTo(okBuilder).build();
        okServer.expect(requestTo("http://ollama:11434/v1/chat/completions"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"TRANSACTIONAL_CONTEXT\"}}]}",
                        MediaType.APPLICATION_JSON
                ));

        OpenAiCompatibleAiClient okClient = new OpenAiCompatibleAiClient(
                props, localRules, okBuilder, objectMapper
        );
        String classified = okClient.classifyContext("Sprzedam USDT za PLN", null);
        assertThat(okClient.wasLastCallFallback()).isFalse();
        assertThat(classified).isEqualTo("TRANSACTIONAL_CONTEXT");
        okServer.verify();
    }

    @Test
    void shouldSanitizeMisleadingPit38PercentageAndFalsePositivePhrasesFromSmallLlm() {
        String raw1 = "Dla podmiotu Jan Kowalski potwierdzono 2 portfele o wolumenie 176 600,00 PLN. "
                + "Oceniono ryzyko podatkowe PIT-38 na 100%, co wynika z niewielkiej liczby sygnałów False Positives.";
        String sanitized1 = OpenAiCompatibleAiClient.sanitizeExecutiveSummaryPl(raw1);
        assertThat(sanitized1)
                .doesNotContain("na 100%")
                .doesNotContain("niewielkiej liczby sygnałów False Positives")
                .contains("Stwierdzono podwyższone ryzyko podatkowe PIT-38 wymagające weryfikacji zeznania rocznego.")
                .contains("odrzucenia adresów instytucjonalnych przez Firewall False Positives");

        String raw2 = "Analiza podnosi ryzyko podatkowe PIT-38 podmiotu do poziomu 100%.";
        assertThat(OpenAiCompatibleAiClient.sanitizeExecutiveSummaryPl(raw2))
                .isEqualTo("Analiza Stwierdzono podwyższone ryzyko podatkowe PIT-38 wymagające weryfikacji zeznania rocznego.");

        String raw3 = "Ryzyko podatkowe PIT-38 wynosi 85%.";
        assertThat(OpenAiCompatibleAiClient.sanitizeExecutiveSummaryPl(raw3))
                .isEqualTo("Stwierdzono podwyższone ryzyko podatkowe PIT-38 wymagające weryfikacji zeznania rocznego.");

        // Verify generateExecutiveSummaryPl applies sanitizeExecutiveSummaryPl to live mock responses
        RestClient.Builder summaryBuilder = RestClient.builder();
        MockRestServiceServer summaryServer = MockRestServiceServer.bindTo(summaryBuilder).build();
        summaryServer.expect(requestTo("http://ollama:11434/v1/chat/completions"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"Podmiot Jan Kowalski: 2 portfele, 176 600,00 PLN. Oceniono ryzyko PIT-38 na 100%.\"}}]}",
                        MediaType.APPLICATION_JSON
                ));

        AiProperties props = new AiProperties("ollama", "http://ollama:11434/v1", "llama3.2:latest", false, 5);
        OpenAiCompatibleAiClient summaryClient = new OpenAiCompatibleAiClient(
                props, localRules, summaryBuilder, objectMapper
        );
        String summary = summaryClient.generateExecutiveSummaryPl(
                "Jan Kowalski", List.of(), List.of(), List.of(), 176600.0
        );
        assertThat(summaryClient.wasLastCallFallback()).isFalse();
        assertThat(summary)
                .doesNotContain("na 100%")
                .contains("Stwierdzono podwyższone ryzyko podatkowe PIT-38 wymagające weryfikacji zeznania rocznego.");
        summaryServer.verify();
    }
}
