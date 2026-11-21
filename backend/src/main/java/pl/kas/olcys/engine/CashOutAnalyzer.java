package pl.kas.olcys.engine;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.kas.olcys.api.dto.CashOutSignalDto;
import pl.kas.olcys.api.dto.EvidenceArtifactDto;
import pl.kas.olcys.api.dto.OnChainMetricsDto;
import pl.kas.olcys.api.dto.TimelineEventDto;
import pl.kas.olcys.connector.web3.EntityRole;
import pl.kas.olcys.connector.web3.EntityTag;
import pl.kas.olcys.connector.web3.EntityTagProvider;
import pl.kas.olcys.connector.web3.RawOnChainProfile;
import pl.kas.olcys.connector.web3.RawTransaction;
import pl.kas.olcys.engine.ai.LocalRulesAiClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyzes on-chain transactions of confirmed unhosted wallets to build chronological
 * timelines and detect Fiat Cash-Out signals (CEX Off-Ramp, Stablecoin Conversion,
 * P2P/Gateway correlation, and Recurring Cash-Out patterns).
 */
@Component
public class CashOutAnalyzer {

    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b(20\\d{2}-\\d{2}-\\d{2})\\b");
    private static final Set<String> STABLECOIN_ASSETS = Set.of("USDT", "USDC", "DAI", "FDUSD", "PYUSD", "BUSD");
    private static final Duration P2P_CORRELATION_WINDOW = Duration.ofHours(72);

    public record WalletAnalysisResult(
            OnChainMetricsDto metrics,
            List<CashOutSignalDto> signals
    ) {
    }

    private final EntityTagProvider entityTagProvider;

    @Autowired
    public CashOutAnalyzer(EntityTagProvider entityTagProvider) {
        this.entityTagProvider = entityTagProvider;
    }

    public WalletAnalysisResult analyzeWallet(
            RawOnChainProfile profile,
            List<EvidenceArtifactDto> evidences
    ) {
        if (profile == null) {
            return new WalletAnalysisResult(null, List.of());
        }

        List<Instant> p2pEventDates = extractP2pDatesFromEvidences(evidences);
        List<RawTransaction> sortedTxs = profile.transactions() == null
                ? List.of()
                : profile.transactions().stream()
                .sorted(Comparator.comparing(RawTransaction::timestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<TimelineEventDto> timeline = new ArrayList<>();
        List<CashOutSignalDto> signals = new ArrayList<>();
        Set<String> interactedEntities = new LinkedHashSet<>();

        int cexOrCaspOutflowCount = 0;
        double recurringTotalPln = 0.0;
        RawTransaction latestRecurringTx = null;
        Set<String> recurringCounterparties = new LinkedHashSet<>();

        for (RawTransaction tx : sortedTxs) {
            boolean isOutflow = "OUT".equalsIgnoreCase(tx.direction());
            String counterpartyAddr = isOutflow ? tx.toAddress() : tx.fromAddress();
            Optional<EntityTag> cpTagOpt = entityTagProvider.findTag(counterpartyAddr, tx.network());

            String cpTagName = cpTagOpt.map(EntityTag::name)
                    .orElse(nonBlank(tx.counterpartyHint()) ? tx.counterpartyHint() : null);
            String cpRole = cpTagOpt.map(t -> t.role().name()).orElse(null);

            if (cpTagOpt.isPresent()) {
                interactedEntities.add(cpTagOpt.get().name() + " (" + cpTagOpt.get().role().name() + ")");
            } else if (nonBlank(tx.counterpartyHint())) {
                interactedEntities.add(tx.counterpartyHint());
            }

            String signalType = classifyTransactionSignal(tx, isOutflow, cpTagOpt.orElse(null), p2pEventDates);
            String descriptionPl = buildTimelineDescriptionPl(tx, isOutflow, cpTagName, cpRole, signalType);

            timeline.add(new TimelineEventDto(
                    tx.txHash(),
                    tx.timestamp(),
                    tx.network(),
                    tx.direction(),
                    tx.fromAddress(),
                    tx.toAddress(),
                    tx.amount(),
                    tx.asset(),
                    tx.estimatedPln(),
                    cpTagName,
                    cpRole,
                    signalType,
                    descriptionPl
            ));

            if (signalType != null) {
                signals.add(buildSignalDto(profile, tx, signalType, cpTagName, cpRole, descriptionPl));
            }

            if (isOutflow && cpTagOpt.isPresent()) {
                EntityRole role = cpTagOpt.get().role();
                if (role == EntityRole.CEX || role == EntityRole.CASP_P2P) {
                    cexOrCaspOutflowCount++;
                    recurringTotalPln += tx.estimatedPln();
                    latestRecurringTx = tx;
                    recurringCounterparties.add(cpTagOpt.get().name());
                }
            }
        }

        if (cexOrCaspOutflowCount >= 2 && latestRecurringTx != null) {
            String cpSummary = String.join(", ", recurringCounterparties);
            signals.add(new CashOutSignalDto(
                    "sig-" + UUID.randomUUID().toString().substring(0, 8),
                    "RECURRING_CASHOUT",
                    "HIGH",
                    profile.address(),
                    profile.network(),
                    latestRecurringTx.txHash(),
                    latestRecurringTx.timestamp(),
                    Math.round(recurringTotalPln * 100.0) / 100.0,
                    "Powtarzalne wypłaty CEX/CASP (" + cexOrCaspOutflowCount + "×)",
                    String.format(
                            "Wykryto %d niezależne transfery wychodzące z portfela %s do giełd/podmiotów CASP (%s) na łączną szacowaną kwotę %.2f PLN — wzorzec regularnego spieniężania aktywów.",
                            cexOrCaspOutflowCount,
                            profile.address(),
                            cpSummary,
                            recurringTotalPln
                    ),
                    "Wystąp do wskazanych podmiotów CASP o zestawienie wszystkich wypłat fiat (IBAN/BLIK) oraz zweryfikuj ciągłość przychodów w zeznaniach PIT-38."
            ));
        }

        OnChainMetricsDto metrics = new OnChainMetricsDto(
                profile.address(),
                profile.network(),
                FalsePositiveFirewall.isSmartContractBytecode(profile.bytecode()),
                FalsePositiveFirewall.formatBytecodePrefix(profile.bytecode()),
                profile.txCount(),
                profile.totalInflowNative(),
                profile.totalOutflowNative(),
                profile.nativeSymbol(),
                profile.estimatedTotalVolumePln(),
                profile.stablecoinVolumeUsd(),
                List.copyOf(interactedEntities),
                List.copyOf(timeline)
        );

        return new WalletAnalysisResult(metrics, List.copyOf(signals));
    }

    private String classifyTransactionSignal(
            RawTransaction tx,
            boolean isOutflow,
            EntityTag cpTag,
            List<Instant> p2pDates
    ) {
        String assetUpper = tx.asset() == null ? "" : tx.asset().trim().toUpperCase(Locale.ROOT);
        String hintLower = tx.counterpartyHint() == null ? "" : tx.counterpartyHint().toLowerCase(Locale.ROOT);
        EntityRole role = cpTag != null ? cpTag.role() : null;

        if (isOutflow && role == EntityRole.CASP_P2P) {
            String tagNameLower = cpTag.name().toLowerCase(Locale.ROOT);
            boolean isP2pOrGateway = tagNameLower.contains("p2p")
                    || tagNameLower.contains("kantor")
                    || tagNameLower.contains("bitomat")
                    || tagNameLower.contains("ramp")
                    || tagNameLower.contains("moonpay")
                    || hintLower.contains("p2p")
                    || hintLower.contains("kantor")
                    || hintLower.contains("bitomat")
                    || hintLower.contains("ramp")
                    || hintLower.contains("korelacja")
                    || isWithinP2pWindow(tx.timestamp(), p2pDates);
            return isP2pOrGateway ? "P2P_OR_GATEWAY" : "CEX_OFFRAMP";
        }

        if (isOutflow && role == EntityRole.CEX) {
            return "CEX_OFFRAMP";
        }

        if (isOutflow && (hintLower.contains("p2p") || hintLower.contains("kantor")
                || hintLower.contains("bitomat") || hintLower.contains("ramp"))
                && isWithinP2pWindow(tx.timestamp(), p2pDates)) {
            return "P2P_OR_GATEWAY";
        }

        if (role == EntityRole.DEX_ROUTER || role == EntityRole.STABLECOIN
                || (isOutflow && STABLECOIN_ASSETS.contains(assetUpper))) {
            return "STABLECOIN_CONVERSION";
        }

        return null;
    }

    private CashOutSignalDto buildSignalDto(
            RawOnChainProfile profile,
            RawTransaction tx,
            String signalType,
            String cpTagName,
            String cpRole,
            String descriptionPl
    ) {
        String severity = switch (signalType) {
            case "CEX_OFFRAMP", "P2P_OR_GATEWAY", "RECURRING_CASHOUT" -> "HIGH";
            default -> "MEDIUM";
        };
        String verificationHint = switch (signalType) {
            case "CEX_OFFRAMP" ->
                    "Skieruj wniosek KAS (art. 48/49 ustawy o KAS / MiCA / DAC8) do operatora giełdy ("
                            + (cpTagName != null ? cpTagName : "CEX")
                            + ") o dane KYC rachunku depozytowego, powiązane rachunki bankowe (IBAN) i historię wypłat PLN/EUR.";
            case "P2P_OR_GATEWAY" ->
                    "Zweryfikuj korelację czasową z ogłoszeniami P2P/OTC, wyciągami bankowymi/BLIK oraz raportowaniem podmiotu CASP ("
                            + (cpTagName != null ? cpTagName : "Kantor/Bramka")
                            + ") pod kątem niezgłoszonych przychodów PIT-38 lub działalności kantorowej.";
            case "STABLECOIN_CONVERSION" ->
                    "Przeanalizuj konwersję na stablecoiny (USDT/USDC/DAI) jako etap przygotowawczy do wyjścia do waluty fiducjarnej i zweryfikuj dalsze przepływy tokenów.";
            default ->
                    "Zweryfikuj strumień wypłat w kontekście rozliczenia rocznego PIT-38.";
        };

        return new CashOutSignalDto(
                "sig-" + UUID.randomUUID().toString().substring(0, 8),
                signalType,
                severity,
                profile.address(),
                profile.network(),
                tx.txHash(),
                tx.timestamp(),
                tx.estimatedPln(),
                cpTagName != null ? cpTagName : (cpRole != null ? cpRole : "Nieoznaczony kontrahent"),
                descriptionPl,
                verificationHint
        );
    }

    private static String buildTimelineDescriptionPl(
            RawTransaction tx,
            boolean isOutflow,
            String cpTagName,
            String cpRole,
            String signalType
    ) {
        String dirLabel = isOutflow ? "Wypływ" : "Wpływ";
        String target = cpTagName != null ? cpTagName : (isOutflow ? tx.toAddress() : tx.fromAddress());
        if ("CEX_OFFRAMP".equals(signalType)) {
            return String.format("%s %.4f %s (~%.2f PLN) na adres giełdy %s — potencjalny Fiat Cash-Out.",
                    dirLabel, tx.amount(), tx.asset(), tx.estimatedPln(), target);
        }
        if ("P2P_OR_GATEWAY".equals(signalType)) {
            return String.format("%s %.4f %s (~%.2f PLN) przez punkt P2P/bramkę %s — wysoka korelacja z wymianą na PLN.",
                    dirLabel, tx.amount(), tx.asset(), tx.estimatedPln(), target);
        }
        if ("STABLECOIN_CONVERSION".equals(signalType)) {
            return String.format("Konwersja / transfer stablecoin: %.4f %s (~%.2f PLN) przez %s.",
                    tx.amount(), tx.asset(), tx.estimatedPln(), target);
        }
        if (nonBlank(tx.counterpartyHint())) {
            return String.format("%s %.4f %s (~%.2f PLN) — %s.",
                    dirLabel, tx.amount(), tx.asset(), tx.estimatedPln(), tx.counterpartyHint());
        }
        return String.format("%s %.4f %s (~%.2f PLN) z/do %s%s.",
                dirLabel, tx.amount(), tx.asset(), tx.estimatedPln(), target,
                cpRole != null ? " [" + cpRole + "]" : "");
    }

    private static List<Instant> extractP2pDatesFromEvidences(List<EvidenceArtifactDto> evidences) {
        if (evidences == null || evidences.isEmpty()) {
            return List.of();
        }
        List<Instant> dates = new ArrayList<>();
        for (EvidenceArtifactDto ev : evidences) {
            if (ev == null || ev.snippet() == null) {
                continue;
            }
            boolean isTransactional = LocalRulesAiClient.RULE_TRANSACTIONAL.equalsIgnoreCase(ev.contextClassification())
                    || (ev.sourceCategory() != null && ev.sourceCategory().toUpperCase(Locale.ROOT).contains("P2P"))
                    || (ev.sourceCategory() != null && ev.sourceCategory().toUpperCase(Locale.ROOT).contains("MESSENGER"));
            if (!isTransactional) {
                continue;
            }
            Matcher matcher = ISO_DATE_PATTERN.matcher(ev.snippet());
            while (matcher.find()) {
                try {
                    LocalDate ld = LocalDate.parse(matcher.group(1));
                    dates.add(ld.atTime(12, 0).toInstant(ZoneOffset.UTC));
                } catch (Exception ignored) {
                    // Ignore malformed dates
                }
            }
        }
        return dates;
    }

    private static boolean isWithinP2pWindow(Instant txTimestamp, List<Instant> p2pDates) {
        if (txTimestamp == null || p2pDates.isEmpty()) {
            return false;
        }
        for (Instant p2pDate : p2pDates) {
            long diffSeconds = Math.abs(Duration.between(txTimestamp, p2pDate).getSeconds());
            if (diffSeconds <= P2P_CORRELATION_WINDOW.getSeconds()) {
                return true;
            }
        }
        return false;
    }

    private static boolean nonBlank(String s) {
        return s != null && !s.isBlank();
    }
}
