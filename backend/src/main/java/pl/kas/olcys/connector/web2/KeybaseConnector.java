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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Passive OSINT connector for Keybase public identity lookup API (profiles, proofs, BTC/crypto addresses).
 */
@Component
public class KeybaseConnector implements Web2Connector {

    private static final Logger log = LoggerFactory.getLogger(KeybaseConnector.class);
    private static final Set<String> HANDLE_TYPES = Set.of("NICK", "SOCIAL_LOGIN", "KEYBASE");
    private static final int MAX_QUERIES = 2;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public KeybaseConnector(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.clone().build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "Keybase";
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
            lookupUser(handle).ifPresent(evidences::add);
        }
        return List.copyOf(evidences);
    }

    private Optional<RawEvidence> lookupUser(String handle) {
        String apiUrl = "https://keybase.io/_/api/1.0/user/lookup.json?usernames="
                + Web2ConnectorSupport.urlEncode(handle);
        Optional<String> bodyOpt = Web2ConnectorSupport.safeGet(
                restClient, apiUrl, log, "application/json");
        if (bodyOpt.isEmpty()) {
            return Optional.empty();
        }

        String rawBody = bodyOpt.get();
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            if (root.path("status").path("code").asInt(-1) != 0) {
                return Optional.empty();
            }
            JsonNode them = root.path("them");
            if (!them.isArray() || them.isEmpty() || them.get(0).isNull()) {
                return Optional.empty();
            }

            JsonNode user = them.get(0);
            String username = user.path("basics").path("username").asText(handle);
            String fullName = user.path("profile").path("full_name").asText("");
            String bio = user.path("profile").path("bio").asText("");

            List<String> proofs = new ArrayList<>();
            for (JsonNode proof : user.path("proofs_summary").path("all")) {
                String pType = proof.path("proof_type").asText("");
                String pNametag = proof.path("nametag").asText("");
                if (!pType.isBlank() && !pNametag.isBlank()) {
                    proofs.add(pType + ":" + pNametag);
                }
                if (proofs.size() >= 6) {
                    break;
                }
            }

            List<String> cryptoAddresses = new ArrayList<>();
            JsonNode cryptoMap = user.path("cryptocurrency_addresses");
            if (cryptoMap.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = cryptoMap.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    String coin = entry.getKey();
                    for (JsonNode addrNode : entry.getValue()) {
                        String addr = addrNode.path("address").asText("");
                        if (!addr.isBlank()) {
                            cryptoAddresses.add(coin + ": " + addr);
                        }
                    }
                }
            }

            StringBuilder summary = new StringBuilder();
            summary.append("Profil Keybase @").append(username);
            if (!fullName.isBlank()) {
                summary.append(" (").append(fullName).append(")");
            }
            if (!bio.isBlank()) {
                summary.append(" | Bio: ").append(bio);
            }
            if (!proofs.isEmpty()) {
                summary.append(" | Zweryfikowane dowody tożsamości: ").append(String.join(", ", proofs));
            }
            if (!cryptoAddresses.isEmpty()) {
                summary.append(" | Przypięte adresy krypto: ").append(String.join(" | ", cryptoAddresses));
            }

            String snippet = Web2ConnectorSupport.truncateSnippet(summary.toString());
            return Optional.of(new RawEvidence(
                    name(),
                    "IDENTITY_PROOF",
                    "https://keybase.io/" + username,
                    Instant.now(),
                    Web2ConnectorSupport.sha256(snippet),
                    snippet,
                    handle,
                    "OWN_PROFILE_VERIFIED"
            ));
        } catch (Exception ex) {
            log.debug("Failed to parse Keybase JSON for {}: {}", handle, ex.getMessage());
            return Optional.empty();
        }
    }
}
