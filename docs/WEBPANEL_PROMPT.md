# Web Panel — Context Sync & Bug Report
> For the Node.js/Express web-admin at `C:\Users\jamie\Documents\Vibe Code\web-admin` (port 3001).
> Shared PostgreSQL DB with the JebaitedCore Paper plugin.
> This document is the source of truth for what the plugin has shipped, what's changing in the DB, and what bugs need fixing now.

---

## 1. Bug Fixes (do these first)

### 1.1 Moderation History — Wrong entries showing

**Vanish on/off must not appear in moderation history.** `/vanish` is a staff-only utility command to hide from cheaters. It is not a moderation action. Remove it from the log/history view entirely.

**Warns must never show as active.** Warns are instant — like kicks, they fire and expire immediately. They have no duration. If `expires_at` is 0 or in the past, it is expired. A warn will never be "active" and should never appear in the active moderation panel or under active actions.

**Kicks show duration "Perm" — that is wrong.** Kicks have 0s duration by definition. They are instant. A kick is never permanent. All kicks are already expired the moment they're issued. The panel must never display a kick as active, and the duration column should show "Instant" or be omitted entirely for kicks.

**Unbans must not appear in history at all.** History should only show: `ban`, `tempban`, `mute`, `tempmute`. And only if they are currently active. Expired bans/mutes and all unban/unmute records should be hidden from the history view. The point of the history is to show what's currently affecting a player, not a full audit trail.

**Summary of what to show in moderation history:**

| Type | Show? | Active only? |
|------|-------|-------------|
| `ban` | ✅ | ✅ active only |
| `tempban` | ✅ | ✅ active only |
| `mute` | ✅ | ✅ active only |
| `tempmute` | ✅ | ✅ active only |
| `warn` | ❌ | Never |
| `kick` | ❌ | Never |
| `vanish_on` | ❌ | Never |
| `vanish_off` | ❌ | Never |
| `unban` | ❌ | Never |
| `unmute` | ❌ | Never |

---

### 1.2 Moderation Page — UI fixes

**Replace the ugly filter dropdown with a search bar.** Free-text search across username, action type, and actor. The current filter UI is cluttered and unhelpful.

**The Pardon button on warns is wrong.** Warns are instant — there is nothing to pardon. The action button on a warn row should say **[View]** (opens the detail/log entry) not **[Pardon]**. Pardon should only appear on active bans and active mutes.

**Remove Watchlist or hide it from the view for now.** It is not wired up on the plugin side. Don't surface a feature that doesn't work.

---

### 1.3 Console — Add colour rendering

The console output should render Minecraft colour codes visually, the same way the server would display them. Map `§` (section sign) colour codes to HTML/CSS spans:

| MC Code | Colour |
|---------|--------|
| `§0` | #000000 |
| `§1` | #0000AA |
| `§2` | #00AA00 |
| `§3` | #00AAAA |
| `§4` | #AA0000 |
| `§5` | #AA00AA |
| `§6` | #FFAA00 |
| `§7` | #AAAAAA |
| `§8` | #555555 |
| `§9` | #5555FF |
| `§a` | #55FF55 |
| `§b` | #55FFFF |
| `§c` | #FF5555 |
| `§d` | #FF55FF |
| `§e` | #FFFF55 |
| `§f` | #FFFFFF |
| `§l` | bold |
| `§o` | italic |
| `§n` | underline |
| `§m` | strikethrough |
| `§r` | reset all formatting |

Also strip ANSI escape codes (`\x1B[...m`) before rendering — the server outputs both and you don't want raw ANSI leaking into the HTML.

---

### 1.4 Rank Requests — DB column missing + stale rank list

**Bug:** Approving or denying a rank change request throws:
```
column "reviewer_username" of relation "rank_change_requests" does not exist
```

The query is referencing a column that doesn't exist. Either:
- The column is named differently in the actual table (check `\d rank_change_requests` in psql), or
- It needs to be added via migration.

Fix the query to match the actual schema. If the column genuinely needs to exist, add a migration to `src/main/resources/db/` as `V012__rank_requests_fix.sql` and append to `migrations.index`. Do not ALTER TABLE inline in application code.

**Stale rank list — the website must reflect these exact ranks:**

Normal ranks (descending power):
```
owner → developer → admin → srmod → moderator → helper → vip → builder → grandmaster → legend → diamond → gold → pleb
```
Note: `srmod` sits between `admin` and `moderator`. This is a new rank that was missing from the panel's rank dropdown/selector.

Donor ranks (separate field `donor_rank` on player profile, not the primary rank):
```
grandmaster → legend → diamond → gold
```
These are cosmetic overlays — a player keeps their normal rank but has a `donor_rank` field set. The panel should show both where relevant (e.g. player profile page: rank + donor rank as separate badges).

---

## 2. What the Plugin Has Shipped (DB changes to be aware of)

All migrations are in `src/main/resources/db/`. Applied in order via `migrations.index`.

| Migration | What it added |
|-----------|--------------|
| V009__friends.sql | `friendships` table, `friend_requests` table |
| V010__friendship_stats.sql | `friendship_stats` table — per-pair XP-together + kills-together |
| V011__party_stats.sql | `player_party_stats` table — per-player party activity stats |

**Player profile fields added (on `players` table or via V002/V003 column catchup):**
- `donor_rank` VARCHAR(32) — null for non-donors, otherwise: `gold`, `diamond`, `legend`, `grandmaster`
- `rank_display_mode` VARCHAR(32) — `"donor"` if player has chosen to show donor rank in tab, otherwise null/`"primary"`

**New systems running on the plugin:**
- **Friends** — friend add/remove/accept/deny, cached per-player on join
- **Private Vaults** — per-UUID paginated vault. Pages by donor rank: gold=1, diamond=3, legend=5, grandmaster=10
- **Party system** — in-memory only (no party state persisted), but party stats ARE written to `player_party_stats`
- **Settings** — stored in `PlayerProfile.preferences` JSONB field (already on the table). Keys are dot-separated e.g. `notify.friend_request`, `sound.gui_click`

---

## 3. What's Coming Next (prepare endpoints/UI now)

### 3.1 Achievement System (V014)
Two new tables incoming:

```sql
CREATE TABLE IF NOT EXISTS player_achievements (
    uuid            VARCHAR(36) NOT NULL,
    achievement_id  VARCHAR(64) NOT NULL,
    progress        BIGINT NOT NULL DEFAULT 0,
    unlocked_at     BIGINT,       -- NULL = not yet unlocked
    claimed_tag     BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (uuid, achievement_id),
    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS achievement_vouchers (
    uuid            VARCHAR(36) PRIMARY KEY,
    voucher_type    VARCHAR(32) NOT NULL,
    redeemed        BOOLEAN NOT NULL DEFAULT FALSE
);
```

Panel needs:
- `GET /api/players/:uuid/achievements` — returns all rows for that player with `achievement_id`, `progress`, `unlocked_at`, `claimed_tag`
- Player profile page: Achievements tab — progress bars, unlock dates, secret tier indicator (secret = `unlocked_at IS NULL AND progress > 0`)
- Staff view: near-miss 100M tiers visible (players close to secret unlocks)

Achievement categories (all config-driven but panel should know the IDs):
`woodcutter`, `miner`, `fisher`, `farmer`, `warrior_pvp`, `warrior_mobs`, `playtime`, `mcmmo_power`, `event_wins`, `messages_sent`, `blocks_placed`, `distance_travelled`

---

### 3.2 Grave Insurance (Legend+)
No new DB tables — extends the existing `player_graves` table if it exists. Adds:
- `armorstand_uuid` column for floating nametag entity tracking
- Graves with `ttl_seconds = -1` = never expire (Legend/Grandmaster perk)

Panel: Player profile should show an "Insurance Active" badge for Legend/Grandmaster players under their grave history.

---

### 3.3 Boosters (Server + Personal) — V007
Two new tables coming:

```sql
-- Server-wide boosters
CREATE TABLE IF NOT EXISTS server_boosters (
    id              SERIAL PRIMARY KEY,
    boost_type      VARCHAR(32) NOT NULL,
    multiplier      DOUBLE PRECISION NOT NULL DEFAULT 1.5,
    activated_by    VARCHAR(36),
    activated_at    BIGINT NOT NULL,
    expires_at      BIGINT NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE
);

-- Per-player booster inventory
CREATE TABLE IF NOT EXISTS player_booster_inventory (
    uuid            VARCHAR(36) NOT NULL,
    boost_type      VARCHAR(32) NOT NULL,
    quantity        INT NOT NULL DEFAULT 0,
    PRIMARY KEY (uuid, boost_type)
);

-- Per-player active boosters
CREATE TABLE IF NOT EXISTS player_active_boosters (
    uuid            VARCHAR(36) NOT NULL,
    boost_type      VARCHAR(32) NOT NULL,
    multiplier      DOUBLE PRECISION NOT NULL DEFAULT 1.5,
    activated_at    BIGINT NOT NULL,
    expires_at      BIGINT NOT NULL,
    PRIMARY KEY (uuid, boost_type)
);
```

Panel needs:
- Dashboard widget: active server boosters with time remaining
- `GET /api/boosters/active` — returns active `server_boosters` rows
- `POST /api/boosters/activate` — staff trigger (calls RCON or DB insert + plugin notify)
- Player profile: owned personal booster inventory

---

### 3.4 Recruit-a-Friend — V006
New table:

```sql
CREATE TABLE IF NOT EXISTS referrals (
    id              SERIAL PRIMARY KEY,
    referrer_uuid   VARCHAR(36) NOT NULL,
    new_player_uuid VARCHAR(36) NOT NULL UNIQUE,
    referral_code   VARCHAR(16) NOT NULL,
    referred_at     BIGINT NOT NULL,
    reward_claimed  BOOLEAN NOT NULL DEFAULT FALSE,
    reward_at       BIGINT
);
```

Also: `ALTER TABLE players ADD COLUMN IF NOT EXISTS referral_code VARCHAR(16) UNIQUE;`

Panel needs:
- Player profile: "Referred by" and "Recruits" count
- Staff page: referral leaderboard (top referrers)

---

### 3.5 Donor Rank Perks — single source of truth

Keep this table up to date on the panel's rank purchase/display pages:

| Rank | Vault Pages | Max Homes | Key Perks |
|------|-------------|-----------|-----------|
| gold | 1 | 3 | /nick, /enderchest, /craft, /anvil, chat delay exempt |
| diamond | 3 | 5 | All gold + /near, /feed, 50% RTP cooldown reduction |
| legend | 5 | 10 | All diamond + instant RTP, grave insurance, Blood Champion auto-equip |
| grandmaster | 10 | unlimited | All legend + /back, /repair, instant TP warmups, smarter vault fallback |

When selling ranks on the panel/Tebex pipeline, show exact perks from this table. Legend and Grandmaster must show the grave insurance indicator.

---

### 3.6 Player Profile Overhaul (/stats) — upcoming GUI
The plugin is adding a full tabbed Player Profile GUI in-game. The panel profile page should mirror the same tab structure for consistency:

Tabs: **Hub Stats** | **SMP Stats** | **Event Stats** | **Party Stats**

Hub: playtime, messages sent, commands sent, cosmetic coins, cosmetic unlocks count
SMP: mcMMO power level, kills/deaths/KDR, mobs killed, bosses killed, blocks broken, fish caught, balance
Events: combat wins, chat wins, HC wins, per-event breakdown
Party: parties created/joined, party kills, party playtime, party XP shared, referrals

---

## 4. Checklist for Panel Dev

- [ ] Fix moderation history filter (only ban/tempban/mute/tempmute, active only)
- [ ] Remove warns, kicks, vanish, unbans from history view
- [ ] Fix "Pardon" button on warns → "[View]"
- [ ] Remove/hide Watchlist section
- [ ] Replace filter with search bar on /moderation/
- [ ] Console: render `§` colour codes + strip ANSI
- [ ] Fix `reviewer_username` column error on rank request approve/deny
- [ ] Update rank list to include `srmod` and all donor ranks
- [ ] Donor rank shows as separate badge on player profile (not replacing primary rank)
- [ ] Prepare `/api/players/:uuid/achievements` endpoint (schema above)
- [ ] Dashboard booster widget (prepare for V007 tables)
- [ ] Referral count on player profile (prepare for V006 table)
- [ ] Donor perk table on store/rank pages (from §3.5 above)
