package pl.kas.olcys.engine.ai;

import org.springframework.stereotype.Component;
import pl.kas.olcys.api.dto.AddressFindingDto;
import pl.kas.olcys.api.dto.CashOutSignalDto;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fully deterministic, offline-safe local rule engine implementing {@link AiClient}.
 * Performs zero network calls and guarantees reproducible KAS classifications and Polish summaries.
 */
@Component
public class LocalRulesAiClient implements AiClient {

    public static final String PROVIDER_NAME = "local-rules";
    public static final String MODEL_NAME = "kas-deterministic-rules-v1";

    public static final String RULE_OWN_PROFILE = "OWN_PROFILE_VERIFIED";
    public static final String RULE_TRANSACTIONAL = "TRANSACTIONAL_CONTEXT";
    public static final String RULE_FORUM = "FORUM_DISCUSSION";
    public static final String RULE_NOISE = "MASS_COMMENT_NOISE";

    private static final Set<String> VALID_KAS_RULES = Set.of(
            RULE_OWN_PROFILE,
            RULE_TRANSACTIONAL,
            RULE_FORUM,
            RULE_NOISE
    );

    private static final List<String> NOISE_KEYWORDS = List.of(
            "giveaway", "airdrop", "konkurs", "rozdanie", "free token", "losowanie"
    );

    private static final List<String> TRANSACTIONAL_KEYWORDS = List.of(
            "sprzedam", "kupię", "kupie", "blik", "otc", "rozliczenia",
            "rozliczeni", "b2b", "płatności", "platnosci", "kantor", "p2p", "faktura"
    );

    private static final List<String> OWN_PROFILE_KEYWORDS = List.of(
            "oficjalny profil", "github", "autorski"
    );

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public String modelName() {
        return MODEL_NAME;
    }

    @Override
    public String classifyContext(String snippet, String defaultHint) {
        if (defaultHint != null && !defaultHint.isBlank()) {
            String normalizedHint = defaultHint.trim().toUpperCase(Locale.ROOT);
            if (VALID_KAS_RULES.contains(normalizedHint)) {
                return normalizedHint;
            }
        }

        if (snippet == null || snippet.isBlank()) {
            return RULE_FORUM;
        }

        String lower = snippet.toLowerCase(Locale.ROOT);
        if (containsAny(lower, NOISE_KEYWORDS)) {
            return RULE_NOISE;
        }
        if (containsAny(lower, OWN_PROFILE_KEYWORDS)) {
            return RULE_OWN_PROFILE;
        }
        if (containsAny(lower, TRANSACTIONAL_KEYWORDS)) {
            return RULE_TRANSACTIONAL;
        }
        return RULE_FORUM;
    }

    @Override
    public String generateExecutiveSummaryPl(
            String subjectDisplayName,
            List<AddressFindingDto> confirmed,
            List<AddressFindingDto> rejected,
            List<CashOutSignalDto> cashOutSignals,
            double totalVolumePln
    ) {
        List<AddressFindingDto> safeConfirmed = confirmed == null ? List.of() : confirmed;
        List<AddressFindingDto> safeRejected = rejected == null ? List.of() : rejected;
        List<CashOutSignalDto> safeSignals = cashOutSignals == null ? List.of() : cashOutSignals;

        int confirmedCount = safeConfirmed.size();
        int rejectedCount = safeRejected.size();
        int maxScore = safeConfirmed.stream()
                .mapToInt(AddressFindingDto::confidenceScore)
                .max()
                .orElse(0);

        String formattedVolume = formatPln(totalVolumePln);
        String subject = (subjectDisplayName == null || subjectDisplayName.isBlank())
                ? "badany podmiot"
                : subjectDisplayName.trim();

        StringBuilder sb = new StringBuilder();
        sb.append("Analiza OSINT i blockchain dla sprawy \"").append(subject).append("\": ");

        if (confirmedCount == 0) {
            sb.append("nie przypisano żadnego prywatnego portfela niehostowanego (0 potwierdzonych adresów). ");
        } else {
            sb.append("potwierdzono ").append(confirmedCount)
                    .append(confirmedCount == 1 ? " portfel niehostowany" : " portfele niehostowane")
                    .append(" z najwyższym Wskaźnikiem Pewności ")
                    .append(maxScore).append("% ")
                    .append("oraz łącznym szacowanym wolumenem on-chain ")
                    .append(formattedVolume).append(". ");
        }

        if (rejectedCount > 0) {
            long interactedCount = safeRejected.stream().filter(AddressFindingDto::interactionRecorded).count();
            sb.append("Firewall False Positives wykluczył z przypisania osobistego ")
                    .append(rejectedCount)
                    .append(rejectedCount == 1 ? " adres instytucjonalny/kontraktowy (0%)" : " adresów instytucjonalnych/kontraktowych (0%)");
            if (interactedCount > 0) {
                sb.append(", odnotowując bezpośrednią interakcję on-chain przy ")
                        .append(interactedCount)
                        .append(interactedCount == 1 ? " z nich" : " z nich");
            }
            sb.append(". ");
        } else {
            sb.append("Firewall False Positives nie wykrył adresów giełdowych, mikserów ani smart kontraktów wymagających odrzucenia. ");
        }

        if (!safeSignals.isEmpty()) {
            String signalTypes = safeSignals.stream()
                    .map(CashOutSignalDto::signalType)
                    .distinct()
                    .collect(Collectors.joining(", "));
            sb.append("Wykryto ").append(safeSignals.size())
                    .append(safeSignals.size() == 1 ? " sygnał Fiat Cash-Out" : " sygnałów Fiat Cash-Out")
                    .append(" (").append(signalTypes).append("), co wskazuje na podwyższone ryzyko podatkowe PIT-38 (art. 30b ustawy o PIT) i wymaga weryfikacji deklaracji oraz wystąpienia o dane KYC do podmiotów CASP.");
        } else if (confirmedCount > 0 && maxScore < 40) {
            sb.append("Ze względu na niski poziom pewności (szum informacyjny) oraz brak sygnałów Fiat Cash-Out materiał nie uzasadnia automatycznego przypisania przychodu PIT-38 bez dodatkowych dowodów.");
        } else {
            sb.append("Nie stwierdzono bezpośrednich sygnałów wyjścia do waluty fiducjarnej (Fiat Cash-Out) wymagających pilnej weryfikacji PIT-38.");
        }

        return sb.toString();
    }

    private static boolean containsAny(String lowerText, List<String> keywords) {
        for (String kw : keywords) {
            if (lowerText.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private static String formatPln(double amount) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.forLanguageTag("pl-PL"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf.format(amount) + " PLN";
    }
}
