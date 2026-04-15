-- =============================================================================
-- V002 — Column catch-up for existing databases
-- Adds columns that were introduced incrementally after the initial deploy.
-- All statements are guarded by IF NOT EXISTS — safe no-ops on fresh installs
-- that already got the full schema from V001.
-- =============================================================================

-- players: columns added post-launch
ALTER TABLE IF EXISTS players ADD COLUMN IF NOT EXISTS active_tag_display  TEXT;
ALTER TABLE IF EXISTS players ADD COLUMN IF NOT EXISTS donor_rank          VARCHAR(32) DEFAULT NULL;
ALTER TABLE IF EXISTS players ADD COLUMN IF NOT EXISTS rank_display_pref   VARCHAR(8)  DEFAULT 'primary';

-- player_stats: columns added post-launch
ALTER TABLE IF EXISTS player_stats ADD COLUMN IF NOT EXISTS mcmmo_level         INT NOT NULL DEFAULT 0;
ALTER TABLE IF EXISTS player_stats ADD COLUMN IF NOT EXISTS event_wins_combat   INT NOT NULL DEFAULT 0;
ALTER TABLE IF EXISTS player_stats ADD COLUMN IF NOT EXISTS event_wins_chat     INT NOT NULL DEFAULT 0;
ALTER TABLE IF EXISTS player_stats ADD COLUMN IF NOT EXISTS event_wins_hardcore INT NOT NULL DEFAULT 0;
