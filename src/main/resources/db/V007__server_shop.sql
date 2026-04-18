-- Server economy shop: priced items + transaction log (ROADMAP §17)

CREATE TABLE IF NOT EXISTS server_shop_prices (
    item_key      VARCHAR(64)    PRIMARY KEY,
    category      VARCHAR(32)    NOT NULL,
    display_name  VARCHAR(64)    NOT NULL,
    buy_price     NUMERIC(18,2)  NOT NULL,
    sell_price    NUMERIC(18,2)  NOT NULL,
    max_stack     INT            NOT NULL DEFAULT 64,
    sort_order    INT            NOT NULL DEFAULT 0,
    enabled       BOOLEAN        NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS shop_transactions (
    id            BIGSERIAL      PRIMARY KEY,
    player_uuid   VARCHAR(36)    NOT NULL REFERENCES players(uuid) ON DELETE CASCADE,
    item_key      VARCHAR(64)    NOT NULL,
    quantity      INT            NOT NULL,
    unit_price    NUMERIC(18,2)  NOT NULL,
    total         NUMERIC(18,2)  NOT NULL,
    action        VARCHAR(4)     NOT NULL CHECK (action IN ('BUY','SELL')),
    transacted_at TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_shop_tx_player ON shop_transactions(player_uuid);
CREATE INDEX IF NOT EXISTS idx_shop_tx_time   ON shop_transactions(transacted_at);
