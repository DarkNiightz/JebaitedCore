# JebaitedCore — Feature Roadmap

> Last updated: April 2026  
> Package root: `com.darkniightz`  
> All DB changes go through SchemaManager migrations (`src/main/resources/db/`).

---

## Feature Index

| # | Feature | Size | Status |
|---|---------|------|--------|
| 1 | [Friends System](#1-friends-system) | Large | Planned |
| 2 | [Party System](#2-party-system) | Large | Planned |
| 3 | [Recruit-a-Friend](#3-recruit-a-friend) | Medium | Planned |
| 4 | [Server Boosters](#4-server-boosters) | Large | Planned |
| 5 | [Personal Boosters](#5-personal-boosters) | Medium | Planned |
| 6 | [Player Profile Overhaul (/stats)](#6-player-profile-overhaul-stats) | Large | Planned |
| 7 | [Graves Overhaul](#7-graves-overhaul) | Medium | Planned |
| 8 | [Quest Lines](#8-quest-lines) | Large | Planned |
| 9 | [Donor Rank Perks](#9-donor-rank-perks) | Large | Planned |

---

## 1. Friends System

### Goal
Persistent, cached friend relationships with real-time notifications, a dedicated GUI, and hooks for Party invites and the Recruit system.

### DB — `V004__friends.sql`
```sql
CREATE TABLE IF NOT EXISTS friendships (
    id              SERIAL PRIMARY KEY,
    player_a        VARCHAR(36) NOT NULL,  -- lower UUID alphabetically
    player_b        VARCHAR(36) NOT NULL,
    created_at      BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000),
    UNIQUE (player_a, player_b),
    FOREIGN KEY (player_a) REFERENCES players(uuid) ON DELETE CASCADE,
    FOREIGN KEY (player_b) REFERENCES players(uuid) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_friendships_a ON friendships(player_a);
CREATE INDEX IF NOT EXISTS idx_friendships_b ON friendships(player_b);

CREATE TABLE IF NOT EXISTS friend_requests (
    id              SERIAL PRIMARY KEY,
    sender_uuid     VARCHAR(36) NOT NULL,
    receiver_uuid   VARCHAR(36) NOT NULL,
    sent_at         BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000),
    UNIQUE (sender_uuid, receiver_uuid),
    FOREIGN KEY (sender_uuid)   REFERENCES players(uuid) ON DELETE CASCADE,
    FOREIGN KEY (receiver_uuid) REFERENCES players(uuid) ON DELETE CASCADE
);
```

### Cache (`core/friends/FriendCache.java`)
- `Map<UUID, Set<UUID>> friendMap` — loaded on join, invalidated on remove/add.
- `Map<UUID, Set<UUID>> pendingRequests` — outbound requests only. Cleared on accept/deny/expire.
- Max 200 friends per player (config: `friends.max_per_player`).
- 30-minute idle expiry for offline players' cache entries.

### Java — Files to create
| File | Purpose |
|------|---------|
| `core/friends/FriendManager.java` | Business logic: add, remove, accept, deny, list, isFriend, isPending |
| `core/friends/FriendCache.java` | In-memory friend/request cache with load/invalidate |
| `core/friends/FriendListener.java` | Join: load cache + notify friend online. Quit: persist and evict |
| `core/gui/FriendsMenu.java` | 54-slot GUI: list friends (heads), status dots, remove button, invite to party |
| `core/commands/FriendCommand.java` | `/friend add|remove|accept|deny|list|gui` |
| `main/FriendDAO.java` | DB ops: insertRequest, acceptRequest, removeFriend, listFriends, listPending |

### Commands
```
/friend add <player>      — send request (rejected silently if already friends)
/friend accept <player>   — accept incoming request → write friendships row
/friend deny <player>     — delete request row
/friend remove <player>   — delete friendship row, both directions
/friend list              — opens FriendsMenu
```

### Wiring checklist
- [ ] `FriendManager.java`
- [ ] `FriendCache.java`  
- [ ] `FriendListener.java`
- [ ] `FriendsMenu.java`
- [ ] `FriendCommand.java`
- [ ] `FriendDAO.java`
- [ ] `V004__friends.sql` + `migrations.index` append
- [ ] `plugin.yml` → `friend` command
- [ ] `JebaitedCore.registerCommands()` bind
- [ ] `PermissionConstants` → `CMD_FRIEND`
- [ ] `CommandSecurityListener` case

### Notifications
- On login: check pending requests → `§d[Friends] §f{n} pending friend request(s). Use §a/friend list §fto view.`
- On friend join: `§d[Friends] §a{name} §fis now online.` (only to online friends; optional in settings)

---

## 2. Party System

### Goal
Players form a temporary in-game party for shared gameplay on SMP. Stats for party activities are tracked separately. Active on SMP only; parties disband if the leader disconnects with no transfer.

### Party Rules
- Max 6 members (config: `party.max_size`).
- Leader can invite, kick, disband, transfer leadership.
- Party disbands automatically when leader disconnects (or optionally auto-transfer to next oldest member — config toggle).
- Party chat: `/p <msg>` or `/party chat` toggle.
- Parties are **in-memory only** — no party state is persisted between sessions. Only the activity stats are persisted.

### DB — `V005__party_stats.sql`
```sql
-- Aggregate party activity stats per player
CREATE TABLE IF NOT EXISTS player_party_stats (
    uuid                    VARCHAR(36) PRIMARY KEY,
    parties_created         INT NOT NULL DEFAULT 0,
    parties_joined          INT NOT NULL DEFAULT 0,
    party_kills             INT NOT NULL DEFAULT 0,   -- kills while in a party
    party_playtime_ms       BIGINT NOT NULL DEFAULT 0,
    party_blocks_broken     INT NOT NULL DEFAULT 0,
    party_fish_caught       INT NOT NULL DEFAULT 0,
    party_bosses_killed     INT NOT NULL DEFAULT 0,
    party_xp_shared         BIGINT NOT NULL DEFAULT 0,  -- total XP points distributed
    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE
);
```

### Java — Files to create
| File | Purpose |
|------|---------|
| `core/party/Party.java` | Data class: UUID id, leader UUID, Set<UUID> members, createdAt, chatToggle set |
| `core/party/PartyManager.java` | Create, disband, invite, accept/deny, kick, transfer, broadcast to party |
| `core/party/PartyListener.java` | Death/kill: check party, share XP. Quit: remove member or disband. Block break/fish: party stat increment |
| `core/party/PartyStatTracker.java` | Async stat writes to `player_party_stats` |
| `core/gui/PartyMenu.java` | 54-slot GUI: member list (heads), role icons, invite, kick, disband |
| `core/commands/PartyCommand.java` | All party subcommands |
| `main/PartyStatDAO.java` | upsert `player_party_stats` |

### Commands
```
/party create             — create party; sender becomes leader
/party invite <player>    — must be friends (config toggle: friends.required_for_party)
/party accept <player>    — accept invite from <player>
/party deny <player>      — deny invite
/party kick <player>      — leader only
/party leave              — leave party; leader leaving disbands unless auto-transfer
/party disband            — leader only
/party transfer <player>  — leader only
/party chat [msg]         — toggle party chat or send msg inline
/party list               — opens PartyMenu
/p <msg>                  — alias for party chat send
```

### XP Share
- When a member earns mcMMO XP, check `McMMOIntegration.isEnabled()`.
- Share % depends on the **best personal booster** among all party members:
  - Gold donor rank present: +5% XP distributed to other members
  - Diamond: +15%
  - Legend: +25%
  - Stacks: best single donor tier wins (no double-dipping across members).
- Use `McMMOPlayerXpGainEvent` (cancel original, re-fire adjusted amount, then distribute remainder).

### Custom Drop Rates in Party
While in a party, each kill/mine/fish event checks `PartyManager.getParty(playerUuid)`:
- **Mining**: +10% chance of bonus ore drop per active party member beyond 1 (capped at party size of 6 → up to +50%). Configured under `party.bonus_drops.mining_chance_per_member`.
- **Fishing**: +8% chance of double catch per extra member. Configured under `party.bonus_drops.fishing_chance_per_member`.
- **Boss kills**: +15% loot multiplier per extra member (cap ×2). Configured under `party.bonus_drops.boss_multiplier_per_member`.
- Listen on `BlockBreakEvent` (ores), `PlayerFishEvent`, `EntityDeathEvent` (bosses by tag/type list in config).

---

## 3. Recruit-a-Friend

### Goal
Incentivise new player acquisition. When player A refers new player B, both receive a reward on B's first qualifying session (e.g. 30 min playtime on SMP). Anti-abuse: IP/referral code uniqueness check.

### DB — `V006__referrals.sql`
```sql
CREATE TABLE IF NOT EXISTS referrals (
    id              SERIAL PRIMARY KEY,
    referrer_uuid   VARCHAR(36) NOT NULL,
    new_player_uuid VARCHAR(36) NOT NULL UNIQUE,  -- one referrer per new player
    referral_code   VARCHAR(16) NOT NULL,
    referred_at     BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000),
    reward_claimed  BOOLEAN NOT NULL DEFAULT FALSE,
    reward_at       BIGINT,
    FOREIGN KEY (referrer_uuid)   REFERENCES players(uuid) ON DELETE CASCADE,
    FOREIGN KEY (new_player_uuid) REFERENCES players(uuid) ON DELETE CASCADE
);

-- Referral codes per player (auto-generated on first join; format "JJXXXX" where XX = random chars)
ALTER TABLE players ADD COLUMN IF NOT EXISTS referral_code VARCHAR(16) UNIQUE;
```

### Flow
1. New player joins → shown welcome message: `§aWere you referred by someone? Use §e/refer <code> §awithin 10 minutes!`
2. `/refer <code>` → look up `referral_code` in `players`, validate: not self, new player has < 10 min playtime, code exists.
3. Insert row into `referrals`. No reward yet.
4. Track new player playtime on SMP. When they hit `referrals.qualifying_playtime_minutes` (config, default 30):
   - Mark `reward_claimed = TRUE`
   - Award referrer: `referrals.referrer_reward_coins` cosmetic coins (config)
   - Award new player: `referrals.new_player_reward_coins` cosmetic coins (config)
   - Broadcast (optional): `§d{referrer} recruited {new_player} — they're officially in!`

### Anti-abuse
- One referral per new player UUID.
- Referring player must have >= `referrals.referrer_min_playtime_hours` hours (config, default 2).
- Code claim window: 10 minutes from first join (configurable).

### Java
| File | Purpose |
|------|---------|
| `core/referral/ReferralManager.java` | validateAndApply, checkQualifyingPlaytime, claimReward |
| `core/commands/ReferCommand.java` | `/refer <code>` |
| `main/ReferralDAO.java` | insertReferral, markClaimed, loadByNewPlayer |

### Wiring checklist
- [ ] DB migration V006
- [ ] `ReferralManager.java`
- [ ] `ReferCommand.java`
- [ ] `ReferralDAO.java`
- [ ] `plugin.yml` → `refer` command
- [ ] `JebaitedCore.registerCommands()` bind
- [ ] `PermissionConstants.CMD_REFER`
- [ ] Hook into `StatsTrackingListener` playtime tick → `ReferralManager.tickPlaytime`
- [ ] `CommandSecurityListener` case

---

## 4. Server Boosters

### Goal
Staff-activatable server-wide boosts that apply to all online players for a fixed duration. Different boost types affect different mechanics. Only one active booster per type at a time.

### Boost Types
| Key | Effect | Mechanic |
|-----|--------|---------|
| `xp_global` | +N% mcMMO XP for all players | `McMMOPlayerXpGainEvent` multiply |
| `mining_ore` | +N% chance of extra ore drop | `BlockBreakEvent` RNG |
| `mining_fortune` | Fortune enchant treated as +N levels | `BlockBreakEvent` loot override |
| `fishing_catch` | +N% second catch chance | `PlayerFishEvent` |
| `fishing_mcmmo` | Chance to fish up a mcMMO special item (`GrapplingHook`, `PartItem`) | `PlayerFishEvent` |
| `boss_loot` | +N% boss drop multiplier | `EntityDeathEvent` |

Boost amounts and defaults configured under `boosters.server.*` in config.

### DB — `V007__boosters.sql`
```sql
CREATE TABLE IF NOT EXISTS server_boosters (
    id              SERIAL PRIMARY KEY,
    boost_type      VARCHAR(32) NOT NULL,
    multiplier      DOUBLE PRECISION NOT NULL DEFAULT 1.5,
    activated_by    VARCHAR(36),  -- actor UUID, NULL = console
    activated_at    BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000),
    expires_at      BIGINT NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (boost_type, active)   -- enforces one active per type via partial index
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_server_boosters_one_active
    ON server_boosters(boost_type) WHERE active = TRUE;

-- Track personal booster inventory
CREATE TABLE IF NOT EXISTS player_booster_inventory (
    uuid            VARCHAR(36) NOT NULL,
    boost_type      VARCHAR(32) NOT NULL,
    quantity        INT NOT NULL DEFAULT 0,
    PRIMARY KEY (uuid, boost_type),
    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE
);
```

### Java
| File | Purpose |
|------|---------|
| `core/booster/BoosterType.java` | Enum of boost types with config key, default multiplier, display name |
| `core/booster/BoosterManager.java` | Singleton. Loads active from DB on startup. Tick expiry check (1/s). `isActive(type)`, `getMultiplier(type)`. Broadcasts on activate/expire |
| `core/booster/BoosterListener.java` | Hooks `BlockBreakEvent`, `PlayerFishEvent`, `EntityDeathEvent`, `McMMOPlayerXpGainEvent`. Applies booster multipliers if active |
| `core/commands/BoosterCommand.java` | `/booster activate <type> <duration> [multiplier]` — admin+ |
| `core/commands/BoosterStatusCommand.java` | `/boosters` — show all active boosters with time remaining |

### Activation
```
/booster activate xp_global 30m 2.0    — start 2× XP for 30 minutes
/booster activate mining_ore 1h         — default multiplier from config
/booster status                         — list active with time remaining
/booster stop <type>                    — admin deactivate early
```

### Config additions
```yaml
boosters:
  server:
    xp_global:
      default_multiplier: 1.5
      default_duration_minutes: 30
      broadcast: true
    mining_ore:
      default_chance_add: 0.25     # +25% base drop chance
      default_duration_minutes: 30
    fishing_catch:
      default_chance_add: 0.20
      default_duration_minutes: 30
    fishing_mcmmo:
      default_chance: 0.05         # 5% chance per cast
      items: [FISHING_ROD]         # mcMMO items to pick from (configured externally)
    boss_loot:
      default_multiplier: 1.5
      default_duration_minutes: 30
```

### Wiring checklist
- [ ] DB migration V007
- [ ] `BoosterType.java`
- [ ] `BoosterManager.java` (register with `JebaitedCore`)
- [ ] `BoosterListener.java`
- [ ] `BoosterCommand.java`
- [ ] `BoosterStatusCommand.java`
- [ ] `plugin.yml` entries
- [ ] `JebaitedCore.registerCommands()` binds
- [ ] `PermissionConstants.CMD_BOOSTER`, `CMD_BOOSTERS`
- [ ] `CommandSecurityListener` cases

---

## 5. Personal Boosters

### Goal
Per-player passive boosts that are always active for that player on SMP. Certain tiers are granted automatically by donor rank. Others can be activated from inventory.

### Passive Boosts by Donor Rank
| Donor Rank | Effect |
|-----------|--------|
| `gold`       | +5% XP shared with party members |
| `diamond`    | +15% XP shared with party members |
| `legend`     | +25% XP shared with party members |

XP share is additive on top of base XP — the party member's XP is not reduced, the donor "tops up" party members.

### Activatable Personal Boosters
Personal boosters are items in the player's booster inventory (from `player_booster_inventory`). Activating one sets a timed row in a new table:

```sql
-- Already in V007; add a personal active booster table
CREATE TABLE IF NOT EXISTS player_active_boosters (
    uuid            VARCHAR(36) NOT NULL,
    boost_type      VARCHAR(32) NOT NULL,
    multiplier      DOUBLE PRECISION NOT NULL DEFAULT 1.5,
    activated_at    BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000),
    expires_at      BIGINT NOT NULL,
    PRIMARY KEY (uuid, boost_type),
    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE
);
```

Add to `V007__boosters.sql` before the migrations.index entry is committed.

### Java
| File | Purpose |
|------|---------|
| `core/booster/PersonalBoosterManager.java` | Load active personal boosters on join. `isActive(uuid, type)`, `getMultiplier(uuid, type)`. Merge with server booster (highest wins, or additive — config toggle) |
| `core/gui/PersonalBoosterMenu.java` | Inventory UI: active boosters with time remaining, inventory of owned boosters, activate slot |

### BoosterListener additions
In every booster hook, check `PersonalBoosterManager.isActive(player, type)` in addition to `BoosterManager`. Use whichever benefit is higher (or both if config `boosters.personal.stack_with_server = true`).

### Donor Rank XP Share (in `PartyListener`)
```java
// In XP gain listener, after granting XP to member:
String donorRank = profile.getDonorRank();
if (donorRank != null) {
    double sharePercent = switch (donorRank) {
        case "gold"   -> 0.05;
        case "diamond" -> 0.15;
        case "legend"  -> 0.25;
        default -> 0.0;
    };
    if (sharePercent > 0 && party != null) {
        long shareAmount = (long)(xpAmount * sharePercent);
        // distribute shareAmount to each other party member (McMMO give XP API)
    }
}
```

---

## 6. Player Profile Overhaul (/stats)

### Goal
Replace the current 45-slot static `StatsMenu` with a tabbed, full-featured **Player Profile** screen. `/stats`, `/profile`, and `/pp` all open it. Works for viewing others (with rank gate: helper+). Contains links to Settings, Friend invite, Party invite, and action buttons.

### Layout — 54 slots

```
[0 ][1 ][2 ][3 ][4 ][5 ][6 ][7 ][8 ]
[9 ][10][11][12][13][14][15][16][17]
[18][19][20][21][22][23][24][25][26]
[27][28][29][30][31][32][33][34][35]
[36][37][38][39][40][41][42][43][44]
[45][46][47][48][49][50][51][52][53]
```

| Slot | Content |
|------|---------|
| 13 | Player head — name, rank, donor rank, first joined, last seen |
| 4 | Active tab indicator (cosmetic glass pane border) |
| 20 | Tab: Hub Stats |
| 22 | Tab: SMP Stats |
| 24 | Tab: Event Stats |
| 26 | Tab: Custom / Party Stats |
| 29–35 | Stat items for current tab (7 slots) |
| 45 | Back / Close button |
| 46 | ← if viewing others: previous player (staff only) |
| 48 | Friend Invite / Already Friends indicator |
| 50 | Party Invite button (only if viewer is party leader and not already in party with target) |
| 51 | Open Settings (only shown if viewing self) |
| 53 | Refresh (re-loads profile from DB) |

### Tab Contents

#### Hub Stats (default tab)
- Playtime (formatted hh mm)
- Messages Sent
- Commands Sent
- Cosmetic Coins
- Cosmetic Unlocks count
- Leaderboard rank for playtime (if configured)
- Active cosmetics (particle, trail, gadget)

#### SMP Stats
- mcMMO Power Level
- Kills / Deaths / KDR
- Mobs Killed
- Bosses Killed
- Blocks Broken
- Crops Broken
- Fish Caught
- Balance ($)

#### Event Stats
- Combat Event Wins
- Chat Event Wins
- Hardcore Event Wins
- Per-event breakdown (scrollable if > 7 entries — use page system with prev/next arrows at slots 27 and 35)

#### Custom / Party Stats
- Parties Created / Joined
- Party Kills
- Party Playtime
- Party XP Shared
- Referrals Made (from referrals table)
- Recruit Count (players they referred who qualified)

### Action Buttons

**Friend Invite (slot 48)**
- Not viewing self + FriendManager loaded:
  - Already friends → `LIME_DYE`, `§aAlready Friends`, click = nothing
  - Request pending → `YELLOW_DYE`, `§ePending Request`, click = cancel request
  - Not friends → `PAPER`, `§fSend Friend Request`, click = `FriendManager.sendRequest(viewer, target)`

**Party Invite (slot 50)**
- Only show if viewer is in a party as leader and target is not in party and target is online:
  - `BLAZE_POWDER`, `§dInvite to Party`, click = `PartyManager.invite(viewer, target)`
- Otherwise: filler or `§8No Party Active`

**Settings (slot 51)**
- Only when viewing self:
  - `COMPARATOR`, `§bSettings`, click = open `SettingsMenu` with back-to-profile navigation

### Java — Files to modify/create
| File | Action |
|------|--------|
| `core/gui/PlayerProfileMenu.java` | **New** — replaces StatsMenu. Tab state tracked per viewer. Accepts `OfflinePlayer target` |
| `core/gui/PlayerProfileMenu.java` (inner) | `TabPage enum { HUB, SMP, EVENTS, CUSTOM }` |
| `core/commands/StatsCommand.java` | **Modify** — open `PlayerProfileMenu` instead of `StatsMenu` |
| `StatsMenu.java` | **Keep** for now, deprecate once PlayerProfileMenu ships |
| `plugin.yml` | Add `profile` and `pp` as aliases for `stats` |
| `JebaitedCore.registerCommands()` | Bind `profile` and `pp` aliases |

### Rank gate
- Any player: `/stats` (their own profile)
- Helper+: `/stats <player>` (view others)
- Same rule for `/profile` and `/pp`

### Back navigation from Settings
`SettingsMenu` needs an optional "back" function injected — pass `() -> new PlayerProfileMenu(...).open(viewer)` as a `Runnable backAction`. `SettingsMenu` already has slot 45 glass pane; replace with ARROW "Back to Profile" if `backAction != null`.

---

## Implementation Order

Recommended sequence to minimise rework:

1. **Player Profile Overhaul** first — it's the root UI that wires Friends, Party, Recruits, and Boosters buttons. Build the shell with empty/placeholder action slots and fill them in as each system ships.
2. **Friends System** — prerequisite for Party invites, shows in profile.
3. **Party System** — depends on Friends. XP share hooks depend on Booster data structure.
4. **Personal Boosters** — donor rank XP share can ship standalone before full booster system.
5. **Server Boosters** — build on top of personal booster infrastructure.
6. **Recruit-a-Friend** — independent, can ship any time after Friends.

---

## Gotchas & Pre-flight Checklist

Before any session, confirm:

- [ ] New party/friend actions that write player data go through `ProfileStore.saveDeferred` — never direct DAO writes on main thread.
- [ ] Every new command = 5 places: class + plugin.yml + registerCommands() + PermissionConstants + SecurityListener.
- [ ] Every new stat column = new migration file + migrations.index append + DAO SQL + tracking listener + display location.
- [ ] `McMMOIntegration.isEnabled()` guard on every mcMMO XP event hook.
- [ ] `MenuService.openMenu()` for all GUIs — never `player.openInventory()` directly.
- [ ] `ProfileStore.invalidate(uuid)` after any rank/balance/friend/party state change that affects display.
- [ ] Server/personal boosters: check `isEnabled()` before loading — avoid NPE if booster system is disabled.
- [ ] Party is in-memory only — don't persist party state to DB; only persist party **stats**.
- [ ] Referral anti-abuse: IP check is intentionally omitted (shared IPs on LAN servers) — use UUID uniqueness only.

---

## 7. Graves Overhaul

### Goal
Replace the basic item-dropping death behaviour with persistent graves that display a floating nametag (ArmorStand) above the grave block showing the player's name, death time, and item count. Donor players auto-equip their best cosmetic preset on grave loot pickup.

### Current State
Graves system exists (`GraveManager`, `/graves` command, `graves:` config block). It likely creates a chest or drops items — the overhaul extends it with visual and donor-specific features.

### Changes

#### Floating Nametag (ArmorStand)
- On death: spawn an invisible `ArmorStand` at grave location, `setCustomNameVisible(true)`, name = `§c{PlayerName} §7• §f{item_count} items §8• §7{time_ago}`.
- Store ArmorStand entity UUID alongside the grave record.
- On grave loot/expiry: remove the ArmorStand.
- Time string: "just now", "2m ago", "1h ago" — refreshed every 30s by a repeating task.
- Config toggle: `graves.floating_nametag.enabled` (default true).

#### Donor Auto-Equip on Loot
- When a donor player (any rank with `donorRank != null`) collects their own grave:
  - Check if player has a favourite/pinned cosmetic preset (or just their currently equipped set).
  - Re-apply it silently after loot is claimed (items return to inventory, then `CosmeticsEngine` is re-applied).
- Config toggle: `graves.donor_auto_reequip.enabled` (default true).
- Only applies to the grave owner collecting their own items — does not trigger when another player loots.

#### DB Changes
No new migration needed if grave data is already stored. If not currently persisted, add:

```sql
-- V008__graves_overhaul.sql (only needed if graves aren't already persisted)
CREATE TABLE IF NOT EXISTS player_graves (
    id              SERIAL PRIMARY KEY,
    owner_uuid      VARCHAR(36) NOT NULL,
    world           VARCHAR(64) NOT NULL,
    x               INT NOT NULL,
    y               INT NOT NULL,
    z               INT NOT NULL,
    item_count      INT NOT NULL DEFAULT 0,
    died_at         BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000),
    looted_at       BIGINT,
    armorstand_uuid VARCHAR(36),
    FOREIGN KEY (owner_uuid) REFERENCES players(uuid) ON DELETE CASCADE
);
```

### Java
| File | Action |
|------|--------|
| `core/world/GraveManager.java` | **Modify** — spawn ArmorStand on grave create, despawn on loot/expire, tick name refresh |
| `core/world/GraveNametagTask.java` | **New** — repeating task (every 30s) updates all active grave nametag text with fresh "X ago" |
| `core/world/GraveListener.java` | **Modify** — on grave loot: trigger donor re-equip if applicable |

### Config additions
```yaml
graves:
  floating_nametag:
    enabled: true
    format: "§c{name} §7• §f{count} items §8• §7{age}"
    refresh_ticks: 600   # 30s
  donor_auto_reequip:
    enabled: true
```

---

## 8. Quest Lines

### Goal
Multi-step quest engine with progress tracking, branching completion, and configurable rewards (cosmetic coins, cosmetic unlocks, donor perks). Quests are defined in config and progress is persisted per-player.

### Quest Types
| Type | Example |
|------|---------|
| `kill_mobs` | Kill 50 zombies |
| `kill_players` | Win 3 FFA events |
| `mine_blocks` | Mine 500 stone |
| `fish` | Catch 20 fish |
| `mcmmo_level` | Reach mcMMO level 100 |
| `playtime` | Play for 5 hours |
| `event_wins` | Win 5 events |
| `chat_message` | Send 100 messages |
| `cosmetic_equip` | Equip 3 different cosmetics |

Quests can have prerequisites (`requires: [quest_id]`) and belong to a `series` for questline ordering.

### DB — `V008__quests.sql` (or `V009` if graves needed V008)
```sql
CREATE TABLE IF NOT EXISTS player_quests (
    uuid            VARCHAR(36) NOT NULL,
    quest_id        VARCHAR(64) NOT NULL,
    progress        INT NOT NULL DEFAULT 0,
    completed_at    BIGINT,
    claimed_at      BIGINT,
    PRIMARY KEY (uuid, quest_id),
    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_player_quests_uuid ON player_quests(uuid);
```

### Config schema
```yaml
quests:
  starter_kill_zombies:
    display_name: "Zombie Slayer"
    type: kill_mobs
    mob: ZOMBIE
    target: 50
    reward_coins: 100
    reward_unlocks: []
    series: starter
    order: 1

  starter_mine_stone:
    display_name: "Rock Bottom"
    type: mine_blocks
    block: STONE
    target: 500
    reward_coins: 75
    series: starter
    order: 2
    requires: [starter_kill_zombies]

  advanced_event_wins:
    display_name: "Event Champion"
    type: event_wins
    target: 10
    reward_coins: 500
    reward_unlocks: ["cosmetic_key_event_champion_tag"]
    series: advanced
    order: 1
    requires: [starter_kill_zombies, starter_mine_stone]
```

### Java
| File | Purpose |
|------|---------|
| `core/quest/QuestManager.java` | Loads quest definitions from config. `incrementProgress(uuid, type, mob/block, amount)`. `checkCompletion`. Cache: `Map<UUID, Map<String, Integer>>` progress per player |
| `core/quest/QuestDefinition.java` | Data class: id, type, target, prereqs, series, rewards |
| `core/quest/QuestListener.java` | `EntityDeathEvent`, `BlockBreakEvent`, `PlayerFishEvent`, `AsyncChatEvent` — routes to `QuestManager.incrementProgress` |
| `core/quest/QuestProgressTracker.java` | Playtime/mcMMO level checked on periodic tick (1 min) |
| `core/gui/QuestMenu.java` | 54-slot GUI: active quests (with progress bars), completed quests, claim reward button |
| `core/commands/QuestsCommand.java` | `/quests` — opens QuestMenu |
| `main/QuestDAO.java` | upsert `player_quests`, load all for UUID, mark claimed |

### Progress persisting
- `QuestManager` keeps an in-memory dirty set. On `saveDeferred` trigger or every 5 minutes, async-flush dirty UUIDs to `player_quests`.
- On join, load quest progress from DB into cache.
- On quit, flush dirty entries.

### Reward types
- `reward_coins: N` → `profile.addCosmeticCoins(N)` + `ProfileStore.saveDeferred`
- `reward_unlocks: [key]` → `CosmeticsManager.unlock(uuid, key)`
- `reward_rank_display: "tag_key"` → unlock a cosmetic tag automatically

### Wiring checklist
- [ ] DB migration + migrations.index
- [ ] `QuestDefinition.java`
- [ ] `QuestManager.java` (register as singleton, inject into listeners)
- [ ] `QuestListener.java`
- [ ] `QuestProgressTracker.java` (scheduled task, 20s tick)
- [ ] `QuestMenu.java`
- [ ] `QuestsCommand.java`
- [ ] `QuestDAO.java`
- [ ] `plugin.yml` → `quests` command
- [ ] `JebaitedCore.registerCommands()` bind
- [ ] `PermissionConstants.CMD_QUESTS`
- [ ] Player Profile overhaul: add Quest progress panel / completion count to custom stats tab

---

## 9. Donor Rank Perks

> Donor ranks sit on top of the normal rank ladder. Players keep their staff/pleb rank but get donor
> perks layered on top via `rankDisplayMode="donor"`. Ranks in ascending order of price/power:
> `gold` → `diamond` → `legend` → `grandmaster`.

### Rank quick-reference

| Rank | Display name | Colour |
|------|-------------|--------|
| gold        | Gold       | Gold `§6`          |
| diamond     | Diamond    | Aqua `§b`          |
| legend      | Legend     | Light purple `§d`  |
| grandmaster | Grand Master | Dark red `§4` + bold |

---

### Gold

| Perk | Detail |
|------|--------|
| Chat delay exempt | No chat cooldown (bypass `chat.delay_ms`) |
| `/nick` | Custom nickname (strip colour codes; staff still see real name) |
| 3 homes | `homes.max` = 3 |
| `/rtp` 25% faster | Cooldown reduced by 25% vs default |
| `/enderchest` | Open own ender chest anywhere |
| `/pv 1` | Private Vault — 1 × 54-slot page |
| `/craft` | Open a crafting table anywhere |
| `/kit gold` | Decent iron-tier kit; 24h cooldown |
| `/anvil` | Open anvil GUI anywhere |

**Suggested extras:**
- Custom join/quit message colour (gold prefix in tab/chat)
- 1 extra cosmetic coin daily login bonus over pleb

---

### Diamond

All Gold perks, plus:

| Perk | Detail |
|------|--------|
| `/near` | List nearby players within configurable radius |
| `/kit diamond` | Full diamond kit + some enchants; 24h cooldown |
| `/pv 3` | 3 × 54-slot private vault pages |
| `/feed` | Restore hunger (blocked while combat-tagged) |
| 5 homes | `homes.max` = 5 |
| `/rtp` 50% faster | Cooldown reduced by 50% vs default |

**Suggested extras:**
- Coloured signs (prefixed with `&` codes)
- 2 extra cosmetic coin daily bonus
- Particle trails enabled (access to non-animated cosmetics)

---

### Legend

All Diamond perks, plus:

| Perk | Detail |
|------|--------|
| 10 homes | `homes.max` = 10 |
| `/kit legend` | High-end kit (strong but not god-tier); 24h cooldown |
| `/rtp` instant | No cooldown |
| `/tpa` instant | No warmup delay |
| `/pv 5` | 5 × 54-slot pages |
| Instant Recovery | `/deathtp` — teleport directly to last death coords (SMP only, out of combat) |
| Server-Wide Boosters | Can activate server XP/drop boosters (see Feature 4) |

**Suggested extras:**
- Keep XP on death in SMP (no item-keep, just XP levels)
- 5 extra cosmetic coin daily bonus
- Access to animated particle trails

---

### Grand Master *(new tier)*

All Legend perks, plus:

| Perk | Detail |
|------|--------|
| `/kit grandmaster` | God-tier kit (full prot IV, sharp V, etc.); 24h cooldown |
| `/pv 10` | 10 × 54-slot pages |
| 0 RTP timer | `/rtp` fires immediately with no warmup |
| 0 TP warmup | `/tpa`, `/home`, `/warp` etc. fire immediately (out of combat) |
| `/back` | Teleport to last death location or last TP origin (out of combat) |
| `/repair` | Repair item in hand (or full inventory with `all` arg); configurable cooldown |
| Unlimited homes | `homes.max` = `Integer.MAX_VALUE` effectively |
| Priority queue | Jump the join queue on full server (future network feature) |

**Suggested extras:**
- Custom particle aura (unique to GM tier — one exclusive cosmetic auto-unlocked)
- 10 extra cosmetic coin daily bonus
- Name colour in chat customisable (within rank colour range)
- Fly in hub (hub only, not SMP — ADVENTURE mode flight)

---

### Implementation notes

**Permission nodes to add to `PermissionConstants`:**
```
DONOR_NICK, DONOR_ENDERCHEST, DONOR_PV, DONOR_CRAFT, DONOR_ANVIL, DONOR_FEED,
DONOR_NEAR, DONOR_DEATHTP, DONOR_BACK, DONOR_REPAIR, DONOR_FLY_HUB,
DONOR_CHAT_NO_DELAY, KIT_GOLD, KIT_DIAMOND, KIT_LEGEND, KIT_GRANDMASTER,
DONOR_RTP_FAST_25, DONOR_RTP_FAST_50, DONOR_RTP_INSTANT, DONOR_TP_INSTANT,
DONOR_BOOSTER_ACTIVATE
```

**New commands needed (5-place wiring each):**
`/nick`, `/enderchest`, `/pv [page]`, `/craft`, `/anvil`, `/feed`, `/near`, `/deathtp`, `/back`, `/repair`, `/kit <tier>`

**Private Vault (pv) — page count from rank:**
```java
int pages = switch (donorRank) {
    case "grandmaster" -> 10;
    case "legend"       -> 5;
    case "diamond"      -> 3;
    default            -> 1;  // gold/pleb and below
};
```
Store open vault page in per-session map; persist selected items to DB (`player_vaults` table, `vault_page` + `items` blob columns).

**Kit cooldowns — new DB column (or flat file):**
`ALTER TABLE players ADD COLUMN IF NOT EXISTS kit_cooldowns JSONB` — key = kit name, value = epoch ms of last use.

**RTP warmup multiplier — look up donor rank at dispatch time:**
```java
double mult = switch (donorRank) {
    case "grandmaster", "legend" -> 0.0;
    case "diamond" -> 0.5;
    case "gold"   -> 0.75;
    default -> 1.0;
};
long warmup = (long)(config.getLong("rtp.warmup_ms", 5000L) * mult);
```

**`/back` — store last death/tp coords in a `BackManager` (in-memory, one entry per UUID).**
Update on: `PlayerDeathEvent`, `PlayerTeleportEvent` (non-plugin-initiated).
Cleared on: server restart.

**`/repair` — use `ItemMeta.setDamage(0)` (Damageable interface). Check `item.getItemMeta() instanceof Damageable`.**

**Wiring checklist:**
- [ ] Add permission constants (all listed above)
- [ ] `CommandSecurityListener` cases for each new command
- [ ] `plugin.yml` entries
- [ ] `JebaitedCore.registerCommands()` binds
- [ ] `KitManager.java` — kit definitions from config, cooldown read/write
- [ ] `PrivateVaultManager.java` — open vault by page, persist to DB
- [ ] `BackManager.java` — last-death/last-tp tracking
- [ ] DB migration for `player_vaults` table + `kit_cooldowns` column
- [ ] `migrations.index` append

**Panel surface:**
```
[PANEL SURFACE]
Feature: Donor Rank Perks
DB tables/columns added: player_vaults, players.kit_cooldowns (JSONB)
Suggested panel page/endpoint: /admin/donors - active donor list, rank, expiry (if timed)
Data shape: { uuid, username, donorRank, pvPages, kitCooldowns: { gold: 1234567890 } }
```
