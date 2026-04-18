-- Samples for Discord /activity chart (last 24–48h retained by plugin trim job)

CREATE TABLE IF NOT EXISTS discord_activity_sample (
    id             BIGSERIAL      PRIMARY KEY,
    sampled_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    online_count   INT            NOT NULL,
    max_players    INT            NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_discord_activity_sample_time ON discord_activity_sample(sampled_at DESC);
