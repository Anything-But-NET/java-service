CREATE TABLE IF NOT EXISTS core.users (
    email VARCHAR(255) NOT NULL,
    allowed_groups TEXT[] NOT NULL DEFAULT '{}',
    last_updated TIMESTAMPTZ,
    last_sync_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (email)
);
