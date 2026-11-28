SQL
ALTER TABLE core.escalations
    ADD COLUMN resolution_id UUID,
    ADD CONSTRAINT uq_escalations_resolution_id UNIQUE (resolution_id);

UPDATE core.escalations
SET resolution_id = gen_random_uuid()
WHERE resolved_at IS NOT NULL
  AND resolution_id IS NULL;
