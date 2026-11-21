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
}
