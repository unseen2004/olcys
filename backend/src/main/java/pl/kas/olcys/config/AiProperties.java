package pl.kas.olcys.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "olcys.ai")
public record AiProperties(
        String provider,
        String baseUrl,
        String model,
        boolean allowExternal,
        int timeoutSeconds
) {
    public AiProperties {
        if (provider == null || provider.isBlank()) {
            provider = "local-rules";
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:11434/v1";
        }
        if (model == null || model.isBlank()) {
            model = "llama3.1:8b";
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 5;
        }
    }
}
