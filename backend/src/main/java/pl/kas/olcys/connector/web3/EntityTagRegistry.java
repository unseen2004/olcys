package pl.kas.olcys.connector.web3;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class EntityTagRegistry implements EntityTagProvider {

    private static final Logger log = LoggerFactory.getLogger(EntityTagRegistry.class);
    private static final String TAGS_RESOURCE_PATH = "data/entity-tags.json";

    private final ObjectMapper objectMapper;
    private volatile List<EntityTag> allTags = List.of();
    private volatile Map<String, EntityTag> ethTagsByLowerAddress = Map.of();
    private volatile Map<String, EntityTag> exactTagsByNetworkAndAddress = Map.of();
    private volatile Map<String, EntityTag> exactTagsByAddress = Map.of();
    private volatile boolean initialized = false;

    @Autowired
    public EntityTagRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Loads reference entity tags from classpath JSON resource.
     */
    @PostConstruct
    public synchronized void init() {
        ClassPathResource resource = new ClassPathResource(TAGS_RESOURCE_PATH);
        try (InputStream is = resource.getInputStream()) {
            List<EntityTag> loaded = objectMapper.readValue(is, new TypeReference<>() {});
            Map<String, EntityTag> ethIndex = new HashMap<>();
            Map<String, EntityTag> netExactIndex = new HashMap<>();
            Map<String, EntityTag> addrExactIndex = new HashMap<>();

            for (EntityTag tag : loaded) {
                if (tag.address() == null || tag.network() == null) {
                    continue;
                }
                String net = tag.network().trim().toUpperCase(Locale.ROOT);
                String addr = tag.address().trim();
                if ("ETH".equals(net)) {
                    ethIndex.put(addr.toLowerCase(Locale.ROOT), tag);
                } else {
                    netExactIndex.put(net + ":" + addr, tag);
                    addrExactIndex.putIfAbsent(addr, tag);
                }
            }

            this.allTags = List.copyOf(loaded);
            this.ethTagsByLowerAddress = Map.copyOf(ethIndex);
            this.exactTagsByNetworkAndAddress = Map.copyOf(netExactIndex);
            this.exactTagsByAddress = Map.copyOf(addrExactIndex);
            this.initialized = true;
            log.info("Loaded {} reference KAS entity tags from {}", loaded.size(), TAGS_RESOURCE_PATH);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load entity tags from " + TAGS_RESOURCE_PATH, e);
        }
    }

    @Override
    public Optional<EntityTag> findTag(String address, String network) {
        ensureInitialized();
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }
        String trimmed = address.trim();
        if (network == null || network.isBlank()) {
            EntityTag ethMatch = ethTagsByLowerAddress.get(trimmed.toLowerCase(Locale.ROOT));
            if (ethMatch != null) {
                return Optional.of(ethMatch);
            }
            return Optional.ofNullable(exactTagsByAddress.get(trimmed));
        }

        String normalizedNet = network.trim().toUpperCase(Locale.ROOT);
        if ("ETH".equals(normalizedNet)) {
            return Optional.ofNullable(ethTagsByLowerAddress.get(trimmed.toLowerCase(Locale.ROOT)));
        }
        return Optional.ofNullable(exactTagsByNetworkAndAddress.get(normalizedNet + ":" + trimmed));
    }

    @Override
    public List<EntityTag> getAllTags() {
        ensureInitialized();
        return allTags;
    }

    private void ensureInitialized() {
        if (!initialized) {
            init();
        }
    }
}
