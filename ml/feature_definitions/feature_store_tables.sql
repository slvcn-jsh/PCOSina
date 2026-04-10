-- PCOSINA ML feature-store table definitions (v1 implemented baseline)

CREATE TABLE IF NOT EXISTS ml_events (
    id TEXT PRIMARY KEY,
    event_name TEXT NOT NULL,
    uid_hash TEXT NOT NULL,
    request_id TEXT NOT NULL,
    plan_id TEXT,
    recipe_id TEXT,
    slot_index INTEGER,
    event_time_ms BIGINT NOT NULL,
    policy_version TEXT NOT NULL,
    schema_version TEXT NOT NULL,
    payload_json TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    dedupe_key TEXT
);

CREATE TABLE IF NOT EXISTS ml_stage1_candidate_features (
    id TEXT PRIMARY KEY,
    request_id TEXT NOT NULL,
    uid_hash TEXT NOT NULL,
    recipe_id TEXT NOT NULL,
    meal_bucket TEXT,
    generated_at_ms BIGINT NOT NULL,
    model_score REAL,
    heuristic_score REAL,
    ranking_strategy TEXT NOT NULL,
    model_version TEXT,
    feature_json TEXT NOT NULL,
    selected_by_solver INTEGER,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ml_events_name_time ON ml_events (event_name, event_time_ms);
CREATE INDEX IF NOT EXISTS idx_ml_events_request ON ml_events (request_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_ml_events_dedupe ON ml_events (dedupe_key);
CREATE INDEX IF NOT EXISTS idx_ml_stage1_generated ON ml_stage1_candidate_features (generated_at_ms);
CREATE INDEX IF NOT EXISTS idx_ml_stage1_request ON ml_stage1_candidate_features (request_id);
