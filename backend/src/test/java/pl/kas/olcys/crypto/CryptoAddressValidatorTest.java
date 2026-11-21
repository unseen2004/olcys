package pl.kas.olcys.crypto;

import org.junit.jupiter.api.Test;
import pl.kas.olcys.crypto.CryptoAddressValidator.ValidatedAddress;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoAddressValidatorTest {

    private final CryptoAddressValidator validator = new CryptoAddressValidator();

    @Test
    void shouldValidateEthereumEip55AndLowercaseAndRejectInvalidChecksum() {
        String validEip55 = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045";
        Optional<ValidatedAddress> res = validator.validateAddress(validEip55);
        assertThat(res).isPresent();
        assertThat(res.get().checksumValid()).isTrue();
        assertThat(res.get().network()).isEqualTo("ETH");
        assertThat(res.get().checksumFormat()).isEqualTo("EIP-55");
        assertThat(res.get().normalizedAddress()).isEqualTo(validEip55);

        String lowercase = validEip55.toLowerCase();
        Optional<ValidatedAddress> lowerRes = validator.validateAddress(lowercase);
        assertThat(lowerRes).isPresent();
        assertThat(lowerRes.get().checksumValid()).isTrue();
        assertThat(lowerRes.get().normalizedAddress()).isEqualTo(validEip55);

        // Corrupt mixed-case EIP-55 (flip one letter case)
        String invalidEip55 = "0xD8dA6BF26964aF9D7eEd9e03E53415D37aA96045";
        Optional<ValidatedAddress> invalidRes = validator.validateAddress(invalidEip55);
        assertThat(invalidRes).isPresent();
        assertThat(invalidRes.get().checksumValid()).isFalse();
    }

    @Test
    void shouldValidateBitcoinLegacyP2shSegwitAndTaproot() {
        Optional<ValidatedAddress> legacy = validator.validateAddress("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa");
        assertThat(legacy).isPresent();
        assertThat(legacy.get().checksumValid()).isTrue();
        assertThat(legacy.get().network()).isEqualTo("BTC");
        assertThat(legacy.get().checksumFormat()).isEqualTo("BASE58CHECK");

        Optional<ValidatedAddress> p2sh = validator.validateAddress("3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy");
        assertThat(p2sh).isPresent();
        assertThat(p2sh.get().checksumValid()).isTrue();
        assertThat(p2sh.get().checksumFormat()).isEqualTo("BASE58CHECK");

        Optional<ValidatedAddress> brokenLegacy = validator.validateAddress("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNb");
        assertThat(brokenLegacy).isPresent();
        assertThat(brokenLegacy.get().checksumValid()).isFalse();

        Optional<ValidatedAddress> bech32 = validator.validateAddress("bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq");
        assertThat(bech32).isPresent();
        assertThat(bech32.get().checksumValid()).isTrue();
        assertThat(bech32.get().network()).isEqualTo("BTC");
        assertThat(bech32.get().checksumFormat()).isEqualTo("BECH32");

        Optional<ValidatedAddress> brokenBech32 = validator.validateAddress("bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdr");
        assertThat(brokenBech32).isPresent();
        assertThat(brokenBech32.get().checksumValid()).isFalse();

        Optional<ValidatedAddress> taproot = validator.validateAddress("bc1p5d7rjq7g6rdk2yhzks9smlaqtedr4dekq08ge8ztwac72sfr9rusxg3297");
        assertThat(taproot).isPresent();
        assertThat(taproot.get().checksumValid()).isTrue();
        assertThat(taproot.get().checksumFormat()).isEqualTo("BECH32M");
    }

    @Test
    void shouldValidateTronBase58Check() {
        Optional<ValidatedAddress> usdtTron = validator.validateAddress("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t");
        assertThat(usdtTron).isPresent();
        assertThat(usdtTron.get().checksumValid()).isTrue();
        assertThat(usdtTron.get().network()).isEqualTo("TRON");
        assertThat(usdtTron.get().checksumFormat()).isEqualTo("TRON_BASE58CHECK");

        Optional<ValidatedAddress> brokenTron = validator.validateAddress("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6u");
        assertThat(brokenTron).isPresent();
        assertThat(brokenTron.get().checksumValid()).isFalse();
    }

    @Test
    void shouldExtractValidAddressesAndIgnoreTxHashesAndCorruptedChecksums() {
        String text = """
                Profil GitHub: rozliczenia na 0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045 lub BTC bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq.
                Transakcja tx hash (ignorowana!): 0x9fc76417374aa880d4449a1f7f31ec597f00b1f6f3dd2d66f4c9c6c445836d8b
                Uszkodzony adres ETH (ignorowany!): 0xD8dA6BF26964aF9D7eEd9e03E53415D37aA96045
                Wpłata USDT TRC20 na adres TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t
                """;

        List<ValidatedAddress> extracted = validator.extractValidatedAddresses(text);
        assertThat(extracted)
                .extracting(ValidatedAddress::normalizedAddress)
                .containsExactlyInAnyOrder(
                        "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045",
                        "bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq",
                        "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
                );
    }

    @Test
    void shouldComputeDeterministicSha256Hex() {
        String hash = CryptoAddressValidator.sha256Hex("OŁCyŚ-KAS-2026");
        assertThat(hash).hasSize(64);
        assertThat(hash).isEqualTo(CryptoAddressValidator.sha256Hex("OŁCyŚ-KAS-2026"));
    }
}
