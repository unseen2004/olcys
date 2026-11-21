package pl.kas.olcys.engine.ai;

import pl.kas.olcys.api.dto.AddressFindingDto;
import pl.kas.olcys.api.dto.CashOutSignalDto;

import java.util.List;

/**
 * Abstraction over local on-premise AI / deterministic rule engines used for OSINT context classification
 * and Polish executive report summarization.
 */
public interface AiClient {

    String providerName();

    String modelName();

    String classifyContext(String snippet, String defaultHint);

    String generateExecutiveSummaryPl(
            String subjectDisplayName,
            List<AddressFindingDto> confirmed,
            List<AddressFindingDto> rejected,
            List<CashOutSignalDto> cashOutSignals,
            double totalVolumePln
    );
}
