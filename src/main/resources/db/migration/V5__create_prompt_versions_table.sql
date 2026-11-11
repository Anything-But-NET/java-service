CREATE TABLE core.prompt_versions (
    id UUID PRIMARY KEY,
    prompt_text TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);