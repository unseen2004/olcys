package pl.kas.olcys.report;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.kas.olcys.api.dto.AddressFindingDto;
import pl.kas.olcys.api.dto.CashOutSignalDto;
import pl.kas.olcys.api.dto.EvidenceArtifactDto;
import pl.kas.olcys.api.dto.OsintReportDto;
import pl.kas.olcys.api.dto.ReportComputationZoneDto;
import pl.kas.olcys.api.dto.ReportSourceFactDto;
import pl.kas.olcys.api.dto.ReportVerificationZoneDto;
import pl.kas.olcys.api.dto.TimelineEventDto;
import pl.kas.olcys.engine.ai.AiClientRouter;

import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the structured 3-zone KAS OSINT report separating:
 * Zone 1 (Source Facts), Zone 2 (Deterministic Computations), and Zone 3 (Verification Conclusions).
 */
@Component
public class ReportBuilder {

    private final AiClientRouter aiClientRouter;

    @Autowired
    public ReportBuilder(AiClientRouter aiClientRouter) {
        this.aiClientRouter = aiClientRouter;
    }

    public OsintReportDto buildReport(
            String subjectDisplayName,
            List<EvidenceArtifactDto> evidences,
            List<AddressFindingDto> confirmed,
            List<AddressFindingDto> rejected,
            List<CashOutSignalDto> cashOutSignals
    ) {
        List<EvidenceArtifactDto> safeEvidences = evidences == null ? List.of() : evidences;
        List<AddressFindingDto> safeConfirmed = confirmed == null ? List.of() : confirmed;
        List<AddressFindingDto> safeRejected = rejected == null ? List.of() : rejected;
        List<CashOutSignalDto> safeSignals = cashOutSignals == null ? List.of() : cashOutSignals;

        // Zone 1: Verifiable Web2 source facts (Chain-of-Custody)
        List<ReportSourceFactDto> zone1 = safeEvidences.stream()
                .map(ev -> new ReportSourceFactDto(
                        ev.id(),
                        ev.sourceName(),
                        ev.url(),
                        ev.fetchedAt(),
                        ev.sha256(),
                        ev.matchedIdentifier(),
                        ev.snippet(),
                        ev.extractedAddresses()
                ))
                .toList();

        // Zone 2: Deterministic calculations
        double totalVolumePln = round2(safeConfirmed.stream()
                .mapToDouble(f -> f.onChainMetrics() != null ? f.onChainMetrics().estimatedTotalVolumePln() : 0.0)
                .sum());
        double totalStablecoinUsd = round2(safeConfirmed.stream()
                .mapToDouble(f -> f.onChainMetrics() != null ? f.onChainMetrics().stablecoinVolumeUsd() : 0.0)
                .sum());
        int totalTransactions = safeConfirmed.stream()
                .mapToInt(f -> f.onChainMetrics() != null ? f.onChainMetrics().txCount() : 0)
                .sum();

        List<TimelineEventDto> chronologicalFlow = buildChronologicalFlow(safeConfirmed);

        String methodologyPl = "Metodyka KAS OŁCyŚ: (1) Kryptograficzna walidacja wyekstrahowanych adresów "
                + "(ETH EIP-55 Keccak-256, BTC Base58Check/Bech32/Bech32m, TRON Base58Check), "
                + "(2) 3-warstwowy Firewall False Positives (bytecode != 0x dla smart kontraktów, rejestr 83+ podmiotów CEX/CASP/DEX/Mixer/Charity/Burn, próg >10 tys. tx), "
                + "(3) Deterministyczna punktacja Confidence Score (85% własny profil, 70% kontekst transakcyjny P2P/B2B, 50% forum, 20% szum konkursowy; najwyższa kategoria bazowa + premia synergii +15 pp przy >=2 niezależnych źródłach, limit 100%).";

        ReportComputationZoneDto zone2 = new ReportComputationZoneDto(
                methodologyPl,
                safeConfirmed,
                safeRejected,
                totalVolumePln,
                totalStablecoinUsd,
                totalTransactions,
                chronologicalFlow
        );

        // Zone 3: Verification conclusions & PIT-38 risk indicators
        List<String> pit38Indicators = buildPit38RiskIndicators(safeConfirmed, safeSignals, totalVolumePln, totalStablecoinUsd);
        List<String> recommendations = buildInvestigativeRecommendations(safeConfirmed, safeRejected, safeSignals);
        List<String> disclaimers = buildDisclaimers();

        ReportVerificationZoneDto zone3 = new ReportVerificationZoneDto(
                safeSignals,
                pit38Indicators,
                recommendations,
                disclaimers
        );

        String executiveSummaryPl = aiClientRouter.generateExecutiveSummaryPl(
                subjectDisplayName,
                safeConfirmed,
                safeRejected,
                safeSignals,
                totalVolumePln
        );
        String aiProviderUsed = aiClientRouter.lastEffectiveProviderName();

        return new OsintReportDto(
                executiveSummaryPl,
                aiProviderUsed,
                Instant.now(),
                zone1,
                zone2,
                zone3
        );
    }

    private static List<TimelineEventDto> buildChronologicalFlow(List<AddressFindingDto> confirmed) {
        Map<String, TimelineEventDto> dedup = new LinkedHashMap<>();
        for (AddressFindingDto finding : confirmed) {
            if (finding.onChainMetrics() == null || finding.onChainMetrics().timeline() == null) {
                continue;
            }
            for (TimelineEventDto ev : finding.onChainMetrics().timeline()) {
                String key = ev.txHash() + "|" + ev.direction() + "|" + ev.asset() + "|" + ev.amount();
                dedup.putIfAbsent(key, ev);
            }
        }
        List<TimelineEventDto> sorted = new ArrayList<>(dedup.values());
        sorted.sort(Comparator.comparing(TimelineEventDto::timestamp, Comparator.nullsLast(Comparator.naturalOrder())));
        return List.copyOf(sorted);
    }

    private static List<String> buildPit38RiskIndicators(
            List<AddressFindingDto> confirmed,
            List<CashOutSignalDto> signals,
            double totalVolumePln,
            double totalStablecoinUsd
    ) {
        List<String> indicators = new ArrayList<>();
        Set<String> signalTypes = signals.stream().map(CashOutSignalDto::signalType).collect(Collectors.toSet());
        int maxScore = confirmed.stream().mapToInt(AddressFindingDto::confidenceScore).max().orElse(0);

        if (confirmed.isEmpty() || maxScore < 40) {
            indicators.add("Brak podstaw do przypisania obowiązku podatkowego PIT-38 — nie potwierdzono portfeli o wystarczającym wskaźniku pewności (maks. " + maxScore + "%).");
            return indicators;
        }

        double estimatedDisposalRevenuePln = signals.stream()
                .filter(s -> "CEX_OFFRAMP".equals(s.signalType()) || "P2P_OR_GATEWAY".equals(s.signalType()))
                .mapToDouble(CashOutSignalDto::estimatedPln)
                .sum();
        if (estimatedDisposalRevenuePln > 0) {
            double estimatedTax19Pct = round2(estimatedDisposalRevenuePln * 0.19);
            indicators.add(String.format(
                    "Kalkulator luki PIT-38 (art. 30b ust. 1 ustawy o PIT, stawka 19%%): szacowana wartość zidentyfikowanych wyjść Fiat Cash-Out wynosi %s, co przy braku udokumentowanych kosztów uzyskania przychodu (KUP = 0 PLN) implikuje szacowany należny podatek do weryfikacji w wysokości %s.",
                    formatPln(estimatedDisposalRevenuePln),
                    formatPln(estimatedTax19Pct)
            ));
        }

        if (signalTypes.contains("CEX_OFFRAMP")) {
            double cexSum = signals.stream()
                    .filter(s -> "CEX_OFFRAMP".equals(s.signalType()))
                    .mapToDouble(CashOutSignalDto::estimatedPln)
                    .sum();
            indicators.add(String.format(
                    "Ryzyko nieujawnienia odpłatnego zbycia waluty wirtualnej w deklaracji PIT-38 (art. 30b ust. 1a ustawy o PIT): wykryto bezpośrednie transfery wychodzące na giełdy scentralizowane CEX/CASP o łącznej szacowanej wartości %s.",
                    formatPln(cexSum)
            ));
        }

        if (signalTypes.contains("P2P_OR_GATEWAY")) {
            indicators.add("Ryzyko niezgłoszonych przychodów z handlu P2P / BLIK lub prowadzenia niezarejestrowanej działalności kantorowej/OTC: zidentyfikowano ogłoszenia sprzedażowe skorelowane z transferami przez kantory, bitomaty lub bramki krypto-fiat.");
        }

        if (signalTypes.contains("RECURRING_CASHOUT")) {
            indicators.add("Zorganizowany i powtarzalny charakter wypłat (RECURRING_CASHOUT): regularne transze wyjścia do CASP/CEX mogą wskazywać na stałe źródło przychodów lub prowadzenie nierejestrowanej działalności gospodarczej (art. 5a pkt 6 ustawy o PIT / ustawa AML).");
        }

        if (signalTypes.contains("STABLECOIN_CONVERSION") || totalStablecoinUsd > 0) {
            indicators.add(String.format(
                    "Konwersje na stablecoiny (%s USD): choć wymiana krypto-krypto jest co do zasady neutralna w momencie zamiany, stanowi bezpośredni etap pośredni przed spieniężeniem za PLN/EUR oraz wymaga weryfikacji salda przychodów i kosztów uzyskania przychodów (KUP) w PIT-38.",
                    formatNumber(totalStablecoinUsd)
            ));
        }

        if (totalVolumePln >= 50_000.0 && indicators.isEmpty()) {
            indicators.add(String.format(
                    "Istotny wolumen obrotów on-chain (%s) na potwierdzonych portfelach niehostowanych — wskazana weryfikacja rocznych zeznań PIT-38 pod kątem wykazania przychodów oraz kosztów nabycia.",
                    formatPln(totalVolumePln)
            ));
        }

        return List.copyOf(indicators);
    }

    private static List<String> buildInvestigativeRecommendations(
            List<AddressFindingDto> confirmed,
            List<AddressFindingDto> rejected,
            List<CashOutSignalDto> signals
    ) {
        List<String> recs = new ArrayList<>();

        if (!confirmed.isEmpty()) {
            recs.add("Zweryfikować w systemach e-Urząd Skarbowy / Poltax złożenie i treść zeznań PIT-38 podmiotu za lata objęte aktywnością potwierdzonych portfeli niehostowanych.");
        }

        Set<String> counterparties = signals.stream()
                .filter(s -> "CEX_OFFRAMP".equals(s.signalType()) || "P2P_OR_GATEWAY".equals(s.signalType()))
                .map(CashOutSignalDto::counterpartyName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!counterparties.isEmpty()) {
            recs.add("Wystąpić na podstawie art. 48/49 ustawy o KAS (oraz procedur AML/MiCA/DAC8) do zidentyfikowanych operatorów CEX/CASP ("
                    + String.join(", ", counterparties)
                    + ") z żądaniem udostępnienia danych KYC właściciela rachunku depozytowego, historii wypłat fiat oraz powiązanych numerów IBAN.");
        }

        long mixerHits = rejected.stream().filter(fp -> "MIXER".equalsIgnoreCase(fp.rejectionCategory())).count();
        if (mixerHits > 0) {
            recs.add("W materiale pojawił się adres miksera objętego sankcjami (MIXER) — nie przypisywać go jako portfela osobistego podmiotu, lecz odnotować w analizie ryzyka AML.");
        }

        recs.add("Zabezpieczyć pobrane artefakty OSINT (URL, znacznik czasu UTC, skrót SHA-256 ze Strefy 1) w aktach czynności sprawdzających jako materiał porównawczy.");
        return List.copyOf(recs);
    }

    private static List<String> buildDisclaimers() {
        return List.of(
                "Analiza opiera się wyłącznie na pasywnym białym wywiadzie (OSINT) z publicznie dostępnych źródeł Web2 oraz publicznych rejestrów blockchain (Web3).",
                "Wskaźnik Pewności (Confidence Score) ma charakter pomocniczy i deterministyczny — ostateczne przypisanie własności portfela niehostowanego wymaga potwierdzenia w toku czynności sprawdzających lub danych KYC z podmiotów CASP.",
                "Wartości w PLN są szacunkami analitycznymi i w postępowaniu podatkowym muszą zostać przeliczone zgodnie ze średnim kursem NBP z dnia powstania przychodu lub poniesienia kosztu (art. 11a ustawy o PIT).",
                "Wszystkie obliczenia oraz podsumowania AI zostały wykonane w pełni lokalnie (On-Premise), bez transferu danych podmiotu poza infrastrukturę analityczną."
        );
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String formatPln(double v) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.forLanguageTag("pl-PL"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf.format(v) + " PLN";
    }

    private static String formatNumber(double v) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.forLanguageTag("pl-PL"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf.format(v);
    }
}
