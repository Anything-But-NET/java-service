ALTER TYPE core.dashboard_operator_authority ADD VALUE 'ANALYTICS';
ALTER TYPE core.dashboard_operator_authority ADD VALUE 'PROMPTS';

CREATE TYPE core.chat_channel AS ENUM (
    'SLACK', 'TEAMS'
);

ALTER TYPE core.generation_outcome RENAME VALUE 'SUCCESS' TO 'ANSWERED';
ALTER TYPE core.generation_outcome ADD VALUE 'SAFE_NO_ANSWER';
ALTER TYPE core.generation_outcome ADD VALUE 'ESCALATED';

CREATE TYPE core.question_outcome_reason_code AS ENUM (
    'LOW_CONFIDENCE', 'EMPTY_RETRIEVAL'
);

ALTER TABLE core.generation_diagnostics
    ADD COLUMN channel core.chat_channel NOT NULL DEFAULT 'SLACK',
    ADD COLUMN reason_code core.question_outcome_reason_code,
    ADD COLUMN latency_ms INTEGER,
    ADD COLUMN prompt_version_id UUID,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE core.generation_diagnostics
    ADD CONSTRAINT uq_generation_diagnostics_query_id UNIQUE (query_id);

DROP INDEX IF EXISTS core.idx_gen_diag_query_id;
CREATE INDEX idx_gen_diag_outcome ON core.generation_diagnostics (outcome);
CREATE INDEX idx_gen_diag_channel ON core.generation_diagnostics (channel);

