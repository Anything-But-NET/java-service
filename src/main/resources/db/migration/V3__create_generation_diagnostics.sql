CREATE TABLE core.generation_diagnostics (
    id UUID PRIMARY KEY,
    query_id VARCHAR(255) NOT NULL,
    outcome VARCHAR(50) NOT NULL,
    error_category VARCHAR(100),
    confidence_score DOUBLE PRECISION,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);