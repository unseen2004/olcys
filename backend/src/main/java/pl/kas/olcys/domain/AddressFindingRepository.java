package pl.kas.olcys.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AddressFindingRepository extends JpaRepository<AddressFindingEntity, String> {
    List<AddressFindingEntity> findByRunId(String runId);
}
