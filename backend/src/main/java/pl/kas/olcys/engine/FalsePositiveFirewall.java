package pl.kas.olcys.engine;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.kas.olcys.connector.web3.EntityRole;
import pl.kas.olcys.connector.web3.EntityTag;
import pl.kas.olcys.connector.web3.EntityTagProvider;
import pl.kas.olcys.connector.web3.RawOnChainProfile;
import pl.kas.olcys.connector.web3.RawTransaction;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Deterministic 3-layer False Positive Firewall protecting against misattribution of
 * smart contracts, exchange/institutional wallets, mixers, charities, and high-volume custodial wallets.
 */
@Component
public class FalsePositiveFirewall {

    public static final int CUSTODIAL_TX_THRESHOLD = 10_000;

    public record FirewallDecision(
            boolean falsePositive,
            String rejectionCategory,
            String rejectionReason,
            String entityTag
    ) {
        public static FirewallDecision accepted() {
            return new FirewallDecision(false, null, null, null);
        }

        public static FirewallDecision rejected(String category, String reason, String entityTag) {
            return new FirewallDecision(true, category, reason, entityTag);
        }
    }

    private final EntityTagProvider entityTagProvider;

    @Autowired
    public FalsePositiveFirewall(EntityTagProvider entityTagProvider) {
        this.entityTagProvider = entityTagProvider;
    }

    public FirewallDecision evaluate(String address, String network, RawOnChainProfile profile) {
        Optional<EntityTag> knownTag = entityTagProvider.findTag(address, network);
        String explorerTag = profile != null ? profile.explorerTag() : null;

        // Layer 1: Smart Contract check (eth_getCode -> bytecode != 0x)
        if (profile != null && isSmartContractBytecode(profile.bytecode())) {
            String category = knownTag
                    .map(EntityTag::role)
                    .filter(role -> role != EntityRole.PERSONAL_UNHOSTED)
                    .map(Enum::name)
                    .orElse("SMART_CONTRACT");
            String tagLabel = knownTag.map(EntityTag::name)
                    .orElse(hasText(explorerTag) ? explorerTag.trim() : "Smart Kontrakt");
            String prefix = formatBytecodePrefix(profile.bytecode());

            StringBuilder reason = new StringBuilder();
            reason.append("Wykluczono w Warstwie 1 (Smart Kontrakt): wywołanie eth_getCode wykazało niepusty kod bajtowy (bytecode != 0x, prefiks: ")
                    .append(prefix)
                    .append("). Smart kontrakt nie może stanowić prywatnego portfela niehostowanego.");
            knownTag.ifPresent(tag -> reason.append(" Zidentyfikowano w rejestrze KAS: ")
                    .append(tag.name()).append(" [").append(tag.role()).append("]. ")
                    .append(tag.notesPl()));

            return FirewallDecision.rejected(category, reason.toString(), tagLabel);
        }

        // Layer 2: Known institutional entities (CEX / CASP / MIXER / CHARITY / BURN / DEX_ROUTER / STABLECOIN)
        if (knownTag.isPresent() && knownTag.get().role() != EntityRole.PERSONAL_UNHOSTED) {
            EntityTag tag = knownTag.get();
            String reason = String.format(
                    "Wykluczono w Warstwie 2 (Rejestr Podmiotów Instytucjonalnych KAS): adres należy do kategorii %s — %s. %s",
                    tag.role().name(),
                    tag.name(),
                    tag.notesPl()
            );
            return FirewallDecision.rejected(tag.role().name(), reason, tag.name());
        }

        if (hasText(explorerTag)) {
            Optional<String> inferredCategory = inferCategoryFromExplorerTag(explorerTag);
            if (inferredCategory.isPresent()) {
                String category = inferredCategory.get();
                String reason = String.format(
                        "Wykluczono w Warstwie 2 (Tag eksploratora on-chain): publiczna etykieta '%s' wskazuje na adres instytucjonalny/usługowy (%s), a nie prywatny portfel podmiotu.",
                        explorerTag.trim(),
                        category
                );
                return FirewallDecision.rejected(category, reason, explorerTag.trim());
            }
        }

        // Layer 3: High-volume custodial / institutional heuristic (> 10,000 transactions)
        if (profile != null && profile.txCount() > CUSTODIAL_TX_THRESHOLD) {
            String tagLabel = hasText(explorerTag) ? explorerTag.trim() : "Adres wysokowolumenowy (>10 tys. tx)";
            String reason = String.format(
                    "Wykluczono w Warstwie 3 (Heurystyka wolumenowa): liczba transakcji on-chain (%d) przekracza próg %d, co wskazuje na portfel powierniczy lub zbiorczy o skali instytucjonalnej (SUSPECTED_CUSTODIAL).",
                    profile.txCount(),
                    CUSTODIAL_TX_THRESHOLD
            );
            return FirewallDecision.rejected("SUSPECTED_CUSTODIAL", reason, tagLabel);
        }

        return FirewallDecision.accepted();
    }

    /**
     * Checks whether any confirmed unhosted wallet of the subject has transacted directly
     * with the rejected address (or an address sharing its exact entity tag).
     */
    public boolean detectInteraction(
            String rejectedAddress,
            String rejectedNetwork,
            String rejectedEntityTag,
            List<RawOnChainProfile> confirmedWalletProfiles
    ) {
        if (rejectedAddress == null || rejectedAddress.isBlank()
                || confirmedWalletProfiles == null || confirmedWalletProfiles.isEmpty()) {
            return false;
        }
        for (RawOnChainProfile walletProfile : confirmedWalletProfiles) {
            if (walletProfile == null || walletProfile.transactions() == null) {
                continue;
            }
            for (RawTransaction tx : walletProfile.transactions()) {
                if (addressesEqual(rejectedAddress, rejectedNetwork, tx.fromAddress(), tx.network())
                        || addressesEqual(rejectedAddress, rejectedNetwork, tx.toAddress(), tx.network())) {
                    return true;
                }
                if (hasText(rejectedEntityTag)) {
                    String counterparty = "OUT".equalsIgnoreCase(tx.direction()) ? tx.toAddress() : tx.fromAddress();
                    Optional<EntityTag> cpTag = entityTagProvider.findTag(counterparty, tx.network());
                    if (cpTag.isPresent() && cpTag.get().name().equalsIgnoreCase(rejectedEntityTag)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isSmartContractBytecode(String bytecode) {
        return bytecode != null && !bytecode.isBlank() && !"0x".equalsIgnoreCase(bytecode.trim());
    }

    public static String formatBytecodePrefix(String bytecode) {
        if (bytecode == null || bytecode.isBlank()) {
            return "0x";
        }
        String trimmed = bytecode.trim();
        if (trimmed.length() <= 20) {
            return trimmed;
        }
        return trimmed.substring(0, 20) + "...";
    }

    private static boolean addressesEqual(String addr1, String net1, String addr2, String net2) {
        if (addr1 == null || addr2 == null) {
            return false;
        }
        if (net1 != null && net2 != null && !net1.equalsIgnoreCase(net2)) {
            return false;
        }
        if ("ETH".equalsIgnoreCase(net1) || "EVM".equalsIgnoreCase(net1)
                || (addr1.startsWith("0x") && addr2.startsWith("0x"))) {
            return addr1.equalsIgnoreCase(addr2);
        }
        return addr1.equals(addr2);
    }

    private static Optional<String> inferCategoryFromExplorerTag(String explorerTag) {
        String lower = explorerTag.toLowerCase(Locale.ROOT);
        if (lower.contains("tornado") || lower.contains("mixer")) {
            return Optional.of("MIXER");
        }
        if (lower.contains("charity") || lower.contains("foundation") || lower.contains("aid for ukraine")
                || lower.contains("zbiórka") || lower.contains("fundacja")) {
            return Optional.of("CHARITY");
        }
        if (lower.contains("burn") || lower.contains("eater") || lower.contains("null address")) {
            return Optional.of("BURN");
        }
        if (lower.contains("uniswap") || lower.contains("sushiswap") || lower.contains("1inch")
                || lower.contains("router") || lower.contains("swap") || lower.contains("dex")) {
            return Optional.of("DEX_ROUTER");
        }
        if (lower.contains("usdt") || lower.contains("usdc") || lower.contains("stablecoin")
                || lower.contains("tether")) {
            return Optional.of("STABLECOIN");
        }
        if (lower.contains("binance") || lower.contains("kraken") || lower.contains("coinbase")
                || lower.contains("okx") || lower.contains("bybit") || lower.contains("bitfinex")
                || lower.contains("kucoin") || lower.contains("gate.io") || lower.contains("crypto.com")
                || lower.contains("htx") || lower.contains("hot wallet") || lower.contains("cold wallet")
                || lower.contains("exchange")) {
            return Optional.of("CEX");
        }
        if (lower.contains("zondacrypto") || lower.contains("bitbay") || lower.contains("kantor")
                || lower.contains("bitomat") || lower.contains("ramp") || lower.contains("moonpay")
                || lower.contains("revolut") || lower.contains("custody")) {
            return Optional.of("CASP_P2P");
        }
        if (lower.contains("contract") || lower.contains("kontrakt")) {
            return Optional.of("SMART_CONTRACT");
        }
        return Optional.empty();
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
