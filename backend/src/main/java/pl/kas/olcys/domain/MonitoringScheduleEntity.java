package pl.kas.olcys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "monitoring_schedules")
public class MonitoringScheduleEntity {

    @Id
    @Column(name = "subject_id", length = 64, nullable = false)
    private String subjectId;

    @Column(name = "subject_display_name", nullable = false)
    private String subjectDisplayName;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "interval_hours", nullable = false)
    private int intervalHours;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    public MonitoringScheduleEntity() {
    }

    public MonitoringScheduleEntity(
            String subjectId,
            String subjectDisplayName,
            boolean enabled,
            int intervalHours,
            Instant updatedAt,
            Instant lastHeartbeatAt
    ) {
        this.subjectId = subjectId;
        this.subjectDisplayName = subjectDisplayName;
        this.enabled = enabled;
        this.intervalHours = intervalHours;
        this.updatedAt = updatedAt;
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public String getSubjectDisplayName() {
        return subjectDisplayName;
    }

    public void setSubjectDisplayName(String subjectDisplayName) {
        this.subjectDisplayName = subjectDisplayName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getIntervalHours() {
        return intervalHours;
    }

    public void setIntervalHours(int intervalHours) {
        this.intervalHours = intervalHours;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(Instant lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }
}
