package pl.kas.olcys.connector;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pl.kas.olcys.connector.web2.ScenarioProvider;
import pl.kas.olcys.connector.web2.Web2Connector;
import pl.kas.olcys.connector.web3.EntityTagProvider;
import pl.kas.olcys.connector.web3.Web3Connector;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ConnectorContextWiringTest {

    @Autowired
    private List<Web2Connector> web2Connectors;

    @Autowired
    private List<Web3Connector> web3Connectors;

    @Autowired
    private EntityTagProvider entityTagProvider;

    @Autowired
    private ScenarioProvider scenarioProvider;

    @Test
    void shouldRegisterAllWeb2AndWeb3ConnectorsInSpringContext() {
        assertThat(web2Connectors)
                .extracting(Web2Connector::name)
                .containsExactlyInAnyOrder("Synthetic", "GitHub", "Reddit", "WaybackArchive", "ENS");
        assertThat(web3Connectors)
                .extracting(Web3Connector::network)
                .containsExactlyInAnyOrder("ETH", "BTC", "TRON");
        assertThat(entityTagProvider.getAllTags().size()).isGreaterThanOrEqualTo(60);
        assertThat(scenarioProvider.getScenarios()).hasSize(3);
    }
}
