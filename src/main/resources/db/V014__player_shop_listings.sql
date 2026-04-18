-- I2 Player Shops — listing rows (ROADMAP); purchase/GUI wired in a follow-up

CREATE TABLE IF NOT EXISTS player_shop_listings (
    id            BIGSERIAL PRIMARY KEY,
    seller_uuid   VARCHAR(36)    NOT NULL REFERENCES players(uuid) ON DELETE CASCADE,
    chest_world   VARCHAR(64)    NOT NULL,
    chest_x       INT            NOT NULL,
    chest_y       INT            NOT NULL,
    chest_z       INT            NOT NULL,
    price_coins   BIGINT         NOT NULL CHECK (price_coins >= 0),
    quantity      INT            NOT NULL DEFAULT 1 CHECK (quantity >= 0),
    item_bytes    BYTEA          NOT NULL,
    active        BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    expires_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_player_shop_seller ON player_shop_listings(seller_uuid);
CREATE INDEX IF NOT EXISTS idx_player_shop_chest ON player_shop_listings(chest_world, chest_x, chest_y, chest_z);
CREATE INDEX IF NOT EXISTS idx_player_shop_active ON player_shop_listings(active) WHERE active = TRUE;
