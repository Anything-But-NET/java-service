CREATE TYPE core.generation_outcome AS ENUM (
    'SUCCESS', 'FAILURE'
);

CREATE TYPE core.error_category AS ENUM (
    'VALIDATION_ERROR', 'RETRIEVAL_TIMEOUT', 'VECTOR_DB_UNAVAILABLE',
    'LLM_PROVIDER_ERROR', 'INTERNAL_ERROR', 'INTERNAL_CONNECTION_ERROR'
);

CREATE TABLE core.generation_diagnostics (
    id UUID PRIMARY KEY,
    query_id VARCHAR(255) NOT NULL,
    outcome core.generation_outcome NOT NULL,
    error_category core.error_category,
    confidence_score DOUBLE PRECISION,
    created_at TIMESTAMPTZ NOT NULL
);