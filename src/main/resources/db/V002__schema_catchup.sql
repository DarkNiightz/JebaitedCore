-- =============================================================================
-- V002 — Schema catch-up for existing DBs that were created before the V001
--         squash. Adds all columns that V001's CREATE TABLE IF NOT EXISTS
--         skipped because the tables already existed with an older structure.
-- Safe to run on both old and new DBs (ADD COLUMN IF NOT EXISTS is idempotent).
-- =============================================================================

-- ── players ──────────────────────────────────────────────────────────────────
ALTER TABLE players ADD COLUMN IF NOT EXISTS donor_rank        VARCHAR(32)  DEFAULT NULL;
ALTER TABLE players ADD COLUMN IF NOT EXISTS rank_display_pref VARCHAR(8)   DEFAULT 'primary';
ALTER TABLE players ADD COLUMN IF NOT EXISTS favorite_cosmetics  TEXT;
ALTER TABLE players ADD COLUMN IF NOT EXISTS previewed_cosmetics TEXT;
ALTER TABLE players ADD COLUMN IF NOT EXISTS cosmetic_loadouts   TEXT;
ALTER TABLE players ADD COLUMN IF NOT EXISTS active_tag          TEXT;
ALTER TABLE players ADD COLUMN IF NOT EXISTS active_tag_display  TEXT;
ALTER TABLE players ADD COLUMN IF NOT EXISTS settings_blob       TEXT;

-- ── player_stats ─────────────────────────────────────────────────────────────
ALTER TABLE player_stats ADD COLUMN IF NOT EXISTS playtime_ms         BIGINT        NOT NULL DEFAULT 0;
ALTER TABLE player_stats ADD COLUMN IF NOT EXISTS mobs_killed         INT           NOT NULL DEFAULT 0;
ALTER TABLE player_stats ADD COLUMN IF NOT EXISTS bosses_killed       INT           NOT NULL DEFAULT 0;
ALTER TABLE player_stats ADD COLUMN IF NOT EXISTS event_wins_combat   INT           NOT NULL DEFAULT 0;
ALTER TABLE player_stats ADD COLUMN IF NOT EXISTS event_wins_chat     INT           NOT NULL DEFAULT 0;
ALTER TABLE player_stats ADD COLUMN IF NOT EXISTS event_wins_hardcore INT           NOT NULL DEFAULT 0;
ALTER TABLE player_stats ADD COLUMN IF NOT EXISTS balance             NUMERIC(18,2) NOT NULL DEFAULT 0;

-- ── maintenance_whitelist ────────────────────────────────────────────────────
-- Old schema had (id, type, value, added_by, added_at).
-- Plugin expects (player_name PRIMARY KEY, added_by, created_at).
-- Whitelist is operational with no user-owned data — safe to replace.
DROP TABLE IF EXISTS maintenance_whitelist;
CREATE TABLE maintenance_whitelist (
    player_name VARCHAR(16) PRIMARY KEY,
    added_by    VARCHAR(64),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
