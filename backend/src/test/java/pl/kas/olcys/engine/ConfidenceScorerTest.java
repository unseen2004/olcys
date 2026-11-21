package pl.kas.olcys.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.kas.olcys.api.dto.AddressFindingDto;
import pl.kas.olcys.api.dto.EvidenceArtifactDto;
import pl.kas.olcys.api.dto.ScoreBreakdownItemDto;
import pl.kas.olcys.crypto.CryptoAddressValidator.ValidatedAddress;
import pl.kas.olcys.engine.FalsePositiveFirewall.FirewallDecision;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfidenceScorerTest {

    private ConfidenceScorer scorer;
    private ValidatedAddress sampleEth;

    @BeforeEach
    void setUp() {
        scorer = new ConfidenceScorer();
        sampleEth = new ValidatedAddress(
                "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
                "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
                "ETH",
                "EIP-55",
                true
        );
    }

    @Test
    void shouldScoreAllFiveDeterministicKasThresholdsCorrectly() {
        AddressFindingDto ownProfile = scorer.scoreCandidate(
                "f1", sampleEth,
                List.of(evidence("ev1", "GitHub", "https://github.com/janek", "OWN_PROFILE_VERIFIED")),
                false, FirewallDecision.accepted(), false, null
        );
        assertThat(ownProfile.confidenceScore()).isEqualTo(85);
        assertThat(ownProfile.confidenceLevelPl()).isEqualTo("BARDZO WYSOKIE PRAWDOPODOBIEŃSTWO");

        AddressFindingDto transactional = scorer.scoreCandidate(
                "f2", sampleEth,
                List.of(evidence("ev2", "KantorP2P.pl", "https://kantorp2p.example.pl/1", "TRANSACTIONAL_CONTEXT")),
                false, FirewallDecision.accepted(), false, null
        );
        assertThat(transactional.confidenceScore()).isEqualTo(70);
        assertThat(transactional.confidenceLevelPl()).isEqualTo("PRAWDOPODOBNY");

        AddressFindingDto forum = scorer.scoreCandidate(
                "f3", sampleEth,
                List.of(evidence("ev3", "ForumKrypto.pl", "https://forumkrypto.example.pl/t/1", "FORUM_DISCUSSION")),
                false, FirewallDecision.accepted(), false, null
        );
        assertThat(forum.confidenceScore()).isEqualTo(50);
        assertThat(forum.confidenceLevelPl()).isEqualTo("ŚREDNI (WYMAGA POTWIERDZENIA)");

        AddressFindingDto directInput = scorer.scoreCandidate(
                "f4", sampleEth,
                List.of(),
                true, FirewallDecision.accepted(), false, null
        );
        assertThat(directInput.confidenceScore()).isEqualTo(50);
        assertThat(directInput.scoreBreakdown()).extracting(ScoreBreakdownItemDto::ruleId)
                .containsExactly("DIRECT_ANALYST_INPUT");

        AddressFindingDto noise = scorer.scoreCandidate(
                "f5", sampleEth,
                List.of(evidence("ev4", "Reddit", "https://www.reddit.com/r/giveaway/1", "MASS_COMMENT_NOISE")),
                false, FirewallDecision.accepted(), false, null
        );
        assertThat(noise.confidenceScore()).isEqualTo(20);
        assertThat(noise.confidenceLevelPl()).isEqualTo("NISKI / SZUM");

        AddressFindingDto rejectedFp = scorer.scoreCandidate(
                "f6", sampleEth,
                List.of(
                        evidence("ev5", "GitHub", "https://github.com/janek", "OWN_PROFILE_VERIFIED"),
                        evidence("ev6", "Reddit", "https://reddit.com/r/krypto/1", "TRANSACTIONAL_CONTEXT")
                ),
                false,
                FirewallDecision.rejected("CEX", "Odrzucono CEX", "Binance 14"),
                true,
                null
        );
        assertThat(rejectedFp.falsePositive()).isTrue();
        assertThat(rejectedFp.confidenceScore()).isZero();
        assertThat(rejectedFp.confidenceLevelPl()).isEqualTo("WYKLUCZENIE / FALSE POSITIVE (0%)");
        assertThat(rejectedFp.interactionRecorded()).isTrue();
        assertThat(rejectedFp.scoreBreakdown()).extracting(ScoreBreakdownItemDto::ruleId)
                .containsExactly("FP_EXCLUSION");
    }

    @Test
    void shouldNotAccumulateBaseScoresOrGrantSynergyOnSamePlatform() {
        List<EvidenceArtifactDto> samePortalEvidences = List.of(
                evidence("ev1", "Reddit", "https://www.reddit.com/r/kryptowaluty/1", "TRANSACTIONAL_CONTEXT"),
                evidence("ev2", "Reddit", "https://reddit.com/r/kryptowaluty/2", "TRANSACTIONAL_CONTEXT"),
                evidence("ev3", "Reddit", "https://www.reddit.com/r/bitcoin/3", "FORUM_DISCUSSION")
        );

        AddressFindingDto result = scorer.scoreCandidate(
                "f1", sampleEth, samePortalEvidences, false, FirewallDecision.accepted(), false, null
        );

        assertThat(result.confidenceScore()).isEqualTo(70);
        assertThat(result.scoreBreakdown()).extracting(ScoreBreakdownItemDto::ruleId)
                .containsExactly("TRANSACTIONAL_CONTEXT");
    }

    @Test
    void shouldApplySynergyBonusAcrossTwoIndependentSourcesAndCapAt100() {
        // 70 + 15 = 85
        AddressFindingDto p2pPlusForum = scorer.scoreCandidate(
                "f1", sampleEth,
                List.of(
                        evidence("ev1", "Telegram OTC", "https://t.me/otc_polska/1", "TRANSACTIONAL_CONTEXT"),
                        evidence("ev2", "Reddit", "https://www.reddit.com/r/kryptowaluty/1", "FORUM_DISCUSSION")
                ),
                false, FirewallDecision.accepted(), false, null
        );
        assertThat(p2pPlusForum.confidenceScore()).isEqualTo(85);
        assertThat(p2pPlusForum.scoreBreakdown()).extracting(ScoreBreakdownItemDto::ruleId)
                .containsExactly("TRANSACTIONAL_CONTEXT", "SYNERGY_MULTI_SOURCE");

        // 85 + 15 = 100 (even with 3 sources, capped at 100)
        AddressFindingDto ownPlusTwoSources = scorer.scoreCandidate(
                "f2", sampleEth,
                List.of(
                        evidence("ev1", "GitHub", "https://github.com/krypto_janek", "OWN_PROFILE_VERIFIED"),
                        evidence("ev2", "KantorP2P.pl", "https://kantorp2p.example.pl/1", "TRANSACTIONAL_CONTEXT"),
                        evidence("ev3", "Reddit", "https://www.reddit.com/r/kryptowaluty/2", "FORUM_DISCUSSION")
                ),
                false, FirewallDecision.accepted(), false, null
        );
        assertThat(ownProfileCheck(ownPlusTwoSources)).isEqualTo(100);
        assertThat(ownPlusTwoSources.scoreBreakdown().get(1).delta()).isEqualTo(15);
    }

    private static int ownProfileCheck(AddressFindingDto dto) {
        return dto.confidenceScore();
    }

    private static EvidenceArtifactDto evidence(String id, String source, String url, String context) {
        return new EvidenceArtifactDto(
                id, source, "OSINT", url, Instant.now(),
                "0".repeat(64), "snippet", "janek", List.of(), context
        );
    }
}
