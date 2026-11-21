package pl.kas.olcys.connector.web2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pl.kas.olcys.api.dto.AnalysisMode;
import pl.kas.olcys.api.dto.IdentifierDto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Passive OSINT connector querying Wayback Machine CDX archive index and archived HTML snapshots.
 */
@Component
public class WaybackConnector implements Web2Connector {

    private static final Logger log = LoggerFactory.getLogger(WaybackConnector.class);
    private static final Set<String> DOMAIN_AND_HANDLE_TYPES = Set.of("DOMAIN", "WEBSITE", "URL", "NICK", "SOCIAL_LOGIN");
    private static final int MAX_QUERIES = 2;
    private static final int MAX_ARCHIVED_BODY_CHARS = 1000;
    private static final Pattern SCRIPT_PATTERN =
            Pattern.compile("<script\\b[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern STYLE_PATTERN =
            Pattern.compile("<style\\b[^>]*>.*?</style>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public WaybackConnector(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.clone().build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "WaybackArchive";
    }

    @Override
    public boolean supports(AnalysisMode mode) {
        return mode == AnalysisMode.LIVE || mode == AnalysisMode.HYBRID;
    }

    @Override
    public List<RawEvidence> search(List<IdentifierDto> identifiers, int snapshotStep) {
        if (identifiers == null || identifiers.isEmpty()) {
            return List.of();
        }

        Set<String> queries = new LinkedHashSet<>(Web2ConnectorSupport.extractCustomEmailDomains(identifiers));
        queries.addAll(Web2ConnectorSupport.extractHandles(identifiers, DOMAIN_AND_HANDLE_TYPES));
        if (queries.isEmpty()) {
            return List.of();
        }

        List<RawEvidence> evidences = new ArrayList<>();
        int count = 0;
        for (String query : queries) {
            if (count++ >= MAX_QUERIES) {
                break;
            }
            String target = (query.contains(".") || query.contains("/"))
                    ? query
                    : "github.com/" + query;
            String cdxUrl = "https://web.archive.org/cdx/search/cdx?url="
                    + Web2ConnectorSupport.urlEncode(target) + "&output=json&limit=3";
            Web2ConnectorSupport.safeGet(restClient, cdxUrl, log, "application/json")
                    .ifPresent(rawBody -> parseCdxResponse(rawBody, query, evidences));
        }

        return List.copyOf(evidences);
    }

    private void parseCdxResponse(String rawBody, String query, List<RawEvidence> sink) {
        try {
            JsonNode rows = objectMapper.readTree(rawBody);
            if (!rows.isArray() || rows.size() <= 1) {
                return;
            }

            Instant now = Instant.now();
            for (int i = 1; i < rows.size(); i++) {
                JsonNode row = rows.get(i);
                if (!row.isArray() || row.size() < 3) {
                    continue;
                }
                String timestamp = row.get(1).asText("");
                String original = row.get(2).asText("");
                String status = row.size() > 4 ? row.get(4).asText("") : "";
                if (timestamp.isBlank() || original.isBlank()) {
                    continue;
                }

                String archiveUrl = "https://web.archive.org/web/" + timestamp + "/" + original;
                StringBuilder rawText = new StringBuilder(String.format(
                        "Migawka Wayback Machine dla '%s' (timestamp=%s, HTTP=%s): %s",
                        query, timestamp, status, original));
                if (status.isBlank() || "200".equals(status)) {
                    String snapshotRawUrl = "https://web.archive.org/web/" + timestamp + "id_/" + original;
                    Web2ConnectorSupport.safeGet(restClient, snapshotRawUrl, log, "text/html")
                            .map(WaybackConnector::extractReadableText)
                            .filter(text -> !text.isBlank())
                            .ifPresent(bodyText -> rawText.append(" | Treść archiwalna: ").append(bodyText));
                }

                String snippet = Web2ConnectorSupport.truncateSnippet(rawText.toString());
                String context = Web2ConnectorSupport.classifyContext(snippet, "FORUM_DISCUSSION");

                sink.add(new RawEvidence(
                        name(),
                        "WEB_ARCHIVE",
                        archiveUrl,
                        now,
                        Web2ConnectorSupport.sha256(snippet),
                        snippet,
                        query,
                        context
                ));
            }
        } catch (Exception ex) {
            log.debug("Failed to parse Wayback CDX JSON for {}: {}", query, ex.getMessage());
        }
    }

    static String extractReadableText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String noScripts = SCRIPT_PATTERN.matcher(html).replaceAll(" ");
        String noStyles = STYLE_PATTERN.matcher(noScripts).replaceAll(" ");
        String stripped = HTML_TAG_PATTERN.matcher(noStyles).replaceAll(" ");
        String collapsed = stripped
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("\\s+", " ")
                .trim();
        if (collapsed.length() > MAX_ARCHIVED_BODY_CHARS) {
            return collapsed.substring(0, MAX_ARCHIVED_BODY_CHARS);
        }
        return collapsed;
    }
}
