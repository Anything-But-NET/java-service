CREATE TYPE core.sync_status AS ENUM (
    'IN_PROGRESS',
    'COMPLETED',
    'FAILED_FETCH',
    'FAILED_PARSE',
    'FAILED_STORAGE'
);

CREATE TABLE core.document_sync_runs (
    id UUID PRIMARY KEY,
    document_id VARCHAR(255) NOT NULL,
    status core.sync_status NOT NULL DEFAULT 'IN_PROGRESS',
    error_message TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ
);

CREATE INDEX idx_sync_runs_doc_id ON core.document_sync_runs(document_id);