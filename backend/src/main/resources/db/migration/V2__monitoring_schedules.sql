CREATE TABLE IF NOT EXISTS monitoring_schedules (
    subject_id VARCHAR(64) PRIMARY KEY REFERENCES subjects(id) ON DELETE CASCADE,
    subject_display_name VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL,
    interval_hours INT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_heartbeat_at TIMESTAMP WITH TIME ZONE
);
