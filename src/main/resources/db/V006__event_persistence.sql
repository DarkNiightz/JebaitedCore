-- =============================================================================
-- V006 — Event session persistence
-- Records every event that runs and each player's participation stats.
-- Also adds party_time_ms to friendship_stats for Wrapped "best party friend" calc.
-- Idempotent: CREATE TABLE IF NOT EXISTS + ADD COLUMN IF NOT EXISTS.
-- =============================================================================

-- ── event_sessions ────────────────────────────────────────────────────────────
-- One row per event. Written on open, finalized on end.
CREATE TABLE IF NOT EXISTS event_sessions (
    id                SERIAL       PRIMARY KEY,
    event_type        VARCHAR(32)  NOT NULL,           -- FFA | KOTH | DUELS | HARDCORE_FFA | HC_KOTH | HC_DUELS | CTF
    arena_key         VARCHAR(64)  DEFAULT NULL,       -- null until arena config system ships
    started_at        BIGINT       NOT NULL,           -- epoch ms
    ended_at          BIGINT       DEFAULT NULL,       -- null while in progress
    winner_uuid       VARCHAR(36)  DEFAULT NULL,       -- null for team events or cancelled events
    winning_team      VARCHAR(16)  DEFAULT NULL,       -- RED/BLUE for future team events
    participant_count INT          NOT NULL DEFAULT 0
);

-- ── event_participants ────────────────────────────────────────────────────────
-- One row per player per event session. Upserted during the event, finalized on end.
CREATE TABLE IF NOT EXISTS event_participants (
    id             SERIAL      PRIMARY KEY,
    session_id     INT         NOT NULL REFERENCES event_sessions(id) ON DELETE CASCADE,
    player_uuid    VARCHAR(36) NOT NULL,
    kills          INT         NOT NULL DEFAULT 0,
    deaths         INT         NOT NULL DEFAULT 0,
    result         VARCHAR(16) NOT NULL DEFAULT 'LOSS',  -- WIN | LOSS | SPECTATE
    coins_earned   INT         NOT NULL DEFAULT 0,
    xp_earned      INT         NOT NULL DEFAULT 0,
    UNIQUE (session_id, player_uuid)
);

CREATE INDEX IF NOT EXISTS idx_event_participants_player  ON event_participants (player_uuid);
CREATE INDEX IF NOT EXISTS idx_event_participants_session ON event_participants (session_id);
CREATE INDEX IF NOT EXISTS idx_event_sessions_type        ON event_sessions (event_type);
CREATE INDEX IF NOT EXISTS idx_event_sessions_started     ON event_sessions (started_at);

-- ── friendship_stats: party time together ─────────────────────────────────────
-- Cumulative ms two players have spent in the same party together.
-- Updated when a party disbands or a player leaves.
-- Used by Wrapped for "best party friend" calculation.
ALTER TABLE friendship_stats ADD COLUMN IF NOT EXISTS party_time_ms BIGINT NOT NULL DEFAULT 0;
