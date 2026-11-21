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
 * Passive OSINT connector for public Reddit submissions and search listings.
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
            String url = "https://www.reddit.com/user/" + Web2ConnectorSupport.urlEncode(handle)
                    + "/submitted.json?limit=10";
            fetchListing(url, handle, evidences, seenUrls);
        }

        List<String> queries = Web2ConnectorSupport.extractNonEmptyValues(identifiers, MAX_QUERIES);
        for (String query : queries) {
            String url = "https://www.reddit.com/search.json?q=" + Web2ConnectorSupport.urlEncode(query)
                    + "&limit=5";
            fetchListing(url, query, evidences, seenUrls);
        }

        return List.copyOf(evidences);
    }

    private void fetchListing(
            String apiUrl,
            String matchedIdentifier,
            List<RawEvidence> sink,
            Set<String> seenUrls
    ) {
        Web2ConnectorSupport.safeGet(restClient, apiUrl, log, "application/json")
                .ifPresent(rawBody -> parseListing(rawBody, matchedIdentifier, sink, seenUrls));
    }

    private void parseListing(
            String rawBody,
            String matchedIdentifier,
            List<RawEvidence> sink,
            Set<String> seenUrls
    ) {
        try {
            JsonNode children = objectMapper.readTree(rawBody).path("data").path("children");
            if (!children.isArray() || children.isEmpty()) {
                return;
            }

            String sha256 = Web2ConnectorSupport.sha256(rawBody);
            Instant now = Instant.now();
            int added = 0;
            for (JsonNode child : children) {
                if (added >= 3) {
                    break;
                }
                JsonNode post = child.path("data");
                String title = post.path("title").asText("");
                String selftext = post.path("selftext").asText("");
                String body = post.path("body").asText("");
                String subreddit = post.path("subreddit").asText("");
                String permalink = post.path("permalink").asText("");

                String combined = String.format("r/%s: %s %s %s", subreddit, title, selftext, body).trim();
                if (combined.length() <= 4) {
                    continue;
                }

                String postUrl = permalink.isBlank()
                        ? "https://www.reddit.com"
                        : "https://www.reddit.com" + permalink;
                if (!seenUrls.add(postUrl)) {
                    continue;
                }
                String context = Web2ConnectorSupport.classifyContext(combined, "FORUM_DISCUSSION");
                String category = switch (context) {
                    case "MASS_COMMENT_NOISE" -> "GIVEAWAY_NOISE";
                    case "TRANSACTIONAL_CONTEXT" -> "P2P_MARKETPLACE";
                    default -> "FORUM";
                };

                sink.add(new RawEvidence(
                        name(),
                        category,
                        postUrl,
                        now,
                        sha256,
                        Web2ConnectorSupport.truncateSnippet(combined),
                        matchedIdentifier,
                        context
                ));
                added++;
            }
        } catch (Exception ex) {
            log.debug("Failed to parse Reddit listing JSON: {}", ex.getMessage());
        }
    }
}
