package pl.kas.olcys.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonitoringScheduleRepository extends JpaRepository<MonitoringScheduleEntity, String> {
    List<MonitoringScheduleEntity> findAllByOrderByUpdatedAtDesc();
    List<MonitoringScheduleEntity> findByEnabledTrue();
}
