package pl.kas.olcys.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SubjectRepository extends JpaRepository<SubjectEntity, String> {
    List<SubjectEntity> findAllByOrderByCreatedAtDesc();
}
