# Web panel — Server shop (§17) handoff bundle

**Purpose:** Single file to paste into a **web-admin** AI chat or attach to a panel issue. The **JebaitedCore** plugin owns in-game `/shop`; the **Node/Express panel** reads/writes the **same PostgreSQL** tables. The plugin does **not** ship HTTP admin APIs for shop in MVP.

**Owner:** Jamie implements routes/UI in the `**web-admin`** repository — this plugin repo only ships the DB schema + in-game behaviour; track panel progress outside Grafter.

**Canonical detail:** [ROADMAP.md §17](../ROADMAP.md) (full GUI, economy rules, staging checklist).

**Plugin migration:** [V007__server_shop.sql](../src/main/resources/db/V007__server_shop.sql)

---

## Copy-paste prompt (give this to the web-panel AI)

```
You are implementing admin UI for the Jebaited Minecraft server shop.

CONSTRAINTS:
- Data lives in PostgreSQL tables shared with the Paper plugin JebaitedCore.
- MVP: implement panel routes/queries against the DB only. Do NOT require new HTTP endpoints on the Minecraft plugin for price edits or transaction listing.
- After staff edit prices in the DB, operators must run /jreload on the game server (or restart) so ShopManager.reload() reloads enabled rows from server_shop_prices.
- Auth: use the panel’s existing staff session; parameterize all SQL.

TABLES (authoritative schema in plugin: src/main/resources/db/V007__server_shop.sql):

1) server_shop_prices
   - item_key VARCHAR(64) PRIMARY KEY  (e.g. minecraft:oak_log)
   - category VARCHAR(32) NOT NULL
   - display_name VARCHAR(64) NOT NULL
   - buy_price, sell_price NUMERIC(18,2) NOT NULL
   - max_stack INT NOT NULL DEFAULT 64
   - sort_order INT NOT NULL DEFAULT 0
   - enabled BOOLEAN NOT NULL DEFAULT TRUE
   Plugin loads WHERE enabled = TRUE only.

2) shop_transactions
   - id BIGSERIAL PRIMARY KEY
   - player_uuid VARCHAR(36) NOT NULL REFERENCES players(uuid) ON DELETE CASCADE
   - item_key VARCHAR(64) NOT NULL
   - quantity INT NOT NULL
   - unit_price, total NUMERIC(18,2) NOT NULL
   - action VARCHAR(4) NOT NULL CHECK (action IN ('BUY','SELL'))
   - transacted_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
   Indexes: player_uuid, transacted_at.

SUGGESTED PANEL PAGES:
- /admin/shop/prices — CRUD-style editor for server_shop_prices (at least list + inline edit buy/sell/enabled/sort/category/display_name/max_stack).
- /admin/shop/log — paginated shop_transactions with filters: player (uuid or name via join players), date range, action BUY/SELL.

EXAMPLE JSON for one transaction row (enriched for UI):
{
  "id": 1042,
  "player_uuid": "...",
  "player_name": "PlayerName",
  "item_key": "minecraft:diamond",
  "quantity": 5,
  "unit_price": 120.00,
  "total": 600.00,
  "action": "BUY",
  "transacted_at": "2026-04-16T20:00:00.000Z"
}

OPERATIONS NOTE:
- Price changes: UPDATE server_shop_prices then remind ops to /jreload on MC.
- Ledger gaps: if shop_transactions insert fails in-game, plugin logs + debug feed; panel should not assume 100% coverage without monitoring DB errors server-side.

DELIVERABLE: spec + safe SQL/ORM patterns + wireframes for the two pages; staff-only gates.
```

---

## What each piece is for


| Piece                    | Role                                                                                                 |
| ------------------------ | ---------------------------------------------------------------------------------------------------- |
| `**server_shop_prices**` | Catalogue of items the in-game shop can sell/buy. Panel edits this for live tuning.                  |
| `**shop_transactions**`  | Append-only ledger of BUY/SELL from the plugin (async insert). Panel reads for audits and analytics. |
| `**players.uuid` FK**    | Ensures transaction rows tie to known players; join for display names.                               |
| `**/jreload` on MC**     | Reconstructs `ShopManager` and reloads price cache from DB without full server restart.              |
| **No plugin HTTP (MVP)** | Keeps panel work independent; avoids duplicating auth and exposing the game server.                  |


---

## Staging checks (panel-relevant)

From ROADMAP §17 — after plugin deploy, confirm in DB:

1. `server_shop_prices` has rows (or seed path documented).
2. After a test BUY and SELL in-game, `shop_transactions` contains two rows with correct `action`, amounts, and timestamps.
3. Edit one price in DB → `/jreload` → in-game shop shows new price.

---

## Example SQL snippets (panel backend)

**List recent transactions:**

```sql
SELECT t.id, t.player_uuid, p.username AS player_name, t.item_key, t.quantity,
       t.unit_price, t.total, t.action, t.transacted_at
FROM shop_transactions t
LEFT JOIN players p ON p.uuid = t.player_uuid
ORDER BY t.transacted_at DESC
LIMIT 100;
```

**Update a price row:**

```sql
UPDATE server_shop_prices
SET buy_price = ?, sell_price = ?, enabled = ?, sort_order = ?
WHERE item_key = ?;
```

(Use parameterized queries in application code.)

---

## Plugin reference (read-only for panel devs)


| File                             | Notes                                                                                     |
| -------------------------------- | ----------------------------------------------------------------------------------------- |
| `core/shop/ShopManager.java`     | Loads prices, writes transactions, validates rate limits from `config.yml` `server_shop:` |
| `core/commands/ShopCommand.java` | `/shop`, alias `market`                                                                   |


---

*Generated for handoff between JebaitedCore repo and web-admin; keep in sync when §17 schema or behaviour changes.*