package pl.kas.olcys.connector.web2;

import pl.kas.olcys.api.dto.ScenarioPresetDto;

import java.util.List;

public interface ScenarioProvider {
    List<ScenarioPresetDto> getScenarios();
}
