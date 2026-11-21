package pl.kas.olcys.engine;

import org.springframework.stereotype.Component;
import pl.kas.olcys.api.dto.AddressFindingDto;
import pl.kas.olcys.api.dto.EvidenceArtifactDto;
import pl.kas.olcys.api.dto.OnChainMetricsDto;
import pl.kas.olcys.api.dto.ScoreBreakdownItemDto;
import pl.kas.olcys.crypto.CryptoAddressValidator.ValidatedAddress;
import pl.kas.olcys.engine.FalsePositiveFirewall.FirewallDecision;
import pl.kas.olcys.engine.ai.LocalRulesAiClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic KAS Confidence Score calculator (0–100%).
 * Implements the single-highest-base-category rule plus a +15 pp synergy bonus
 * when >= 2 independent Web2 sources corroborate the same address (capped at 100%).
 */
@Component
public class ConfidenceScorer {

    public static final int SCORE_OWN_PROFILE = 85;
    public static final int SCORE_TRANSACTIONAL = 70;
    public static final int SCORE_FORUM = 50;
    public static final int SCORE_NOISE = 20;
    public static final int SCORE_DIRECT_INPUT = 50;
    public static final int SYNERGY_BONUS = 15;

    private record BaseRuleSpec(String ruleId, String label, int score, String explanationTemplate) {
    }

    public AddressFindingDto scoreCandidate(
            String findingId,
            ValidatedAddress address,
            List<EvidenceArtifactDto> matchingEvidences,
            boolean directAnalystInput,
            FirewallDecision firewallDecision,
            boolean interactionRecorded,
            OnChainMetricsDto metrics
    ) {
        List<EvidenceArtifactDto> evidences = matchingEvidences == null ? List.of() : matchingEvidences;
        List<String> evidenceIds = evidences.stream()
                .map(EvidenceArtifactDto::id)
                .distinct()
                .toList();

        if (firewallDecision != null && firewallDecision.falsePositive()) {
            ScoreBreakdownItemDto fpItem = new ScoreBreakdownItemDto(
                    "FP_EXCLUSION",
                    "Ochrona przed błędnym przypisaniem (False Positive)",
                    0,
                    evidenceIds,
                    firewallDecision.rejectionReason()
            );
            return new AddressFindingDto(
                    findingId,
                    address.normalizedAddress(),
                    address.network(),
                    address.checksumFormat(),
                    address.checksumValid(),
                    0,
                    "WYKLUCZENIE / FALSE POSITIVE (0%)",
                    true,
                    firewallDecision.rejectionReason(),
                    firewallDecision.rejectionCategory(),
                    firewallDecision.entityTag(),
                    interactionRecorded,
                    List.of(fpItem),
                    evidenceIds,
                    metrics
            );
        }

        List<ScoreBreakdownItemDto> breakdown = new ArrayList<>();
        int totalScore = 0;

        if (evidences.isEmpty()) {
            if (directAnalystInput) {
                totalScore = SCORE_DIRECT_INPUT;
                breakdown.add(new ScoreBreakdownItemDto(
                        "DIRECT_ANALYST_INPUT",
                        "Bezpośredni identyfikator wejściowy analityka KAS",
                        SCORE_DIRECT_INPUT,
                        List.of(),
                        "Adres został przekazany bezpośrednio w zapytaniu wejściowym (Reverse OSINT) bez towarzyszącego cytatu Web2 — punktacja bazowa 50% do czasu korelacji źródłowej."
                ));
            }
        } else {
            // Find highest single base rule across evidences (never sum multiple posts on the same or lower tier!)
            BaseRuleSpec bestRule = null;
            List<EvidenceArtifactDto> bestRuleEvidences = new ArrayList<>();

            for (EvidenceArtifactDto ev : evidences) {
                BaseRuleSpec spec = mapContextToBaseRule(ev.contextClassification());
                if (bestRule == null || spec.score() > bestRule.score()) {
                    bestRule = spec;
                    bestRuleEvidences = new ArrayList<>();
                    bestRuleEvidences.add(ev);
                } else if (spec.score() == bestRule.score()) {
                    bestRuleEvidences.add(ev);
                }
            }

            if (bestRule != null) {
                totalScore = bestRule.score();
                List<String> bestIds = bestRuleEvidences.stream().map(EvidenceArtifactDto::id).distinct().toList();
                String sourcesDesc = bestRuleEvidences.stream()
                        .map(EvidenceArtifactDto::sourceName)
                        .distinct()
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("źródło OSINT");
                String explanation = bestRule.explanationTemplate() + " (źródło bazowe: " + sourcesDesc + ").";
                breakdown.add(new ScoreBreakdownItemDto(
                        bestRule.ruleId(),
                        bestRule.label(),
                        bestRule.score(),
                        bestIds,
                        explanation
                ));
            }

            // Synergy rule: >= 2 independent Web2 sources -> +15 pp (max 100%)
            Map<String, List<EvidenceArtifactDto>> byIndependentSource = groupByIndependentSource(evidences);
            if (byIndependentSource.size() >= 2) {
                int cappedScore = Math.min(100, totalScore + SYNERGY_BONUS);
                int actualDelta = cappedScore - totalScore;
                totalScore = cappedScore;

                String distinctSources = byIndependentSource.values().stream()
                        .map(list -> list.get(0).sourceName())
                        .distinct()
                        .reduce((a, b) -> a + " + " + b)
                        .orElse("wiele źródeł");

                breakdown.add(new ScoreBreakdownItemDto(
                        "SYNERGY_MULTI_SOURCE",
                        "Premia synergii (>=2 niezależne źródła Web2)",
                        actualDelta,
                        evidenceIds,
                        String.format(
                                "Adres potwierdzono niezależnie w %d odrębnych źródłach/platformach Web2 (%s). Dodano +%d pkt (limit KAS: 100%%).",
                                byIndependentSource.size(),
                                distinctSources,
                                actualDelta
                        )
                ));
            }
        }

        return new AddressFindingDto(
                findingId,
                address.normalizedAddress(),
                address.network(),
                address.checksumFormat(),
                address.checksumValid(),
                totalScore,
                mapScoreToLevelPl(totalScore),
                false,
                null,
                null,
                null,
                false,
                List.copyOf(breakdown),
                evidenceIds,
                metrics
        );
    }

    public static String mapScoreToLevelPl(int score) {
        if (score >= 80) {
            return "BARDZO WYSOKIE PRAWDOPODOBIEŃSTWO";
        }
        if (score >= 60) {
            return "PRAWDOPODOBNY";
        }
        if (score >= 40) {
            return "ŚREDNI (WYMAGA POTWIERDZENIA)";
        }
        if (score >= 1) {
            return "NISKI / SZUM";
        }
        return "WYKLUCZENIE / FALSE POSITIVE";
    }

    private static BaseRuleSpec mapContextToBaseRule(String contextClassification) {
        String normalized = contextClassification == null
                ? LocalRulesAiClient.RULE_FORUM
                : contextClassification.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case LocalRulesAiClient.RULE_OWN_PROFILE -> new BaseRuleSpec(
                    LocalRulesAiClient.RULE_OWN_PROFILE,
                    "Oficjalny, autorski profil podmiotu (przedział KAS: 80–100%)",
                    SCORE_OWN_PROFILE,
                    "Adres opublikowany na zweryfikowanym profilu autorskim podmiotu"
            );
            case LocalRulesAiClient.RULE_TRANSACTIONAL -> new BaseRuleSpec(
                    LocalRulesAiClient.RULE_TRANSACTIONAL,
                    "Kontekst transakcyjny / ogłoszenie P2P / stopka rozliczeniowa (przedział KAS: 60–79%)",
                    SCORE_TRANSACTIONAL,
                    "Adres wskazany bezpośrednio w kontekście rozliczeń P2P/B2B lub stopce kontaktowej"
            );
            case LocalRulesAiClient.RULE_NOISE -> new BaseRuleSpec(
                    LocalRulesAiClient.RULE_NOISE,
                    "Masowy komentarz / konkurs / giveaway (przedział KAS: 10–39%)",
                    SCORE_NOISE,
                    "Adres wystąpił wyłącznie w masowym komentarzu konkursowym lub airdropowym (wysokie ryzyko szumu)"
            );
            default -> new BaseRuleSpec(
                    LocalRulesAiClient.RULE_FORUM,
                    "Dyskusja na forum branżowym lub grupie tematycznej (przedział KAS: 40–59%)",
                    SCORE_FORUM,
                    "Adres wzmiankowany w dyskusji na forum lub grupie tematycznej bez bezpośredniego ogłoszenia rozliczeniowego"
            );
        };
    }

    static Map<String, List<EvidenceArtifactDto>> groupByIndependentSource(List<EvidenceArtifactDto> evidences) {
        Map<String, List<EvidenceArtifactDto>> grouped = new LinkedHashMap<>();
        for (EvidenceArtifactDto ev : evidences) {
            String key = extractIndependentSourceKey(ev);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(ev);
        }
        return grouped;
    }

    static String extractIndependentSourceKey(EvidenceArtifactDto ev) {
        if (ev.url() != null && !ev.url().isBlank()) {
            try {
                URI uri = URI.create(ev.url().trim());
                String host = uri.getHost();
                if (host != null && !host.isBlank()) {
                    String lowerHost = host.toLowerCase(Locale.ROOT);
                    if (lowerHost.startsWith("www.")) {
                        lowerHost = lowerHost.substring(4);
                    }
                    if (!"localhost".equals(lowerHost) && !"127.0.0.1".equals(lowerHost)) {
                        return "host:" + lowerHost;
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // Fall back to sourceName
            }
        }
        String src = ev.sourceName() == null ? "unknown" : ev.sourceName().trim().toLowerCase(Locale.ROOT);
        return "source:" + src;
    }
}
