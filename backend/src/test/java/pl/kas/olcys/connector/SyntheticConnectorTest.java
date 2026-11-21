package pl.kas.olcys.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.kas.olcys.api.dto.AnalysisMode;
import pl.kas.olcys.api.dto.IdentifierDto;
import pl.kas.olcys.api.dto.ScenarioPresetDto;
import pl.kas.olcys.connector.web2.RawEvidence;
import pl.kas.olcys.connector.web2.SyntheticConnector;
import pl.kas.olcys.crypto.CryptoAddressValidator;
import pl.kas.olcys.crypto.CryptoAddressValidator.ValidatedAddress;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SyntheticConnectorTest {

    private SyntheticConnector connector;
    private CryptoAddressValidator validator;

    @BeforeEach
    void setUp() {
        connector = new SyntheticConnector(new ObjectMapper());
        connector.init();
        validator = new CryptoAddressValidator();
    }

    @Test
    void shouldExposeThreeValidationScenariosAndSupportModesCorrectly() {
        assertThat(connector.name()).isEqualTo("Synthetic");
        assertThat(connector.supports(AnalysisMode.SYNTHETIC)).isTrue();
        assertThat(connector.supports(AnalysisMode.HYBRID)).isTrue();
        assertThat(connector.supports(AnalysisMode.LIVE)).isFalse();

        List<ScenarioPresetDto> scenarios = connector.getScenarios();
        assertThat(scenarios).hasSize(3);
        assertThat(scenarios)
                .extracting(ScenarioPresetDto::id)
                .containsExactly(
                        "scenariusz-1-janek",
                        "scenariusz-2-reverse-osint",
                        "scenariusz-3-szum-fp"
                );
        assertThat(scenarios.get(0).supportsWalletRotationDemo()).isTrue();
        assertThat(scenarios.get(1).supportsWalletRotationDemo()).isFalse();
        assertThat(scenarios.get(2).supportsWalletRotationDemo()).isFalse();
    }

    @Test
    void shouldReturnStep1AndStep2WalletRotationEvidencesForScenario1() {
        List<IdentifierDto> janekIds = List.of(
                new IdentifierDto("NICK", "krypto_janek"),
                new IdentifierDto("FULL_NAME", "Jan Kowalski"),
                new IdentifierDto("EMAIL", "janek.dev@example.pl")
        );

        List<RawEvidence> step1 = connector.search(janekIds, 1);
        assertThat(step1).hasSize(4);

        RawEvidence githubStep1 = step1.stream()
                .filter(e -> "GitHub".equals(e.sourceName()))
                .findFirst()
                .orElseThrow();
        assertThat(githubStep1.url()).isEqualTo("https://github.com/krypto_janek");
        assertThat(githubStep1.contextHint()).isEqualTo("OWN_PROFILE_VERIFIED");
        assertThat(extractAddresses(githubStep1)).containsExactly("0x71C7656EC7ab88b098defB751B7401B5f6d8976F");

        RawEvidence p2pStep1 = step1.stream()
                .filter(e -> "KantorP2P.pl".equals(e.sourceName()))
                .findFirst()
                .orElseThrow();
        assertThat(p2pStep1.contextHint()).isEqualTo("TRANSACTIONAL_CONTEXT");
        assertThat(extractAddresses(p2pStep1)).containsExactly("0x71C7656EC7ab88b098defB751B7401B5f6d8976F");

        RawEvidence waybackStep1 = step1.stream()
                .filter(e -> "WaybackArchive".equals(e.sourceName()))
                .findFirst()
                .orElseThrow();
        assertThat(extractAddresses(waybackStep1)).containsExactly("bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq");

        RawEvidence forumStep1 = step1.stream()
                .filter(e -> "ForumKrypto.pl".equals(e.sourceName()))
                .findFirst()
                .orElseThrow();
        assertThat(extractAddresses(forumStep1)).containsExactlyInAnyOrder(
                "0xE592427A0AEce92De3Edee1F18E0157C05861564",
                "0x28C6c06298d514Db089934071355E5743bf21d60"
        );

        // Step 2: wallet rotation on GitHub + new KantorP2P ad
        List<RawEvidence> step2 = connector.search(janekIds, 2);
        assertThat(step2).hasSize(4);

        RawEvidence githubStep2 = step2.stream()
                .filter(e -> "GitHub".equals(e.sourceName()))
                .findFirst()
                .orElseThrow();
        assertThat(githubStep2.url()).isEqualTo("https://github.com/krypto_janek");
        assertThat(githubStep2.contextHint()).isEqualTo("OWN_PROFILE_VERIFIED");
        assertThat(extractAddresses(githubStep2)).containsExactly("0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045");

        RawEvidence p2pStep2 = step2.stream()
                .filter(e -> "KantorP2P.pl".equals(e.sourceName()))
                .findFirst()
                .orElseThrow();
        assertThat(extractAddresses(p2pStep2)).containsExactly("0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045");

        assertAllEvidencesHaveValidMetadataAndExtractedAddresses(step1);
        assertAllEvidencesHaveValidMetadataAndExtractedAddresses(step2);
    }

    @Test
    void shouldResolveScenario2ReverseOsintAndScenario3Noise() {
        // Scenario 2 matched strictly by crypto address (Reverse OSINT)
        List<RawEvidence> s2 = connector.search(
                List.of(new IdentifierDto("CRYPTO_ADDRESS", "0xab5801a7d398351b8be11c439e05c5b3259aec9b")), 1);
        assertThat(s2).hasSize(2);
        assertThat(s2).extracting(RawEvidence::sourceName).containsExactly("Telegram OTC", "Reddit");
        assertThat(extractAddresses(s2.get(0))).containsExactlyInAnyOrder(
                "0xAb5801a7D398351b8bE11C439e05C5B3259aeC9B",
                "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb"
        );
        assertAllEvidencesHaveValidMetadataAndExtractedAddresses(s2);

        // Scenario 3: @lucky_drop_2026 noise and FP charity/mixer
        List<RawEvidence> s3 = connector.search(
                List.of(new IdentifierDto("NICK", "lucky_drop_2026")), 1);
        assertThat(s3).hasSize(2);
        assertThat(s3.get(0).contextHint()).isEqualTo("MASS_COMMENT_NOISE");
        assertThat(extractAddresses(s3.get(0)))
                .containsExactly("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed");
        assertThat(extractAddresses(s3.get(1))).containsExactlyInAnyOrder(
                "0x165CD37b4C644C2921454429E7F9358d18A45e14",
                "0xd90e2f925DA726b50C4Ed8D0Fb90Ad053324F31b"
        );
        assertAllEvidencesHaveValidMetadataAndExtractedAddresses(s3);
    }

    @Test
    void shouldReturnEmptyListSafelyForUnknownIdentifiers() {
        List<RawEvidence> unknown = connector.search(
                List.of(new IdentifierDto("NICK", "nieznany_uzytkownik_xyz_999")), 1);
        assertThat(unknown).isEmpty();
    }

    private List<String> extractAddresses(RawEvidence evidence) {
        return validator.extractValidatedAddresses(evidence.snippet()).stream()
                .map(ValidatedAddress::normalizedAddress)
                .toList();
    }

    private void assertAllEvidencesHaveValidMetadataAndExtractedAddresses(List<RawEvidence> evidences) {
        for (RawEvidence ev : evidences) {
            assertThat(ev.url()).startsWith("https://");
            assertThat(ev.fetchedAt()).isNotNull();
            assertThat(ev.sha256()).hasSize(64);
            assertThat(ev.sha256()).isEqualTo(CryptoAddressValidator.sha256Hex(ev.snippet()));
            List<ValidatedAddress> extracted = validator.extractValidatedAddresses(ev.snippet());
            assertThat(extracted)
                    .as("Evidence %s must yield >= 1 valid address", ev.url())
                    .isNotEmpty();
            assertThat(extracted).allMatch(ValidatedAddress::checksumValid);
        }
    }
}
