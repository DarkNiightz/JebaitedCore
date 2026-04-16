-- =============================================================================
-- V001 — Complete schema
-- Single source-of-truth for a fresh install. Contains every table, index,
-- and seed row the plugin requires. Squashed from V001–V016.
-- Safe on existing DBs (CREATE TABLE IF NOT EXISTS, IF NOT EXISTS index).
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
    reviewer_username  TEXT,
    decision_note      TEXT,
    notes              TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    decided_at         TIMESTAMPTZ,
    applied_at         TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_rank_change_requests_status ON rank_change_requests (status, applied_at);
CREATE INDEX IF NOT EXISTS idx_rank_change_requests_target ON rank_change_requests (target_uuid, target_name);

-- Chat audit log
CREATE TABLE IF NOT EXISTS chat_logs (
    id          BIGSERIAL   PRIMARY KEY,
    player_uuid UUID        NOT NULL,
    player_name VARCHAR(16) NOT NULL,
    message     TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_chat_logs_player_created ON chat_logs (player_uuid, created_at DESC);

-- Per-player command log (individual command entries — aggregates live in player_stats.commands_sent)
CREATE TABLE IF NOT EXISTS player_command_log (
    id          BIGSERIAL   PRIMARY KEY,
    uuid        VARCHAR(36) NOT NULL,
    username    VARCHAR(16) NOT NULL,
    command     TEXT        NOT NULL,
    timestamp   BIGINT      NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_player_command_log_uuid ON player_command_log (uuid, timestamp DESC);

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

-- Private vaults (donor-rank players, paginated 54-slot pages)
CREATE TABLE IF NOT EXISTS player_vaults (
    uuid        VARCHAR(36) NOT NULL,
    page        SMALLINT    NOT NULL DEFAULT 0,
    items       BYTEA,
    updated_at  BIGINT      NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000),
    PRIMARY KEY (uuid, page),
    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE
);

-- Broadcaster and bossbar rotation messages
-- type = 'broadcast' | 'bossbar' | 'motd' | 'rule'
CREATE TABLE IF NOT EXISTS server_messages (
    id          SERIAL      PRIMARY KEY,
    type        VARCHAR(16) NOT NULL,
    message     TEXT        NOT NULL,
    sort_order  INT         NOT NULL DEFAULT 0,
    enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_server_messages_type ON server_messages (type, enabled, sort_order);

INSERT INTO server_messages (type, message, sort_order) VALUES
    ('broadcast', '&6&lJebaited &8| &fDiscord: discord.gg/jebaited',        0),
    ('broadcast', '&6&lJebaited &8| &fUse &e/rankup &fto check your rank!', 1),
    ('broadcast', '&6&lJebaited &8| &fReport issues with &e/report',         2),
    ('bossbar',   '&6&lJEBAITED &8» &fWelcome to the server!',              0),
    ('bossbar',   '&6&lJEBAITED &8» &fUse &e/help &ffor a list of commands', 1),
    ('bossbar',   '&6&lJEBAITED &8» &fJoin our Discord!',                    2),
    ('motd',      '&eWelcome back, {player}!',                               0),
    ('motd',      '&bOnline: {online} players',                              1),
    ('rule',      '§7- Be respectful to other players.',                     0),
    ('rule',      '§7- No cheating, exploiting, or unfair clients.',         1),
    ('rule',      '§7- No griefing, stealing, or bypass attempts.',          2),
    ('rule',      '§7- Keep chat clean and follow staff instructions.',      3);

-- Chat game data (scrabble words + quiz Q&A)
-- type = 'scrabble' | 'quiz'
CREATE TABLE IF NOT EXISTS chat_game_data (
    id      SERIAL      PRIMARY KEY,
    type    VARCHAR(16) NOT NULL,
    content TEXT        NOT NULL,
    answer  TEXT,
    enabled BOOLEAN     NOT NULL DEFAULT TRUE
);
CREATE INDEX IF NOT EXISTS idx_chat_game_data_type ON chat_game_data (type, enabled);

INSERT INTO chat_game_data (type, content) VALUES
    ('scrabble', 'minecraft'), ('scrabble', 'diamond'), ('scrabble', 'creeper'),
    ('scrabble', 'survival'), ('scrabble', 'redstone'), ('scrabble', 'enderdragon'),
    ('scrabble', 'nether'), ('scrabble', 'potion'), ('scrabble', 'enchantment'),
    ('scrabble', 'furnace'), ('scrabble', 'obsidian'), ('scrabble', 'villager'),
    ('scrabble', 'jebaited'), ('scrabble', 'blaze'), ('scrabble', 'skeleton'),
    ('scrabble', 'enderpearl'), ('scrabble', 'pumpkin'), ('scrabble', 'crafting'),
    ('scrabble', 'beacon'), ('scrabble', 'firework');

INSERT INTO chat_game_data (type, content, answer) VALUES
    ('quiz', 'What ore drops lapis lazuli?',                                             'lapis ore'),
    ('quiz', 'How many obsidian are needed for a nether portal frame minimum?',          '10'),
    ('quiz', 'Which mob explodes when it gets close to you?',                            'creeper'),
    ('quiz', 'Which block can be used to respawn the Ender Dragon?',                     'end crystal'),
    ('quiz', 'Which enchantment allows you to walk on water by turning it into ice?',    'frost walker'),
    ('quiz', 'Which food item can be used to breed pigs?',                               'carrot'),
    ('quiz', 'Which item is required to craft a beacon?',                                'nether star'),
    ('quiz', 'Which mob can be sheared for its wool?',                                   'sheep'),
    ('quiz', 'What is the maximum level of Protection enchantment?',                     '4'),
    ('quiz', 'What dimension do you need Eyes of Ender to reach?',                       'end'),
    ('quiz', 'Which biome is the only place you can find naturally spawned blue orchids?','swamp'),
    ('quiz', 'What item do you use to ride a pig?',                                      'carrot on a stick'),
    ('quiz', 'How many bookshelves are needed to unlock the max enchantment level?',     '15'),
    ('quiz', 'What mob drops the totem of undying?',                                     'evoker'),
    ('quiz', 'Which item is used to tame a cat?',                                        'raw cod'),
    ('quiz', 'What is the hardest naturally occurring block in Minecraft?',               'obsidian'),
    ('quiz', 'What does a lightning rod attract?',                                       'lightning'),
    ('quiz', 'Which potion ingredient gives you the water breathing effect?',             'pufferfish'),
    ('quiz', 'Which enchantment allows you to mine blocks faster?',                      'efficiency'),
    ('quiz', 'Which mob can spawn with a carved pumpkin on its head?',                   'snow golem'),
    ('quiz', 'Which item can be used to cure a zombie villager?',                        'golden apple'),
    ('quiz', 'What item do you need to enter the stronghold?',                           'eye of ender'),
    ('quiz', 'Which Nether mob drops blaze rods?',                                       'blaze');

-- Moderation presets (staff tab-completion for ban/mute reasons and durations)
CREATE TABLE IF NOT EXISTS moderation_presets (
    id      SERIAL      PRIMARY KEY,
    type    VARCHAR(16) NOT NULL,
    value   TEXT        NOT NULL,
    enabled BOOLEAN     NOT NULL DEFAULT TRUE
);
CREATE INDEX IF NOT EXISTS idx_moderation_presets_type ON moderation_presets (type, enabled);

INSERT INTO moderation_presets (type, value) VALUES
    ('reason', 'hacking'), ('reason', 'griefing'), ('reason', 'chat violation'),
    ('reason', 'exploiting'), ('reason', 'harassment'), ('reason', 'being a cock'),
    ('duration', '30m'), ('duration', '1h'), ('duration', '6h'),
    ('duration', '1d'), ('duration', '7d'), ('duration', 'permanent');

-- Friends system
CREATE TABLE IF NOT EXISTS friendships (
    player_a   VARCHAR(36) NOT NULL,
    player_b   VARCHAR(36) NOT NULL,
    created_at BIGINT      NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000),
    PRIMARY KEY (player_a, player_b),
    CHECK (player_a < player_b),
    FOREIGN KEY (player_a) REFERENCES players(uuid) ON DELETE CASCADE,
    FOREIGN KEY (player_b) REFERENCES players(uuid) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_friendships_a ON friendships (player_a);
CREATE INDEX IF NOT EXISTS idx_friendships_b ON friendships (player_b);

CREATE TABLE IF NOT EXISTS friend_requests (
    sender_uuid   VARCHAR(36) NOT NULL,
    receiver_uuid VARCHAR(36) NOT NULL,
    sent_at       BIGINT      NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000),
    PRIMARY KEY (sender_uuid, receiver_uuid),
    FOREIGN KEY (sender_uuid)   REFERENCES players(uuid) ON DELETE CASCADE,
    FOREIGN KEY (receiver_uuid) REFERENCES players(uuid) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_friend_requests_receiver ON friend_requests (receiver_uuid);

-- XP/kill stats shared between friends while online together
CREATE TABLE IF NOT EXISTS friendship_stats (
    player_a       VARCHAR(36) NOT NULL,
    player_b       VARCHAR(36) NOT NULL,
    xp_together    BIGINT      NOT NULL DEFAULT 0,
    kills_together INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (player_a, player_b),
    FOREIGN KEY (player_a, player_b) REFERENCES friendships(player_a, player_b) ON DELETE CASCADE
);

-- Party system (in-memory state; only aggregate stats persist)
CREATE TABLE IF NOT EXISTS player_party_stats (
    uuid                VARCHAR(36) PRIMARY KEY,
    parties_created     INT    NOT NULL DEFAULT 0,
    parties_joined      INT    NOT NULL DEFAULT 0,
    party_kills         INT    NOT NULL DEFAULT 0,
    party_playtime_ms   BIGINT NOT NULL DEFAULT 0,
    party_blocks_broken INT    NOT NULL DEFAULT 0,
    party_fish_caught   INT    NOT NULL DEFAULT 0,
    party_bosses_killed INT    NOT NULL DEFAULT 0,
    party_xp_shared     BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE
);

-- Achievements and milestone progress
CREATE TABLE IF NOT EXISTS player_achievements (
    uuid            VARCHAR(36) NOT NULL,
    achievement_id  VARCHAR(64) NOT NULL,
    progress        BIGINT      NOT NULL DEFAULT 0,
    tier_reached    INT         NOT NULL DEFAULT 0,
    first_unlock_at BIGINT,
    last_updated    BIGINT      NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000),
    PRIMARY KEY (uuid, achievement_id),
    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_player_achievements_uuid ON player_achievements (uuid);
CREATE INDEX IF NOT EXISTS idx_player_achievements_id   ON player_achievements (achievement_id);

CREATE TABLE IF NOT EXISTS achievement_vouchers (
    id             SERIAL      PRIMARY KEY,
    uuid           VARCHAR(36) NOT NULL,
    achievement_id VARCHAR(64) NOT NULL,
    tier           INT         NOT NULL,
    reward_type    VARCHAR(32) NOT NULL,
    reward_value   VARCHAR(128) NOT NULL,
    granted_at     BIGINT      NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000),
    redeemed_at    BIGINT,
    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_achievement_vouchers_uuid ON achievement_vouchers (uuid);

-- Server-wide key/value settings (spawn coords, toggles, etc.)
CREATE TABLE IF NOT EXISTS server_settings (
    setting_key   VARCHAR(64) PRIMARY KEY,
    setting_value TEXT        NOT NULL,
    updated_at    BIGINT      NOT NULL DEFAULT 0
);

-- Player homes (canonical new schema — V003 reshapes this on existing DBs)
CREATE TABLE IF NOT EXISTS player_homes (
    player_uuid UUID         NOT NULL,
    home_name   VARCHAR(64)  NOT NULL,
    world_name  VARCHAR(64)  NOT NULL,
    x           DOUBLE PRECISION NOT NULL,
    y           DOUBLE PRECISION NOT NULL,
    z           DOUBLE PRECISION NOT NULL,
    yaw         REAL         NOT NULL DEFAULT 0,
    pitch       REAL         NOT NULL DEFAULT 0,
    created_at  BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (player_uuid, home_name)
);
