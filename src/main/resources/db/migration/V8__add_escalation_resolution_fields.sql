ALTER TABLE core.escalations
    ADD COLUMN resolution_text TEXT,
    ADD COLUMN resolution_updated_at TIMESTAMPTZ,
    ADD COLUMN resolved_by_operator_id UUID,
    ADD CONSTRAINT fk_escalations_resolved_by_operator_id
        FOREIGN KEY (resolved_by_operator_id)
        REFERENCES core.dashboard_operators (id) ON DELETE SET NULL;
