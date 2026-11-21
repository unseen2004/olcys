package pl.kas.olcys.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.kas.olcys.api.dto.HealthStatusDto;
import pl.kas.olcys.config.AiProperties;
import pl.kas.olcys.connector.web2.Web2Connector;
import pl.kas.olcys.connector.web3.Web3Connector;
import pl.kas.olcys.domain.SubjectRepository;
import pl.kas.olcys.engine.ai.AiClientRouter;

import java.util.List;

@RestController
@RequestMapping("/api/health")
@Tag(name = "Diagnostyka i Bezpieczeństwo", description = "Status bazy danych, konektorów Web2/Web3, Virtual Threads i lokalnego AI")
public class HealthController {

    private final SubjectRepository subjectRepository;
    private final List<Web2Connector> web2Connectors;
    private final List<Web3Connector> web3Connectors;
    private final AiClientRouter aiClientRouter;
    private final AiProperties aiProperties;
    private final boolean virtualThreadsEnabled;

    @Autowired
    public HealthController(
            SubjectRepository subjectRepository,
            List<Web2Connector> web2Connectors,
            List<Web3Connector> web3Connectors,
            AiClientRouter aiClientRouter,
            AiProperties aiProperties,
            @Value("${spring.threads.virtual.enabled:true}") boolean virtualThreadsEnabled
    ) {
        this.subjectRepository = subjectRepository;
        this.web2Connectors = web2Connectors;
        this.web3Connectors = web3Connectors;
        this.aiClientRouter = aiClientRouter;
        this.aiProperties = aiProperties;
        this.virtualThreadsEnabled = virtualThreadsEnabled;
    }

    @GetMapping
    @Operation(summary = "Zwraca status bazy danych, zarejestrowanych konektorów oraz gwarancję braku transferu zewnętrznego")
    public HealthStatusDto health() {
        String dbStatus;
        String overallStatus = "UP";
        try {
            long subjectCount = subjectRepository.count();
            dbStatus = "UP (sprawy: " + subjectCount + ")";
        } catch (Exception ex) {
            dbStatus = "DOWN: " + ex.getMessage();
            overallStatus = "DEGRADED";
        }

        List<String> web2Names = web2Connectors.stream()
                .map(Web2Connector::name)
                .sorted()
                .toList();
        List<String> web3Networks = web3Connectors.stream()
                .map(Web3Connector::network)
                .sorted()
                .toList();

        return new HealthStatusDto(
                overallStatus,
                "olcys-backend",
                System.getProperty("java.version"),
                virtualThreadsEnabled,
                dbStatus,
                web2Names,
                web3Networks,
                aiClientRouter.providerName(),
                aiClientRouter.modelName(),
                aiProperties.allowExternal()
        );
    }
}
