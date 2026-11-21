package pl.kas.olcys.crypto;

import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CryptoAddressValidator {

    public record ValidatedAddress(
            String rawAddress,
            String normalizedAddress,
            String network,
            String checksumFormat,
            boolean checksumValid
    ) {
    }

    private static final String BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final int[] BASE58_INDEXES = new int[128];
    private static final String BECH32_CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l";
    private static final int[] BECH32_GENERATOR = {0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3};
    private static final int BECH32_CONST = 1;
    private static final int BECH32M_CONST = 0x2bc830a3;

    private static final Pattern ETH_PATTERN = Pattern.compile("(?<![0-9a-fA-F])0x[0-9a-fA-F]{40}(?![0-9a-fA-F])");
    private static final Pattern BTC_BECH32_PATTERN = Pattern.compile("(?<![0-9a-zA-Z])[bB][cC]1[qpzry9x8gf2tvdw0s3jn54khce6mua7lQPZRY9X8GF2TVDW0S3JN54KHCE6MUA7L]{38,59}(?![0-9a-zA-Z])");
    private static final Pattern BTC_BASE58_PATTERN = Pattern.compile("(?<![0-9a-zA-Z])[13][1-9A-HJ-NP-Za-km-z]{25,34}(?![0-9a-zA-Z])");
    private static final Pattern TRON_PATTERN = Pattern.compile("(?<![0-9a-zA-Z])T[1-9A-HJ-NP-Za-km-z]{33}(?![0-9a-zA-Z])");

    static {
        Arrays.fill(BASE58_INDEXES, -1);
        for (int i = 0; i < BASE58_ALPHABET.length(); i++) {
            BASE58_INDEXES[BASE58_ALPHABET.charAt(i)] = i;
        }
    }

    public List<ValidatedAddress> extractValidatedAddresses(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Map<String, ValidatedAddress> deduplicated = new LinkedHashMap<>();

        collectMatches(ETH_PATTERN, text, deduplicated);
        collectMatches(BTC_BECH32_PATTERN, text, deduplicated);
        collectMatches(BTC_BASE58_PATTERN, text, deduplicated);
        collectMatches(TRON_PATTERN, text, deduplicated);

        return List.copyOf(deduplicated.values());
    }

    private void collectMatches(Pattern pattern, String text, Map<String, ValidatedAddress> sink) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group();
            validateAddress(candidate)
                    .filter(ValidatedAddress::checksumValid)
                    .ifPresent(v -> sink.putIfAbsent(v.network() + ":" + v.normalizedAddress(), v));
        }
    }

    public Optional<ValidatedAddress> validateAddress(String rawCandidate) {
        if (rawCandidate == null) {
            return Optional.empty();
        }
        String candidate = rawCandidate.trim();
        if (candidate.isEmpty()) {
            return Optional.empty();
        }

        if (candidate.startsWith("0x") || candidate.startsWith("0X")) {
            return validateEthereum(candidate);
        }
        String lower = candidate.toLowerCase(Locale.ROOT);
        if (lower.startsWith("bc1")) {
            return validateBtcBech32(candidate);
        }
        if (candidate.startsWith("1") || candidate.startsWith("3")) {
            return validateBtcBase58Check(candidate);
        }
        if (candidate.startsWith("T") && candidate.length() == 34) {
            return validateTronBase58Check(candidate);
        }
        return Optional.empty();
    }

    private Optional<ValidatedAddress> validateEthereum(String candidate) {
        if (!candidate.matches("^0[xX][0-9a-fA-F]{40}$")) {
            return Optional.empty();
        }
        String hexBody = candidate.substring(2);
        String lowerHex = hexBody.toLowerCase(Locale.ROOT);
        String eip55Address = toEip55ChecksumAddress(lowerHex);

        boolean allLower = hexBody.equals(lowerHex);
        boolean allUpper = hexBody.equals(hexBody.toUpperCase(Locale.ROOT));
        boolean matchesEip55 = candidate.equals(eip55Address);
        boolean valid = allLower || allUpper || matchesEip55;

        return Optional.of(new ValidatedAddress(
                candidate,
                eip55Address,
                "ETH",
                "EIP-55",
                valid
        ));
    }

    public static String toEip55ChecksumAddress(String hexWithoutPrefixLower) {
        Keccak.Digest256 keccak = new Keccak.Digest256();
        byte[] hash = keccak.digest(hexWithoutPrefixLower.getBytes(StandardCharsets.US_ASCII));
        StringBuilder sb = new StringBuilder(42);
        sb.append("0x");
        for (int i = 0; i < 40; i++) {
            char c = hexWithoutPrefixLower.charAt(i);
            int hashByte = hash[i / 2] & 0xFF;
            int nibble = (i % 2 == 0) ? (hashByte >> 4) : (hashByte & 0x0F);
            if (c >= 'a' && c <= 'f' && nibble >= 8) {
                sb.append(Character.toUpperCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Optional<ValidatedAddress> validateBtcBase58Check(String candidate) {
        if (candidate.length() < 26 || candidate.length() > 35) {
            return Optional.empty();
        }
        byte[] decoded = decodeBase58(candidate);
        if (decoded == null || decoded.length != 25) {
            return Optional.of(new ValidatedAddress(candidate, candidate, "BTC", "BASE58CHECK", false));
        }
        int version = decoded[0] & 0xFF;
        boolean versionOk = (version == 0x00 || version == 0x05);
        boolean checksumOk = verifyBase58Checksum(decoded);
        return Optional.of(new ValidatedAddress(
                candidate,
                candidate,
                "BTC",
                "BASE58CHECK",
                versionOk && checksumOk
        ));
    }

    private Optional<ValidatedAddress> validateTronBase58Check(String candidate) {
        if (candidate.length() != 34) {
            return Optional.empty();
        }
        byte[] decoded = decodeBase58(candidate);
        if (decoded == null || decoded.length != 25) {
            return Optional.of(new ValidatedAddress(candidate, candidate, "TRON", "TRON_BASE58CHECK", false));
        }
        int version = decoded[0] & 0xFF;
        boolean versionOk = (version == 0x41);
        boolean checksumOk = verifyBase58Checksum(decoded);
        return Optional.of(new ValidatedAddress(
                candidate,
                candidate,
                "TRON",
                "TRON_BASE58CHECK",
                versionOk && checksumOk
        ));
    }

    private Optional<ValidatedAddress> validateBtcBech32(String candidate) {
        boolean hasLower = false;
        boolean hasUpper = false;
        for (int i = 0; i < candidate.length(); i++) {
            char ch = candidate.charAt(i);
            if (ch >= 'a' && ch <= 'z') hasLower = true;
            if (ch >= 'A' && ch <= 'Z') hasUpper = true;
        }
        if (hasLower && hasUpper) {
            return Optional.of(new ValidatedAddress(candidate, candidate, "BTC", "BECH32", false));
        }

        String lower = candidate.toLowerCase(Locale.ROOT);
        int pos = lower.lastIndexOf('1');
        if (pos < 1 || pos + 7 > lower.length() || lower.length() > 90) {
            return Optional.of(new ValidatedAddress(candidate, lower, "BTC", "BECH32", false));
        }
        String hrp = lower.substring(0, pos);
        if (!"bc".equals(hrp)) {
            return Optional.of(new ValidatedAddress(candidate, lower, "BTC", "BECH32", false));
        }
        String dataPart = lower.substring(pos + 1);
        int[] data = new int[dataPart.length()];
        for (int i = 0; i < dataPart.length(); i++) {
            int idx = BECH32_CHARSET.indexOf(dataPart.charAt(i));
            if (idx < 0) {
                return Optional.of(new ValidatedAddress(candidate, lower, "BTC", "BECH32", false));
            }
            data[i] = idx;
        }

        int polymod = bech32Polymod(hrpExpand(hrp), data);
        if (polymod != BECH32_CONST && polymod != BECH32M_CONST) {
            return Optional.of(new ValidatedAddress(candidate, lower, "BTC", "BECH32", false));
        }

        int witnessVersion = data[0];
        if (witnessVersion > 16) {
            return Optional.of(new ValidatedAddress(candidate, lower, "BTC", "BECH32", false));
        }
        if (witnessVersion == 0 && polymod != BECH32_CONST) {
            return Optional.of(new ValidatedAddress(candidate, lower, "BTC", "BECH32", false));
        }
        if (witnessVersion >= 1 && polymod != BECH32M_CONST) {
            return Optional.of(new ValidatedAddress(candidate, lower, "BTC", "BECH32M", false));
        }

        int[] program5Bit = Arrays.copyOfRange(data, 1, data.length - 6);
        byte[] program = convertBits5To8(program5Bit);
        if (program == null || program.length < 2 || program.length > 40) {
            return Optional.of(new ValidatedAddress(candidate, lower, "BTC", witnessVersion == 0 ? "BECH32" : "BECH32M", false));
        }
        if (witnessVersion == 0 && program.length != 20 && program.length != 32) {
            return Optional.of(new ValidatedAddress(candidate, lower, "BTC", "BECH32", false));
        }
        if (witnessVersion == 1 && program.length != 32) {
            return Optional.of(new ValidatedAddress(candidate, lower, "BTC", "BECH32M", false));
        }

        String format = (witnessVersion == 0) ? "BECH32" : "BECH32M";
        return Optional.of(new ValidatedAddress(candidate, lower, "BTC", format, true));
    }

    private static int[] hrpExpand(String hrp) {
        int len = hrp.length();
        int[] ret = new int[len * 2 + 1];
        for (int i = 0; i < len; i++) {
            ret[i] = hrp.charAt(i) >> 5;
            ret[i + len + 1] = hrp.charAt(i) & 31;
        }
        ret[len] = 0;
        return ret;
    }

    private static int bech32Polymod(int[] hrpExpanded, int[] values) {
        int chk = 1;
        for (int v : hrpExpanded) {
            chk = polymodStep(chk, v);
        }
        for (int v : values) {
            chk = polymodStep(chk, v);
        }
        return chk;
    }

    private static int polymodStep(int chk, int v) {
        int top = chk >>> 25;
        chk = ((chk & 0x1ffffff) << 5) ^ v;
        for (int i = 0; i < 5; i++) {
            if (((top >> i) & 1) != 0) {
                chk ^= BECH32_GENERATOR[i];
            }
        }
        return chk;
    }

    private static byte[] convertBits5To8(int[] in) {
        int acc = 0;
        int bits = 0;
        List<Byte> out = new ArrayList<>();
        for (int value : in) {
            if (value < 0 || value >= 32) {
                return null;
            }
            acc = (acc << 5) | value;
            bits += 5;
            while (bits >= 8) {
                bits -= 8;
                out.add((byte) ((acc >> bits) & 0xFF));
            }
        }
        if (bits >= 5 || ((acc << (8 - bits)) & 0xFF) != 0) {
            return null;
        }
        byte[] result = new byte[out.size()];
        for (int i = 0; i < out.size(); i++) {
            result[i] = out.get(i);
        }
        return result;
    }

    private static byte[] decodeBase58(String input) {
        if (input.isEmpty()) {
            return null;
        }
        BigInteger value = BigInteger.ZERO;
        BigInteger base = BigInteger.valueOf(58);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            int digit = (c < 128) ? BASE58_INDEXES[c] : -1;
            if (digit < 0) {
                return null;
            }
            value = value.multiply(base).add(BigInteger.valueOf(digit));
        }
        byte[] raw = value.toByteArray();
        boolean stripSignByte = raw.length > 1 && raw[0] == 0 && raw[1] < 0;
        int leadingZeros = 0;
        for (int i = 0; i < input.length() && input.charAt(i) == '1'; i++) {
            leadingZeros++;
        }
        int offset = stripSignByte ? 1 : 0;
        int payloadLen = (raw.length == 1 && raw[0] == 0) ? 0 : (raw.length - offset);
        byte[] result = new byte[leadingZeros + payloadLen];
        if (payloadLen > 0) {
            System.arraycopy(raw, offset, result, leadingZeros, payloadLen);
        }
        return result;
    }

    private static boolean verifyBase58Checksum(byte[] decoded25) {
        byte[] payload = Arrays.copyOfRange(decoded25, 0, 21);
        byte[] expectedChecksum = Arrays.copyOfRange(decoded25, 21, 25);
        byte[] hash1 = sha256(payload);
        byte[] hash2 = sha256(hash1);
        for (int i = 0; i < 4; i++) {
            if (expectedChecksum[i] != hash2[i]) {
                return false;
            }
        }
        return true;
    }

    public static byte[] sha256(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public static String sha256Hex(String input) {
        byte[] bytes = (input == null ? "" : input).getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(sha256(bytes));
    }
}
