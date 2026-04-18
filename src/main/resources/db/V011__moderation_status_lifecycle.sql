ALTER TABLE moderation_history
    ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'active',
    ADD COLUMN IF NOT EXISTS pardon_actor VARCHAR(16),
    ADD COLUMN IF NOT EXISTS pardon_actor_uuid VARCHAR(36),
    ADD COLUMN IF NOT EXISTS pardon_at BIGINT;

CREATE INDEX IF NOT EXISTS idx_moderation_history_target_status
    ON moderation_history (target_uuid, status, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_moderation_history_type_status
    ON moderation_history (type, status, timestamp DESC);
