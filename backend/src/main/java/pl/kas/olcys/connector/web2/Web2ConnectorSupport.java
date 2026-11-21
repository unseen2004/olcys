package pl.kas.olcys.connector.web2;

import org.slf4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import pl.kas.olcys.api.dto.IdentifierDto;
import pl.kas.olcys.crypto.CryptoAddressValidator;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shared utilities for Web2 OSINT connectors (identifier extraction, safe HTTP GET, context classification).
 */
final class Web2ConnectorSupport {

    static final String USER_AGENT = "OLCyS-KAS-OSINT/1.0 (https://github.com/kas-olcys)";
    private static final int MAX_SNIPPET_LENGTH = 1400;

    private static final Set<String> GENERIC_EMAIL_DOMAINS = Set.of(
            "gmail.com", "googlemail.com", "outlook.com", "hotmail.com", "live.com",
            "yahoo.com", "icloud.com", "proton.me", "protonmail.com",
            "wp.pl", "onet.pl", "o2.pl", "interia.pl", "tlen.pl", "gazeta.pl"
    );

    private static final Pattern SAFE_HANDLE_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{2,64}$");

    private Web2ConnectorSupport() {
    }

    static Optional<String> safeGet(RestClient restClient, String url, Logger log, String acceptHeader) {
        try {
            RestClient.RequestHeadersSpec<?> spec = restClient.get()
                    .uri(url)
                    .header("User-Agent", USER_AGENT);
            if (acceptHeader != null && !acceptHeader.isBlank()) {
                spec = spec.header("Accept", acceptHeader);
            } else {
                spec = spec.accept(MediaType.APPLICATION_JSON);
            }
            String body = spec.retrieve().body(String.class);
            if (body == null || body.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(body);
        } catch (Exception ex) {
            log.debug("Web2 GET request failed for {}: {}", url, ex.getMessage());
            return Optional.empty();
        }
    }

    static List<String> extractHandles(List<IdentifierDto> identifiers, Set<String> preferredTypes) {
        if (identifiers == null || identifiers.isEmpty()) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (IdentifierDto id : identifiers) {
            if (id == null || id.value() == null || id.value().isBlank()) {
                continue;
            }
            String type = id.type() == null ? "" : id.type().trim().toUpperCase(Locale.ROOT);
            if (!preferredTypes.isEmpty() && !preferredTypes.contains(type)) {
                continue;
            }
            String cleaned = stripLeadingAt(id.value().trim());
            if (SAFE_HANDLE_PATTERN.matcher(cleaned).matches()) {
                result.add(cleaned);
            }
        }
        return List.copyOf(result);
    }

    static List<String> extractCustomEmailDomains(List<IdentifierDto> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            return List.of();
        }
        Set<String> domains = new LinkedHashSet<>();
        for (IdentifierDto id : identifiers) {
            if (id == null || id.value() == null) {
                continue;
            }
            String val = id.value().trim();
            int atIdx = val.indexOf('@');
            if (atIdx > 0 && atIdx < val.length() - 1) {
                String domain = val.substring(atIdx + 1).toLowerCase(Locale.ROOT);
                if (!GENERIC_EMAIL_DOMAINS.contains(domain) && domain.contains(".")) {
                    domains.add(domain);
                }
            }
        }
        return List.copyOf(domains);
    }

    static List<String> extractNonEmptyValues(List<IdentifierDto> identifiers, int limit) {
        if (identifiers == null || identifiers.isEmpty()) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (IdentifierDto id : identifiers) {
            if (id == null || id.value() == null || id.value().isBlank()) {
                continue;
            }
            values.add(id.value().trim());
            if (values.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(values);
    }

    static String stripLeadingAt(String value) {
        return value.startsWith("@") ? value.substring(1) : value;
    }

    static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static String sha256(String rawContent) {
        return CryptoAddressValidator.sha256Hex(rawContent);
    }

    static String truncateSnippet(String text) {
        if (text == null) {
            return "";
        }
        String collapsed = text.replaceAll("\\s+", " ").trim();
        if (collapsed.length() <= MAX_SNIPPET_LENGTH) {
            return collapsed;
        }
        return collapsed.substring(0, MAX_SNIPPET_LENGTH - 3) + "...";
    }

    static String classifyContext(String text, String defaultContext) {
        if (text == null || text.isBlank()) {
            return defaultContext;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("giveaway") || lower.contains("airdrop") || lower.contains("konkurs")
                || lower.contains("rozdanie") || lower.contains("free token")) {
            return "MASS_COMMENT_NOISE";
        }
        if (lower.contains("p2p") || lower.contains("otc") || lower.contains("blik")
                || lower.contains("sprzedam") || lower.contains("kupię") || lower.contains("kupie")
                || lower.contains("kantor") || lower.contains("faktura") || lower.contains("kontakt")) {
            return "TRANSACTIONAL_CONTEXT";
        }
        return defaultContext;
    }
}
