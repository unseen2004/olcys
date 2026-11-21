package pl.kas.olcys.engine.ai;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import pl.kas.olcys.api.dto.AddressFindingDto;
import pl.kas.olcys.api.dto.CashOutSignalDto;
import pl.kas.olcys.config.AiProperties;

import java.util.List;
import java.util.Locale;

/**
 * Primary {@link AiClient} facade selecting between {@link LocalRulesAiClient} and
 * {@link OpenAiCompatibleAiClient} based on {@code olcys.ai.provider}.
 */
@Component
@Primary
public class AiClientRouter implements AiClient {

    private final AiProperties aiProperties;
    private final LocalRulesAiClient localRulesAiClient;
    private final OpenAiCompatibleAiClient openAiCompatibleAiClient;
    private volatile String lastEffectiveProvider;

    @Autowired
    public AiClientRouter(
            AiProperties aiProperties,
            LocalRulesAiClient localRulesAiClient,
            OpenAiCompatibleAiClient openAiCompatibleAiClient
    ) {
        this.aiProperties = aiProperties;
        this.localRulesAiClient = localRulesAiClient;
        this.openAiCompatibleAiClient = openAiCompatibleAiClient;
        this.lastEffectiveProvider = resolveDelegate().providerName();
    }

    @Override
    public String providerName() {
        return resolveDelegate().providerName();
    }

    @Override
    public String modelName() {
        return resolveDelegate().modelName();
    }

    /**
     * Returns the provider name actually used during the last AI operation (reflecting fallback to {@code local-rules}).
     */
    public String lastEffectiveProviderName() {
        return lastEffectiveProvider != null ? lastEffectiveProvider : providerName();
    }

    @Override
    public String classifyContext(String snippet, String defaultHint) {
        AiClient delegate = resolveDelegate();
        String result = delegate.classifyContext(snippet, defaultHint);
        updateEffectiveProvider(delegate);
        return result;
    }

    @Override
    public String generateExecutiveSummaryPl(
            String subjectDisplayName,
            List<AddressFindingDto> confirmed,
            List<AddressFindingDto> rejected,
            List<CashOutSignalDto> cashOutSignals,
            double totalVolumePln
    ) {
        AiClient delegate = resolveDelegate();
        String result = delegate.generateExecutiveSummaryPl(
                subjectDisplayName, confirmed, rejected, cashOutSignals, totalVolumePln
        );
        updateEffectiveProvider(delegate);
        return result;
    }

    private AiClient resolveDelegate() {
        String provider = aiProperties.provider() == null
                ? LocalRulesAiClient.PROVIDER_NAME
                : aiProperties.provider().trim().toLowerCase(Locale.ROOT);
        if ("ollama".equals(provider) || "openai-compatible".equals(provider)) {
            return openAiCompatibleAiClient;
        }
        return localRulesAiClient;
    }

    private void updateEffectiveProvider(AiClient delegate) {
        if (delegate == openAiCompatibleAiClient && openAiCompatibleAiClient.wasLastCallFallback()) {
            this.lastEffectiveProvider = localRulesAiClient.providerName();
        } else {
            this.lastEffectiveProvider = delegate.providerName();
        }
    }
}
