CREATE TABLE IF NOT EXISTS document_metadata (
    document_id VARCHAR(255) NOT NULL,
    source_system VARCHAR(100),
    title TEXT,
    url TEXT,
    last_updated TIMESTAMPTZ,
    allowed_groups TEXT[] NOT NULL DEFAULT '{}',
    last_sync_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_document_metadata PRIMARY KEY (document_id)
);

CREATE INDEX IF NOT EXISTS idx_document_metadata_allowed_groups
    ON document_metadata USING GIN (allowed_groups);
