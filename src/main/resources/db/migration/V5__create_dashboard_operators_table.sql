CREATE TYPE core.dashboard_operator_authority AS ENUM (
    'LOGS', 'METADATA', 'USERS', 'OPERATORS'
);

CREATE TYPE core.dashboard_operator_status AS ENUM (
    'ACTIVE', 'BLOCKED'
);

CREATE TABLE core.dashboard_operators (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    authorities core.dashboard_operator_authority[] NOT NULL DEFAULT '{}',
    password_hash TEXT NOT NULL,
    status core.dashboard_operator_status NOT NULL DEFAULT 'ACTIVE',
    last_refreshed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_dashboard_operators_email UNIQUE (email)
);

CREATE UNIQUE INDEX uq_dashboard_operators_email_lower
    ON core.dashboard_operators (LOWER(email));