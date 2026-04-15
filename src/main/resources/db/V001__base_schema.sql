-- =============================================================================
-- V001 — Base schema
-- Creates all tables with their full current column set.
-- Safe to run against an existing DB (CREATE TABLE IF NOT EXISTS / IF NOT EXISTS).
-- =============================================================================

-- Players
CREATE TABLE IF NOT EXISTS players (
    uuid              VARCHAR(36)  PRIMARY KEY,
    username          VARCHAR(16)  NOT NULL,
    rank              VARCHAR(32)  NOT NULL DEFAULT 'pleb',
    donor_rank        VARCHAR(32)  DEFAULT NULL,
    rank_display_pref VARCHAR(8)   DEFAULT 'primary',
    first_joined      BIGINT       NOT NULL,
    last_joined       BIGINT       NOT NULL,
    favorite_cosmetics  TEXT,
    previewed_cosmetics TEXT,
    cosmetic_loadouts   TEXT,
    active_tag          TEXT,
    active_tag_display  TEXT,
    settings_blob       TEXT
);

-- Player aggregate stats (one row per player)
CREATE TABLE IF NOT EXISTS player_stats (
    uuid             VARCHAR(36)   PRIMARY KEY,
    kills            INT           NOT NULL DEFAULT 0,
    deaths           INT           NOT NULL DEFAULT 0,
    mobs_killed      INT           NOT NULL DEFAULT 0,
    bosses_killed    INT           NOT NULL DEFAULT 0,
    blocks_broken    INT           NOT NULL DEFAULT 0,
    crops_broken     INT           NOT NULL DEFAULT 0,
    fish_caught      INT           NOT NULL DEFAULT 0,
    playtime_ms      BIGINT        NOT NULL DEFAULT 0,
    playtime_seconds BIGINT        NOT NULL DEFAULT 0,
    messages_sent    INT           NOT NULL DEFAULT 0,
    commands_sent    INT           NOT NULL DEFAULT 0,
    cosmetic_coins   INT           NOT NULL DEFAULT 0,
    balance          NUMERIC(18,2) NOT NULL DEFAULT 0,
    mcmmo_level      INT           NOT NULL DEFAULT 0,
    event_wins_combat   INT        NOT NULL DEFAULT 0,
    event_wins_chat     INT        NOT NULL DEFAULT 0,
    event_wins_hardcore INT        NOT NULL DEFAULT 0,
    CONSTRAINT fk_player_stats FOREIGN KEY (uuid) REFERENCES players (uuid) ON DELETE CASCADE
);

-- Per-event-key stats (flexible — no schema change needed per new event type)
CREATE TABLE IF NOT EXISTS player_event_stats (
    player_uuid  VARCHAR(36) NOT NULL,
    event_key    VARCHAR(64) NOT NULL,
    participated INT         NOT NULL DEFAULT 0,
    won          INT         NOT NULL DEFAULT 0,
    lost         INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (player_uuid, event_key),
    CONSTRAINT fk_event_player FOREIGN KEY (player_uuid) REFERENCES players (uuid) ON DELETE CASCADE
);

-- Moderation history
CREATE TABLE IF NOT EXISTS moderation_history (
    id          SERIAL      PRIMARY KEY,
    target_uuid VARCHAR(36) NOT NULL,
    target_name VARCHAR(16),
    type        VARCHAR(32) NOT NULL,
    actor       VARCHAR(16),
    actor_uuid  VARCHAR(36),
    reason      VARCHAR(255),
    duration_ms BIGINT,
    expires_at  BIGINT,
    timestamp   BIGINT      NOT NULL
);

-- Moderation live state (active bans, mutes etc.)
CREATE TABLE IF NOT EXISTS moderation_state (
    state_key   VARCHAR(96) PRIMARY KEY,
    state_value TEXT        NOT NULL,
    updated_at  BIGINT      NOT NULL
);

-- Cosmetics ownership
CREATE TABLE IF NOT EXISTS player_cosmetics (
    id            SERIAL      PRIMARY KEY,
    player_uuid   VARCHAR(36) NOT NULL,
    cosmetic_id   VARCHAR(64) NOT NULL,
    cosmetic_type VARCHAR(32) NOT NULL,
    is_active     BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_player_cosmetic FOREIGN KEY (player_uuid) REFERENCES players (uuid) ON DELETE CASCADE,
    UNIQUE (player_uuid, cosmetic_id, cosmetic_type)
);

-- Server-wide aggregate counters
CREATE TABLE IF NOT EXISTS overall_stats (
    stat_key   VARCHAR(96) PRIMARY KEY,
    stat_value BIGINT      NOT NULL DEFAULT 0,
    updated_at BIGINT      NOT NULL
);

-- Rank upgrade requests
CREATE TABLE IF NOT EXISTS rank_change_requests (
    id                 BIGSERIAL   PRIMARY KEY,
    requester_username TEXT,
    target_uuid        VARCHAR(36),
    target_name        TEXT,
    requested_rank     TEXT        NOT NULL,
    status             TEXT        NOT NULL DEFAULT 'pending',
    approved_by        TEXT,
    notes              TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    decided_at         TIMESTAMPTZ,
    applied_at         TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_rank_change_requests_status ON rank_change_requests (status, applied_at);
CREATE INDEX IF NOT EXISTS idx_rank_change_requests_target ON rank_change_requests (target_uuid, target_name);

-- Chat and command audit logs
CREATE TABLE IF NOT EXISTS chat_logs (
    id          BIGSERIAL   PRIMARY KEY,
    player_uuid UUID        NOT NULL,
    player_name VARCHAR(16) NOT NULL,
    message     TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_chat_logs_player_created ON chat_logs (player_uuid, created_at DESC);

CREATE TABLE IF NOT EXISTS command_logs (
    id          BIGSERIAL   PRIMARY KEY,
    player_uuid UUID        NOT NULL,
    player_name VARCHAR(16) NOT NULL,
    command     TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_command_logs_player_created ON command_logs (player_uuid, created_at DESC);

-- Maintenance mode
CREATE TABLE IF NOT EXISTS server_maintenance (
    id         SERIAL      PRIMARY KEY,
    enabled    BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_by VARCHAR(64),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- Seed one row so there's always a state to read
INSERT INTO server_maintenance (enabled, updated_by)
SELECT FALSE, 'bootstrap'
WHERE NOT EXISTS (SELECT 1 FROM server_maintenance);

CREATE TABLE IF NOT EXISTS maintenance_whitelist (
    player_name VARCHAR(16) PRIMARY KEY,
    added_by    VARCHAR(64),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Staff notes on players
CREATE TABLE IF NOT EXISTS player_notes (
    id          BIGSERIAL   PRIMARY KEY,
    target_uuid VARCHAR(36),
    target_name VARCHAR(32),
    author      VARCHAR(64),
    note        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_player_notes_target ON player_notes (target_uuid, created_at DESC);

-- Player watchlist
CREATE TABLE IF NOT EXISTS watchlist_entries (
    id          BIGSERIAL   PRIMARY KEY,
    target_uuid VARCHAR(36),
    target_name VARCHAR(32),
    reason      TEXT,
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_watchlist_entries_target ON watchlist_entries (target_uuid, target_name);
