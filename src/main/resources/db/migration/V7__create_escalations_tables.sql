ALTER TYPE core.dashboard_operator_authority ADD VALUE 'ESCALATIONS';

CREATE TYPE core.escalation_status AS ENUM (
    'PENDING',
    'IN_PROGRESS',
    'RESOLVED',
    'CLOSED',
    'CANCELLED'
);

CREATE TABLE core.escalations (
    id UUID PRIMARY KEY,
    query_id VARCHAR(255) NOT NULL,
    status core.escalation_status NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    CONSTRAINT uq_escalations_query_id UNIQUE (query_id),
    CONSTRAINT fk_escalations_query_id FOREIGN KEY (query_id)
        REFERENCES core.generation_diagnostics (query_id) ON DELETE RESTRICT
);

CREATE INDEX idx_escalations_status ON core.escalations (status);
CREATE INDEX idx_escalations_created_at ON core.escalations (created_at);

CREATE TABLE core.escalation_status_history (
    id UUID PRIMARY KEY,
    escalation_id UUID NOT NULL,
    from_status core.escalation_status,
    to_status core.escalation_status NOT NULL,
    changed_by_operator_id UUID,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_esc_history_escalation_id FOREIGN KEY (escalation_id)
        REFERENCES core.escalations (id) ON DELETE CASCADE,
    CONSTRAINT fk_esc_history_operator_id FOREIGN KEY (changed_by_operator_id)
        REFERENCES core.dashboard_operators (id) ON DELETE SET NULL
);

CREATE INDEX idx_esc_history_escalation_id ON core.escalation_status_history (escalation_id);
CREATE INDEX idx_esc_history_changed_at ON core.escalation_status_history (changed_at);