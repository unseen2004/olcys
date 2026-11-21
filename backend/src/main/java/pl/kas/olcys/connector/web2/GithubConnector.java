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
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Passive OSINT connector for public GitHub profiles and public activity events.
 */
@Component
public class GithubConnector implements Web2Connector {

    private static final Logger log = LoggerFactory.getLogger(GithubConnector.class);
    private static final Set<String> HANDLE_TYPES = Set.of("NICK", "SOCIAL_LOGIN", "GITHUB");
    private static final int MAX_QUERIES = 2;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GithubConnector(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.clone().build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "GitHub";
    }

    @Override
    public boolean supports(AnalysisMode mode) {
        return mode == AnalysisMode.LIVE || mode == AnalysisMode.HYBRID;
    }

    @Override
    public List<RawEvidence> search(List<IdentifierDto> identifiers, int snapshotStep) {
        List<String> handles = Web2ConnectorSupport.extractHandles(identifiers, HANDLE_TYPES);
        if (handles.isEmpty()) {
            return List.of();
        }

        List<RawEvidence> evidences = new ArrayList<>();
        int count = 0;
        for (String handle : handles) {
            if (count++ >= MAX_QUERIES) {
                break;
            }
            fetchUserProfile(handle).ifPresent(evidences::add);
            fetchPublicEvents(handle).ifPresent(evidences::add);
        }
        return List.copyOf(evidences);
    }

    private Optional<RawEvidence> fetchUserProfile(String handle) {
        String apiUrl = "https://api.github.com/users/" + Web2ConnectorSupport.urlEncode(handle);
        Optional<String> bodyOpt = Web2ConnectorSupport.safeGet(
                restClient, apiUrl, log, "application/vnd.github+json");
        if (bodyOpt.isEmpty()) {
            return Optional.empty();
        }

        String rawBody = bodyOpt.get();
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String login = textOrEmpty(root, "login");
            String name = textOrEmpty(root, "name");
            String bio = textOrEmpty(root, "bio");
            String blog = textOrEmpty(root, "blog");
            String company = textOrEmpty(root, "company");
            String email = textOrEmpty(root, "email");
            String twitter = textOrEmpty(root, "twitter_username");
            String htmlUrl = textOrEmpty(root, "html_url");
            if (htmlUrl.isBlank()) {
                htmlUrl = "https://github.com/" + handle;
            }

            StringBuilder summary = new StringBuilder();
            summary.append("GitHub profil @").append(login.isBlank() ? handle : login);
            if (!name.isBlank()) {
                summary.append(" (").append(name).append(")");
            }
            if (!bio.isBlank()) {
                summary.append(" | Bio: ").append(bio);
            }
            if (!blog.isBlank()) {
                summary.append(" | Blog: ").append(blog);
            }
            if (!company.isBlank()) {
                summary.append(" | Firma: ").append(company);
            }
            if (!email.isBlank()) {
                summary.append(" | Email: ").append(email);
            }
            if (!twitter.isBlank()) {
                summary.append(" | X: @").append(twitter);
            }

            return Optional.of(new RawEvidence(
                    name(),
                    "CODE_PROFILE",
                    htmlUrl,
                    Instant.now(),
                    Web2ConnectorSupport.sha256(rawBody),
                    Web2ConnectorSupport.truncateSnippet(summary.toString()),
                    handle,
                    "OWN_PROFILE_VERIFIED"
            ));
        } catch (Exception ex) {
            log.debug("Failed to parse GitHub profile JSON for {}: {}", handle, ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<RawEvidence> fetchPublicEvents(String handle) {
        String eventsUrl = "https://api.github.com/users/" + Web2ConnectorSupport.urlEncode(handle)
                + "/events/public?per_page=10";
        Optional<String> bodyOpt = Web2ConnectorSupport.safeGet(
                restClient, eventsUrl, log, "application/vnd.github+json");
        if (bodyOpt.isEmpty()) {
            return Optional.empty();
        }

        String rawBody = bodyOpt.get();
        try {
            JsonNode events = objectMapper.readTree(rawBody);
            if (!events.isArray() || events.isEmpty()) {
                return Optional.empty();
            }

            StringBuilder sb = new StringBuilder();
            for (JsonNode event : events) {
                String type = textOrEmpty(event, "type");
                JsonNode payload = event.path("payload");
                if ("PushEvent".equals(type) && payload.path("commits").isArray()) {
                    for (JsonNode commit : payload.path("commits")) {
                        String msg = textOrEmpty(commit, "message");
                        String authorEmail = textOrEmpty(commit.path("author"), "email");
                        if (!msg.isBlank() || !authorEmail.isBlank()) {
                            sb.append("Commit [").append(authorEmail).append("]: ").append(msg).append(" | ");
                        }
                    }
                } else if (payload.has("comment")) {
                    String commentBody = textOrEmpty(payload.path("comment"), "body");
                    if (!commentBody.isBlank()) {
                        sb.append("Comment: ").append(commentBody).append(" | ");
                    }
                }
            }
            if (sb.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(new RawEvidence(
                    name(),
                    "CODE_ACTIVITY",
                    "https://github.com/" + handle,
                    Instant.now(),
                    Web2ConnectorSupport.sha256(rawBody),
                    Web2ConnectorSupport.truncateSnippet(sb.toString()),
                    handle,
                    "OWN_PROFILE_VERIFIED"
            ));
        } catch (Exception ex) {
            log.debug("Failed to parse GitHub events JSON for {}: {}", handle, ex.getMessage());
            return Optional.empty();
        }
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode val = node.path(field);
        return val.isMissingNode() || val.isNull() ? "" : val.asText("");
    }
}
