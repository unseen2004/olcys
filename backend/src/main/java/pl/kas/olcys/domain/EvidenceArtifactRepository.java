package pl.kas.olcys.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EvidenceArtifactRepository extends JpaRepository<EvidenceArtifactEntity, String> {
    List<EvidenceArtifactEntity> findByRunId(String runId);
}
