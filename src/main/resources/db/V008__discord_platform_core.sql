-- Discord platform core tables (bot + plugin + panel contracts)

CREATE TABLE IF NOT EXISTS discord_links (
    player_uuid       VARCHAR(36)    NOT NULL REFERENCES players(uuid) ON DELETE CASCADE,
    discord_user_id   VARCHAR(32)    NOT NULL,
    linked_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    unlinked_at       TIMESTAMPTZ,
    link_source       VARCHAR(32)    NOT NULL DEFAULT 'INGAME_CODE',
    PRIMARY KEY (player_uuid, discord_user_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_discord_links_active_player
    ON discord_links(player_uuid)
    WHERE unlinked_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_discord_links_active_discord
    ON discord_links(discord_user_id)
    WHERE unlinked_at IS NULL;

CREATE TABLE IF NOT EXISTS discord_link_codes (
    code              VARCHAR(32)    PRIMARY KEY,
    player_uuid       VARCHAR(36)    NOT NULL REFERENCES players(uuid) ON DELETE CASCADE,
    issued_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    expires_at        TIMESTAMPTZ    NOT NULL,
    consumed_at       TIMESTAMPTZ,
    consumed_by       VARCHAR(32)
);

CREATE INDEX IF NOT EXISTS idx_discord_link_codes_player ON discord_link_codes(player_uuid);
CREATE INDEX IF NOT EXISTS idx_discord_link_codes_expires ON discord_link_codes(expires_at);

CREATE TABLE IF NOT EXISTS integration_audit_log (
    id                BIGSERIAL      PRIMARY KEY,
    source_service    VARCHAR(32)    NOT NULL,
    event_type        VARCHAR(64)    NOT NULL,
    correlation_id    VARCHAR(64)    NOT NULL,
    actor_id          VARCHAR(64),
    target_id         VARCHAR(64),
    payload_json      TEXT           NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_integration_audit_created ON integration_audit_log(created_at);
CREATE INDEX IF NOT EXISTS idx_integration_audit_correlation ON integration_audit_log(correlation_id);

CREATE TABLE IF NOT EXISTS webhook_nonces (
    nonce             VARCHAR(96)    PRIMARY KEY,
    source_service    VARCHAR(32)    NOT NULL,
    seen_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    expires_at        TIMESTAMPTZ    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_webhook_nonces_expires ON webhook_nonces(expires_at);
