CREATE TABLE IF NOT EXISTS booster_inventory (
    player_uuid VARCHAR(36) NOT NULL,
    booster_key VARCHAR(64) NOT NULL,
    quantity INT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, booster_key),
    CONSTRAINT fk_booster_inventory_player FOREIGN KEY (player_uuid) REFERENCES players (uuid) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS active_boosters (
    id BIGSERIAL PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    booster_key VARCHAR(64) NOT NULL,
    multiplier NUMERIC(6,2) NOT NULL DEFAULT 1.00,
    started_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    source VARCHAR(32),
    server VARCHAR(16),
    CONSTRAINT fk_active_boosters_player FOREIGN KEY (player_uuid) REFERENCES players (uuid) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_active_boosters_player_expires
    ON active_boosters (player_uuid, expires_at DESC);

CREATE TABLE IF NOT EXISTS player_quests (
    player_uuid VARCHAR(36) NOT NULL,
    quest_key VARCHAR(96) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    progress INT NOT NULL DEFAULT 0,
    goal INT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL,
    completed_at BIGINT,
    metadata TEXT,
    PRIMARY KEY (player_uuid, quest_key),
    CONSTRAINT fk_player_quests_player FOREIGN KEY (player_uuid) REFERENCES players (uuid) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_player_quests_status_updated
    ON player_quests (status, updated_at DESC);
