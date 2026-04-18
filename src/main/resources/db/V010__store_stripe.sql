-- Stripe checkout + fulfillment audit (plugin)

CREATE TABLE IF NOT EXISTS store_orders (
    id                   BIGSERIAL      PRIMARY KEY,
    stripe_session_id    VARCHAR(128)   NOT NULL UNIQUE,
    stripe_payment_intent VARCHAR(128),
    minecraft_uuid       VARCHAR(36)    NOT NULL REFERENCES players(uuid) ON DELETE CASCADE,
    package_id           VARCHAR(64)    NOT NULL,
    amount_cents         INT            NOT NULL,
    currency             VARCHAR(8)     NOT NULL DEFAULT 'usd',
    status               VARCHAR(32)    NOT NULL DEFAULT 'pending',
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    fulfilled_at         TIMESTAMPTZ,
    error_message        TEXT
);

CREATE INDEX IF NOT EXISTS idx_store_orders_player ON store_orders(minecraft_uuid);
CREATE INDEX IF NOT EXISTS idx_store_orders_status ON store_orders(status);
