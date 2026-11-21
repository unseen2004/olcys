CREATE TABLE IF NOT EXISTS subjects (
    id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    identifiers_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS analysis_runs (
    id VARCHAR(64) PRIMARY KEY,
    subject_id VARCHAR(64) NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    run_number INT NOT NULL,
    snapshot_step INT NOT NULL,
    mode VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_ms BIGINT NOT NULL,
    validated_address_count INT NOT NULL,
    rejected_fp_count INT NOT NULL,
    max_confidence_score INT NOT NULL,
    cash_out_signal_count INT NOT NULL,
    total_volume_pln DOUBLE PRECISION NOT NULL,
    result_json TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_analysis_runs_subject ON analysis_runs(subject_id, run_number);

CREATE TABLE IF NOT EXISTS evidence_artifacts (
    id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL REFERENCES analysis_runs(id) ON DELETE CASCADE,
    source_name VARCHAR(128) NOT NULL,
    source_category VARCHAR(64) NOT NULL,
    url TEXT NOT NULL,
    fetched_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    snippet TEXT NOT NULL,
    matched_identifier VARCHAR(255),
    context_classification VARCHAR(64) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_evidence_run ON evidence_artifacts(run_id);

CREATE TABLE IF NOT EXISTS address_findings (
    id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL REFERENCES analysis_runs(id) ON DELETE CASCADE,
    address VARCHAR(128) NOT NULL,
    network VARCHAR(32) NOT NULL,
    confidence_score INT NOT NULL,
    is_false_positive BOOLEAN NOT NULL,
    rejection_reason TEXT,
    entity_tag VARCHAR(255),
    interaction_recorded BOOLEAN NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_findings_run ON address_findings(run_id);
