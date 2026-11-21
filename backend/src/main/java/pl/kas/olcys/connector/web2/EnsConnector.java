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
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Passive OSINT connector resolving Ethereum Name Service (ENS) names and reverse records.
 */
@Component
public class EnsConnector implements Web2Connector {

    private static final Logger log = LoggerFactory.getLogger(EnsConnector.class);
    private static final Pattern ETH_ADDRESS_PATTERN = Pattern.compile("^0[xX][0-9a-fA-F]{40}$");
    private static final int MAX_QUERIES = 3;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public EnsConnector(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.clone().build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "ENS";
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

        Set<String> ensQueries = new LinkedHashSet<>();
        for (IdentifierDto id : identifiers) {
            if (id == null || id.value() == null || id.value().isBlank()) {
                continue;
            }
            String value = id.value().trim();
            if (value.toLowerCase(Locale.ROOT).endsWith(".eth")
                    || ETH_ADDRESS_PATTERN.matcher(value).matches()) {
                ensQueries.add(value);
            }
            if (ensQueries.size() >= MAX_QUERIES) {
                break;
            }
        }

        if (ensQueries.isEmpty()) {
            return List.of();
        }

        List<RawEvidence> evidences = new ArrayList<>();
        for (String query : ensQueries) {
            resolveEns(query).ifPresent(evidences::add);
        }
        return List.copyOf(evidences);
    }

    private Optional<RawEvidence> resolveEns(String query) {
        String apiUrl = "https://api.ensideas.com/ens/resolve/" + Web2ConnectorSupport.urlEncode(query);
        Optional<String> bodyOpt = Web2ConnectorSupport.safeGet(
                restClient, apiUrl, log, "application/json");
        if (bodyOpt.isEmpty()) {
            return Optional.empty();
        }

        String rawBody = bodyOpt.get();
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String resolvedAddress = root.path("address").asText("");
            String resolvedName = root.path("name").asText("");
            String displayName = root.path("displayName").asText("");

            if (resolvedAddress.isBlank() && resolvedName.isBlank()) {
                return Optional.empty();
            }

            String targetLabel = resolvedName.isBlank() ? query : resolvedName;
            String snippet = String.format(
                    "Rekord ENS (Ethereum Name Service): nazwa '%s' (display: '%s') powiązana z adresem ETH %s",
                    targetLabel, displayName, resolvedAddress);

            return Optional.of(new RawEvidence(
                    name(),
                    "WEB3_IDENTITY",
                    "https://app.ens.domains/" + targetLabel,
                    Instant.now(),
                    Web2ConnectorSupport.sha256(rawBody),
                    Web2ConnectorSupport.truncateSnippet(snippet),
                    query,
                    "OWN_PROFILE_VERIFIED"
            ));
        } catch (Exception ex) {
            log.debug("Failed to parse ENS JSON for {}: {}", query, ex.getMessage());
            return Optional.empty();
        }
    }
}
