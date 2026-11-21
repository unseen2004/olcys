package pl.kas.olcys.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AnalysisRunRepository extends JpaRepository<AnalysisRunEntity, String> {
    List<AnalysisRunEntity> findBySubjectIdOrderByRunNumberAsc(String subjectId);
    List<AnalysisRunEntity> findBySubjectIdOrderByRunNumberDesc(String subjectId);
}
