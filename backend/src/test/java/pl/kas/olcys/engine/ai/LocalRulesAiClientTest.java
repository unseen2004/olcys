package pl.kas.olcys.engine.ai;

import org.junit.jupiter.api.Test;
import pl.kas.olcys.api.dto.AddressFindingDto;
import pl.kas.olcys.api.dto.CashOutSignalDto;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalRulesAiClientTest {

    private final LocalRulesAiClient client = new LocalRulesAiClient();

    @Test
    void shouldClassifyContextDeterministicallyAndPreserveValidKasHints() {
        assertThat(client.providerName()).isEqualTo("local-rules");
        assertThat(client.classifyContext("dowolny tekst", "OWN_PROFILE_VERIFIED"))
                .isEqualTo("OWN_PROFILE_VERIFIED");
        assertThat(client.classifyContext("dowolny tekst", "TRANSACTIONAL_CONTEXT"))
                .isEqualTo("TRANSACTIONAL_CONTEXT");
        assertThat(client.classifyContext("dowolny tekst", "FORUM_DISCUSSION"))
                .isEqualTo("FORUM_DISCUSSION");
        assertThat(client.classifyContext("dowolny tekst", "MASS_COMMENT_NOISE"))
                .isEqualTo("MASS_COMMENT_NOISE");

        assertThat(client.classifyContext("Wielki giveaway airdrop konkurs!", null))
                .isEqualTo("MASS_COMMENT_NOISE");
        assertThat(client.classifyContext("Sprzedam USDT za BLIK w kantor OTC", null))
                .isEqualTo("TRANSACTIONAL_CONTEXT");
        assertThat(client.classifyContext("Oficjalny profil GitHub autora projektu", null))
                .isEqualTo("OWN_PROFILE_VERIFIED");
        assertThat(client.classifyContext("Dyskusja o opłatach sieciowych w sieci ETH", "UNKNOWN"))
                .isEqualTo("FORUM_DISCUSSION");
    }

    @Test
    void shouldGenerateConcretePolishExecutiveSummary() {
        AddressFindingDto confirmed = new AddressFindingDto(
                "a1", "0x71C7656EC7ab88b098defB751B7401B5f6d8976F", "ETH", "EIP-55",
                true, 100, "BARDZO WYSOKIE PRAWDOPODOBIEŃSTWO", false,
                null, null, null, false, List.of(), List.of("e1"), null
        );
        AddressFindingDto rejected = new AddressFindingDto(
                "a2", "0x28C6c06298d514Db089934071355E5743bf21d60", "ETH", "EIP-55",
                true, 0, "WYKLUCZENIE / FALSE POSITIVE (0%)", true,
                "CEX", "CEX", "Binance 14", true, List.of(), List.of("e2"), null
        );
        CashOutSignalDto sig = new CashOutSignalDto(
                "s1", "CEX_OFFRAMP", "HIGH", "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
                "ETH", "0xabc", Instant.now(), 20350.0, "Binance 14", "Opis", "Hint"
        );

        String summary = client.generateExecutiveSummaryPl(
                "Jan Kowalski", List.of(confirmed), List.of(rejected), List.of(sig), 176600.0
        );
        assertThat(summary)
                .contains("Jan Kowalski")
                .contains("100%")
                .contains("PIT-38")
                .contains("CEX_OFFRAMP");
    }
}
