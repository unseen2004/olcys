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

/**
 * Passive OSINT connector for public Reddit submissions and search listings,
 * with automatic fallback to the public PullPush Reddit archive API on HTTP 403/blocks.
 */
@Component
public class RedditConnector implements Web2Connector {

    private static final Logger log = LoggerFactory.getLogger(RedditConnector.class);
    private static final Set<String> HANDLE_TYPES = Set.of("NICK", "SOCIAL_LOGIN", "REDDIT");
    private static final int MAX_QUERIES = 2;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public RedditConnector(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.clone().build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "Reddit";
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

        List<RawEvidence> evidences = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        List<String> handles = Web2ConnectorSupport.extractHandles(identifiers, HANDLE_TYPES);
        int handleCount = 0;
        for (String handle : handles) {
            if (handleCount++ >= MAX_QUERIES) {
                break;
            }
            String encoded = Web2ConnectorSupport.urlEncode(handle);
            String primaryUrl = "https://www.reddit.com/user/" + encoded + "/submitted.json?limit=10";
            String fallbackUrl = "https://api.pullpush.io/reddit/search/submission/?author=" + encoded + "&size=5";
            fetchListing(primaryUrl, fallbackUrl, handle, evidences, seenUrls);
        }

        List<String> queries = Web2ConnectorSupport.extractNonEmptyValues(identifiers, MAX_QUERIES);
        for (String query : queries) {
            String encoded = Web2ConnectorSupport.urlEncode(query);
            String primaryUrl = "https://www.reddit.com/search.json?q=" + encoded + "&limit=5";
            String fallbackUrl = "https://api.pullpush.io/reddit/search/submission/?q=" + encoded + "&size=5";
            fetchListing(primaryUrl, fallbackUrl, query, evidences, seenUrls);
        }

        return List.copyOf(evidences);
    }

    private void fetchListing(
            String primaryUrl,
            String fallbackUrl,
            String matchedIdentifier,
            List<RawEvidence> sink,
            Set<String> seenUrls
    ) {
        Web2ConnectorSupport.safeGet(restClient, primaryUrl, log, "application/json")
                .or(() -> Web2ConnectorSupport.safeGet(restClient, fallbackUrl, log, "application/json"))
                .ifPresent(rawBody -> parseListing(rawBody, matchedIdentifier, sink, seenUrls));
    }

    private void parseListing(
            String rawBody,
            String matchedIdentifier,
            List<RawEvidence> sink,
            Set<String> seenUrls
    ) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode dataNode = root.path("data");
            boolean pullPushArray = dataNode.isArray();
            JsonNode items = pullPushArray ? dataNode : dataNode.path("children");
            if (!items.isArray() || items.isEmpty()) {
                return;
            }

            Instant now = Instant.now();
            int added = 0;
            for (JsonNode item : items) {
                if (added >= 3) {
                    break;
                }
                JsonNode post = pullPushArray ? item : (item.has("data") ? item.path("data") : item);
                String title = post.path("title").asText("");
                String selftext = post.path("selftext").asText("");
                String body = post.path("body").asText("");
                String subreddit = post.path("subreddit").asText("");
                String permalink = post.path("permalink").asText("");
                String fullLink = post.path("full_link").asText("");

                String combined = subreddit.isBlank()
                        ? String.format("%s %s %s", title, selftext, body).trim()
                        : String.format("r/%s: %s %s %s", subreddit, title, selftext, body).trim();
                if (combined.length() <= 4) {
                    continue;
                }

                String postUrl = resolvePostUrl(permalink, fullLink);
                if (!seenUrls.add(postUrl)) {
                    continue;
                }
                String context = Web2ConnectorSupport.classifyContext(combined, "FORUM_DISCUSSION");
                String category = switch (context) {
                    case "MASS_COMMENT_NOISE" -> "GIVEAWAY_NOISE";
                    case "TRANSACTIONAL_CONTEXT" -> "P2P_MARKETPLACE";
                    default -> "FORUM";
                };
                String snippet = Web2ConnectorSupport.truncateSnippet(combined);

                sink.add(new RawEvidence(
                        name(),
                        category,
                        postUrl,
                        now,
                        Web2ConnectorSupport.sha256(snippet),
                        snippet,
                        matchedIdentifier,
                        context
                ));
                added++;
            }
        } catch (Exception ex) {
            log.debug("Failed to parse Reddit listing JSON: {}", ex.getMessage());
        }
    }

    private static String resolvePostUrl(String permalink, String fullLink) {
        if (permalink != null && !permalink.isBlank()) {
            String trimmed = permalink.trim();
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                return trimmed;
            }
            return trimmed.startsWith("/")
                    ? "https://www.reddit.com" + trimmed
                    : "https://www.reddit.com/" + trimmed;
        }
        if (fullLink != null && !fullLink.isBlank()) {
            return fullLink.trim();
        }
        return "https://www.reddit.com";
    }
}
