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

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Passive OSINT connector for public GitHub profiles and public activity events.
 */
@Component
public class GithubConnector implements Web2Connector {

    private static final Logger log = LoggerFactory.getLogger(GithubConnector.class);
    private static final Set<String> HANDLE_TYPES = Set.of("NICK", "SOCIAL_LOGIN", "GITHUB");
    private static final Set<String> SEARCH_TYPES = Set.of("FULL_NAME", "EMAIL");
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
        if (identifiers == null || identifiers.isEmpty()) {
            return List.of();
        }

        List<RawEvidence> evidences = new ArrayList<>();
        Set<String> fetchedLogins = new LinkedHashSet<>();

        List<String> handles = Web2ConnectorSupport.extractHandles(identifiers, HANDLE_TYPES);
        int count = 0;
        for (String handle : handles) {
            if (count++ >= MAX_QUERIES) {
                break;
            }
            if (fetchedLogins.add(handle.toLowerCase(Locale.ROOT))) {
                fetchUserProfile(handle, handle, null).ifPresent(evidences::add);
                fetchProfileReadme(handle, handle, null).ifPresent(evidences::add);
                fetchPublicEvents(handle, handle, null).ifPresent(evidences::add);
            }
        }

        int searchCount = 0;
        for (IdentifierDto id : identifiers) {
            if (searchCount >= MAX_QUERIES) {
                break;
            }
            if (id == null || id.value() == null || id.value().isBlank()) {
                continue;
            }
            String type = id.type() == null ? "" : id.type().trim().toUpperCase(Locale.ROOT);
            if (!SEARCH_TYPES.contains(type)) {
                continue;
            }
            searchCount++;
            String queryValue = id.value().trim();
            searchUserLogin(queryValue).ifPresent(login -> {
                if (fetchedLogins.add(login.toLowerCase(Locale.ROOT))) {
                    Optional<RawEvidence> profileOpt = fetchUserProfile(login, queryValue, type);
                    if (profileOpt.isPresent()) {
                        evidences.add(profileOpt.get());
                        fetchProfileReadme(login, queryValue, type).ifPresent(evidences::add);
                        fetchPublicEvents(login, queryValue, type).ifPresent(evidences::add);
                    }
                }
            });
        }

        return List.copyOf(evidences);
    }

    private Optional<String> searchUserLogin(String query) {
        String searchUrl = "https://api.github.com/search/users?q="
                + Web2ConnectorSupport.urlEncode(query) + "&per_page=1";
        Optional<String> bodyOpt = Web2ConnectorSupport.safeGet(
                restClient, searchUrl, log, "application/vnd.github+json");
        if (bodyOpt.isEmpty()) {
            return Optional.empty();
        }
        try {
            JsonNode items = objectMapper.readTree(bodyOpt.get()).path("items");
            if (!items.isArray() || items.isEmpty()) {
                return Optional.empty();
            }
            String login = textOrEmpty(items.get(0), "login");
            return login.isBlank() ? Optional.empty() : Optional.of(login);
        } catch (Exception ex) {
            log.debug("Failed to parse GitHub user search JSON for {}: {}", query, ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<RawEvidence> fetchUserProfile(
            String handle,
            String matchedIdentifier,
            String searchType
    ) {
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

            if (searchType != null && !corroboratesSearchQuery(
                    searchType, matchedIdentifier, login.isBlank() ? handle : login, name, bio, email)) {
                log.debug("Discarding uncorroborated GitHub {} search hit @{} for query '{}'",
                        searchType, handle, matchedIdentifier);
                return Optional.empty();
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

            String snippet = Web2ConnectorSupport.truncateSnippet(summary.toString());
            String context = "FULL_NAME".equals(searchType)
                    ? Web2ConnectorSupport.classifyContext(summary.toString(), "FORUM_DISCUSSION")
                    : "OWN_PROFILE_VERIFIED";
            return Optional.of(new RawEvidence(
                    name(),
                    "CODE_PROFILE",
                    htmlUrl,
                    Instant.now(),
                    Web2ConnectorSupport.sha256(snippet),
                    snippet,
                    matchedIdentifier,
                    context
            ));
        } catch (Exception ex) {
            log.debug("Failed to parse GitHub profile JSON for {}: {}", handle, ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<RawEvidence> fetchProfileReadme(
            String handle,
            String matchedIdentifier,
            String searchType
    ) {
        String encoded = Web2ConnectorSupport.urlEncode(handle);
        String readmeApiUrl = "https://api.github.com/repos/" + encoded + "/" + encoded + "/readme";
        Optional<String> bodyOpt = Web2ConnectorSupport.safeGet(
                restClient, readmeApiUrl, log, "application/vnd.github.raw+json");
        if (bodyOpt.isEmpty()) {
            return Optional.empty();
        }
        String rawReadme = bodyOpt.get();
        String snippet = Web2ConnectorSupport.truncateSnippet("GitHub profilowy README.md (@" + handle + "): " + rawReadme);
        String context = "FULL_NAME".equals(searchType)
                ? Web2ConnectorSupport.classifyContext(snippet, "FORUM_DISCUSSION")
                : "OWN_PROFILE_VERIFIED";
        return Optional.of(new RawEvidence(
                name(),
                "CODE_PROFILE",
                "https://github.com/" + handle + "/" + handle + "#readme",
                Instant.now(),
                Web2ConnectorSupport.sha256(snippet),
                snippet,
                matchedIdentifier,
                context
        ));
    }

    private Optional<RawEvidence> fetchPublicEvents(
            String handle,
            String matchedIdentifier,
            String searchType
    ) {
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

            String snippet = Web2ConnectorSupport.truncateSnippet(sb.toString());
            String context = "FULL_NAME".equals(searchType)
                    ? Web2ConnectorSupport.classifyContext(snippet, "FORUM_DISCUSSION")
                    : "OWN_PROFILE_VERIFIED";
            return Optional.of(new RawEvidence(
                    name(),
                    "CODE_ACTIVITY",
                    "https://github.com/" + handle,
                    Instant.now(),
                    Web2ConnectorSupport.sha256(snippet),
                    snippet,
                    matchedIdentifier,
                    context
            ));
        } catch (Exception ex) {
            log.debug("Failed to parse GitHub events JSON for {}: {}", handle, ex.getMessage());
            return Optional.empty();
        }
    }

    private static boolean corroboratesSearchQuery(
            String searchType,
            String query,
            String login,
            String name,
            String bio,
            String email
    ) {
        if ("FULL_NAME".equals(searchType)) {
            return matchesFullNameQuery(query, login, name, bio);
        }
        if ("EMAIL".equals(searchType)) {
            return matchesEmailQuery(query, login, bio, email);
        }
        return true;
    }

    private static boolean matchesFullNameQuery(String query, String login, String name, String bio) {
        String normQuery = normalizeForSearch(query);
        if (normQuery.isBlank()) {
            return false;
        }
        String normName = normalizeForSearch(name);
        if (!normName.isBlank()) {
            String[] tokens = normQuery.split("\\s+");
            boolean allTokensPresent = true;
            int checked = 0;
            for (String token : tokens) {
                if (token.length() < 2) {
                    continue;
                }
                checked++;
                if (!normName.contains(token)) {
                    allTokensPresent = false;
                    break;
                }
            }
            if ((checked > 0 && allTokensPresent) || (checked == 0 && normName.contains(normQuery))) {
                return true;
            }
        }
        String compactQuery = normQuery.replaceAll("[\\s._-]+", "");
        String compactLogin = normalizeForSearch(login).replaceAll("[\\s._-]+", "");
        if (!compactQuery.isBlank() && !compactLogin.isBlank() && compactLogin.equals(compactQuery)) {
            return true;
        }
        String normBio = normalizeForSearch(bio);
        return !normBio.isBlank() && normBio.contains(normQuery);
    }

    private static boolean matchesEmailQuery(String query, String login, String bio, String email) {
        String trimmedQuery = query == null ? "" : query.trim();
        if (trimmedQuery.isBlank()) {
            return false;
        }
        if (email != null && !email.isBlank() && email.trim().equalsIgnoreCase(trimmedQuery)) {
            return true;
        }
        if (bio != null && !bio.isBlank()
                && bio.toLowerCase(Locale.ROOT).contains(trimmedQuery.toLowerCase(Locale.ROOT))) {
            return true;
        }
        int atIdx = trimmedQuery.indexOf('@');
        if (atIdx > 0) {
            String localPart = trimmedQuery.substring(0, atIdx);
            if (login != null && !login.isBlank() && login.trim().equalsIgnoreCase(localPart)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeForSearch(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String replaced = raw.replace('ł', 'l').replace('Ł', 'L');
        String decomposed = Normalizer.normalize(replaced, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode val = node.path(field);
        return val.isMissingNode() || val.isNull() ? "" : val.asText("");
    }
}
