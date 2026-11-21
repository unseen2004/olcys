package pl.kas.olcys.engine.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pl.kas.olcys.api.dto.AddressFindingDto;
import pl.kas.olcys.api.dto.CashOutSignalDto;
import pl.kas.olcys.config.AiProperties;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * OpenAI-compatible client aimed strictly at local/internal inference endpoints (e.g., Ollama).
 * Enforces a hard security block against external AI APIs when {@code olcys.ai.allow-external=false},
 * and falls back seamlessly to {@link LocalRulesAiClient} when local inference is unavailable.
 */
@Component
public class OpenAiCompatibleAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleAiClient.class);

    private static final Set<String> ALLOWED_LOCAL_HOSTS = Set.of(
            "localhost",
            "127.0.0.1",
            "::1",
            "ollama",
            "host.docker.internal"
    );

    private final AiProperties aiProperties;
    private final LocalRulesAiClient fallbackClient;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private volatile boolean lastCallUsedFallback = false;

    @Autowired
    public OpenAiCompatibleAiClient(
            AiProperties aiProperties,
            LocalRulesAiClient fallbackClient,
            ObjectMapper objectMapper
    ) {
        this.aiProperties = aiProperties;
        this.fallbackClient = fallbackClient;
        this.objectMapper = objectMapper;
        this.restClient = buildTimeoutRestClient(aiProperties);
    }

    /**
     * Package/test constructor preserving caller-supplied {@link RestClient.Builder} (e.g. MockRestServiceServer).
     */
    public OpenAiCompatibleAiClient(
            AiProperties aiProperties,
            LocalRulesAiClient fallbackClient,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.aiProperties = aiProperties;
        this.fallbackClient = fallbackClient;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.clone().build();
    }

    private static RestClient buildTimeoutRestClient(AiProperties aiProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(5, aiProperties.timeoutSeconds())));
        return RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String providerName() {
        String configured = aiProperties.provider().toLowerCase(Locale.ROOT);
        if ("ollama".equals(configured) || "openai-compatible".equals(configured)) {
            return configured;
        }
        return "openai-compatible";
    }

    @Override
    public String modelName() {
        return aiProperties.model();
    }

    public boolean wasLastCallFallback() {
        return lastCallUsedFallback;
    }

    /**
     * Validates that {@link AiProperties#baseUrl()} points exclusively to an allowed local/internal host
     * unless external data transfer is explicitly permitted.
     */
    public URI validateEndpointSecurity() {
        String rawBaseUrl = aiProperties.baseUrl();
        if (rawBaseUrl == null || rawBaseUrl.isBlank()) {
            throw new SecurityException("Brak adresu URL lokalnego serwera AI (olcys.ai.base-url).");
        }
        URI uri;
        try {
            uri = URI.create(rawBaseUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw new SecurityException("Nieprawidłowy format adresu URL serwera AI: " + rawBaseUrl, ex);
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new SecurityException("Niedozwolony protokół serwera AI: " + scheme);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SecurityException("Brak hosta w adresie serwera AI: " + rawBaseUrl);
        }

        if (!aiProperties.allowExternal() && !isAllowedInternalHost(host)) {
            throw new SecurityException(
                    "BLOKADA BEZPIECZEŃSTWA KAS: Zablokowano próbę połączenia z zewnętrznym API AI ("
                            + host + "). Przy olcys.ai.allow-external=false dozwolone są wyłącznie hosty lokalne/wewnętrzne."
            );
        }
        return uri;
    }

    static boolean isAllowedInternalHost(String host) {
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (ALLOWED_LOCAL_HOSTS.contains(normalized)) {
            return true;
        }
        // Single-label Docker Compose service name without dots (e.g., "ollama", "vllm", "localai")
        return !normalized.isEmpty()
                && !normalized.contains(".")
                && !normalized.contains(":");
    }

    @Override
    public String classifyContext(String snippet, String defaultHint) {
        URI endpointUri = validateEndpointSecurity();
        if (defaultHint != null && !defaultHint.isBlank()) {
            String normalizedHint = defaultHint.trim().toUpperCase(Locale.ROOT);
            if (LocalRulesAiClient.VALID_KAS_RULES.contains(normalizedHint)) {
                this.lastCallUsedFallback = false;
                return normalizedHint;
            }
        }

        String systemPrompt = "Sklasyfikuj tekst OSINT do dokładnie jednej kategorii. Zasady:\n"
                + "- OWN_PROFILE_VERIFIED: oficjalny profil autora, bio GitHub/Keybase/ENS, własny portfel do dotacji\n"
                + "- TRANSACTIONAL_CONTEXT: ogłoszenie sprzedaży/kupna krypto, P2P, OTC, BLIK, kantor, B2B\n"
                + "- MASS_COMMENT_NOISE: konkurs, giveaway, airdrop, losowanie darmowych tokenów\n"
                + "- FORUM_DISCUSSION: ogólna dyskusja na forum\n"
                + "Odpowiedz wyłącznie nazwą jednej kategorii.";
        String userPrompt = snippet == null ? "" : snippet;

        try {
            String response = invokeChatCompletion(endpointUri, systemPrompt, userPrompt, 32);
            String cleaned = response.trim().toUpperCase(Locale.ROOT);
            for (String rule : List.of(
                    LocalRulesAiClient.RULE_OWN_PROFILE,
                    LocalRulesAiClient.RULE_TRANSACTIONAL,
                    LocalRulesAiClient.RULE_FORUM,
                    LocalRulesAiClient.RULE_NOISE)) {
                if (cleaned.contains(rule)) {
                    this.lastCallUsedFallback = false;
                    return rule;
                }
            }
            this.lastCallUsedFallback = true;
            return fallbackClient.classifyContext(snippet, defaultHint);
        } catch (SecurityException se) {
            throw se;
        } catch (Exception ex) {
            log.warn("Local AI endpoint {} unavailable during classifyContext ({}). Falling back to local-rules.",
                    endpointUri, ex.getMessage());
            this.lastCallUsedFallback = true;
            return fallbackClient.classifyContext(snippet, defaultHint);
        }
    }

    @Override
    public String generateExecutiveSummaryPl(
            String subjectDisplayName,
            List<AddressFindingDto> confirmed,
            List<AddressFindingDto> rejected,
            List<CashOutSignalDto> cashOutSignals,
            double totalVolumePln
    ) {
        URI endpointUri = validateEndpointSecurity();
        String deterministicBaseline = fallbackClient.generateExecutiveSummaryPl(
                subjectDisplayName, confirmed, rejected, cashOutSignals, totalVolumePln
        );
        String systemPrompt = "Jesteś lokalnym asystentem analitycznym KAS. Napisz zwięzłe, formalne podsumowanie wykonawcze "
                + "po polsku (2-3 pełne zdania) na podstawie poniższych danych. Obowiązkowo przytocz nazwę podmiotu, "
                + "liczbę potwierdzonych portfeli, dokładną kwotę wolumenu w PLN oraz ocenę ryzyka PIT-38.";
        String userPrompt = "Podmiot: " + subjectDisplayName
                + " | Potwierdzone portfele: " + (confirmed == null ? 0 : confirmed.size())
                + " | Odrzucone FP: " + (rejected == null ? 0 : rejected.size())
                + " | Sygnały Cash-Out: " + (cashOutSignals == null ? 0 : cashOutSignals.size())
                + " | Wolumen PLN: " + String.format(Locale.forLanguageTag("pl-PL"), "%.2f", totalVolumePln)
                + "\nWnioski bazowe:\n" + deterministicBaseline;

        try {
            String response = invokeChatCompletion(endpointUri, systemPrompt, userPrompt, 260);
            if (!response.isBlank()) {
                this.lastCallUsedFallback = false;
                return response.trim();
            }
            this.lastCallUsedFallback = true;
            return deterministicBaseline;
        } catch (SecurityException se) {
            throw se;
        } catch (Exception ex) {
            log.warn("Local AI endpoint {} unavailable during generateExecutiveSummaryPl ({}). Falling back to local-rules.",
                    endpointUri, ex.getMessage());
            this.lastCallUsedFallback = true;
            return deterministicBaseline;
        }
    }

    private String invokeChatCompletion(
            URI baseUri,
            String systemPrompt,
            String userPrompt,
            int maxTokens
    ) throws Exception {
        String normalizedBase = baseUri.toString().replaceAll("/+$", "");
        String url = normalizedBase + "/chat/completions";

        Map<String, Object> payload = Map.of(
                "model", aiProperties.model(),
                "temperature", 0.0,
                "max_tokens", maxTokens,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        String responseBody = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);

        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        JsonNode root = objectMapper.readTree(responseBody);
        return root.path("choices").path(0).path("message").path("content").asText("");
    }
}
