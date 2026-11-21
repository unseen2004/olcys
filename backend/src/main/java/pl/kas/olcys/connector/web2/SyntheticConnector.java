package pl.kas.olcys.connector.web2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import pl.kas.olcys.api.dto.AnalysisMode;
import pl.kas.olcys.api.dto.IdentifierDto;
import pl.kas.olcys.api.dto.ScenarioPresetDto;
import pl.kas.olcys.crypto.CryptoAddressValidator;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Provides built-in KAS validation scenarios and deterministic synthetic evidences.
 */
@Component
public class SyntheticConnector implements Web2Connector, ScenarioProvider {

    private static final Logger log = LoggerFactory.getLogger(SyntheticConnector.class);
    private static final String SCENARIOS_RESOURCE_PATH = "data/scenarios.json";

    private record ScenarioEvidenceEntry(
            int minStep,
            Integer maxStep,
            String sourceName,
            String sourceCategory,
            String url,
            String matchedIdentifier,
            String contextHint,
            String snippet
    ) {
        boolean matchesStep(int step) {
            return step >= minStep && (maxStep == null || step <= maxStep);
        }
    }

    private record ScenarioDefinition(
            String id,
            String titlePl,
            String subtitlePl,
            String descriptionPl,
            String expectedOutcomePl,
            String displayName,
            List<IdentifierDto> identifiers,
            AnalysisMode recommendedMode,
            List<String> sources,
            List<String> networks,
            boolean supportsWalletRotationDemo,
            List<String> personalAddresses,
            List<ScenarioEvidenceEntry> evidences
    ) {
        ScenarioPresetDto toDto() {
            return new ScenarioPresetDto(
                    id,
                    titlePl,
                    subtitlePl,
                    descriptionPl,
                    expectedOutcomePl,
                    displayName,
                    identifiers,
                    recommendedMode,
                    sources,
                    networks,
                    supportsWalletRotationDemo
            );
        }
    }

    private final ObjectMapper objectMapper;
    private volatile List<ScenarioDefinition> definitions = List.of();
    private volatile List<ScenarioPresetDto> presets = List.of();
    private volatile boolean initialized = false;

    @Autowired
    public SyntheticConnector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public synchronized void init() {
        ClassPathResource resource = new ClassPathResource(SCENARIOS_RESOURCE_PATH);
        try (InputStream is = resource.getInputStream()) {
            List<ScenarioDefinition> loaded = objectMapper.readValue(is, new TypeReference<>() {});
            this.definitions = List.copyOf(loaded);
            this.presets = loaded.stream().map(ScenarioDefinition::toDto).toList();
            this.initialized = true;
            log.info("Loaded {} KAS validation scenarios from {}", loaded.size(), SCENARIOS_RESOURCE_PATH);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load scenarios from " + SCENARIOS_RESOURCE_PATH, e);
        }
    }

    @Override
    public String name() {
        return "Synthetic";
    }

    @Override
    public boolean supports(AnalysisMode mode) {
        return mode == AnalysisMode.SYNTHETIC || mode == AnalysisMode.HYBRID;
    }

    @Override
    public List<ScenarioPresetDto> getScenarios() {
        ensureInitialized();
        return presets;
    }

    @Override
    public List<RawEvidence> search(List<IdentifierDto> identifiers, int snapshotStep) {
        ensureInitialized();
        if (identifiers == null || identifiers.isEmpty()) {
            return List.of();
        }
        int effectiveStep = Math.max(1, snapshotStep);
        Set<ScenarioDefinition> matchedScenarios = new LinkedHashSet<>();

        for (IdentifierDto query : identifiers) {
            if (query == null || query.value() == null || query.value().isBlank()) {
                continue;
            }
            for (ScenarioDefinition scenario : definitions) {
                if (matchesScenario(query.value().trim(), scenario)) {
                    matchedScenarios.add(scenario);
                }
            }
        }

        if (matchedScenarios.isEmpty()) {
            return List.of();
        }

        Instant now = Instant.now();
        List<RawEvidence> results = new ArrayList<>();
        for (ScenarioDefinition scenario : matchedScenarios) {
            if (scenario.evidences() == null) {
                continue;
            }
            for (ScenarioEvidenceEntry entry : scenario.evidences()) {
                if (!entry.matchesStep(effectiveStep)) {
                    continue;
                }
                String sha256 = CryptoAddressValidator.sha256Hex(entry.snippet());
                results.add(new RawEvidence(
                        entry.sourceName(),
                        entry.sourceCategory(),
                        entry.url(),
                        now,
                        sha256,
                        entry.snippet(),
                        entry.matchedIdentifier(),
                        entry.contextHint()
                ));
            }
        }
        return List.copyOf(results);
    }

    private boolean matchesScenario(String rawQuery, ScenarioDefinition scenario) {
        String normalizedQuery = normalizeIdentifier(rawQuery);
        if (normalizedQuery.equalsIgnoreCase(scenario.id())
                || normalizedQuery.equalsIgnoreCase(scenario.displayName())) {
            return true;
        }
        if (scenario.identifiers() != null) {
            for (IdentifierDto id : scenario.identifiers()) {
                if (id.value() != null && normalizeIdentifier(id.value()).equalsIgnoreCase(normalizedQuery)) {
                    return true;
                }
            }
        }
        if (scenario.personalAddresses() != null) {
            for (String addr : scenario.personalAddresses()) {
                if (addr == null) {
                    continue;
                }
                if (addr.startsWith("0x") || addr.startsWith("0X")) {
                    if (addr.equalsIgnoreCase(rawQuery)) {
                        return true;
                    }
                } else if (addr.equals(rawQuery)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String normalizeIdentifier(String value) {
        String trimmed = value.trim();
        return trimmed.startsWith("@") ? trimmed.substring(1).trim() : trimmed;
    }

    private void ensureInitialized() {
        if (!initialized) {
            init();
        }
    }
}
