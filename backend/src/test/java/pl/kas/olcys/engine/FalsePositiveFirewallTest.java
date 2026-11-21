package pl.kas.olcys.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.kas.olcys.connector.web3.EntityTagRegistry;
import pl.kas.olcys.connector.web3.RawOnChainProfile;
import pl.kas.olcys.connector.web3.RawTransaction;
import pl.kas.olcys.engine.FalsePositiveFirewall.FirewallDecision;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FalsePositiveFirewallTest {

    private FalsePositiveFirewall firewall;

    @BeforeEach
    void setUp() {
        EntityTagRegistry registry = new EntityTagRegistry(new ObjectMapper());
        registry.init();
        firewall = new FalsePositiveFirewall(registry);
    }

    @Test
    void shouldRejectSmartContractBytecodeNot0xWithExplicitReason() {
        String uniswapV3 = "0xE592427A0AEce92De3Edee1F18E0157C05861564";
        RawOnChainProfile contractProfile = new RawOnChainProfile(
                uniswapV3, "ETH", "0x608060405234801561001057", 620_000,
                0, 0, "ETH", 0, 0, "Uniswap V3: SwapRouter", List.of()
        );

        FirewallDecision taggedDecision = firewall.evaluate(uniswapV3, "ETH", contractProfile);
        assertThat(taggedDecision.falsePositive()).isTrue();
        assertThat(taggedDecision.rejectionCategory()).isEqualTo("DEX_ROUTER");
        assertThat(taggedDecision.rejectionReason()).contains("bytecode != 0x");
        assertThat(taggedDecision.entityTag()).contains("Uniswap V3");

        String unknownContract = "0x1111111111111111111111111111111111111111";
        RawOnChainProfile untaggedContract = new RawOnChainProfile(
                unknownContract, "ETH", "0x6080604052", 42,
                0, 0, "ETH", 0, 0, null, List.of()
        );
        FirewallDecision untaggedDecision = firewall.evaluate(unknownContract, "ETH", untaggedContract);
        assertThat(untaggedDecision.falsePositive()).isTrue();
        assertThat(untaggedDecision.rejectionCategory()).isEqualTo("SMART_CONTRACT");
        assertThat(untaggedDecision.rejectionReason()).contains("bytecode != 0x");
    }

    @Test
    void shouldRejectKnownCexMixerAndCharityEntitiesAndExplorerTags() {
        // Binance 14 (CEX)
        String binance = "0x28C6c06298d514Db089934071355E5743bf21d60";
        RawOnChainProfile binanceProfile = new RawOnChainProfile(
                binance, "ETH", "0x", 450_000, 0, 0, "ETH", 0, 0, "Binance 14 (Hot Wallet)", List.of()
        );
        FirewallDecision cexDecision = firewall.evaluate(binance, "ETH", binanceProfile);
        assertThat(cexDecision.falsePositive()).isTrue();
        assertThat(cexDecision.rejectionCategory()).isEqualTo("CEX");
        assertThat(cexDecision.entityTag()).contains("Binance");

        // Tornado Cash (MIXER + contract)
        String tornado = "0xd90e2f925DA726b50C4Ed8D0Fb90Ad053324F31b";
        RawOnChainProfile tornadoProfile = new RawOnChainProfile(
                tornado, "ETH", "0x60806040", 145_000, 0, 0, "ETH", 0, 0, "Tornado Cash: Router", List.of()
        );
        FirewallDecision mixerDecision = firewall.evaluate(tornado, "ETH", tornadoProfile);
        assertThat(mixerDecision.falsePositive()).isTrue();
        assertThat(mixerDecision.rejectionCategory()).isEqualTo("MIXER");

        // Aid For Ukraine (CHARITY)
        String charity = "0x165CD37b4C644C2921454429E7F9358d18A45e14";
        RawOnChainProfile charityProfile = new RawOnChainProfile(
                charity, "ETH", "0x", 14_500, 0, 0, "ETH", 0, 0, "Aid For Ukraine (Official ETH)", List.of()
        );
        FirewallDecision charityDecision = firewall.evaluate(charity, "ETH", charityProfile);
        assertThat(charityDecision.falsePositive()).isTrue();
        assertThat(charityDecision.rejectionCategory()).isEqualTo("CHARITY");

        // Unknown address flagged only via explorerTag
        String unknownAddr = "0x9999999999999999999999999999999999999999";
        RawOnChainProfile taggedByExplorer = new RawOnChainProfile(
                unknownAddr, "ETH", "0x", 500, 0, 0, "ETH", 0, 0, "Unknown Exchange Hot Wallet", List.of()
        );
        FirewallDecision explorerDecision = firewall.evaluate(unknownAddr, "ETH", taggedByExplorer);
        assertThat(explorerDecision.falsePositive()).isTrue();
        assertThat(explorerDecision.rejectionCategory()).isEqualTo("CEX");
    }

    @Test
    void shouldRejectHighVolumeSuspectedCustodialOver10000TxAndAcceptPersonalWallet() {
        String highVolAddr = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F";
        RawOnChainProfile custodial = new RawOnChainProfile(
                highVolAddr, "ETH", "0x", 10_001, 0, 0, "ETH", 0, 0, null, List.of()
        );
        FirewallDecision rejected = firewall.evaluate(highVolAddr, "ETH", custodial);
        assertThat(rejected.falsePositive()).isTrue();
        assertThat(rejected.rejectionCategory()).isEqualTo("SUSPECTED_CUSTODIAL");

        RawOnChainProfile personal = new RawOnChainProfile(
                highVolAddr, "ETH", "0x", 34, 7.8, 6.8, "ETH", 112_400, 12_100, null, List.of()
        );
        FirewallDecision accepted = firewall.evaluate(highVolAddr, "ETH", personal);
        assertThat(accepted.falsePositive()).isFalse();
    }

    @Test
    void shouldDetectInteractionRecordedForRejectedAddresses() {
        String personalAddr = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F";
        String binanceAddr = "0x28C6c06298d514Db089934071355E5743bf21d60";
        String aidAddr = "0x165CD37b4C644C2921454429E7F9358d18A45e14";

        RawOnChainProfile confirmedProfile = new RawOnChainProfile(
                personalAddr,
                "ETH",
                "0x",
                5,
                1.0,
                1.0,
                "ETH",
                11_000.0,
                0.0,
                null,
                List.of(new RawTransaction(
                        "0xabc", Instant.now(), "ETH", "OUT",
                        personalAddr, binanceAddr.toLowerCase(), 1.0, "ETH", 11_000.0, "Binance 14"
                ))
        );

        assertThat(firewall.detectInteraction(binanceAddr, "ETH", "Binance 14 (Hot Wallet)", List.of(confirmedProfile)))
                .isTrue();
        assertThat(firewall.detectInteraction(aidAddr, "ETH", "Aid For Ukraine (Official ETH)", List.of(confirmedProfile)))
                .isFalse();
    }
}
