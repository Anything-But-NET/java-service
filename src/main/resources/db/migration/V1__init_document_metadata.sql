CREATE SCHEMA IF NOT EXISTS core;

CREATE TABLE IF NOT EXISTS core.document_metadata (
    document_id VARCHAR(255) NOT NULL,
    source_system VARCHAR(100),
    title TEXT,
    url TEXT,
    last_updated TIMESTAMPTZ,
    last_sync_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_document_metadata PRIMARY KEY (document_id)
    );