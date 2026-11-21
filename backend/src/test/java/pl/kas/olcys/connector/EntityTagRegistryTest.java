package pl.kas.olcys.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.kas.olcys.connector.web3.EntityRole;
import pl.kas.olcys.connector.web3.EntityTag;
import pl.kas.olcys.connector.web3.EntityTagRegistry;
import pl.kas.olcys.crypto.CryptoAddressValidator;
import pl.kas.olcys.crypto.CryptoAddressValidator.ValidatedAddress;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class EntityTagRegistryTest {

    private EntityTagRegistry registry;
    private CryptoAddressValidator validator;

    @BeforeEach
    void setUp() {
        registry = new EntityTagRegistry(new ObjectMapper());
        registry.init();
        validator = new CryptoAddressValidator();
    }

    @Test
    void shouldLoadAtLeastSixtyEntriesAndCoverAllEntityRoles() {
        List<EntityTag> tags = registry.getAllTags();
        assertThat(tags.size()).isGreaterThanOrEqualTo(60);

        Set<EntityRole> rolesPresent = tags.stream()
                .map(EntityTag::role)
                .collect(Collectors.toSet());
        assertThat(rolesPresent).containsExactlyInAnyOrder(
                EntityRole.CEX,
                EntityRole.CASP_P2P,
                EntityRole.DEX_ROUTER,
                EntityRole.STABLECOIN,
                EntityRole.MIXER,
                EntityRole.CHARITY,
                EntityRole.BURN
        );
    }

    @Test
    void everyAddressInEntityTagsJsonMustPassChecksumValidation() {
        List<EntityTag> tags = registry.getAllTags();
        assertThat(tags).isNotEmpty();

        for (EntityTag tag : tags) {
            assertThat(tag.name()).as("Name for %s", tag.address()).isNotBlank();
            assertThat(tag.notesPl()).as("notesPl for %s", tag.address()).isNotBlank();

            Optional<ValidatedAddress> validated = validator.validateAddress(tag.address());
            assertThat(validated)
                    .as("Validator must recognize address %s (%s)", tag.address(), tag.name())
                    .isPresent();
            assertThat(validated.get().checksumValid())
                    .as("Checksum must be valid for %s (%s)", tag.address(), tag.name())
                    .isTrue();
            assertThat(validated.get().network())
                    .as("Network must match for %s", tag.address())
                    .isEqualTo(tag.network());
        }
    }

    @Test
    void shouldMatchEthCaseInsensitivelyAndBtcTronExactly() {
        String binanceEthLower = "0x28c6c06298d514db089934071355e5743bf21d60";
        String binanceEthUpper = "0X28C6C06298D514DB089934071355E5743BF21D60".replace("0X", "0x");
        assertThat(registry.findTag(binanceEthLower, "ETH"))
                .isPresent()
                .get()
                .extracting(EntityTag::role)
                .isEqualTo(EntityRole.CEX);
        assertThat(registry.findTag(binanceEthUpper, "eth"))
                .isPresent()
                .get()
                .extracting(EntityTag::name)
                .isEqualTo("Binance 14 (Hot Wallet)");

        // Network == null lookup works for ETH and non-ETH
        assertThat(registry.findTag(binanceEthLower, null)).isPresent();
        assertThat(registry.findTag("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", null))
                .isPresent()
                .get()
                .extracting(EntityTag::role)
                .isEqualTo(EntityRole.STABLECOIN);

        // Exact match required for TRON and BTC
        assertThat(registry.findTag("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t".toLowerCase(Locale.ROOT), "TRON"))
                .isEmpty();
        assertThat(registry.findTag("bc1qm34lsc65zpw79lxes69zkqmk6ee3ewf0j77s3h", "BTC"))
                .isPresent()
                .get()
                .extracting(EntityTag::role)
                .isEqualTo(EntityRole.CEX);
        assertThat(registry.findTag("BC1QM34LSC65ZPW79LXES69ZKQMK6EE3EWF0J77S3H", "BTC"))
                .isEmpty();
    }

    @Test
    void scenarioPersonalWalletsMustNotBeTaggedAsKnownEntities() {
        List<String> personalWallets = List.of(
                "0x71C7656EC7ab88b098defB751B7401B5f6d8976F",
                "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045",
                "bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq",
                "0xAb5801a7D398351b8bE11C439e05C5B3259aeC9B",
                "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb",
                "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"
        );
        for (String wallet : personalWallets) {
            assertThat(registry.findTag(wallet, null))
                    .as("Personal wallet %s must not be in entity-tags.json", wallet)
                    .isEmpty();
        }
    }
}
