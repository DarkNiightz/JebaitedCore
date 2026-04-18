# JebaitedCore — Feature Roadmap

> Last updated: April 2026 — **§17 Server shop:** MVP **shipped** in-plugin (layout, DB `V007`, settings, rate-limit validation + loaded-row logging, tx-log failure → debug feed, `[ShopManager](src/main/java/com/darkniightz/core/shop/ShopManager.java)` refresh on `/jreload`). **Debug:** `/shop` quick-open in `[DebugMenu](src/main/java/com/darkniightz/core/cosmetics/DebugMenu.java)` command list. **Staging:** [checklist](#staging-verification-checklist) + [Jamie handoff](#jamie-handoff-web-panel) + [PANEL_SERVER_SHOP_HANDOFF.md](docs/PANEL_SERVER_SHOP_HANDOFF.md). **§21:** KOTH **unc. leader**; CTF **YAML kits** + **ground flag** item pickup (`CtfGroundFlagListener`). **Discord platform (§23):** **Bidirectional chat** (global/staff/faction channels), **Paper inbound HTTP** (`/integrations/discord/status|bridge|console`), **console mirror** + **remote commands** (`>` prefix, developer role), slash **`/ping` `/server` `/player` `/activity`**, **live status embed**, DB **`V009` activity** samples, JDA **MESSAGE_CONTENT**. **Current focus:** tablist live Discord member count + **I2** Player Shops; panel shop editor optional. **Versioning:** [§18](#18-version-labelling). Chat games: `[ChatGameManager](src/main/java/com/darkniightz/core/eventmode/ChatGameManager.java)` + `/chatgame`.  
> Package root: `com.darkniightz`  
> All DB changes go through SchemaManager migrations (`src/main/resources/db/`).  
> **Web admin (Node `web-admin`):** not edited from this repo — document DB/API contracts here; Jamie implements panel routes and UI.  
> **Next session:** Paste the copy-paste block from `[SESSION_HANDOFF.md](SESSION_HANDOFF.md)` into a new chat; pick §17 staging, panel handoff, §21 `TeamEngine` party-aware CTF, or I2 per **Current focus** below.

---

## Feature Showcase — What the Plugin Has

> Use this section for advertising copy. All features listed as ✅ are live and functional.  
> Public = visible/usable by all players. Staff = restricted to helper+ or higher.

### Player-Facing Features (Public)


| Feature                   | Description                                                                                                                                                                                                                                                                                       | Status    |
| ------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------- |
| **Rank System**           | 13-tier rank ladder (pleb → owner). Separate donor track (gold, diamond, legend, grandmaster). Colored names, tab prefixes, rank-specific perks throughout.                                                                                                                                       | ✅ Shipped |
| **Friends System**        | Persistent friends list with online notifications, request flow, GUI browser, and friend stats (XP together, kills together). Rank-based limits.                                                                                                                                                  | ✅ Shipped |
| **Party System**          | Temporary groups with shared XP bonuses (tier-scaled by donor rank), drop bonuses, party chat, open/locked parties, friendly fire toggle, warp-to-party, and a 54-slot GUI.                                                                                                                       | ✅ Shipped |
| **Private Vaults**        | Personal paginated item storage for donor ranks. Gold=1 page, Diamond=3, Legend=5, Grandmaster=10. Auto-loot from graves on death.                                                                                                                                                                | ✅ Shipped |
| **Achievements**          | 5-category milestone system (kills, blocks, fish, distance, playtime) with 5 tiers each. Rewards: coins, cosmetic tags, unlocks. Paginated GUI with progress bars.                                                                                                                                | ✅ Shipped |
| **Grave System**          | Items saved on death in a grave at death location. Normal players get a 10-minute timer; Legend/Grandmaster get insured permanent graves.                                                                                                                                                         | ✅ Shipped |
| **Grave Insurance**       | Legend/Grandmaster players: grave never expires, items auto-looted to vault on death. 90% full vault warning sent before overflow.                                                                                                                                                                | ✅ Shipped |
| **Cosmetics Wardrobe**    | Browse, preview on ArmorStand, and equip cosmetics (particles, hats, tags, effects). Per-player ownership tracked in DB.                                                                                                                                                                          | ✅ Shipped |
| **Cosmetic Coins**        | Premium server currency earned through gameplay, events, and achievements. Used to buy cosmetics in the coin shop.                                                                                                                                                                                | ✅ Shipped |
| **Cosmetic Tags**         | Customisable colored chat tags with `&` color code support. Unlocked via achievements or coins. Prefix/suffix position configurable.                                                                                                                                                              | ✅ Shipped |
| **Cosmetic Loadouts**     | Save and reapply up to 10 cosmetic outfit combinations by name.                                                                                                                                                                                                                                   | ✅ Shipped |
| **Settings Menu**         | 6-category settings hub (Notifications, Social, Display, Gameplay, Privacy, Experimental). GUI-driven, DB-backed, rank display toggle for donors.                                                                                                                                                 | ✅ Shipped |
| **Stats Tracking**        | Tracks kills, deaths, mob kills, boss kills, blocks broken, fish caught, playtime, messages, commands, event wins. Persisted to DB.                                                                                                                                                               | ✅ Shipped |
| **Stats Menu (/stats)**   | 45-slot GUI showing all player stats, rank, balance, and achievements. Staff can view other players.                                                                                                                                                                                              | ✅ Shipped |
| **Leaderboards**          | Hologram leaderboards placeable anywhere in-world for any stat (kills, playtime, blocks, etc.). Top 10 with rank-colored names. Auto-refresh.                                                                                                                                                     | ✅ Shipped |
| **Economy**               | In-game balance with `/balance`, `/pay`, and staff eco commands. Displayed in stats menu.                                                                                                                                                                                                         | ✅ Shipped |
| **Server Shop**           | `/shop` (alias `/market`) — 9-category buy/sell GUI, PostgreSQL prices (`server_shop_prices`) + audit trail (`shop_transactions`), per-player rate limits and donor cadence, optional stack-buy confirm (`SettingKey` Gameplay). **SMP only.** Reload: `/jreload` rebuilds `ShopManager` from DB. | ✅ Shipped |
| **Homes**                 | Save and teleport to named home locations. Rank-based limits (pleb=1 up to grandmaster=unlimited).                                                                                                                                                                                                | ✅ Shipped |
| **Warps**                 | Public server warps with optional entry fee. Listable via `/warps`.                                                                                                                                                                                                                               | ✅ Shipped |
| **Random Teleport (RTP)** | Teleport to a safe random SMP location. Configurable radius.                                                                                                                                                                                                                                      | ✅ Shipped |
| **Events — KOTH**         | King of the Hill: timed hill control in a configured zone; winner by hill rule (see §21 — evolving toward **uncontested** time). Coin reward.                                                                                                                                                     | ✅ Shipped |
| **Events — FFA**          | Free-for-All: last player standing wins. Coin reward.                                                                                                                                                                                                                                             | ✅ Shipped |
| **Events — Duels**        | 1v1 combat events. Coin reward.                                                                                                                                                                                                                                                                   | ✅ Shipped |
| **Events — Hardcore**     | HC_FFA, HC_DUELS, HC_KOTH: enter without inventory, permanent item loss on death, higher coin rewards. Exclusive win cosmetics (planned).                                                                                                                                                         | ✅ Shipped |
| **Chat Games**            | Scrabble (unscramble a word), Math (solve an equation), Quiz (trivia Q&A). Winners earn cosmetic coins.                                                                                                                                                                                           | ✅ Shipped |
| **Private Messages**      | `/msg` + `/reply` for player-to-player DMs. Optional color codes for gold+.                                                                                                                                                                                                                       | ✅ Shipped |
| **Nicknames**             | Set a custom display name in chat and tab. Staff can override any player's nick.                                                                                                                                                                                                                  | ✅ Shipped |
| **Trading**               | `/trade <player>` — request a nearby item trade. In-memory, one-time flow.                                                                                                                                                                                                                        | ✅ Shipped |
| **Scoreboard**            | Per-player sidebar scoreboard showing rank, playtime, active event, and achievements. Updates every 2 seconds.                                                                                                                                                                                    | ✅ Shipped |
| **BossBar**               | Persistent rotating bossbar with server branding and custom messages. Configurable from DB.                                                                                                                                                                                                       | ✅ Shipped |
| **Night Skip Voting**     | Players vote to skip night by sneaking. Skips when 50%+ agree.                                                                                                                                                                                                                                    | ✅ Shipped |
| **Hub Navigation**        | Hotbar items for cosmetics, toybox, and navigator menu. Cleared on SMP entry, restored on hub return.                                                                                                                                                                                             | ✅ Shipped |
| **Toybox Gadgets**        | Equippable hub gadgets via hotbar slot.                                                                                                                                                                                                                                                           | ✅ Shipped |
| **Rules**                 | `/rules` displays server rules from config.                                                                                                                                                                                                                                                       | ✅ Shipped |
| **Back (Grandmaster)**    | One-use death location teleport for Grandmaster rank only. Requires SMP, no combat tag.                                                                                                                                                                                                           | ✅ Shipped |
| **Near (Diamond+)**       | `/near` lists players within a configurable radius.                                                                                                                                                                                                                                               | ✅ Shipped |
| **Cosmetic Preview**      | `/preview` to try any cosmetic on an ArmorStand before equipping.                                                                                                                                                                                                                                 | ✅ Shipped |
| **Combat Tag**            | Prevents teleport commands while in recent PvP combat. Configurable duration.                                                                                                                                                                                                                     | ✅ Shipped |
| **Priority Queue**        | Donor/staff ranks get join priority when the server is full; lowest-priority non-staff player is removed to make room.                                                                                                                                                                            | ✅ Shipped |


### Staff/Mod Features


| Feature                 | Min Rank  | Description                                                                                                                                        | Status    |
| ----------------------- | --------- | -------------------------------------------------------------------------------------------------------------------------------------------------- | --------- |
| **Kick**                | Helper    | Immediate removal from server with reason.                                                                                                         | ✅ Shipped |
| **Warn**                | Helper    | Issue and record a formal warning.                                                                                                                 | ✅ Shipped |
| **Mute / Tempmute**     | Moderator | Silence a player (permanent or timed).                                                                                                             | ✅ Shipped |
| **Ban / Tempban**       | Moderator | Prevent a player joining (permanent or timed).                                                                                                     | ✅ Shipped |
| **Unban / Unmute**      | Moderator | Reverse bans and mutes.                                                                                                                            | ✅ Shipped |
| **Freeze**              | Moderator | Immobilise a player while you investigate.                                                                                                         | ✅ Shipped |
| **Vanish**              | Helper    | Become invisible to non-staff players.                                                                                                             | ✅ Shipped |
| **Staff Chat**          | Helper    | Private channel for staff. `/sc` shorthand.                                                                                                        | ✅ Shipped |
| **Clear Chat**          | Helper    | Blank 150 lines of chat for non-staff.                                                                                                             | ✅ Shipped |
| **Slowmode**            | Moderator | Apply a global chat cooldown timer.                                                                                                                | ✅ Shipped |
| **Moderation History**  | Helper    | View full ban/mute/warn record per player.                                                                                                         | ✅ Shipped |
| **Player Notes**        | Helper    | View staff notes attached to a player (write via panel).                                                                                           | ✅ Shipped |
| **Watchlist**           | Helper    | Flag players for monitoring; visible in panel.                                                                                                     | ✅ Shipped |
| **Whois**               | Helper    | Full diagnostic: rank, balance, stats, IP, last seen.                                                                                              | ✅ Shipped |
| **Set Rank**            | Admin     | Promote/demote any player to any rank.                                                                                                             | ✅ Shipped |
| **Set Donor**           | Admin     | Grant a donor rank track to a player.                                                                                                              | ✅ Shipped |
| **Economy Control**     | Admin     | Give/take/set balance or cosmetic coins for any player.                                                                                            | ✅ Shipped |
| **Event Management**    | Admin     | Start, stop, configure, and complete all event types.                                                                                              | ✅ Shipped |
| **Leaderboard Control** | Admin     | Place, remove, refresh hologram leaderboards anywhere.                                                                                             | ✅ Shipped |
| **Warp Management**     | Helper    | Create and delete public warps with optional cost.                                                                                                 | ✅ Shipped |
| **Set Spawn**           | Admin     | Set the hub spawn point (persisted to DB).                                                                                                         | ✅ Shipped |
| **Maintenance Mode**    | Admin     | Lock server to staff+whitelist only. Whitelist management included.                                                                                | ✅ Shipped |
| **Broadcast System**    | Admin     | Rotating server-wide message rotation with configurable interval.                                                                                  | ✅ Shipped |
| **Restart Scheduling**  | Admin     | `/restart [time] [reason]` with countdown and cancel.                                                                                              | ✅ Shipped |
| **Reload Plugin**       | Developer | Hot-reload config and managers without a server reboot.                                                                                            | ✅ Shipped |
| **Audit Logging**       | System    | All admin actions (rank changes, bans, eco, cosmetics) logged async to DB.                                                                         | ✅ Shipped |
| **Command Log**         | System    | Individual player command history stored in `player_command_log`.                                                                                  | ✅ Shipped |
| **Compat Report**       | Developer | DB status, migration state, world loading times.                                                                                                   | ✅ Shipped |
| **Debug Cockpit**       | Developer | Full dev GUI — system health, cosmetics, events, DB controls, live feed, command cheat-sheet (opens GUIs where applicable, including `**/shop`**). | ✅ Shipped |
| **Web Panel Auth**      | Player    | `/generatepassword` creates a one-time panel login token.                                                                                          | ✅ Shipped |
| **Version Monitor**     | System    | Alerts staff when a new Paper version is available.                                                                                                | ✅ Shipped |


---

## Upcoming Features


| #   | Feature                                                                                                                                                                                             | Who Benefits           | Priority    | Status                                              |
| --- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------- | ----------- | --------------------------------------------------- |
| B   | MOTD / Login Summary                                                                                                                                                                                | All players            | Soon        | Planned                                             |
| C   | Server Boosters                                                                                                                                                                                     | All players            | Soon        | Planned                                             |
| D   | Personal Boosters                                                                                                                                                                                   | Donor players          | Soon        | Planned                                             |
| E   | Recruit-a-Friend                                                                                                                                                                                    | All players            | Soon        | Planned                                             |
| F   | Player Profile Overhaul (`/stats` tabbed GUI)                                                                                                                                                       | All players            | Soon        | Planned                                             |
| G   | Graves Overhaul (nametag ArmorStand, donor auto-equip)                                                                                                                                              | All players            | Soon        | Planned                                             |
| H   | **Events System Overhaul** — full rewrite: per-event-type arenas, auto-countdown lobby, team auto-balance (party-aware), CTF, live sidebar + boss bar, coins+XP+item rewards, spectator mode polish | All players            | **Next**    | 🔧 In Progress (death arch shipped P21)             |
| H2  | Exclusive Event Skins + Blood Champion Banner                                                                                                                                                       | Hardcore event winners | Soon        | Planned                                             |
| I   | **Server Shop — follow-ups** — web panel `/admin/shop/prices` + `/admin/shop/log`, staging burn-in on economy + transactions                                                                        | Staff / economy ready  | Next        | Planned                                             |
| I2  | Player Shops — player-to-player storefronts                                                                                                                                                         | All players            | Later       | Planned                                             |
| I3  | **Version Tagging** — label v0.1 through v1.0. Starts after Economy Store + Player Shops ship. Each milestone tag captures full feature state.                                                      | All                    | After I2    | Milestone                                           |
| J   | Jebaited Wrapped (year-end stats showcase)                                                                                                                                                          | All players            | Soon        | Planned                                             |
| K   | Quest Lines                                                                                                                                                                                         | All players            | Later       | Planned                                             |
| L   | Custom Enchants / Special Items                                                                                                                                                                     | All players            | Later       | Deferred                                            |
| M   | Temp Rank System                                                                                                                                                                                    | Staff / Admin          | Later       | Deferred                                            |
| N   | In-Game Store + Discord Checkout Orchestration (replaces Tebex)                                                                                                                                     | Donor players          | Next        | Planned                                             |
| O   | [Multi-Server Network (Velocity)](#22-network-overhaul-full-velocity-network)                                                                                                                       | All players            | Deferred    | 🧱 Scaffold done (ServerType + NetworkManager stub) |
| P   | **Pre-Production Audit** — full codebase security pass (OWASP Top 10), messy code cleanup, dead code removal, SQL injection/XSS review, permission audit. Hard gate before v1.0 public release.     | Dev                    | Before v1.0 | Planned                                             |


### Current focus (Jamie — ordering)


| Order | Track                                               | Notes                                                                                                                                                                                                                                                                                                                                      |
| ----- | --------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **1** | **Economy + `/shop`** ([§17](#17-server-shop-shop)) | **MVP shipped in plugin** (buy/sell GUI, DB, seed, settings). **Next:** operator-run [staging checklist](#staging-verification-checklist) + Jamie panel via [PANEL_SERVER_SHOP_HANDOFF.md](docs/PANEL_SERVER_SHOP_HANDOFF.md), then **I2** (Player Shops) / [§18 Version labelling](#18-version-labelling).                                |
| **2** | **§21 KOTH polish**                                 | Small iteration surface; uncontested scoring + UX already evolving in code.                                                                                                                                                                                                                                                                |
| **3** | **CTF team mode**                                   | Same-team PvP blocked (`[CtfTeamDamageListener](src/main/java/com/darkniightz/core/eventmode/CtfTeamDamageListener.java)`). **Shipped:** kits + strip/reapply; ground-flag `Item` pickup (`[CtfGroundFlagListener](src/main/java/com/darkniightz/core/eventmode/CtfGroundFlagListener.java)`). **Next:** party-aware `TeamEngine`, HC-CTF. |
| —     | Web panel hooks                                     | Ship after shop + stabilised events APIs.                                                                                                                                                                                                                                                                                                  |
| —     | Parkour `EventKind`                                 | After KOTH/CTF kit pipeline feels solid (do not reuse KOTH hill cuboid).                                                                                                                                                                                                                                                                   |


End a Cursor session with a short copy-paste block in `[SESSION_HANDOFF.md](SESSION_HANDOFF.md)` so the next chat starts cheap.

### Active P1 implementation backlog (tracked in repo plan)

**Ongoing (process)** — not a one-time checkbox:


| Item                          | Notes                                                                                                                                                                                                                                                                                                                                           |
| ----------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Grafter: Settings + Debug** | On every feature/fix pass, review player **Settings** (`SettingKey`, category menus, persistence) and dev **Debug** (`DebugMenu`, `DebugFeedManager`, `DebugStateManager`)—see `[.cursor/skills/grafter/SKILL.md](.cursor/skills/grafter/SKILL.md)`, section **Player and developer surfaces**.                                                 |
| **Next theme pick (default)** | Continue **mcMMO wrapper parity** (`/mcability`, `/mccooldown`, `/ptp` when party TP is defined) in parallel with roadmap pillars: **events overhaul** ([§21](#21-events-system-overhaul)), **profile GUI** ([§6](#6-player-profile-overhaul-stats)), **shop follow-ups** ([§17](#17-server-shop-shop) — panel + staging, MVP already shipped). |


**Baseline shipped in code** — spot-check in staging when convenient (paths are authoritative):


| Item                           | Where it lives                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Screen effects**             | `PresentationPreference`, `SettingsCategoryMenu` — **Gameplay** → Screen effects (Full / Some / None).                                                                                                                                                                                                                                                                                                                                                                               |
| **Moderation matrix**          | `[CommandSecurityListener](src/main/java/com/darkniightz/core/system/CommandSecurityListener.java)`: helper `tempban`/`tempmute`; mod `ban`/`mute`; srmod `unban`/`unmute`. `[BanCommand](src/main/java/com/darkniightz/core/commands/mod/BanCommand.java)` / `[MuteCommand](src/main/java/com/darkniightz/core/commands/mod/MuteCommand.java)` + `[ModerationLimits](src/main/java/com/darkniightz/core/moderation/ModerationLimits.java)` (helper max **7 days** on temp actions). |
| **Grave insurance**            | `[GraveManager.isInsuredRank](src/main/java/com/darkniightz/core/system/GraveManager.java)` — donor **Legend+** only (not staff-alone; Diamond does not qualify).                                                                                                                                                                                                                                                                                                                    |
| **Combat tag on death**        | `[CombatTagListener.onDeath](src/main/java/com/darkniightz/core/system/CombatTagListener.java)` clears tag + message.                                                                                                                                                                                                                                                                                                                                                                |
| **Scoreboard compact economy** | `[ServerScoreboardManager.compactNumber](src/main/java/com/darkniightz/core/system/ServerScoreboardManager.java)` for hub coins + SMP balance.                                                                                                                                                                                                                                                                                                                                       |
| **Private vault instant save** | `[PrivateVaultListener](src/main/java/com/darkniightz/core/gui/PrivateVaultListener.java)` `onInventoryClickSave` / `onInventoryDragSave` (monitor), not only on close.                                                                                                                                                                                                                                                                                                              |
| **mcMMO command ownership**    | `[JebaitedCore.registerCommands](src/main/java/com/darkniightz/main/JebaitedCore.java)` → `reassertMcMMOCommandOwnership()` (`party`, `pa`, `p`, `inspect`, `mcinspect`, `mmoinspect`, `mcrank`, `mcstats`, `mctop`).                                                                                                                                                                                                                                                                |
| **Server shop + reload**       | `[ShopManager](src/main/java/com/darkniightz/core/shop/ShopManager.java)` + `[reloadCore()](src/main/java/com/darkniightz/main/JebaitedCore.java)` — `/jreload` reconstructs shop manager and `start()` so DB price edits apply without restart.                                                                                                                                                                                                                                     |


### Resolved quick wins (staging verification only)

Former BUGS table — implementation is in place; only **in-game verification** (especially with mcMMO loaded) remains:


| Topic                                             | Verify                                                                                                                                                                                                   |
| ------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| mcMMO `/party` + `/p`                             | Tab completes Jebaited behaviour after eviction.                                                                                                                                                         |
| mcMMO `/compat`                                   | Shows mcMMO version, live `getPowerLevel` bridge line, and `bridge_self_test` flag; follow the printed staging checklist.                                                                                |
| mcMMO `/mcstats`, `/mctop`, `/inspect`, `/mcrank` | Jebaited prefix + no `[mcMMO]`; `/inspect` requires a player argument.                                                                                                                                   |
| Graves / insurance copy                           | No Diamond-tier insurance; messaging in `[GraveManager](src/main/java/com/darkniightz/core/system/GraveManager.java)` / `[GraveListener](src/main/java/com/darkniightz/core/system/GraveListener.java)`. |
| Moderation ranks                                  | Helpers cannot perm-ban or exceed 7d temp; mods/srmod gates as above.                                                                                                                                    |
| Scoreboard + PV                                   | Compact numbers + click/drag save paths as linked.                                                                                                                                                       |


### OVERHAULS (multi-session)

**1. Combat tag polish (medium — 1–2 sessions)**  
**Shipped:** tag clears on death + player message (`[CombatTagListener.onDeath](src/main/java/com/darkniightz/core/system/CombatTagListener.java)`); `/combatlogs` (+ `/combatlog`, `/ctag`) — `[CombatLogsCommand](src/main/java/com/darkniightz/core/commands/CombatLogsCommand.java)`.  
**Still open:** optional enter/exit messages; **party members exempt** from tagging each other; align **TP warmup** blocks with the combat-tag command gate in `[CombatTagListener](src/main/java/com/darkniightz/core/system/CombatTagListener.java)`.  
**mcMMO overlaps:** `[reassertMcMMOCommandOwnership](src/main/java/com/darkniightz/main/JebaitedCore.java)` runs on a short delay and when mcMMO enables — if another plugin still wins, check load order / `plugin.yml` `loadbefore` / soft-depend.

**2. Moderation GUI overhaul (large — 2–3 sessions)**  
Replace inline-only staff commands with optional GUI: action picker, duration presets, reasons via `[ChatInputService](src/main/java/com/darkniightz/core/gui/ChatInputService.java)`, offender history from `[ModerationManager](src/main/java/com/darkniightz/core/moderation/ModerationManager.java)` / DB. New menus under `core/gui/`, `/mod` or `/staff` entry, moderator+ gate.

**3. Events overhaul + chat games separation (large — 3–4 sessions)**  
**Shipped:** `[ChatGameManager](src/main/java/com/darkniightz/core/eventmode/ChatGameManager.java)` + `[ChatGameEngine](src/main/java/com/darkniightz/core/eventmode/ChatGameEngine.java)` — chat rounds run **in parallel** with combat `[EventEngine](src/main/java/com/darkniightz/core/eventmode/EventEngine.java)` (`/chatgame` / `cg`; config `chat_games.games` + `chat_games.automation`; word/quiz lists remain `event_mode.chat`). DB: `event_sessions.event_type` uses config keys `chat_math` / `chat_scrabble` / `chat_quiz`; `[ChatGamePanelNotifier](src/main/java/com/darkniightz/core/eventmode/ChatGamePanelNotifier.java)` → `{webpanel}/api/server/chat-game-event` (JSON: `type`, `serverId`, `configKey`, `displayName`, `sessionId`, `winnerUuid`, `rewardCoins`, `startedAt`, `endedAt`; header `X-Provision-Secret`).  
**Still open:** **KOTH v2** — uncontested hill time + HC tie → split loot ([§21](#21-events-system-overhaul)); **CTF** — teammate damage off (listener shipped); uniform kit + inventory strip/restores per arena row; **Parkour race** — separate `EventKind`/handler (not hill reuse); plugin disable persist (`[EventParticipantDAO](src/main/java/com/darkniightz/core/eventmode/EventParticipantDAO.java)`); hardcore loot pool GUI, `/loot`, `LootPoolManager` — see `eventmode` package and §21.

---

## Feature Index (Detailed Design Docs)


| #   | Feature                                                                                               | Size    | Status                                                                       |
| --- | ----------------------------------------------------------------------------------------------------- | ------- | ---------------------------------------------------------------------------- |
| 1   | [Friends System](#1-friends-system)                                                                   | Large   | ✅ Shipped (P9, P12, P13, P14)                                                |
| 2   | [Party System](#2-party-system)                                                                       | Large   | ✅ Shipped (P15, P17)                                                         |
| 3   | [Recruit-a-Friend](#3-recruit-a-friend)                                                               | Medium  | Planned                                                                      |
| 4   | [Server Boosters](#4-server-boosters)                                                                 | Large   | Planned                                                                      |
| 5   | [Personal Boosters](#5-personal-boosters)                                                             | Medium  | Planned                                                                      |
| 6   | [Player Profile Overhaul (/stats)](#6-player-profile-overhaul-stats)                                  | Large   | Planned                                                                      |
| 7   | [Graves Overhaul](#7-graves-overhaul)                                                                 | Medium  | Planned                                                                      |
| 8   | [Quest Lines](#8-quest-lines)                                                                         | Large   | Planned                                                                      |
| 9   | [Donor Rank Perks](#9-donor-rank-perks)                                                               | Large   | ✅ Core shipped — §9 perk ladder live; optional extras + panel polish ongoing |
| 10  | [Settings Overhaul](#10-settings-overhaul)                                                            | Large   | ✅ Shipped (P16)                                                              |
| 11  | [MOTD / Login Summary](#11-motd--login-summary)                                                       | Medium  | Planned                                                                      |
| 12  | [Rank Purchase / Upgrade Pipeline (Tebex)](#12-rank-purchase--upgrade-pipeline-tebex)                 | Medium  | Deferred                                                                     |
| 13  | [Temp Rank System](#13-temp-rank-system-future)                                                       | Medium  | Deferred                                                                     |
| 14  | [Plugin Command Wrappers](#14-plugin-command-wrappers)                                                | Ongoing | Planned                                                                      |
| 15  | [Achievement / Milestone System (The Grind Bible)](#15-achievement--milestone-system-the-grind-bible) | XL      | ✅ Shipped (P18)                                                              |
| 16  | [Jebaited Wrapped](#16-jebaited-wrapped)                                                              | Medium  | Planned                                                                      |
| 17  | [Server Shop (`/shop`)](#17-server-shop-shop)                                                         | Large   | ✅ Shipped (MVP in-plugin; panel editor optional)                             |
| 21  | [Events System Overhaul](#21-events-system-overhaul)                                                  | XL      | 🔧 In Progress — death arch shipped P21                                      |
| 22  | [Network Overhaul (Full Velocity Network)](#22-network-overhaul-full-velocity-network)                | XL      | 🧱 Scaffold done — full overhaul deferred until Velocity ready               |


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


| File                               | Purpose                                                                        |
| ---------------------------------- | ------------------------------------------------------------------------------ |
| `core/friends/FriendManager.java`  | Business logic: add, remove, accept, deny, list, isFriend, isPending           |
| `core/friends/FriendCache.java`    | In-memory friend/request cache with load/invalidate                            |
| `core/friends/FriendListener.java` | Join: load cache + notify friend online. Quit: persist and evict               |
| `core/gui/FriendsMenu.java`        | 54-slot GUI: list friends (heads), status dots, remove button, invite to party |
| `core/commands/FriendCommand.java` | `/friend add                                                                   |
| `main/FriendDAO.java`              | DB ops: insertRequest, acceptRequest, removeFriend, listFriends, listPending   |


### Commands

```
/friend add <player>      — send request (rejected silently if already friends)
/friend accept <player>   — accept incoming request → write friendships row
/friend deny <player>     — delete request row
/friend remove <player>   — delete friendship row, both directions
/friend list              — opens FriendsMenu
```

### Wiring checklist

- `FriendManager.java`
- `FriendCache.java`  
- `FriendListener.java`
- `FriendsMenu.java`
- `FriendCommand.java`
- `FriendDAO.java`
- `V004__friends.sql` + `migrations.index` append
- `plugin.yml` → `friend` command
- `JebaitedCore.registerCommands()` bind
- `PermissionConstants` → `CMD_FRIEND`
- `CommandSecurityListener` case

### Notifications

- On login: check pending requests → `§d[Friends] §f{n} pending friend request(s). Use §a/friend list §fto view.`
- On friend join: `§d[Friends] §a{name} §fis now online.` (only to online friends; optional in settings)

---

## 2. Party System

### Goal

Players form a temporary in-game party for shared gameplay on SMP. Stats for party activities are tracked separately. Active on SMP only; parties disband if the leader disconnects with no transfer.

**Standards for this feature:**

- All limits configurable (`party.max_size`, XP share %, drop rate bonuses) — config validation must reject values outside sane bounds (e.g. max_size 1–16, XP share 0–100).
- All messages use Adventure API + `Messages.prefix()`. No legacy `§` codes in Java. Rank-coloured player names via `RankManager` wherever a player name appears in chat.
- `/party` and `/p` tab completions are ours. mcMMO's `/party` must be evicted on plugin load. Tab completions must not leak subcommands the player doesn't have access to (e.g. leader-only subcommands hidden for non-leaders).
- **Panel surface:** Party stats per player (`player_party_stats`) should be exposed on the player profile page in the panel.

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


| File                               | Purpose                                                                                                   |
| ---------------------------------- | --------------------------------------------------------------------------------------------------------- |
| `core/party/Party.java`            | Data class: UUID id, leader UUID, Set members, createdAt, chatToggle set                                  |
| `core/party/PartyManager.java`     | Create, disband, invite, accept/deny, kick, transfer, broadcast to party                                  |
| `core/party/PartyListener.java`    | Death/kill: check party, share XP. Quit: remove member or disband. Block break/fish: party stat increment |
| `core/party/PartyStatTracker.java` | Async stat writes to `player_party_stats`                                                                 |
| `core/gui/PartyMenu.java`          | 54-slot GUI: member list (heads), role icons, invite, kick, disband                                       |
| `core/commands/PartyCommand.java`  | All party subcommands                                                                                     |
| `main/PartyStatDAO.java`           | upsert `player_party_stats`                                                                               |


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

**Standards for this feature:**

- Qualifying playtime, reward coin amounts, code claim window, and referrer minimum playtime all configurable with validated bounds (e.g. qualifying playtime 1–120 mins, rewards 0–10000 coins — reject nonsense values on startup).
- All messages use Adventure API + `Messages.prefix()`. Welcome message on first join is the only unsolicited message; all others are direct feedback to the player who ran the command.
- `/refer` tab completion shows nothing useful to the player (code is user-supplied, not auto-completable). No tab leak from any plugin.
- **Panel surface:** Referral history per player — who they referred, whether the reward was claimed, date. Staff page: total referral counts per player for competition tracking.

### DB — `V006__referrals.sql` *(assign V-number at implementation; next available after V005__boosters)*

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


| File                                 | Purpose                                                |
| ------------------------------------ | ------------------------------------------------------ |
| `core/referral/ReferralManager.java` | validateAndApply, checkQualifyingPlaytime, claimReward |
| `core/commands/ReferCommand.java`    | `/refer <code>`                                        |
| `main/ReferralDAO.java`              | insertReferral, markClaimed, loadByNewPlayer           |


### Wiring checklist

- DB migration V006
- `ReferralManager.java`
- `ReferCommand.java`
- `ReferralDAO.java`
- `plugin.yml` → `refer` command
- `JebaitedCore.registerCommands()` bind
- `PermissionConstants.CMD_REFER`
- Hook into `StatsTrackingListener` playtime tick → `ReferralManager.tickPlaytime`
- `CommandSecurityListener` case

---

## 4. Server Boosters

### Goal

Staff-activatable server-wide boosts that apply to all online players for a fixed duration. Different boost types affect different mechanics. Only one active booster per type at a time.

**Standards for this feature:**

- Each boost type has configurable default multiplier, max multiplier cap (e.g. 5×), and max duration (e.g. 24h). Staff cannot pass values beyond the cap — reject with a clear error message.
- Activation/expiry broadcasts use Adventure API + `Messages.prefix()`. Boost active reminders (e.g. on join mid-boost) must be a single informational line, not spam. Format: `§e[Booster] §fActive: §a{type} §7(×{mult} — {time_remaining})`.
- `/booster` tab completes boost types and subcommands only for the sender's rank. Players can run `/boosters` (status) — no admin subcommands shown to non-staff in tab.
- **Panel surface:** Active booster dashboard (type, multiplier, activated_by, expires_at, time remaining). Staff can activate/stop boosters from the panel via RCON.

### Boost Types


| Key              | Effect                                                               | Mechanic                          |
| ---------------- | -------------------------------------------------------------------- | --------------------------------- |
| `xp_global`      | +N% mcMMO XP for all players                                         | `McMMOPlayerXpGainEvent` multiply |
| `mining_ore`     | +N% chance of extra ore drop                                         | `BlockBreakEvent` RNG             |
| `mining_fortune` | Fortune enchant treated as +N levels                                 | `BlockBreakEvent` loot override   |
| `fishing_catch`  | +N% second catch chance                                              | `PlayerFishEvent`                 |
| `fishing_mcmmo`  | Chance to fish up a mcMMO special item (`GrapplingHook`, `PartItem`) | `PlayerFishEvent`                 |
| `boss_loot`      | +N% boss drop multiplier                                             | `EntityDeathEvent`                |


Boost amounts and defaults configured under `boosters.server.`* in config.

### DB — `V005__boosters.sql` *(assign V-number at implementation; next after V004__donor_perks)*

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


| File                                      | Purpose                                                                                                                                     |
| ----------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `core/booster/BoosterType.java`           | Enum of boost types with config key, default multiplier, display name                                                                       |
| `core/booster/BoosterManager.java`        | Singleton. Loads active from DB on startup. Tick expiry check (1/s). `isActive(type)`, `getMultiplier(type)`. Broadcasts on activate/expire |
| `core/booster/BoosterListener.java`       | Hooks `BlockBreakEvent`, `PlayerFishEvent`, `EntityDeathEvent`, `McMMOPlayerXpGainEvent`. Applies booster multipliers if active             |
| `core/commands/BoosterCommand.java`       | `/booster activate <type> <duration> [multiplier]` — admin+                                                                                 |
| `core/commands/BoosterStatusCommand.java` | `/boosters` — show all active boosters with time remaining                                                                                  |


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

- DB migration V007
- `BoosterType.java`
- `BoosterManager.java` (register with `JebaitedCore`)
- `BoosterListener.java`
- `BoosterCommand.java`
- `BoosterStatusCommand.java`
- `plugin.yml` entries
- `JebaitedCore.registerCommands()` binds
- `PermissionConstants.CMD_BOOSTER`, `CMD_BOOSTERS`
- `CommandSecurityListener` cases

---

## 5. Personal Boosters

### Goal

Per-player passive boosts that are always active for that player on SMP. Certain tiers are granted automatically by donor rank. Others can be activated from inventory.

**Standards for this feature:**

- Boost multipliers and durations for each donor tier are configurable (`boosters.personal.`*). Sane caps enforced — no single player can exceed server booster cap values. Inventory-item boosters have a configurable max stack (e.g. 10 per type per player).
- All feedback messages use Adventure API + `Messages.prefix()`. Passive boosts are silent (no spam on every XP gain). Show a one-time activation notice and a single line on login if a timed booster is active.
- `/booster` tab completions already handled by Server Booster spec. No separate command needed unless there's a `/mybooster` — if added, tab must only show options relevant to that player.
- **Panel surface:** Per-player booster inventory and active personal boost status on the player profile page. Staff can grant or remove booster items from the panel.

### Passive Boosts by Donor Rank


| Donor Rank | Effect                            |
| ---------- | --------------------------------- |
| `gold`     | +5% XP shared with party members  |
| `diamond`  | +15% XP shared with party members |
| `legend`   | +25% XP shared with party members |


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


| File                                       | Purpose                                                                                                                                                           |
| ------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `core/booster/PersonalBoosterManager.java` | Load active personal boosters on join. `isActive(uuid, type)`, `getMultiplier(uuid, type)`. Merge with server booster (highest wins, or additive — config toggle) |
| `core/gui/PersonalBoosterMenu.java`        | Inventory UI: active boosters with time remaining, inventory of owned boosters, activate slot                                                                     |


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

**Standards for this feature:**

- All display text customisable in config (`profile.labels.`*) with validation — empty strings rejected, max length enforced so item names don't overflow Minecraft's 50-char lore limit.
- All item display names and lore use Adventure API only. Rank colour on player head title must use `RankManager.getRankColor(target)`. Stat numbers formatted with locale-aware commas (e.g. `1,452,310` not `1452310`).
- `/stats`, `/profile`, `/pp` are all ours — evict any plugin that registers these. Tab completions for player-name argument complete only online players visible to the sender (respect vanish). Staff viewer sees all.
- **Panel surface:** Player profile data already surfaced via DB. This overhaul adds the Achievements tab — panel should mirror the tab layout: hub stats, smp stats, event stats, achievements, friend/party counts.

### Layout — 54 slots

```
[0 ][1 ][2 ][3 ][4 ][5 ][6 ][7 ][8 ]
[9 ][10][11][12][13][14][15][16][17]
[18][19][20][21][22][23][24][25][26]
[27][28][29][30][31][32][33][34][35]
[36][37][38][39][40][41][42][43][44]
[45][46][47][48][49][50][51][52][53]
```


| Slot  | Content                                                                                   |
| ----- | ----------------------------------------------------------------------------------------- |
| 13    | Player head — name, rank, donor rank, first joined, last seen                             |
| 4     | Active tab indicator (cosmetic glass pane border)                                         |
| 20    | Tab: Hub Stats                                                                            |
| 22    | Tab: SMP Stats                                                                            |
| 24    | Tab: Event Stats                                                                          |
| 26    | Tab: Custom / Party Stats                                                                 |
| 29–35 | Stat items for current tab (7 slots)                                                      |
| 45    | Back / Close button                                                                       |
| 46    | ← if viewing others: previous player (staff only)                                         |
| 48    | Friend Invite / Already Friends indicator                                                 |
| 50    | Party Invite button (only if viewer is party leader and not already in party with target) |
| 51    | Open Settings (only shown if viewing self)                                                |
| 53    | Refresh (re-loads profile from DB)                                                        |


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


| File                                      | Action                                                                                     |
| ----------------------------------------- | ------------------------------------------------------------------------------------------ |
| `core/gui/PlayerProfileMenu.java`         | **New** — replaces StatsMenu. Tab state tracked per viewer. Accepts `OfflinePlayer target` |
| `core/gui/PlayerProfileMenu.java` (inner) | `TabPage enum { HUB, SMP, EVENTS, CUSTOM }`                                                |
| `core/commands/StatsCommand.java`         | **Modify** — open `PlayerProfileMenu` instead of `StatsMenu`                               |
| `StatsMenu.java`                          | **Keep** for now, deprecate once PlayerProfileMenu ships                                   |
| `plugin.yml`                              | Add `profile` and `pp` as aliases for `stats`                                              |
| `JebaitedCore.registerCommands()`         | Bind `profile` and `pp` aliases                                                            |


### Rank gate

- Any player: `/stats` (their own profile)
- Helper+: `/stats <player>` (view others)
- Same rule for `/profile` and `/pp`

### Back navigation from Settings

`SettingsMenu` needs an optional "back" function injected — pass `() -> new PlayerProfileMenu(...).open(viewer)` as a `Runnable backAction`. `SettingsMenu` already has slot 45 glass pane; replace with ARROW "Back to Profile" if `backAction != null`.

---

## Implementation Order

Recommended sequence to minimise rework (updated post-P20):

> ✅ Shipped: Friends (P9/P12/P13/P14), Private Vaults (P10), Settings (P16), Party (P15/P17), Achievements (P18), Graves + Insurance + /back (P19), Bug-fix pass + nether/end (P20)

1. **Donor perk commands** — `/feed`, `/near`, `/kit`, `/repair`, `/deathtp`. `/back` already shipped P19. `kit_cooldowns JSONB` column needed → **V004** migration.
2. **MOTD / Login Summary** — Can ship any time; login listener hook + MotdService singleton. No DB.
3. **Server/Personal Boosters** — Fully independent; **V005**__boosters.sql + BoosterManager.
4. **Recruit-a-Friend** — Fully independent; **V006**__referrals.sql + ReferralManager.
5. **Player Profile overhaul** (`/stats`) — Tabbed 54-slot GUI. Achievements, friends, and party tabs all have live systems now. No DB.
6. **Graves overhaul** — Floating ArmorStand nametag, donor auto-equip on loot, GraveNametagTask. No DB.
7. **Exclusive Event Skins + Blood Champion banner** — HC win wardrobe auto-unlock + NBT banner item. No DB.
8. **Economy Store + Player Shops** — **V007**__shop.sql + ShopManager, listing table, PanelConnectorService push.
9. **Version Tagging starts** — After Economy Store + Player Shops: tag v0.1. Every major feature after gets a version bump.
10. **Jebaited Wrapped** — End of year; depends on a full year of stats data.
11. **Quest Lines** — **V008**__quests.sql. Design phase only until store ships (rewards need to exist first).
12. **Pre-Production Audit** — full OWASP + code quality pass. Hard gate before v1.0 public release.

---

## 10. Settings Overhaul

### Goal

A fully categorised, persistent per-player settings system. Players can customise their experience across sounds, chat formatting, notifications, social interactions, and more. Everything is DB-backed, GUI-driven, and togglable per-category. Designed to ship **after Party and Player Profile Overhaul** — so that friend/party/event settings all have real systems to toggle.

### Design Principles

- **Categories, not a flat list.** `SettingsMenu` becomes a category picker (27-slot); each category opens a 54-slot page.
- **DB-backed.** Settings stored in a new table — no YAML per-player files.
- **Sensible defaults.** All settings default to ON / the expected behaviour; the DB only stores non-default values (reduces row bloat).
- **Settings gate features.** Friend invites, party invites, event broadcasts, chat visibility — all gated by the player's own preferences.
- **Panel-visible.** The panel should surface per-player toggles for moderation context (e.g. "player has disabled PMs" is useful in moderation view).

---

### DB — `VNEXT__settings.sql` *(assign V-number at implementation; after all prior features' migrations)*

```sql
CREATE TABLE IF NOT EXISTS player_settings (
    uuid            VARCHAR(36) PRIMARY KEY,
    settings_json   JSONB        NOT NULL DEFAULT '{}',
    updated_at      BIGINT       NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000),
    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE
);
```

Using JSONB lets us add new settings keys without schema migrations — only the initial table creation needs a migration. Individual settings are read/written as JSON keys within the blob.

---

### Settings Categories

#### 🔔 Notifications


| Key                       | Default | Description                                 |
| ------------------------- | ------- | ------------------------------------------- |
| `notify.friend_request`   | `true`  | Show incoming friend request in chat        |
| `notify.friend_online`    | `true`  | Notify when a friend comes online           |
| `notify.friend_accept`    | `true`  | Notify when your friend request is accepted |
| `notify.party_invite`     | `true`  | Show party invites in chat                  |
| `notify.event_start`      | `true`  | Notify when an event starts                 |
| `notify.event_end`        | `true`  | Notify when an event ends                   |
| `notify.server_broadcast` | `true`  | Receive scheduled server broadcasts         |
| `notify.booster_active`   | `true`  | Notify when a server booster activates      |
| `notify.levelup`          | `true`  | Show mcMMO level-up messages                |


#### 🔊 Sounds


| Key                    | Default | Description                      |
| ---------------------- | ------- | -------------------------------- |
| `sound.friend_request` | `true`  | Play ding on friend request      |
| `sound.friend_online`  | `true`  | Play sound when a friend logs in |
| `sound.party_invite`   | `true`  | Play ding on party invite        |
| `sound.event_start`    | `true`  | Play fanfare on event start      |
| `sound.gui_click`      | `true`  | GUI click sounds                 |
| `sound.level_up`       | `true`  | mcMMO level-up sound             |
| `sound.coin_earn`      | `true`  | Cosmetic coin earn sound         |


#### 💬 Chat


| Key                                 | Default | Description                               |
| ----------------------------------- | ------- | ----------------------------------------- |
| `chat.receive_global`               | `true`  | See global chat                           |
| `chat.receive_private_messages`     | `true`  | Receive /msg and /r                       |
| `chat.show_join_leave`              | `true`  | See player join/leave messages            |
| `chat.compact_friend_notifications` | `false` | Compact multi-friend-online into one line |
| `chat.show_rank_prefix`             | `true`  | Show your rank prefix in chat             |
| `chat.show_donor_prefix`            | `true`  | Show donor rank tag (if applicable)       |


#### 👥 Social


| Key                              | Default | Description                                      |
| -------------------------------- | ------- | ------------------------------------------------ |
| `social.receive_friend_requests` | `true`  | Allow incoming friend requests                   |
| `social.receive_party_invites`   | `true`  | Allow incoming party invites                     |
| `social.show_on_friends_list`    | `true`  | Appear online to friends (ghost mode when false) |
| `social.show_in_leaderboards`    | `true`  | Include your stats in leaderboards               |


#### ⚔️ Events


| Key                            | Default | Description                                           |
| ------------------------------ | ------- | ----------------------------------------------------- |
| `event.auto_join_notification` | `true`  | Get pinged when an event you previously played starts |
| `event.spectate_on_death`      | `true`  | Auto-spectate when eliminated in HC events            |


#### 🎮 Gameplay


| Key                           | Default | Description                                   |
| ----------------------------- | ------- | --------------------------------------------- |
| `gameplay.show_scoreboard`    | `true`  | Show the sidebar scoreboard                   |
| `gameplay.show_bossbar`       | `true`  | Show boss bars (event timers etc.)            |
| `gameplay.show_hotbar_tips`   | `true`  | Show hub hotbar action-bar hints              |
| `gameplay.combat_tag_warning` | `true`  | Show warning when entering/leaving combat tag |


---

### Settings Menu Layout

**Category picker — 27 slots (`SettingsMenu.java` rewrite)**

```
[ ][ ][ ][ ][ ][ ][ ][ ][ ]   row 0 — border
[ ][ 🔔 Notify ][ 🔊 Sound ][ 💬 Chat ][ 👥 Social ][ ⚔️ Events ][ 🎮 Gameplay ][ ]
[ ][ ][ ][ ][ ][ ][ ][ ][ ]   row 2 — border + close
```


| Slot | Item                | Category      |
| ---- | ------------------- | ------------- |
| 10   | BELL                | Notifications |
| 12   | NOTE_BLOCK          | Sounds        |
| 14   | BOOK                | Chat          |
| 16   | PLAYER_HEAD (Steve) | Social        |
| 19   | DIAMOND_SWORD       | Events        |
| 21   | COMPARATOR          | Gameplay      |
| 22   | BARRIER             | Close         |


**Category page — 54 slots**

Rows 2–5 display toggle items. Each item:

- Name: `§f<Setting Name>`
- Lore line 1: `§7<description>`
- Lore line 2: `§a✔ Enabled` (GREEN_DYE icon) or `§c✘ Disabled` (RED_DYE icon)
- Click → toggle + async DB write + re-render

Slot 49: `§7← Back to Settings` (ARROW, runs `SettingsMenu.open(player)`)

---

### Java — Files to create/modify


| File                                              | Action                                                                                                                                         |
| ------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| `core/system/PlayerSettingsManager.java`          | **New** — loads/caches settings per player. `get(uuid, key, default)`, `set(uuid, key, value)` async. Cache populated on join, cleared on quit |
| `core/system/PlayerSettingsDAO.java`              | **New** — `loadSettings(uuid)`, `upsertSettings(uuid, Map<String,Object>)` — reads/writes JSONB blob                                           |
| `core/gui/SettingsMenu.java`                      | **Overhaul** — becomes category picker. Existing cosmetic/rank toggles migrate to relevant category pages                                      |
| `core/gui/settings/NotificationSettingsPage.java` | **New** — 54-slot toggle page for Notifications category                                                                                       |
| `core/gui/settings/SoundSettingsPage.java`        | **New** — Sounds                                                                                                                               |
| `core/gui/settings/ChatSettingsPage.java`         | **New** — Chat                                                                                                                                 |
| `core/gui/settings/SocialSettingsPage.java`       | **New** — Social (friend/party toggles)                                                                                                        |
| `core/gui/settings/EventSettingsPage.java`        | **New** — Events                                                                                                                               |
| `core/gui/settings/GameplaySettingsPage.java`     | **New** — Gameplay (scoreboard, bossbar, hotbar)                                                                                               |
| `core/system/FriendManager.java`                  | Check `settings.receive_friend_requests` before delivering invite                                                                              |
| `core/moderation/MessageManager.java`             | Check `settings.chat.receive_private_messages` before delivering PM                                                                            |
| `core/tracking/StatsTrackingListener.java`        | Check `settings.sound.`* + `settings.notify.*` before playing sounds/sending notifications                                                     |


### Wiring checklist

- DB migration `VNEXT__settings.sql` (assign V-number at implementation time)
- `PlayerSettingsManager.java` + `PlayerSettingsDAO.java`
- `PlayerSettingsManager` registered as singleton in `JebaitedCore`
- Cache load in `FriendListener.onJoin` (or a dedicated `SettingsListener`)
- Cache eviction on quit
- `SettingsMenu.java` overhauled to category picker
- 6 category page classes
- Existing rank-display and donor toggles in old `SettingsMenu` migrated to `SocialSettingsPage`
- `FriendManager` checks `receive_friend_requests`
- `MessageManager` checks `receive_private_messages`
- Notification sends gated by `notify.*` keys
- Sound plays gated by `sound.*` keys
- Panel endpoint: `GET /api/players/:uuid/settings` — returns full settings map for moderation context

### Panel Surface

```
[PANEL SURFACE]
Feature: Settings Overhaul
DB tables/columns added: player_settings (uuid, settings_json, updated_at)
Suggested panel page/endpoint: GET /api/players/:uuid/settings
Data shape: { "notify.friend_request": true, "chat.receive_global": false, ... }
```

---

## 11. MOTD / Login Summary

### Goal

When a player logs in, show a clean personalised summary in chat — friends online, pending requests, unread mail, recent shop sales, and more. Every line is configurable per-player via Settings (after that system ships). Deferred until Settings Overhaul is complete so the toggle infrastructure exists.

**Standards for this feature:**

- Every MOTD line individually toggleable via a `motd.`* settings key (already designed into the settings system). Global MOTD header format configurable in `config.yml` — but must always use `Messages.prefix()` as the anchor, never freeform. Max configurable MOTD line count (e.g. 12 lines to avoid scroll flooding).
- Entire MOTD output uses Adventure API. No legacy codes. Rank-coloured player name in the greeting line. Clickable components (e.g. friend name → runs `/friend info <name>`) where applicable.
- No command registered by this feature. MOTD fires on `PlayerJoinEvent` only. No tab completion concerns, but ensure `PanelConnectorService` is not called synchronously during login.
- **Panel surface:** Staff can preview any player's expected MOTD from the panel (what they'd see on login). Useful for debugging wrong state. Panel can also push a one-time server-wide MOTD override (e.g. maintenance window notice) via RCON.

### Login summary lines (all toggleable in Settings under `motd.`*)


| Key                       | Default | Example output                                                  |
| ------------------------- | ------- | --------------------------------------------------------------- |
| `motd.friends_online`     | `true`  | `§b✦ Friends online: §fDarkNiightz, xXSniper99 §7(+3 more)`     |
| `motd.pending_requests`   | `true`  | `§e⚡ You have §f2 §epending friend requests. §7[Click to view]` |
| `motd.unread_mail`        | `true`  | `§d✉ §f3 §dunread mail messages. §7[/mail read]`                |
| `motd.shop_sales`         | `true`  | `§6⬡ §fDiamond Sword §6sold for §f2,500 coins §7(last 24h)`     |
| `motd.event_active`       | `true`  | `§c⚔ An event is live: §fHARDCORE_FFA §7[/join]`                |
| `motd.booster_active`     | `true`  | `§a⬆ §fServer 2× XP Booster §aactive for §f14m 32s`             |
| `motd.playtime_milestone` | `true`  | `§a✔ New playtime milestone: §f50 hours!`                       |


### Design

- Displayed async after join — data is all read-through from cache. If cache is cold, skip gracefully; don't block join event.
- Fully skipped for staff joining in dev mode (no noise during debugging).
- Section header + footer match the existing `Messages` theme (aqua bar line, prefix).
- Each toggle lives in `motd.*` namespace inside `player_settings` JSONB — zero new DB columns needed once Settings Overhaul ships.
- Short-term (before Settings Overhaul): the MOTD fires with all items, no per-player toggle yet. Toggles wired in once `PlayerSettingsManager` exists.

### Implementation sketch

```java
// MotdService.java — sendLoginSummary(Player, plugin, managers...)
// Called from PlayerJoinListener after profile is warm in cache (100ms delay)
// Each section: check cache → build Component → append to list → player.sendMessage(list)
```

### Settings keys added to Settings Overhaul

Add to the `🔔 Notifications` category in Settings Overhaul:

```
notify.motd_friends_online    — true
notify.motd_pending_requests  — true
notify.motd_unread_mail       — true
notify.motd_shop_sales        — true
notify.motd_event_active      — true
notify.motd_booster_active    — true
notify.motd_playtime_milestone — true
```

### Java files


| File                                      | Action                                                                  |
| ----------------------------------------- | ----------------------------------------------------------------------- |
| `core/system/MotdService.java`            | **New** — `sendLoginSummary(Player)`, async, gated by settings          |
| `core/playerjoin/PlayerJoinListener.java` | **Modify** — schedule `MotdService.sendLoginSummary` after 3-tick delay |


### Wiring

- `MotdService` registered as singleton in `JebaitedCore`
- Depends on: `FriendManager`, `ProfileStore`, `EconomyManager` (shop sales), `EventModeManager`, `BossBarManager` (boosters)
- Mail integration: once a mail system ships, add to `MotdService.sendLoginSummary`
- Shop sales: once Economy Store ships, query `shop_sales` DB view for player sales in last 24h

### Panel Surface

```
[PANEL SURFACE]
Feature: MOTD / Login Summary
No new DB tables — reads from existing data sources
Suggested panel page: player profile page sidebar widget showing "what greeted this player on login"
```

---

Before any session, confirm:

- New party/friend actions that write player data go through `ProfileStore.saveDeferred` — never direct DAO writes on main thread.
- Every new command = 5 places: class + plugin.yml + registerCommands() + PermissionConstants + SecurityListener.
- Every new stat column = new migration file + migrations.index append + DAO SQL + tracking listener + display location.
- `McMMOIntegration.isEnabled()` guard on every mcMMO XP event hook.
- `MenuService.openMenu()` for all GUIs — never `player.openInventory()` directly.
- `ProfileStore.invalidate(uuid)` after any rank/balance/friend/party state change that affects display.
- Server/personal boosters: check `isEnabled()` before loading — avoid NPE if booster system is disabled.
- Party is in-memory only — don't persist party state to DB; only persist party **stats**.
- Referral anti-abuse: IP check is intentionally omitted (shared IPs on LAN servers) — use UUID uniqueness only.

---

## 7. Graves Overhaul

### Goal

Replace the basic item-dropping death behaviour with persistent graves that display a floating nametag (ArmorStand) above the grave block showing the player's name, death time, and item count. Donor players auto-equip their best cosmetic preset on grave loot pickup.

**Standards for this feature:**

- TTL, display format, and auto-equip behaviour all configurable per donor tier (`graves.`*). Death message format configurable but must keep rank colour on player name. Configurable TTL floor: no TTL below 60 seconds for regular players (prevent instant despawn abuse).
- Floating ArmorStand nametag uses Adventure API display name. Death message in chat uses `Messages.prefix()` and rank-coloured player name. Auto-equip notification is a single silent message, not a broadcast.
- `/graves` is ours — evict any grave plugin that registers this. Tab completes to nothing (no useful completable args for regular players). Staff `/graves <player>` completes online player names only.
- **Panel surface:** Active graves table — player, location, death time, item count, TTL remaining. Staff can view and force-clear graves from the panel. Grave Insurance status visible on player profile.

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


| File                               | Action                                                                                        |
| ---------------------------------- | --------------------------------------------------------------------------------------------- |
| `core/world/GraveManager.java`     | **Modify** — spawn ArmorStand on grave create, despawn on loot/expire, tick name refresh      |
| `core/world/GraveNametagTask.java` | **New** — repeating task (every 30s) updates all active grave nametag text with fresh "X ago" |
| `core/world/GraveListener.java`    | **Modify** — on grave loot: trigger donor re-equip if applicable                              |


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

**Standards for this feature:**

- Entire quest tree defined in config — quest ID, steps, completion conditions, rewards. Rewards validated on startup: coin amounts capped (e.g. max 5000 per quest step), cosmetic IDs verified to exist, donor perk strings validated against known perk keys. Bad config = startup warning + quest disabled, never a crash.
- All quest messages use Adventure API + `Messages.prefix()`. Step completion uses a distinctive but non-spammy format. Quest objectives shown in chat only on progress, not every tick. No raw `§` in Java.
- `/quest` is ours. Tab completes to the player's currently active quest IDs only — they never see quests they haven't unlocked. No plugin should be registering `/quest`; evict if found.
- **Panel surface:** Per-player quest progress on the player profile page — active quests, completed quests, completion dates. Staff page shows quest completion rates across the server.

### Quest Types


| Type             | Example                     |
| ---------------- | --------------------------- |
| `kill_mobs`      | Kill 50 zombies             |
| `kill_players`   | Win 3 FFA events            |
| `mine_blocks`    | Mine 500 stone              |
| `fish`           | Catch 20 fish               |
| `mcmmo_level`    | Reach mcMMO level 100       |
| `playtime`       | Play for 5 hours            |
| `event_wins`     | Win 5 events                |
| `chat_message`   | Send 100 messages           |
| `cosmetic_equip` | Equip 3 different cosmetics |


Quests can have prerequisites (`requires: [quest_id]`) and belong to a `series` for questline ordering.

### DB — `V008__quests.sql` *(assign V-number at implementation; exact number depends on features before it)*

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


| File                                   | Purpose                                                                                                                                                                  |
| -------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `core/quest/QuestManager.java`         | Loads quest definitions from config. `incrementProgress(uuid, type, mob/block, amount)`. `checkCompletion`. Cache: `Map<UUID, Map<String, Integer>>` progress per player |
| `core/quest/QuestDefinition.java`      | Data class: id, type, target, prereqs, series, rewards                                                                                                                   |
| `core/quest/QuestListener.java`        | `EntityDeathEvent`, `BlockBreakEvent`, `PlayerFishEvent`, `AsyncChatEvent` — routes to `QuestManager.incrementProgress`                                                  |
| `core/quest/QuestProgressTracker.java` | Playtime/mcMMO level checked on periodic tick (1 min)                                                                                                                    |
| `core/gui/QuestMenu.java`              | 54-slot GUI: active quests (with progress bars), completed quests, claim reward button                                                                                   |
| `core/commands/QuestsCommand.java`     | `/quests` — opens QuestMenu                                                                                                                                              |
| `main/QuestDAO.java`                   | upsert `player_quests`, load all for UUID, mark claimed                                                                                                                  |


### Progress persisting

- `QuestManager` keeps an in-memory dirty set. On `saveDeferred` trigger or every 5 minutes, async-flush dirty UUIDs to `player_quests`.
- On join, load quest progress from DB into cache.
- On quit, flush dirty entries.

### Reward types

- `reward_coins: N` → `profile.addCosmeticCoins(N)` + `ProfileStore.saveDeferred`
- `reward_unlocks: [key]` → `CosmeticsManager.unlock(uuid, key)`
- `reward_rank_display: "tag_key"` → unlock a cosmetic tag automatically

### Wiring checklist

- DB migration + migrations.index
- `QuestDefinition.java`
- `QuestManager.java` (register as singleton, inject into listeners)
- `QuestListener.java`
- `QuestProgressTracker.java` (scheduled task, 20s tick)
- `QuestMenu.java`
- `QuestsCommand.java`
- `QuestDAO.java`
- `plugin.yml` → `quests` command
- `JebaitedCore.registerCommands()` bind
- `PermissionConstants.CMD_QUESTS`
- Player Profile overhaul: add Quest progress panel / completion count to custom stats tab

---

## 9. Donor Rank Perks

### Goal

Implement and gate all donor rank perks so they are fully functional, permission-checked, and individually configurable. Donor ranks (`gold`, `diamond`, `legend`, `grandmaster`) layer perks on top of the normal rank system via `rankDisplayMode="donor"`. The Donor Perks Tracker in Section 15 is the single source of truth.

**Standards for this feature:**

- Every perk has a configurable value (`donor.perks.`*) with enforced limits. Home counts: 1–50. Vault pages: 1–20. XP share %: 0–100. Coin grants: 0–50000. Reject out-of-range values at startup — never silently clamp without warning.
- All perk messages use Adventure API + `Messages.prefix()`. Rank colour always comes from `RankManager`. "You don't have this perk" messages must reference the donor rank name clearly so the player knows what to upgrade to.
- Every perk command (e.g. `/feed`, `/near`, `/repair`, `/back`, `/kit`) is registered and tab-completed by JebaitedCore. Zero tab completions visible to non-donor players for donor-only commands — `CommandSecurityListener` must hide these subcommands in tab. `/kit` tab should only show kits the player can actually use.
- **Panel surface:** Donor rank breakdown chart (active counts per tier). Per-player donor rank, display mode, and all active perks visible on player profile. Staff can grant/revoke donor rank from the panel and preview the exact perk set the player will receive.

> Donor ranks sit on top of the normal rank ladder. Players keep their staff/pleb rank but get donor
> perks layered on top via `rankDisplayMode="donor"`. Ranks in ascending order of price/power:
> `gold` → `diamond` → `legend` → `grandmaster`.

### Rank quick-reference


| Rank        | Display name | Colour               |
| ----------- | ------------ | -------------------- |
| gold        | Gold         | Gold `§6`            |
| diamond     | Diamond      | Aqua `§b`            |
| legend      | Legend       | Light purple `§d`    |
| grandmaster | Grand Master | Dark red `§4` + bold |


---

### Gold


| Perk              | Detail                                                          |
| ----------------- | --------------------------------------------------------------- |
| Chat delay exempt | No chat cooldown (bypass `chat.delay_ms`)                       |
| `/nick`           | Custom nickname (strip colour codes; staff still see real name) |
| 3 homes           | `homes.max` = 3                                                 |
| `/rtp` 25% faster | Cooldown reduced by 25% vs default                              |
| `/enderchest`     | Open own ender chest anywhere                                   |
| `/pv 1`           | Private Vault — 1 × 54-slot page                                |
| `/craft`          | Open a crafting table anywhere                                  |
| `/kit gold`       | Decent iron-tier kit; 24h cooldown                              |
| `/anvil`          | Open anvil GUI anywhere                                         |


**Suggested extras:**

- Custom join/quit message colour (gold prefix in tab/chat)
- 1 extra cosmetic coin daily login bonus over pleb

---

### Diamond

All Gold perks, plus:


| Perk              | Detail                                         |
| ----------------- | ---------------------------------------------- |
| `/near`           | List nearby players within configurable radius |
| `/kit diamond`    | Full diamond kit + some enchants; 24h cooldown |
| `/pv 3`           | 3 × 54-slot private vault pages                |
| `/feed`           | Restore hunger (blocked while combat-tagged)   |
| 5 homes           | `homes.max` = 5                                |
| `/rtp` 50% faster | Cooldown reduced by 50% vs default             |


**Suggested extras:**

- Coloured signs (prefixed with `&` codes)
- 2 extra cosmetic coin daily bonus
- Particle trails enabled (access to non-animated cosmetics)

---

### Legend

All Diamond perks, plus:


| Perk                 | Detail                                                                        |
| -------------------- | ----------------------------------------------------------------------------- |
| 10 homes             | `homes.max` = 10                                                              |
| `/kit legend`        | High-end kit (strong but not god-tier); 24h cooldown                          |
| `/rtp` instant       | No cooldown                                                                   |
| `/tpa` instant       | No warmup delay                                                               |
| `/pv 5`              | 5 × 54-slot pages                                                             |
| Instant Recovery     | `/deathtp` — teleport directly to last death coords (SMP only, out of combat) |
| Server-Wide Boosters | Can activate server XP/drop boosters (see Feature 4)                          |


**Suggested extras:**

- Keep XP on death in SMP (no item-keep, just XP levels)
- 5 extra cosmetic coin daily bonus
- Access to animated particle trails

---

### Grand Master *(new tier)*

All Legend perks, plus:


| Perk               | Detail                                                                        |
| ------------------ | ----------------------------------------------------------------------------- |
| `/kit grandmaster` | God-tier kit (full prot IV, sharp V, etc.); 24h cooldown                      |
| `/pv 10`           | 10 × 54-slot pages                                                            |
| 0 RTP timer        | `/rtp` fires immediately with no warmup                                       |
| 0 TP warmup        | `/tpa`, `/home`, `/warp` etc. fire immediately (out of combat)                |
| `/back`            | Teleport to last death location or last TP origin (out of combat)             |
| `/repair`          | Repair item in hand (or full inventory with `all` arg); configurable cooldown |
| Unlimited homes    | `homes.max` = `Integer.MAX_VALUE` effectively                                 |
| Priority queue     | Jump the join queue on full server (future network feature)                   |


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

`**/back` — store last death/tp coords in a `BackManager` (in-memory, one entry per UUID).**
Update on: `PlayerDeathEvent`, `PlayerTeleportEvent` (non-plugin-initiated).
Cleared on: server restart.

`**/repair` — use `ItemMeta.setDamage(0)` (Damageable interface). Check `item.getItemMeta() instanceof Damageable`.**

**Wiring checklist:**

- Add permission constants (all listed above)
- `CommandSecurityListener` cases for each new command
- `plugin.yml` entries
- `JebaitedCore.registerCommands()` binds
- `KitManager.java` — kit definitions from config, cooldown read/write
- `PrivateVaultManager.java` — open vault by page, persist to DB
- `BackManager.java` — last-death/last-tp tracking
- DB migration `V004__donor_perks.sql` — adds `kit_cooldowns JSONB` column to `players`
- `migrations.index` append
DB tables/columns added: player_vaults, players.kit_cooldowns (JSONB)
Suggested panel page/endpoint: /admin/donors - active donor list, rank, expiry (if timed)
Data shape: { uuid, username, donorRank, pvPages, kitCooldowns: { gold: 1234567890 } }

```

---

## 12. Rank Purchase / Upgrade Pipeline (Tebex)

### Goal
Donor rank upgrades are a purchase, not an admin command. The flow:

**Standards for this feature:**
- Webhook secret validated on every inbound request (HMAC or header token). Rank names in the webhook payload validated against the known donor ladder before any DB write. Reject unknown rank strings — never pass unsanitised webhook data to `/setdonor`.
- Confirmation messages to the player use Adventure API + `Messages.prefix()`. Welcome message on rank grant is rank-coloured. `/store` command output is a single Adventure component with a clickable link — no paste-into-chat URL.
- `/store` is ours. Tab completes nothing. No other plugin should be registering store/shop commands — evict if found.
- **Panel surface:** Rank purchase log — player, previous rank, new rank, purchase date, Tebex transaction ID. Staff can view pending/failed webhooks and manually retry. Active donor rank breakdown chart.
1. Player visits the store (Tebex webstore link via `/store` command or hub NPC).
2. Tebex webhook fires on purchase → hits a secured endpoint on the web-admin panel.
3. Web-admin panel calls RCON or DB to run `/setdonor <player> <rank>`.
4. If the player already has a donor rank, the webhook/admin must explicitly clear the old one first (`/setdonor <player> none`) before setting the new tier.

### Rules
- A player can only hold **one donor rank at a time**. Overwriting without clearing is blocked at the command level.
- Donor rank upgrades replace the old tier — the purchase price reflects the delta (handled Tebex-side).
- Admins still use `/setdonor <player> none` → `/setdonor <player> <rank>` for manual corrections.

### Deferred items
- [ ] Tebex webhook listener on web-admin panel (`POST /tebex/webhook`)
- [ ] RCON bridge from panel to server for automatic grant
- [ ] `/store` command with clickable chat link
- [ ] Hub store NPC (Citizens or ArmorStand-based)

### Notes
- This does **not** change any in-plugin command logic — the guard is already in `SetDonorCommand`.
- The Tebex package must set the correct tier only; the webhook handler clears any existing donor rank before granting the new one.

---

## 13. Temp Rank System (Future)

### Goal
Temporary ranks awarded as event prizes (e.g. 7-day Legend for winning a server-wide tournament). They coexist with the donor rank system but follow different rules.

**Standards for this feature:**
- Duration configurable per award (`tempranks.default_duration_days`, override per command). Max duration cap enforced (e.g. 90 days — staff can't accidentally grant a permanent-looking temp rank). Rank name must be from the known ladder; reject anything else.
- Expiry notification uses Adventure API + `Messages.prefix()`. Player gets a warning 24h before expiry and a message on expiry. No spam — once per event per player.
- `/temprank` is staff-only. Tab completion hides subcommands and rank names from non-staff. Rank names tab complete from the known ladder only.
- **Panel surface:** Active temp ranks table — player, rank, granted_by, expires_at, time remaining. Expired temp ranks in history. Staff can extend or revoke from the panel.

### Rules
- Temp ranks are **time-limited** and stored with an expiry timestamp.
- A player with a temp rank **cannot** have it upgraded via `/setdonor` — upgrades are purchases of permanent donor tiers. Temp ranks expire naturally.
- Temp rank display overrides the primary rank display while active.
- On expiry, the player reverts to their previous primary/donor rank display.
- Rank display selector in Settings **does not** expose temp ranks — they are auto-applied and non-configurable.

### Planned DB columns (future migration)
```sql
ALTER TABLE players ADD COLUMN IF NOT EXISTS temp_rank        VARCHAR(32);
ALTER TABLE players ADD COLUMN IF NOT EXISTS temp_rank_expiry BIGINT;
```

### Deferred items

- `TempRankManager` — expiry check on join + scheduled task
- `getDisplayRank()` chain: temp rank > donor/selector > primary
- `SetTempRankCommand` — admin only, requires duration argument
- `CommandSecurityListener` guard (admin+)
- DB migration + `SchemaManager` hook
- Panel surface: active temp ranks list with expiry countdown

---

## 14. Plugin Command Wrappers

### Goal

Players never see the underlying plugin branding. Every command that another plugin exposes (mcMMO, etc.) should be wrapped or overridden so it feels native to JebaitedCore. This is an **ongoing, every-session obligation** — not a one-off task. Whenever a command is added, modified, or a new plugin is introduced, the wrapper table below must be checked and updated.

**Standards for this feature (apply every session):**

- **Tab completion hygiene:** After evicting a plugin command, its tab completions must be gone too. Register our own `TabCompleter` on every wrapped command — even if it returns an empty list. A player should never see a `<player>` tab completion from mcMMO's `/party` because our `/party` doesn't expose member lists to non-members.
- **Zero foreign branding:** Wrapped commands must produce output via Adventure API + `Messages.prefix()`. Any error message, help text, or feedback that leaks the original plugin name (e.g. `[mcMMO]`, `[Essentials]`) is a bug. Fix it.
- **Rank-gating on wrappers:** Every wrapped command must go through `CommandSecurityListener` or inline rank check. No wrapper silently allows any player to run a command without at least a rank gate.
- **Config-driven eviction list:** Maintain the eviction table below. If a new plugin is added to the server, check its command list against ours before it goes live. Conflicts must be resolved before deploy.
- **Panel surface:** Wrapper coverage table should be surfaced as a panel health check — which commands are wrapped, which are pending, which plugins are on the server.

### Strategy

- Use `evictBuiltInCommand(name)` (already in `JebaitedCore.java`) to remove the foreign plugin's command from the `CommandMap`.
- Register a thin wrapper `CommandExecutor` that delegates to the original plugin's API **or** reimplements the functionality cleanly.
- Register a matching `TabCompleter` on every wrapped command — never leave tab completion to the evicted plugin.
- All wrapper commands follow the standard wiring checklist (class + plugin.yml + bind + PermissionConstants + SecurityListener).

### mcMMO — version and staging

- **Reference build:** upstream mcMMO **2.2.049** (API surface `com.gmail.nossr50.api.`*; bridge is reflection-only in `[McMMOIntegration](src/main/java/com/darkniightz/core/system/McMMOIntegration.java)`).
- **Staging checklist:** `/compat` shows mcMMO enabled + version; leaderboard/stat UIs that use `mcmmo_level` show a number (not N/A everywhere); optional `integrations.mcmmo.bridge_self_test: true` logs a one-line `getPowerLevel` probe on enable.
- **Alias conflicts (do not ignore):** mcMMO registers `mcstats` with alias `stats` — Jebaited **does not** register that alias so `/stats` stays the **server stats GUI**. mcMMO `partychat` aliases include `p` — Jebaited owns `/p` for **party chat** via the same eviction list as `/party`.

### mcMMO wrappers (status)


| Original command                                                             | Wrapper / handling                                                                                                  | Status                                   |
| ---------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- | ---------------------------------------- |
| `/party`, `/pa`, `/p`                                                        | `[PartyCommand](src/main/java/com/darkniightz/core/commands/PartyCommand.java)` + `reassertMcMMOCommandOwnership`   | **Wrapped / evicted**                    |
| `/mcstats`                                                                   | `[McStatsCommand](src/main/java/com/darkniightz/core/commands/McStatsCommand.java)`                                 | **Wrapped / evicted** (no `stats` alias) |
| `/mctop`                                                                     | `[McTopCommand](src/main/java/com/darkniightz/core/commands/McTopCommand.java)`                                     | **Wrapped / evicted**                    |
| `/mcrank`                                                                    | `[McRankCommand](src/main/java/com/darkniightz/core/commands/McRankCommand.java)`                                   | **Wrapped / evicted**                    |
| `/inspect`                                                                   | `[McInspectCommand](src/main/java/com/darkniightz/core/commands/McInspectCommand.java)` (`mcinspect`, `mmoinspect`) | **Wrapped / evicted**                    |
| `/mcability`, `/mccooldown`, `/mcc` (if used), `/ptp`, `/xprate`, admin cmds | —                                                                                                                   | Pending / staff-only review              |
| `/mmoinfo`, `/mcmmo`, `/skillreset`, per-skill detail cmds                   | —                                                                                                                   | Pending                                  |


### mcMMO upstream command inventory (2.2.x `plugin.yml` — for diffs on upgrade)

Use this row list when a new mcMMO JAR lands: diff its `commands:` against this set and update the wrapper table + `[JebaitedCore.MCMMO_OWNED_COMMANDS](src/main/java/com/darkniightz/main/JebaitedCore.java)` if new overlaps appear.

`mmoxpbar`, `mmocompat`, `mmodebug`, `mmoinfo`, `xprate`, `mcmmo`, `mctop`, `mcrank`, `addxp`, `addlevels`, `mcability`, `mcrefresh`, `mccooldown`, `mcchatspy`, `mcgod`, `mcstats`, `mcremove`, `mmoedit`, `ptp`, `party`, `inspect`, `mmoshowdb`, `mcconvert`, `partychat`, `skillreset`, per-skill commands (`excavation`, `herbalism`, `mining`, `woodcutting`, `axes`, `archery`, … — see upstream file), plus any new entries in that release.

### Other plugins

- Any plugin that registers `/home`, `/warp`, `/spawn`, `/tp` etc. must be evicted on enable — we already own those.
- Track all active third-party plugins in the table below and confirm each has been evicted/wrapped before shipping.


| Plugin                 | Commands owned                                                                                                                                                                            | Status                    |
| ---------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------- |
| mcMMO                  | Core overlaps: `/party`, `/p`, `/inspect` (+aliases), `/mcrank`, `/mcstats`, `/mctop` (**Jebaited eviction + wrappers**); remaining mcMMO commands still use mcMMO handlers until wrapped | Partial — see table above |
| EssentialsX (if added) | All `/tpa`, `/home`, `/spawn`, `/warp`, `/bal`, `/pay` etc.                                                                                                                               | Must evict — we own these |
| Vault (if added)       | No player-facing commands                                                                                                                                                                 | OK                        |


---

## 15. Achievement / Milestone System (The Grind Bible)

**Status:** ✅ Shipped (P18) — design doc below is historical; canonical tables are in `[V001__base_schema.sql](src/main/resources/db/V001__base_schema.sql)` (`player_achievements`, `achievement_vouchers`); code in `[AchievementManager](src/main/java/com/darkniightz/core/achievements/AchievementManager.java)` and related classes.  
**Size:** XL  
**Dependencies:** (met) `StatsTrackingListener`, `CosmeticsManager`, `PrivateVaultManager`, `[GraveManager](src/main/java/com/darkniightz/core/system/GraveManager.java)`

> **Note:** Sections below (SQL, class table, wiring checklist) mix original design intent with what shipped. When in doubt, trust `V001` + `migrations.index`, not draft migration names (`V012` / `V014`) in older paragraphs.

### Goal

Pure stat-based progression for dedicated players. No items, no coins, no kits handed out — only unpurchasable cosmetic tags that prove real grind. Progress is tracked silently on every existing stat action. Milestones unlock special tags visible in `/tag` (Achievements tab) and Player Profile. Players can choose which 0–6 to display.

Hardcore winners receive exclusive limited-time wardrobe + placeable "Blood Champion" banner. Legend and above get permanent graves with smart auto-vault looting.

**Standards for this feature:**

- All thresholds, tier names, tag text, reward types, and secret flags configurable in `config.yml` under `achievements.categories.`*. Validation on startup: threshold must be > 0, tag text max 48 chars, reward type must be a known value (`coins_N`, `voucher_type`, `none`) — bad entries log a warning and the tier is skipped, never a crash.
- All unlock messages use Adventure API + `Messages.prefix()`. Secret tier unlocks get a special announcement format (configurable: server broadcast or private-only). Achievement hover text in `/tag` and Player Profile uses `Component.text()` — no legacy codes.
- `/achievements` (or whatever command opens the GUI) is ours. Tab completes player name for staff viewing others. No other plugin registers this command; evict if found. `/tag` tab completions must already be filtered by rank — achievement tags must not appear as completable options for players who haven't unlocked them.
- **Panel surface:** Per-player achievements endpoint (`/api/players/:uuid/achievements`) with progress bars, unlock dates, voucher redemption status. Staff view: near-miss tracking (players within 10% of a secret threshold). Server-wide achievement completion chart for engagement monitoring.

### Core Philosophy

- Achievements are **purely stat-based** (no quests, no one-time tasks).
- Progress uses **existing listeners** (`StatsTrackingListener`, `McMMOIntegration`, playtime ticks, etc.).
- Unlocking a milestone automatically grants a special **cosmetic tag** (unpurchasable, greyed out for others in `/tag`).
- Hover on tag (in `/tag` or Player Profile) shows exact count and name (e.g. `"Tree Feller — 1,347,892 logs chopped"`).
- Players toggle displayed achievements in Settings → Social → Achievement Display.
- Panel gets full visibility: `/api/players/:uuid/achievements`.

---

### DB (canonical)

**Authoritative DDL:** `[V001__base_schema.sql](src/main/resources/db/V001__base_schema.sql)` (search for `player_achievements`, `achievement_vouchers`). No separate `V013__achievements.sql` / `V014__achievements.sql` exists in `[migrations.index](src/main/resources/db/migrations.index)` — those names were planning-era only.

**Historical draft (superseded):** early docs proposed `unlocked_at` / `claimed_tag` columns; production uses `tier_reached`, `first_unlock_at`, `last_updated`, and voucher `tier` + `reward_type` / `reward_value`. Do not paste the old draft SQL into new migrations.

---

### New / Modified Classes


| File                                                    | Purpose                                                                                         |
| ------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| `core/achievements/AchievementManager.java`             | Singleton; cache per-player; `incrementProgress(uuid, id, amount)` with auto-unlock + tag grant |
| `core/achievements/AchievementDefinition.java`          | Data class loaded from config — `id`, `category`, `threshold`, `tag`, `secret`, `reward`        |
| `core/achievements/AchievementListener.java`            | Hooks into existing tracking listeners for blocks placed, distance, etc.                        |
| `core/gui/AchievementsMenu.java`                        | 54-slot GUI; category tabs across top row; progress bars per tier                               |
| `core/gui/PlayerProfileMenu.java`                       | Add `ACHIEVEMENTS` tab; show unlocked tags with progress rings                                  |
| `core/gui/settings/AchievementDisplaySettingsPage.java` | Toggle which achievements to display (0–6 slots)                                                |
| `core/system/TagCustomizationManager.java`              | Add `"Achievements"` category; hover text shows exact stat + unlock date                        |
| `core/system/GraveManager.java`                         | Legend+ insurance (ttl=-1), auto-loot to vault on death                                         |
| `core/system/PrivateVaultManager.java`                  | 90% full warning, multi-page fallback logic for auto-vault                                      |
| `core/cosmetics/CosmeticsManager.java`                  | Generalized auto-equip for any wardrobe set (HC wins, future events)                            |


---

### Config Additions (`config.yml`)

```yaml
achievements:
  display_in_profile: true
  max_displayed: 6     # max achievement tags shown on a player
  categories:
    woodcutter:
      icon: OAK_LOG
      tiers:
        - id: woodcutter_1k
          name: "Sapling Slayer"
          threshold: 1000
          tag: "§2🌱 Sapling"
        - id: woodcutter_10k
          name: "Lumberjack"
          threshold: 10000
          tag: "§2🪓 Lumberjack"
        - id: woodcutter_100k
          name: "Forest Lord"
          threshold: 100000
          tag: "§2🌲 Forest Lord"
        - id: woodcutter_1m
          name: "Tree Feller"
          threshold: 1000000
          tag: "§2🌳 Tree Feller"
        - id: woodcutter_10m
          name: "Deforester"
          threshold: 10000000
          tag: "§2🌴 Deforester"
        - id: woodcutter_100m
          name: "World Eater"
          threshold: 100000000
          tag: "§c☠ World Eater"
          secret: true
          reward: "voucher_1000_coins"
    # ... (all categories follow the same pattern)

graves:
  insurance:
    legend_ttl: -1          # never expire
    grandmaster_ttl: -1
    auto_loot_to_vault: true
    default_vault_page: 1   # configurable per player in Settings → Gameplay
    warning_at_percent: 90
```

---

### Full Stat Categories & Tiers

All tiers use the same config pattern as woodcutter above. Secret tiers are hidden in the GUI (shown as `???`) until unlocked.


| Category          | Stat Source                      | Tiers                                      | Secret Name                     |
| ----------------- | -------------------------------- | ------------------------------------------ | ------------------------------- |
| **Woodcutter**    | `logs_chopped`                   | 1k / 10k / 100k / 1M / 10M / **100M**      | `"World Eater"`                 |
| **Miner**         | `blocks_broken`                  | 5k / 50k / 500k / 5M / 50M / **500M**      | `"Bedrock Bane"`                |
| **Fisher**        | `fish_caught`                    | 500 / 5k / 50k / 500k / 5M / **50M**       | `"Ocean Emperor"`               |
| **Farmer**        | `crops_broken` + `crops_planted` | 2k / 20k / 200k / 2M / 20M / **200M**      | `"Harvest God"`                 |
| **Warrior (PvP)** | `player_kills`                   | 100 / 1k / 10k / **100k**                  | `"Blood Champion"`              |
| **Warrior (PvE)** | `mob_kills` + `boss_kills`       | 1k / 10k / 100k / 1M / **10M**             | `"Warlord"`                     |
| **Playtime**      | ticks online                     | 24h / 7d / 30d / 90d / 180d / **365d**     | `"Veteran"` + 2000 coin voucher |
| **McMMO Power**   | power level                      | 50 / 100 / 250 / 500 / 1000 / **2000**     | `"Ascendant"`                   |
| **Event Wins**    | `event_wins`                     | 10 / 50 / 100 / 500 / **1000**             | `"Unbreakable"`                 |
| **HC Wins**       | `hardcore_wins`                  | 1 / 5 / 10 / 25 / **50**                   | `"Immortal"`                    |
| **Explorer**      | `distance_travelled` (new col)   | 100km / 1,000km / 10,000km / **100,000km** | `"Wanderer"`                    |
| **Builder**       | `blocks_placed` (new col)        | 5k / 50k / 500k / 5M / **50M**             | `"Architect"`                   |
| **Social**        | `messages_sent`                  | 5k / 25k / 100k / 500k / **2M**            | `"Chat God"`                    |


**Special cross-stat secret — `"Jebaited Legend"`:**

- Awarded for: 100M of any **single** stat OR 50M wood + 50M stone combo (configurable in yml).
- Unique visual: glowing dark-red tag + unique chat name glow that stacks on top of whatever tag they have equipped.

---

### Tier Rewards — ⚠️ PLANNING PHASE (suggestions, not finalized)

These are ideas only. Jamie to decide on exact values before implementation.


| Tier Level                     | Suggested Reward                                                   |
| ------------------------------ | ------------------------------------------------------------------ |
| Tier 1–2 (low)                 | Nothing — just the tag. Keep early tiers pure cosmetic.            |
| Tier 3–4 (mid)                 | Small cosmetic coin bonus (50–100 coins)                           |
| Tier 5 (high)                  | Larger coin bonus (250–500 coins)                                  |
| Secret tier (100M+)            | **Voucher item** (1000–2000 coins) + achievement-only cosmetic tag |
| `"Jebaited Legend"` cross-stat | Unique chat glow effect (not purchasable anywhere, ever)           |
| `"Veteran"` playtime           | 2000 coin voucher (already referenced in config example)           |


**Ideas for achievement-exclusive cosmetics (can't buy, only earn):**

- A dedicated `"Grind"` category in `/tag` with particle trails and name styles only unlockable via achievement milestones
- Profile badge frame visible in `/stats` — shows a glowing border on the player head icon
- Custom death message variants for high-tier Warriors

---

### Blood Champion Banner + Exclusive HC Wardrobe

Winner of any Hardcore event (`HARDCORE_FFA`, `HARDCORE_DUELS`, `HARDCORE_KOTH`) receives:

1. A **limited-time wardrobe set** auto-unlocked via `CosmeticsManager.autoEquipSet(uuid, "hc_win_YYYY_MM_DD")`.
2. A **"Blood Champion" banner item** with custom NBT (pattern + lore + event date). Only the owner or staff can pick it up after placing.
3. Right-click placed banner → `ArmorStand` + floating hologram (reuse `BossBarManager` / `DebugFeedManager`):
  ```
   §cBlood Champion §f— {winner}
   §7Hardcore FFA • {event_date} • {kills} kills
  ```
4. Banner data persists in item NBT on pickup — same pattern as `PrivateVaultManager` serialization.

`CosmeticsManager.autoEquipSet()` should be **generalized** (not HC-specific) — so future event types just pass a set name and it handles equip logic + grace period handling.

---

### Grave Insurance (Legend / Grandmaster)


| Feature                                   | Legend                  | Grandmaster             |
| ----------------------------------------- | ----------------------- | ----------------------- |
| Grave TTL                                 | Never expire (`ttl=-1`) | Never expire (`ttl=-1`) |
| Auto-loot to Private Vault on death       | ✅                       | ✅                       |
| Preferred vault page (Settings)           | ✅                       | ✅                       |
| Fallback to next available vault pages    | ✅                       | ✅                       |
| 90% vault full warning on death           | ✅                       | ✅                       |
| `/back` after death (out of combat, once) | ❌                       | ✅                       |


When vault is full and no fallback: items remain in grave (standard behaviour). Player gets a chat warning: `§cVault full — your grave has been created. Use /grave to retrieve items.`

`GraveManager` changes:

- Check `RankManager.isAtLeast(player, "legend")` before setting TTL
- On death: `PrivateVaultManager.autoLootToVault(uuid, grave)` — fill pages in order, return overflow list back to grave
- `PrivateVaultManager` emits a 90% warning event if usage ≥ threshold after auto-loot

---

### Donor Perks Tracker (Single Source of Truth)

Keep this table updated whenever perks are added or changed. Used for pricing, web panel selling, and permission checks.


| Rank            | Perks                                                                                                                         | Permissions (`jebaited.donor.`*)                                                                         |
| --------------- | ----------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| **Gold**        | 3 homes, chat delay exempt, `/nick`, `/enderchest`, `/pv 1`, `/craft`, `/anvil`, `/kit gold`                                  | `.homes.3`, `.chat.no_delay`, `.nick`, `.pv.1`, `.craft`, `.anvil`, `.kit.gold`                          |
| **Diamond**     | All Gold + 5 homes, `/near`, `/feed`, `/pv 3`, `/kit diamond`, 50% faster RTP                                                 | `.homes.5`, `.near`, `.feed`, `.pv.3`, `.kit.diamond`, `.rtp.50`                                         |
| **Legend**      | All Diamond + 10 homes, `/pv 5`, instant RTP, `/kit legend`, Grave Insurance (ttl=-1 + auto-vault), Blood Champion auto-equip | `.homes.10`, `.pv.5`, `.rtp.instant`, `.kit.legend`, `.grave.insurance`, `.grave.auto_equip`             |
| **Grandmaster** | All Legend + unlimited homes, `/pv 10`, `/back`, `/repair`, smarter vault fallback, instant TP warmups                        | `.homes.unlimited`, `.pv.10`, `.back`, `.repair`, `.grave.insurance`, `.grave.auto_equip`, `.tp.instant` |


---

### Wiring Checklist (P18 — core plugin)

- Achievement tables in `[V001__base_schema.sql](src/main/resources/db/V001__base_schema.sql)` + `[migrations.index](src/main/resources/db/migrations.index)`
- `AchievementDefinition`, `AchievementDAO`, `AchievementManager`, `AchievementListener` + JebaitedCore wiring
- `AchievementsMenu` / `AchievementDetailMenu`, `AchievementsCommand`
- Settings → Social → achievement display (registry-driven; see `SettingKey` / achievements UI)
- `StatsTrackingListener` — stat hooks for achievement categories in config
- `[GraveManager](src/main/java/com/darkniightz/core/system/GraveManager.java)` — donor Legend+ insurance + `autoLootToVault`
- `[PrivateVaultManager](src/main/java/com/darkniightz/core/system/PrivateVaultManager.java)` — vault fill warning + auto-loot paths
- `CosmeticsManager` — generalized `autoEquipSet()` for **exclusive HC/event wardrobe** (Blood Champion, etc.) — partial / event skins still on roadmap
- `EventModeManager` — HC win → auto-equip + banner item (ties to **H2** Exclusive Event Skins)
- Confirm `ProfileStore.invalidate(uuid)` after achievement/tier unlock if scoreboard/tab still stale (verify in code)
- Panel endpoint `/api/players/:uuid/achievements` (web admin — separate repo)

---

```
[PANEL SURFACE]
Feature: Achievement / Milestone System
DB tables added: player_achievements, achievement_vouchers
Suggested panel page/endpoint: GET /api/players/:uuid/achievements
Data shape: {
  achievements: [
    { id: "woodcutter_1m", category: "woodcutter", progress: 1452310, unlocked_at: 1712345678000, claimed_tag: true },
    ...
  ],
  vouchers: [
    { voucher_type: "coins_1000", granted_at: 1712345678000, redeemed: false }
  ]
}
Panel use: Per-player achievements tab showing progress bars, unlock dates, secret tier tracking. Staff can monitor near-miss 100M grind candidates.
```

---

## 16. Jebaited Wrapped

### Goal

Annual year-end stats showcase. Every player gets a personal "Wrapped" — top moments, biggest stats, highlights from the year. Server-wide records too. Inspired by Spotify Wrapped.

### Concept

- Triggered via `/wrapped [year]` (staff or self).  
- Pulls data from `player_stats`, `player_event_stats`, `player_achievements`, `player_party_stats`, and `chat_logs` scoped to `EXTRACT(YEAR FROM ...)`.  
- Presents a guided 54-slot "slideshow" GUI: one slide per stat category, animated with fireworks/particles.  
- Panel surface: `GET /api/wrapped?year=YYYY&uuid=...` returns the JSON summary.

### Per-player highlights


| Category     | Shown                                                |
| ------------ | ---------------------------------------------------- |
| Combat       | Total kills, K/D ratio, most kills in a single event |
| Builder      | Total blocks broken, most broken block type          |
| Social       | Messages sent, commands used, friends made           |
| Events       | Events entered/won by type, best winning streak      |
| Achievements | Tiers unlocked this year, rarest unlock              |
| Economy      | Total cosmetic coins earned, peak balance            |
| Playtime     | Total hours online, most active day-of-week          |


### Server-wide records

- Most kills overall
- Most blocks broken
- Most events won
- Longest play session
- Most messages sent
- Top cosmetic coin earner

### Technical notes

- `WrappedManager` — queries all wrapped data async per UUID.
- `WrappedCommand` — `/wrapped [year] [player]`, helper+ can view others.
- `WrappedMenu` — 54-slot slideshow GUI, NEXT button advances through category slides.
- All data comes from existing tables (`player_stats`, `player_achievements`, `player_party_stats`, `event_sessions`, `event_participants`, `friendship_stats`) scoped by year via epoch ms range.
- Epoch ms timestamps can be year-filtered with: `WHERE timestamp >= epoch_start AND timestamp < epoch_end`.

**Data sources per category:**


| Wrapped category  | DB source                                                                               |
| ----------------- | --------------------------------------------------------------------------------------- |
| Combat            | `player_stats.kills` / `deaths`; `event_participants` for event-specific K/D            |
| Events            | `event_sessions` JOIN `event_participants` — entries, wins, favourite type, best streak |
| Best party friend | `friendship_stats.party_time_ms` (pair with highest ms = best friend)                   |
| Social            | `friendship_stats.xp_together` + `kills_together`; `player_stats.messages_sent`         |
| Builder / Fisher  | `player_stats.blocks_broken`, `fish_caught`                                             |
| Achievements      | `player_achievements` filtered by `first_unlock_at` epoch ms                            |
| Economy           | `player_stats` + `players.cosmetic_coins` snapshots                                     |
| Playtime          | `players.playtime_seconds` diff from year start snapshot                                |


### Panel surface

```
[PANEL SURFACE]
Feature: Jebaited Wrapped
DB tables/columns used: player_stats, player_event_stats, player_achievements, player_party_stats (all scoped to year via epoch ms)
Suggested panel endpoint: GET /api/wrapped?year=YYYY&uuid=...
Data shape:
{
  year: 2025,
  uuid: "...",
  username: "...",
  stats: {
    kills: 1234, deaths: 456, kd_ratio: 2.71,
    blocks_broken: 99000, fish_caught: 350,
    playtime_hours: 142, messages_sent: 4200, commands_sent: 1800,
    cosmetic_coins_earned: 5500, events_entered: 28, events_won: 7
  },
  top_achievement_tier: "GOLD",
  friends_made: 3
}
```

---

## 17. Server Shop (`/shop`)

### Implementation status *(keep in sync with code)*

- **Shipped (plugin):** `V007__server_shop.sql`, `ShopManager` / `ShopCatalog` / `ShopPriceRow`, `ShopCommand` + alias `market`, `ShopMenu`, `ShopStackConfirmMenu`, `SettingKey` stack-buy confirm, `PermissionConstants.CMD_SHOP`, `CommandSecurityListener` cooldown bucket, config `server_shop:` (rate limits, menu title, etc.). `**ShopManager`:** startup warnings for bad `rate_limit_ms` / `donor_rate_limit_ms`; INFO log of loaded price row count after each `reload()`; on `shop_transactions` insert failure, warning log + one **Live feed** line via `DebugFeedManager` when available.
- **Shipped (Debug):** `[DebugMenu.commandEntries()](src/main/java/com/darkniightz/core/cosmetics/DebugMenu.java)` — `**/shop*`* row opens the same GUI path as the command (availability + `canUseShop` guards).
- **Not shipped:** Web panel price editor + transaction views (see [Panel Surface](#panel-surface) below); economy staging burn-in is operational, not a code checkbox.

### Staging verification checklist

Run on a **staging** server with PostgreSQL enabled before treating `/shop` as production-ready. **Execution** is manual on a real Paper staging instance (this repo has no automated staging job for these checks).


| Check                    | Pass criteria                                                                                                                                                                                                                                           |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **DB seed**              | After first start with empty `server_shop_prices`, table has rows (or `seed_on_empty: false` and you inserted prices manually).                                                                                                                         |
| **DB transactions**      | After a BUY and a SELL, `shop_transactions` has rows with correct `action`, `quantity`, `unit_price`, `total`, `transacted_at`.                                                                                                                         |
| **SMP only**             | In hub/non-SMP world, `/shop` shows “only in SMP”; in SMP, menu opens.                                                                                                                                                                                  |
| **Creative / spectator** | Shop refuses creative and spectator with the configured message.                                                                                                                                                                                        |
| **Insufficient balance** | Buy with balance < cost: error, no debit, no item.                                                                                                                                                                                                      |
| **Inventory full**       | Buy with full inventory: refund full price, error message (no partial debit).                                                                                                                                                                           |
| **Rate limit**           | Non-donor: rapid clicks eventually show “too fast”; donor uses `donor_rate_limit_ms` (0 = no gap).                                                                                                                                                      |
| `**/jreload`**           | Change a price in DB → `/jreload` → reopen shop shows new price without server restart. Startup log includes loaded row count (see `ShopManager`).                                                                                                      |
| **Panel handoff doc**    | Staff-facing price/log UI is implemented in `web-admin` using the same DB — use the copy-paste bundle [docs/PANEL_SERVER_SHOP_HANDOFF.md](docs/PANEL_SERVER_SHOP_HANDOFF.md) (operator check: doc present in repo; implementation lives in panel repo). |


### Goal

A convenient server-run bulk buy/sell GUI covering 9 item categories. Priced deliberately worse than what a smart player shop can offer — the shop is a floor, not the ceiling. Drives player trade by design. DB-backed prices mean live tweaks from the web panel with zero restarts.

### Economy Philosophy

- **Buy price** = 1.5–2.5× the natural effort/value of obtaining the item.
- **Sell price** = 40–60% of buy price. Creates an economy sink, prevents infinite farming loops.
- **Stack math** is calculated on-the-fly — all prices stored per single unit in DB. GUI multiplies by 64 for shift-click display.
- **No inflation loop:** selling to the server is never more profitable than farming events or using player shops.
- **Adjustable:** prices stored in `server_shop_prices` table — admin can edit via panel page without a server restart.

### GUI Layout (54 slots) — **as implemented**

```
Row 0 (slots 0–8):   [Category tabs ×9] — Blocks … Redstone
Row 1 (slots 9–17):  [spacer strip]
Rows 2–4 (18–44):    Item grid — 21 slots per page (7×3 centred)
Row 5 (45–53):       [Head + balance] [Prev] … [Search] … [Next] — head bottom-left (45), search centre (49)
```

- **Player Head** (slot **45**, bottom-left): skull with player's skin; lore = display name + **balance** (refreshes when menu reopens after buy/sell).
- **9 category tabs** on **row 0 only**: Blocks, Farming, Mobs, Ores, Dyes, Music, Food, Decoration, Redstone. Active tab uses enchant glint.
- **Item grid**: 21 items per page. Clicking an item buys/sells. Hover lore shows buy price, sell price, and shift-click stack price in yellow.
- **Search** (bottom row): **left-click** → `ChatInputService` filter by name within category; **right-click** → clear filter (no typing “cancel”). No Anvil GUI.
- **Pagination**: prev/next arrows only shown when needed.

### Interaction Model


| Action              | Result                                                                                                                                 |
| ------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| Left-click item     | Buy 1                                                                                                                                  |
| Shift + Left-click  | Buy stack (up to item max stack) — if **Gameplay → Shop stack confirm** is on, opens a **confirm GUI** first; if off, buys immediately |
| Right-click item    | Sell 1 (scans inventory for exact material)                                                                                            |
| Shift + Right-click | Sell all of that material from inventory                                                                                               |


**Rate limit:** configurable `server_shop.rate_limit_ms` (default ~120 ms); players with a **donor rank** use `server_shop.donor_rate_limit_ms` (default **0** = no gap). Prevents macro abuse while allowing normal fast clicking.

All actions are instant with sound feedback via `SoundCompat`. Insufficient balance → `Messages.prefix()` error in chat, no GUI close.

### Categories & Curated Item Lists

**Blocks** — building staples, not exhaustive
All 8 log types, Bamboo Block, Stone, Cobblestone, Deepslate, Andesite, Diorite, Granite, Sand, Gravel, Dirt, Grass Block, Mycelium, Podzol, Clay, Terracotta (all 16 colors), Concrete (all 16 colors), Wool (all 16 colors), Glass (plain + 16 stained).

**Farming**
Wheat Seeds, Wheat, Carrots, Potatoes, Beetroot Seeds, Beetroot, Pumpkin Seeds, Pumpkin, Melon Seeds, Melon, Sugarcane, Cactus, Bamboo, Cocoa Beans, Sweet Berries, Glow Berries, Nether Wart, Bonemeal, Hay Bale, Dried Kelp Block.

**Mobs** (drops + limited common eggs only)
Rotten Flesh, Bone, String, Spider Eye, Gunpowder, Ender Pearl, Blaze Rod, Ghast Tear, Phantom Membrane, Magma Cream, Shulker Shell, Totem of Undying, Spawn Eggs (Cow/Pig/Chicken/Sheep/Zombie/Skeleton only — no wither/ender dragon).

**Ores / Resources**
Coal, Raw Iron, Iron Ingot, Raw Gold, Gold Ingot, Raw Copper, Copper Ingot, Lapis Lazuli, Redstone Dust, Diamond, Emerald, Nether Quartz, Netherite Scrap, Ancient Debris, Netherite Ingot. Ore blocks (Iron/Gold/Diamond/Emerald Block) at hardcoded 9× ingot price.

**Dyes** — all 16 colors, uniform price.

**Music**
All 16 music discs + all 8 Goat Horn variants.

**Food**
Bread, Cookie, Pumpkin Pie, Cake, Apple, Golden Apple, Enchanted Golden Apple (very expensive), Baked Potato, Cooked Chicken, Cooked Beef, Cooked Porkchop, Cooked Mutton, Cooked Salmon, Rabbit Stew, Suspicious Stew, Honey Bottle, Sweet Berries, Glow Berries.

**Decoration**
All standard flowers, Lantern, Soul Lantern, Campfire, Soul Campfire, Item Frame, Glow Item Frame, Painting, Armor Stand, Bookshelf, Lectern, Flower Pot, Scaffolding, Chain, Bell, Carpet (all 16 colors), Banner (base).

**Redstone**
Redstone Dust, Redstone Torch, Repeater, Comparator, Piston, Sticky Piston, Observer, Hopper, Dropper, Dispenser, Rail, Powered Rail, Detector Rail, Activator Rail, Lever, Stone/Wood Button, Pressure Plates (all), Target Block, Sculk Sensor, Calibrated Sculk Sensor, Daylight Sensor.

### Pricing Reference Table


| Category   | Item                                      | Buy      | Sell          |
| ---------- | ----------------------------------------- | -------- | ------------- |
| Blocks     | Any log (all 8 types)                     | $8       | $3.50         |
| Blocks     | Bamboo Block                              | $7       | $3            |
| Blocks     | Stone                                     | $4       | $1.80         |
| Blocks     | Cobblestone                               | $3       | $1.40         |
| Blocks     | Deepslate                                 | $6       | $2.70         |
| Blocks     | Andesite / Diorite / Granite              | $5       | $2.20         |
| Blocks     | Sand / Gravel                             | $4       | $1.80         |
| Blocks     | Dirt / Grass Block                        | $3       | $1.40         |
| Blocks     | Mycelium / Podzol                         | $12      | $5.50         |
| Blocks     | Clay                                      | $6       | $2.70         |
| Blocks     | Terracotta (any color)                    | $9       | $4            |
| Blocks     | Concrete (any color)                      | $14      | $6.50         |
| Blocks     | Wool (any color)                          | $8       | $3.50         |
| Blocks     | Glass (plain)                             | $7       | $3            |
| Blocks     | Stained Glass (any color)                 | $11      | $5            |
| Farming    | Wheat Seeds                               | $2       | $0.90         |
| Farming    | Wheat                                     | $4       | $1.80         |
| Farming    | Carrots / Potatoes                        | $5       | $2.20         |
| Farming    | Beetroot Seeds / Beetroot                 | $4       | $1.80         |
| Farming    | Pumpkin / Melon Seeds                     | $6       | $2.70         |
| Farming    | Pumpkin / Melon                           | $8       | $3.50         |
| Farming    | Sugarcane                                 | $5       | $2.20         |
| Farming    | Cactus                                    | $7       | $3            |
| Farming    | Bamboo                                    | $4       | $1.80         |
| Farming    | Cocoa Beans                               | $9       | $4            |
| Farming    | Sweet Berries / Glow Berries              | $8       | $3.50         |
| Farming    | Nether Wart                               | $12      | $5.50         |
| Farming    | Bonemeal                                  | $10      | $4.50         |
| Farming    | Hay Bale                                  | $18      | $8            |
| Farming    | Dried Kelp Block                          | $15      | $7            |
| Mobs       | Rotten Flesh                              | $3       | $1.40         |
| Mobs       | Bone                                      | $5       | $2.20         |
| Mobs       | String                                    | $6       | $2.70         |
| Mobs       | Spider Eye / Gunpowder                    | $8       | $3.50         |
| Mobs       | Ender Pearl                               | $35      | $16           |
| Mobs       | Blaze Rod                                 | $25      | $11           |
| Mobs       | Ghast Tear / Phantom Membrane             | $60      | $27           |
| Mobs       | Magma Cream                               | $18      | $8            |
| Mobs       | Shulker Shell                             | $120     | $55           |
| Mobs       | Totem of Undying                          | $450     | $95           |
| Mobs       | Spawn Egg (Cow/Pig/Chicken/Sheep)         | $40      | $18           |
| Mobs       | Spawn Egg (Zombie/Skeleton)               | $55      | $25           |
| Ores       | Coal                                      | $6       | $2.70         |
| Ores       | Raw Iron                                  | $9       | $4            |
| Ores       | Iron Ingot                                | $12      | $5.50         |
| Ores       | Raw Gold                                  | $18      | $8            |
| Ores       | Gold Ingot                                | $25      | $11           |
| Ores       | Raw Copper                                | $7       | $3            |
| Ores       | Copper Ingot                              | $10      | $4.50         |
| Ores       | Lapis Lazuli                              | $15      | $7            |
| Ores       | Redstone Dust                             | $14      | $6.50         |
| Ores       | Diamond                                   | $120     | $55           |
| Ores       | Emerald                                   | $90      | $40           |
| Ores       | Nether Quartz                             | $22      | $10           |
| Ores       | Netherite Scrap                           | $80      | $36           |
| Ores       | Ancient Debris                            | $95      | $43           |
| Ores       | Netherite Ingot                           | $450     | $210          |
| Ores       | Iron Block                                | $108     | $49.50        |
| Ores       | Gold Block                                | $225     | $99           |
| Ores       | Diamond Block                             | $1,080   | $495          |
| Ores       | Emerald Block                             | $810     | $360          |
| Dyes       | Any dye (all 16)                          | $5       | $2.20         |
| Music      | Any music disc                            | $85      | $18           |
| Music      | Any goat horn                             | $45      | $20           |
| Food       | Bread / Cookie                            | $6       | $2.70         |
| Food       | Pumpkin Pie                               | $18      | $8            |
| Food       | Cake                                      | $45      | $20           |
| Food       | Apple                                     | $4       | $1.80         |
| Food       | Baked Potato                              | $5       | $2.20         |
| Food       | Cooked meats (all)                        | $12      | $5.50         |
| Food       | Golden Apple                              | $85      | $38           |
| Food       | Enchanted Golden Apple                    | $2,500   | $1,100        |
| Food       | Rabbit Stew / Suspicious Stew             | $22      | $10           |
| Food       | Honey Bottle                              | $15      | $7            |
| Decoration | Flowers (all)                             | $4       | $1.80         |
| Decoration | Lantern / Soul Lantern                    | $18      | $8            |
| Decoration | Campfire / Soul Campfire                  | $15      | $7            |
| Decoration | Item Frame / Glow Item Frame              | $10      | $4.50         |
| Decoration | Painting                                  | $25      | $11           |
| Decoration | Armor Stand                               | $35      | $16           |
| Decoration | Bookshelf / Lectern                       | $22      | $10           |
| Decoration | Flower Pot / Scaffolding                  | $8 / $12 | $3.50 / $5.50 |
| Decoration | Chain / Bell                              | $14      | $6.50         |
| Decoration | Carpet (any color)                        | $7       | $3            |
| Decoration | Banner (base)                             | $20      | $9            |
| Redstone   | Redstone Dust                             | $14      | $6.50         |
| Redstone   | Redstone Torch / Repeater                 | $16      | $7            |
| Redstone   | Comparator                                | $22      | $10           |
| Redstone   | Piston / Sticky Piston                    | $28      | $12.50        |
| Redstone   | Observer / Hopper                         | $35      | $16           |
| Redstone   | Dispenser / Dropper                       | $32      | $14.50        |
| Redstone   | Rails (normal/powered/detector/activator) | $12–$18  | $5.50–$8      |
| Redstone   | Lever / Button / Pressure Plate           | $10–$14  | $4.50–$6.50   |
| Redstone   | Target Block                              | $40      | $18           |
| Redstone   | Sculk Sensor                              | $85      | $38           |
| Redstone   | Calibrated Sculk Sensor                   | $120     | $55           |
| Redstone   | Daylight Sensor                           | $28      | $12.50        |


> ⚠️ Music disc sell price is intentionally capped at $18 (not 40–60% of buy) to prevent disc-farming loops via cat/jukebox. Totem sell capped at $95 for the same reason.

### DB Schema — `V007__server_shop.sql`

```sql
CREATE TABLE IF NOT EXISTS server_shop_prices (
    item_key      VARCHAR(64)    PRIMARY KEY,   -- e.g. "minecraft:oak_log"
    category      VARCHAR(32)    NOT NULL,       -- "blocks", "farming", etc.
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
```

### Java — Files (shipped)


| File                                     | Purpose                                                                                                                 |
| ---------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| `core/shop/ShopManager.java`             | Loads `server_shop_prices`, seeds via `ShopCatalog` if empty, buy/sell, rate limits, async `shop_transactions` + audit. |
| `core/shop/ShopCatalog.java`             | Default seed rows (categories + pricing reference).                                                                     |
| `core/shop/ShopPriceRow.java`            | Cached price row.                                                                                                       |
| `core/commands/ShopCommand.java`         | `/shop` + alias `market`; `TabCompleter` returns empty list.                                                            |
| `core/gui/ShopMenu.java`                 | Main 54-slot GUI.                                                                                                       |
| `core/gui/ShopStackConfirmMenu.java`     | Optional shift-stack confirmation (27 slots).                                                                           |
| `SettingKey.GAMEPLAY_SHOP_STACK_CONFIRM` | Gameplay toggle (`PlayerProfile.PREF_SHOP_STACK_CONFIRM`).                                                              |
| `core/cosmetics/DebugMenu.java`          | Dev cockpit → command list → `**/shop**` (opens `ShopMenu` when shop is available).                                     |


### Wiring checklist (all 5 required)

- `ShopCommand.java` — executor + tab completer
- `plugin.yml` — command entry for `shop` with alias `market`
- `JebaitedCore.java` — `bindCommand("shop", …)` + `ShopManager` in `finishEnable`
- `PermissionConstants.java` — `CMD_SHOP = "jebaited.shop.use"` (default true, no rank gate)
- `CommandSecurityListener.java` — `shop` / `market` on 600ms cooldown bucket (no rank gate)

### Security & Dupe Protection

1. **Atomic transactions:** check balance → deduct/add money via `EconomyManager` → modify inventory → `ProfileStore.saveDeferred`. If inventory give fails (full), refund balance before returning.
2. **Sell validation:** scan player inventory for exact `Material` match before accepting sell. Quantity capped at what player actually holds.
3. **Rate limit:** `server_shop.rate_limit_ms` + optional donor bypass via `server_shop.donor_rate_limit_ms` (see Interaction Model above).
4. **Survival/Adventure only:** block shop use in creative mode.
5. **Audit log:** every buy/sell written to `shop_transactions` and `AuditLogService` (wrapped in try/catch — failure never crashes).
6. **Hub block:** shop unavailable in hub world — SMP only.

### Panel Surface

```
[PANEL SURFACE]
Feature: Server Shop
DB tables added: server_shop_prices, shop_transactions
Suggested panel pages:
  - /admin/shop/prices  — live price editor (edit buy/sell/enabled per item)
  - /admin/shop/log     — transaction log with player/item/qty/total/timestamp
Data shape (transaction row):
{
  "id": 1042,
  "player_uuid": "...",
  "player_name": "darkniightz",
  "item_key": "minecraft:diamond",
  "quantity": 5,
  "unit_price": 120.00,
  "total": 600.00,
  "action": "BUY",
  "transacted_at": "2026-04-16T20:00:00Z"
}
```

### Jamie handoff (web panel)

**Pasteable bundle for the web-admin AI:** `[docs/PANEL_SERVER_SHOP_HANDOFF.md](docs/PANEL_SERVER_SHOP_HANDOFF.md)` (schema, example queries, suggested routes, copy-paste prompt).

The plugin does **not** expose HTTP APIs for shop admin in MVP. The panel and plugin share **PostgreSQL**; implement routes in `web-admin` against the same DB the server uses.


| Concern                | Approach                                                                                                                                                                                                                                                                                                                                 |
| ---------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Price editor**       | `SELECT`/`UPDATE` `server_shop_prices` (`item_key` PK, `buy_price`, `sell_price`, `enabled`, `sort_order`, `category`, `display_name`, `max_stack`). After edits, ops run `/jreload` on the MC server (or wait for next restart) so `[ShopManager.reload()](src/main/java/com/darkniightz/core/shop/ShopManager.java)` picks up changes. |
| **Transaction log**    | `SELECT` from `shop_transactions` — filter by `player_uuid`, date range on `transacted_at`, optional `action IN ('BUY','SELL')`. Join to `players` for display names if needed.                                                                                                                                                          |
| **Auth**               | Use the panel’s existing staff/auth; no new plugin endpoints.                                                                                                                                                                                                                                                                            |
| **Failure visibility** | If `shop_transactions` insert fails, the plugin logs a warning and (when dev feed exists) records one line in `/debug` → Live feed — check server logs if players report missing ledger rows.                                                                                                                                            |


### I2 Player Shops — design contract (pre-implementation)

Ship **after** §17 is stable in production and the panel can maintain server prices. **Web-admin** listing UI is optional for v1; core loop is in-plugin.


| Piece       | Direction                                                                                                                                                                                                                                                                                                                  |
| ----------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **DB**      | New migration (next V after current `[migrations.index](src/main/resources/db/migrations.index)`): e.g. `player_shop_listings` with `seller_uuid` → `players(uuid)`, price, quantity, serialized item (bytes or JSON), location or chest reference, `created_at`, `expires_at`, `active`. Exact columns when implementing. |
| **Economy** | Buyer debit / seller credit via existing economy APIs; optional listing fee or tax to sink.                                                                                                                                                                                                                                |
| **Plugin**  | Seller: create listing from inventory block or command; buyer: browse GUI (nearby or warp); concurrency + dupes guarded in `ShopManager`-style transactions.                                                                                                                                                               |
| **Panel**   | Optional staff dashboard for listings — document JSON routes in ROADMAP when added.                                                                                                                                                                                                                                        |
| **§18**     | v0.1 tag remains gated on §17 + I2 per [Version labelling](#18-version-labelling).                                                                                                                                                                                                                                         |


### Notes

- Player shops (P2P storefronts) are a separate feature (I2 in the roadmap) — deferred until the server economy has been live long enough to understand real supply/demand.
- Prices in `server_shop_prices` are seeded on first run from config, but the config is source-of-truth only for fresh installs. Subsequent changes go via the panel to avoid config/DB drift.
- No A–Z letter filter — search bar (via `ChatInputService`) covers all filtering needs within a category.
- No Anvil GUI for search — `ChatInputService` is already in the codebase and is more consistent.

---

## 18. Version Labelling

### When it starts

After Economy Store + Player Shops (§17 + I2) ship and are stable, the plugin gets its first public version tag. Before that, it's internal/beta — no external release promise.

### Scheme

```
v0.x-beta  — pre-economy-store releases (current state: in-progress)
v0.1       — Economy Store + Player Shops shipped, server actively running
v0.x       — incremental feature milestones post-v0.1
v1.0       — after Pre-Production Audit passes (§19), full public release
```

### Rules

- Tags are git tags. Each tag is cut on a clean `BUILD SUCCESS` with no known P1/P2 bugs in the changelog.
- Version is displayed in the startup log block and in `/version` (add to `PluginCommand` or a small `VersionCommand`).
- Each minor version bump (v0.1 → v0.2 etc.) requires at least one shipped item from the Upcoming Features table.
- No version tag is cut from a branch — only from main.

### Checklist before v0.1

- Economy Store (`/shop`) — buy/sell GUI fully functional *(plugin; panel editor still optional)*
- Player Shops — P2P storefronts working
- All V-numbered migrations from V001–V007 verified idempotent + clean on fresh install
- No P1 bugs in the live changelog

### Checklist before v1.0

- Pre-Production Audit (§19) complete with all critical findings resolved
- Web admin panel surfacing economy, moderation, stats, and achievements
- Full server test run with a real player session — no ghost players, no boot loops

---

## 20. Broadcaster Overhaul

### Current state

`BroadcasterManager` round-robins a flat list of plain-text config messages on a fixed interval. No formatting variety, no per-message cooldowns, no context awareness, no interactivity.

### Goals

Make broadcasts feel like part of the server — not a spam bot. Messages should be contextual, rich, and occasional. Players should read them, not ignore them.

### Features

#### Message types

Each message entry in config gets a `type:` field:


| Type             | Behaviour                                                                                |
| ---------------- | ---------------------------------------------------------------------------------------- |
| `text`           | Plain broadcast — existing behaviour, full Adventure API formatting                      |
| `tip`            | `💡 Tip:` prefix in gold, italic. Rotates through tips list independently                |
| `vote`           | Links to vote site — clickable hover + URL ClickEvent                                    |
| `event_hint`     | Only fires when an event is active; shows current event name + `/event join` button      |
| `rule_reminder`  | Random pick from a `rules:` list; `📜 Rule:` prefix in red                               |
| `stat_spotlight` | Pulls a random online player's top stat and shows it — `§e{name} §7has §a{value} kills!` |


#### Per-message config shape

```yaml
broadcaster:
  enabled: true
  shuffle: false          # if true, randomise order instead of round-robin
  interval_seconds: 300
  messages:
    - type: text
      text: "&6Welcome to &lJebaited&6! Type &e/help &6to get started."
    - type: tip
      text: "&eUse &f/kit &eto claim your daily gear kit!"
    - type: event_hint
      text: "&d{event_name} &7is live — &f/event join &7to enter!"
    - type: vote
      text: "&bSupport the server! &fVote at &n{url}"
      url: "https://your-vote-url.com"
    - type: stat_spotlight
      stat: kills            # pulls from player_stats
```

#### Formatting rules

- All messages prefix with `Messages.prefix()` unless `raw: true` is set.
- `event_hint` messages are **skipped** if no event is active — don't count toward the rotation index when skipped.
- `stat_spotlight` is skipped if no players are online.
- All text fields support `&` colour codes translated at send-time (not load-time — allows config hot-reload).
- `vote` type renders a hoverable clickable link: `[Click to Vote]` with `HoverEvent.showText` + `ClickEvent.openUrl`.

#### Sending channel options

```yaml
broadcaster:
  channel: all         # all = all online, smp = SMP world only, hub = hub only
```

#### `/broadcast` command overhaul (admin)

- `/broadcast <message>` — immediate one-off broadcast (existing)
- `/broadcast reload` — hot-reloads `broadcaster.messages` list without restart
- `/broadcast skip` — skips to next queued message
- `/broadcast pause` / `/broadcast resume` — halts/resumes the timer
- Tab completes: `reload`, `skip`, `pause`, `resume` (helper+ only; inline message for everyone helper+)

#### Panel surface

```
[PANEL SURFACE]
Feature: Broadcaster Overhaul
DB tables/columns added: none (config-driven)
Suggested panel page/endpoint: /api/broadcaster/status, /api/broadcaster/send
Data shape: { "index": 2, "total": 8, "paused": false, "lastFiredAt": 1713300000000, "lastMessage": "..." }
```

Panel can show current queue position, last fired message, pause/resume toggle, and a "fire now" button.

### Implementation checklist

- `BroadcastMessage` record (type, text, url, stat, channel, raw)
- `BroadcasterManager` refactor: load typed entries, context-skip logic, shuffle mode, pause/resume state
- `BroadcastCommand` overhaul: reload/skip/pause/resume subcommands, PermissionConstants.CMD_BROADCAST_ADMIN
- `stat_spotlight` — async DB pull of a random online player's top stat column
- `vote` type — Adventure API ClickEvent.openUrl + HoverEvent.showText
- `event_hint` type — `EventModeManager.isEventActive()` guard
- Panel connector push on each fire: `PanelConnectorService.push("broadcaster", payload)`
- Config validation at startup: unknown `type` → warn + skip, not crash
- plugin.yml `/broadcast` usage string updated

### Notes

- Jamie: Keep it dead simple to add new types — the switch/dispatch on `type` should be one method, new type = new case + handler, nothing else. — Jamie

---

## 19. Pre-Production Audit (Gate to v1.0)

### Purpose

Before any "v1.0" public release tag, the entire codebase goes through a security and quality pass. Nothing ships with a v1.0 tag until this is done.

### Security scope (OWASP-guided)


| Check                     | Applies here                                                                                    |
| ------------------------- | ----------------------------------------------------------------------------------------------- |
| SQL injection             | All `PreparedStatement` usage — confirm zero string-concatenated SQL in production paths        |
| Broken Access Control     | Every command checks rank/permission before acting; TabCompleters filter by rank                |
| Sensitive data exposure   | No plaintext passwords, tokens, or UUIDs in logs/chat (check AuditLog, OpsAlert, startup block) |
| Security misconfiguration | config.yml defaults validated in `onEnable`; no debug flags enabled in prod                     |
| Insecure deserialization  | `ItemStack.deserializeItemsFromBytes` paths — confirm schema/version guard                      |
| Injection (command)       | `AsyncChatEvent` → `PlainTextComponentSerializer` used everywhere; no `getMessage()`            |
| Logging & monitoring      | AuditLog + ModerationManager cover all privileged actions                                       |
| Rate limiting             | All commands behind `CommandSecurityListener` cooldown buckets                                  |


### Code quality scope

- Dead code removal — classes/methods that were superseded but never deleted
- Messy/duplicated logic — any copy-pasted blocks that should be helpers
- TODO/FIXME comments — resolve or close each one with a decision
- Unused imports / fields — clean build with zero warnings after `-Xlint:all`
- Inconsistent naming — any field/method that doesn't match the project's naming conventions
- Config keys that are documented but never read (or read but never documented)

### Process

1. Run `mvn compile -Xlint:all` — log every warning, fix or suppress with justification.
2. Grep for `// TODO`, `// FIXME`, `//noinspection` — review each.
3. Manual command-by-command permission audit: every command executor → confirmed permission check → confirmed TabCompleter rank filter.
4. Database query audit: every raw query string reviewed for injection risk.
5. Write a one-page "Security & Quality Sign-off" doc in `docs/` before cutting v1.0 tag.

### Gate condition

The v1.0 tag is only cut after:

- All OWASP checks above have a "pass" or "N/A" verdict
- Zero compiler warnings on `-Xlint:all`
- Sign-off doc committed to `docs/SECURITY_SIGNOFF.md`

---

## 21. Events System Overhaul

---

### What Has Shipped (P21 — April 2026)

**Root cause found and fixed:** `PlayerDeathEvent` sends the death packet to the client *before* any plugin handler runs. `DO_IMMEDIATE_RESPAWN`, `spigot().respawn()`, `setKeepInventory(true)`, and `setCancelled(true)` on `PlayerDeathEvent` are **completely ineffective** at preventing the death screen. This was the bug that persisted across multiple failed fix attempts.

**Proven solution:** Cancel the `EntityDamageEvent` before the player's HP reaches zero. No death event fires, no packet sent, no death screen.

**Files changed:**


| File                           | What changed                                                                                                                                                                                                                                                                                                        |
| ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `EventModeCombatListener.java` | **Full rewrite.** Single `@EventHandler(priority=HIGHEST, ignoreCancelled=true) onFatalDamage(EntityDamageEvent)`. Cancels when `player.getHealth() - event.getFinalDamage() <= 0` for any active participant. All `PlayerDeathEvent`, `PlayerRespawnEvent`, `setKeepInventory`, `spigot().respawn()` code removed. |
| `EventEngine.java`             | Added `handleParticipantFatalDamage(Player)`, `collectHardcoreLootFromInventory(Player)`, `checkAndScheduleEnd()`. Updated `onEliminationTrigger` to use `ENDING` state + 5-second (100L) delay. Updated `restoreSnapshots()` to force `setGameMode(SURVIVAL)` on spectating/eliminated players before restoring.   |
| `EventModeManager.java`        | Added `handleParticipantFatalDamage(Player)` delegate → `engine.handleParticipantFatalDamage(player)`.                                                                                                                                                                                                              |
| `GraveListener.java`           | Returns early if `isParticipant(player)` OR event world. Sends killer message when victim has grave insurance.                                                                                                                                                                                                      |
| `GraveManager.java`            | `createNormalGrave` short-circuits for event participants and event world. `isInsuredRank()` made public.                                                                                                                                                                                                           |


**Behaviour by event type:**


| Event type                      | On fatal damage                                                                                                                                                                   |
| ------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| FFA / Duels / HC_FFA / HC_Duels | HC: collect inventory into `hardcoreLootPool` then clear. All: move snapshot to `spectatorSnapshots`, add to `eliminated`, set SPECTATOR next tick, call `checkAndScheduleEnd()`. |
| KOTH / HC_KOTH                  | HC: collect loot then clear inventory. All: full heal, teleport to world spawn, player stays active (still scores hill time).                                                     |


**End-of-event flow:**

- `checkAndScheduleEnd()` counts non-eliminated active players. If ≤ 1: set `ENDING` state, broadcast countdown, `runTaskLater(100L)` → `finalizeEvent`. ENDING state prevents double-trigger.
- `restoreSnapshots()`: active players from `session.snapshots`; spectating/eliminated forced SURVIVAL then restored from `session.spectatorSnapshots`.

**What's still planned (remaining work):**

- **Shipped (V006):** `event_sessions` / `event_participants` via `[EventParticipantDAO](src/main/java/com/darkniightz/core/eventmode/EventParticipantDAO.java)` + `[EventEngine](src/main/java/com/darkniightz/core/eventmode/EventEngine.java)`; `friendship_stats.party_time_ms` via `[FriendDAO.addPartyTimeTogether](src/main/java/com/darkniightz/core/system/FriendDAO.java)` + `[PartyManager](src/main/java/com/darkniightz/core/party/PartyManager.java)`.
- **Shipped (arena + UX slice):** `[EventArenaRegistry](src/main/java/com/darkniightz/core/eventmode/EventArenaRegistry.java)` + `[ArenaConfig](src/main/java/com/darkniightz/core/eventmode/ArenaConfig.java)` (`event_mode.arena_registry` in `config.yml`); KOTH hill + duration from registry; DB spawns still override YAML when present; `[TeamEngine](src/main/java/com/darkniightz/core/eventmode/team/TeamEngine.java)` / `[Team](src/main/java/com/darkniightz/core/eventmode/team/Team.java)` (even split; party cohesion still TODO); `[CtfHandler](src/main/java/com/darkniightz/core/eventmode/handler/CtfHandler.java)` + `[CtfFlagListener](src/main/java/com/darkniightz/core/eventmode/CtfFlagListener.java)`; live event lines in `[ServerScoreboardManager](src/main/java/com/darkniightz/core/system/ServerScoreboardManager.java)`; `/event spectate` (helper+), `info`, `setreward`, `arenas`, `start <event> [arena]`.
- Lobby countdown polish (boss bar tuning, `/event leave` during lobby edge cases)
- **CTF follow-ups:** **Shipped:** ground-flag pickup (`Item` + PDC `ctf_ground_flag`, `[CtfGroundFlagListener](src/main/java/com/darkniightz/core/eventmode/CtfGroundFlagListener.java)`). **Next:** party-aware `TeamEngine`, HC-CTF rules, nametag colours *(kits + strip/restore: `ctf.red_kit` / `ctf.blue_kit`, `[CtfKitUtil](src/main/java/com/darkniightz/core/eventmode/CtfKitUtil.java)`; KOTH unc. leader in `[KothHandler](src/main/java/com/darkniightz/core/eventmode/handler/KothHandler.java)`)*
- Blood Champion banner + exclusive HC win cosmetics (§H2)

### Parkour vs KOTH — separate modes (design)

These are **different event products**, not one mode replacing the other. Implementation should prefer a dedicated parkour kind/handler (or race session) rather than overloading today’s hill logic in `[KothHandler](src/main/java/com/darkniightz/core/eventmode/handler/KothHandler.java)`; arena shape and spawns stay in `[ArenaConfig](src/main/java/com/darkniightz/core/eventmode/ArenaConfig.java)` / registry as they evolve.

**Parkour race (friendly)**

- Mass start from a shared line; checkpointed track with explicit **start** and **end** markers.
- Optional: clear inventory before join; **PvP disabled** for the whole round.
- Participants **vanished from each other** during the run so placement is unknown until finish (spectators/staff visibility TBD).

**KOTH (hill control)**

- Setup: staff configure **player spawns** around the hill; `**pos1` / `pos2`** bound a hill region/volume (current registry patterns apply).
- **Win metric (implemented):** `[KothHandler.onTick](src/main/java/com/darkniightz/core/eventmode/handler/KothHandler.java)` awards +1s per second only when **exactly one** active participant is inside the hill cuboid; two or more on the hill = contested (no credit that second). Timer expiry: highest uncontested total wins; ties split cosmetic coins and HC loot per `[EventEngine](src/main/java/com/darkniightz/core/eventmode/EventEngine.java)`.
- Players **bring inventory** into the match.
- **Normal mode:** respawn flows end with rewards; **no permanent item loss** for participants.
- **Hardcore mode:** respawn remains inside the event; same uncontested-time metric decides the winner. **If top players tie** on that metric, **split the HC loot pool equally** among tied leaders.

---

### Goal

Full clean-room rewrite of the events system. The current `EventModeManager` is a single 600-line god-class that conflates arena config, participant tracking, inventory snapshots, HC loot, spectator state, KOTH scoring, and elimination logic into one synchronized blob. Every new event type makes it worse.

The rewrite introduces a proper layered architecture: a thin `EventEngine` coordinator, per-kind `EventHandler` strategy classes, a clean arena registry, a team engine, and a lobby countdown system — while keeping all external surfaces stable so stats, commands, the scoreboard, and the web panel require zero changes.

### External surfaces that must NOT break


| Surface                   | What it reads                                                                                                                                         | Contract to preserve                            |
| ------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------- |
| `StatsTrackingListener`   | `PlayerDeathEvent`, kills/deaths — no event-specific coupling                                                                                         | No change needed                                |
| `ServerScoreboardManager` | `EventModeManager.getStatusLine()`                                                                                                                    | Keep this method, update its impl               |
| `/event` command          | `EventModeManager` public API                                                                                                                         | Keep same command interface                     |
| `GraveListener`           | `EventModeManager.isParticipant(player)`                                                                                                              | Keep this method                                |
| `EventModeCombatListener` | `isParticipant`, `handleParticipantDeath`, `shouldKeepInventoryOnDeath`, `isParticipantInHardcore`, `collectHardcoreLoot`, `handleParticipantRespawn` | Preserve all, delegate to new engine internally |
| Web panel                 | No direct event DB table — reads `player_stats.event_wins`                                                                                            | No schema change needed                         |
| `AuditLogService`         | Logs event start/stop/winner                                                                                                                          | Preserve audit calls                            |


---

### Architecture

```
eventmode/
  EventEngine.java            ← replaces EventModeManager as the brain
  EventState.java             ← enum: IDLE | OPEN | LOBBY_COUNTDOWN | RUNNING | ENDING
  EventKind.java              ← enum: FFA, KOTH, DUELS, HARDCORE_FFA, HC_DUELS, HC_KOTH, CTF (new)
  EventSpec.java              ← record: kind, arenaKey, displayName, minPlayers, maxPlayers, coinReward, xpReward, itemReward?
  EventSession.java           ← live session state: participants, teams, snapshots, spectators, score
  EventArenaRegistry.java     ← loads arena configs from config.yml; maps EventKind → ArenaConfig
  ArenaConfig.java            ← record: spawnPoints[], teamSpawns[][], flagLocations[], hillRegion, etc.
  team/
    TeamEngine.java           ← auto-balance + party-cohesion algorithm; assigns participants → Team
    Team.java                 ← record: id, color, members, score
  handler/
    EventHandler.java         ← interface: onStart, onDeath, onRespawn, onTick, onEnd, getScoreboardLines
    FfaHandler.java           ← FFA and HC_FFA logic
    KothHandler.java          ← KOTH timer, hill capture logic, HC_KOTH variant
    DuelsHandler.java         ← 1v1 bracket, HC_DUELS variant
    CtfHandler.java           ← Capture the Flag: flag pickup, carry, score, drop-on-death
  EventCommand.java           ← /event — unchanged interface
  EventCommandTabCompleter.java
```

`EventModeManager.java` becomes a thin façade that delegates every call to `EventEngine` — this preserves all existing references across the codebase without a mass refactor.

---

### Event lifecycle (new)

```
IDLE
  └─ /event open <kind> [arena]   → OPEN      (staff only)
       │  Queue is visible. /event join works.
       │  If minPlayers already met when opened → immediately enter LOBBY_COUNTDOWN.
       └─ player joins → broadcast count
            └─ minPlayers met → LOBBY_COUNTDOWN (30s default, configurable)
                  │  Boss bar countdown visible to all event participants.
                  │  /event join still accepted up to maxPlayers.
                  │  Staff can /event forcestart to skip countdown.
                  └─ countdown expires → RUNNING
                        │  EventHandler.onStart() called.
                        │  Teams assigned (if team event).
                        │  Players teleported to arena spawn points.
                        │  Inventory snapshots taken.
                        └─ win condition met → ENDING
                              │  EventHandler.onEnd() called.
                              │  Rewards distributed.
                              │  Snapshots restored, players teleported home.
                              └─ IDLE
```

Staff can still force-stop at any time with `/event stop` (graceful cleanup — restores inventories).

---

### Arena config (`config.yml`)

Each event kind maps to one arena config. Multiple arenas per kind are supported (staff picks, or the engine rotates).

```yaml
events:
  lobby_countdown_seconds: 30
  arenas:
    ffa:
      - key: ffa_main
        display: "§cBloody Plains"
        spawns:
          - world: event world: 100,64,100
          - world: event_world: -100,64,-100
          # ... up to maxPlayers spawn points
    koth:
      - key: koth_arena
        display: "§6The Fortress"
        spawns: [...]
        hill:
          world: event_world
          min: 10,63,10
          max: 20,67,20
        capture_seconds: 120
    ctf:
      - key: ctf_arena
        display: "§bFlag Wars"
        red_spawn: [...]
        blue_spawn: [...]
        red_flag: world: event_world: 50,65,50
        blue_flag: world: event_world: -50,65,-50
        flag_return_seconds: 30
    duels:
      - key: duels_arena
        spawns_1v1:
          - [100,64,0, -100,64,0]  # pair of spawn points per duel
    hardcore_ffa:
      # reuses ffa_main by default unless overridden
      inherit: ffa_main
    hardcore_koth:
      inherit: koth_arena
    hardcore_duels:
      inherit: duels_arena
```

---

### Teams (auto-balance + party-aware)

Applies to CTF and future TDM-style events. Algorithm:

1. Collect all queued participants.
2. Group by party — parties stay together.
3. Sort parties descending by size. Assign each party to the smallest team so far (greedy pack).
4. Remaining solo players fill the smaller team first.
5. Resulting teams are named by colour (Red / Blue) and stored in `EventSession.teams`.

Team members get coloured nametags (NameTagVisibility per team) and cannot damage teammates unless friendly fire is toggled on by staff.

---

### Capture the Flag — design


| Element       | Detail                                                                                                                    |
| ------------- | ------------------------------------------------------------------------------------------------------------------------- |
| Flags         | 2 wool blocks (Red = RED_WOOL, Blue = BLUE_WOOL) placed at config positions on arena load                                 |
| Pickup        | `PlayerInteractEvent` on flag block → pick up if enemy team, carry as item in hotbar slot 0                               |
| Carry         | Carrier gets a Slowness I debuff + their name highlighted in scoreboard                                                   |
| Capture       | Walk into own flag base region while holding enemy flag item → score point, return flags                                  |
| Drop on death | `handleParticipantDeath` → drop flag item at death location, re-place if not picked up within 30s (`flag_return_seconds`) |
| Win condition | First team to `ctf.win_score` captures (default 3); or most captures when time limit expires                              |
| Time limit    | Configurable (default 10 minutes) — `KothHandler`-style boss bar countdown                                                |


---

### Live scoreboard + boss bar

**Sidebar (all event types):**

```
§d§lEVENT §8» §fBloody Plains
§8————————————
§fYou:  §c3 kills  §70 deaths
§8————————————
§7Top:
§e1. PlayerA §8— §c7
§e2. PlayerB §8— §c5
§e3. You     §8— §c3
§8————————————
§7Status: §aRunning
```

**KOTH / CTF boss bar:**

- KOTH: `§6PlayerA §7is holding §8| §f87s §7remaining`
- CTF: `§cRed §72  §8vs  §92  §bBlue §8| §f8:34 §7remaining`
- HC: `§c§lHARDCORE — §f5 §7players remain`

Boss bar is updated every second via a `BukkitRunnable`. Clears when event ends.

---

### Rewards


| Place                                 | Coins                  | XP                   | Item reward                                                                                                 |
| ------------------------------------- | ---------------------- | -------------------- | ----------------------------------------------------------------------------------------------------------- |
| 1st (winner / winning team)           | `coinReward` from spec | `xpReward` from spec | Optional — staff sets `itemReward` item when opening the event. Stored as Base64 NBT string in `EventSpec`. |
| 2nd (if FFA / CTF)                    | 50% of coinReward      | 50% of xpReward      | None                                                                                                        |
| 3rd                                   | 25% of coinReward      | 25% of xpReward      | None                                                                                                        |
| Participation (joined but didn't win) | 20 coins               | 200 XP               | None                                                                                                        |


Rewards are distributed in `finalizeEvent()` which already fires `AuditLogService.log()`. XP is raw Bukkit XP (`player.giveExp(xpReward)`).

For HC events the winner also gets auto-unlocked cosmetics (Blood Champion banner — deferred to §H2 Exclusive Event Skins feature).

---

### Spectator mode (polish)

Eliminated players in non-HC events:

- Set to `GameMode.SPECTATOR` immediately on respawn (already implemented).
- `player.spectatorTarget` not forced — player can fly around the arena freely.
- Cannot interact with any blocks or entities (SPECTATOR enforces this).
- On event end → restored to pre-event inventory + teleported to their return location.
- No spectator chat channel (silent spectate only, as decided).

Non-participant spectators (players not in the event who want to watch):

- `/event spectate` — teleports to arena as SPECTATOR. Added to a `spectatorVisitors` set in `EventSession`.
- Restored to their original location + gamemode on `/event leave` or event end.
- Gated at `helper+` (any staff can spectate; players cannot).

---

### Commands (unchanged interface, extended)


| Command                                | Rank   | Change                                                                                         |
| -------------------------------------- | ------ | ---------------------------------------------------------------------------------------------- |
| `/event open <kind> [arena]`           | Admin  | Extended: optional arena key; if omitted uses first configured arena for that kind             |
| `/event join`                          | All    | No change in UX — countdown starts automatically when minPlayers met                           |
| `/event join confirm`                  | All    | HC warning confirmation — bypass rate-limiter already done                                     |
| `/event leave`                         | All    | New: works during LOBBY_COUNTDOWN phase too; removes from queue                                |
| `/event start`                         | Admin  | Now renamed `/event forcestart` internally (alias kept); skips countdown if in LOBBY_COUNTDOWN |
| `/event stop`                          | Admin  | No change                                                                                      |
| `/event spectate`                      | Helper | New: teleport to arena as spectator without joining                                            |
| `/event info`                          | All    | New: shows current event kind, arena, participant count, current score/status                  |
| `/event setreward <coins> <xp> [item]` | Admin  | New: override reward for the current open event spec. Item = item in hand                      |
| `/event arenas`                        | Admin  | New: lists all configured arenas for all event kinds                                           |


Tab completion shows arena keys to admins, hides staff-only subcommands from players.

---

### Migration requirements

**V006 — shipped (schema + Java).** `event_sessions` + `event_participants` tables; `friendship_stats.party_time_ms`; runtime wiring in `EventParticipantDAO`, `EventEngine`, `FriendDAO` / `PartyManager`.

Every completed event writes one `event_sessions` row (via `EventEngine.finalizeEvent()`) and one `event_participants` row per player (upserted during the event). This feeds Jebaited Wrapped (favourite event type, total events, event K/D, win streaks) and the web panel events dashboard.

`friendship_stats.party_time_ms` accumulates milliseconds two friends have spent in the same party together. Written when a party disbands or a member leaves. Feeds Wrapped "best party friend" calculation.

---

### Wiring plan (new files)


| File                                       | Action                                                              |
| ------------------------------------------ | ------------------------------------------------------------------- |
| `core/eventmode/EventEngine.java`          | New — central coordinator, replaces god-class logic                 |
| `core/eventmode/EventState.java`           | New — state enum                                                    |
| `core/eventmode/EventSession.java`         | New — live session snapshot                                         |
| `core/eventmode/EventArenaRegistry.java`   | New — reads config.yml arenas block                                 |
| `core/eventmode/ArenaConfig.java`          | New — record                                                        |
| `core/eventmode/team/TeamEngine.java`      | New — auto-balance algorithm                                        |
| `core/eventmode/team/Team.java`            | New — record                                                        |
| `core/eventmode/handler/EventHandler.java` | New — interface                                                     |
| `core/eventmode/handler/FfaHandler.java`   | New                                                                 |
| `core/eventmode/handler/KothHandler.java`  | New                                                                 |
| `core/eventmode/handler/DuelsHandler.java` | New                                                                 |
| `core/eventmode/handler/CtfHandler.java`   | New (CTF)                                                           |
| `core/system/EventModeManager.java`        | Gutted to thin façade; all public methods delegate to `EventEngine` |
| `core/system/EventModeCombatListener.java` | No external change — calls same `EventModeManager` API              |
| `config.yml`                               | Add `events.arenas.`* block                                         |


**EventModeManager stays as the public façade** — this is non-negotiable. Every other system in the plugin calls into it and we are not doing a mass refactor.

---

### Session plan (implementation order)


| Step | What                                                                                              | Risk                                           |
| ---- | ------------------------------------------------------------------------------------------------- | ---------------------------------------------- |
| 1    | `EventEngine`, `EventState`, `EventSession`, `EventSpec` — skeleton with no logic                 | Zero — nothing wired yet                       |
| 2    | `EventArenaRegistry` + `ArenaConfig` — loads from config.yml, logs missing arenas as WARNINGs     | Zero                                           |
| 3    | `EventModeManager` converted to façade — all existing method bodies moved into `EventEngine`      | Medium — must pass all existing tests manually |
| 4    | `EventHandler` interface + `FfaHandler`, `KothHandler`, `DuelsHandler` implementing current logic | Medium — HC variants included here             |
| 5    | Lobby countdown system — `EventState.LOBBY_COUNTDOWN`, boss bar countdown, forcestart             | Low                                            |
| 6    | `TeamEngine` + `Team` — auto-balance with party cohesion                                          | Low (used by CTF only initially)               |
| 7    | `CtfHandler` — flag placement, pickup, carry, capture, return-on-death                            | High (new logic)                               |
| 8    | Live scoreboard lines — `EventHandler.getScoreboardLines()` fed into `ServerScoreboardManager`    | Low                                            |
| 9    | `/event spectate`, `/event info`, `/event setreward`, `/event arenas` commands                    | Low                                            |
| 10   | Reward: XP + optional item via `setreward`                                                        | Low                                            |
| 11   | Arena spawn teleport on event start (replace current hardcoded warp)                              | Medium                                         |
| 12   | Full manual test cycle on all existing event kinds before CTF                                     | Critical                                       |


---

## 22. Network Overhaul (Full Velocity Network)

> **Status:** 🧱 Scaffold done (P23). Full overhaul is deferred until the Velocity proxy is provisioned and both servers are ready. **Do not enable `network.enabled: true` until the overhaul session.**

### Goal

Convert JebaitedCore into a true multi-server Velocity network plugin while keeping a **single JAR** deployed on every backend server.

- **1× HUB** — cosmetics lounge, hotbar navigator, toybox, preview pedestal, NPC quests, player shops, community area
- **1× SMP** — survival worlds (`smp`, `smp_nether`, `smp_the_end`), events, graves, RTP, combat, etc.
- Future servers (`creative`, `pvp`, `minigames`, …) added easily with a new `server_type` value

**Global via shared PostgreSQL:** ranks, stats, economy, cosmetics ownership, friends, parties, achievements, graves, private vaults — all network-aware, all in the same DB.

---

### What is already done (P23 scaffold)


| File                              | What was added                                                                                                                                            |
| --------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `core/system/ServerType.java`     | Enum: `HUB`, `SMP`, `CREATIVE`, `PVP`, `MINIGAMES`, `UNKNOWN`. Helpers: `isHub()`, `isSmp()`, `fromConfig(String)`                                        |
| `core/system/NetworkManager.java` | Stub singleton — reads `network.`* config, exposes `isHub()`, `isSmp()`, `getServerId()`, `getServerName()`, `isNetworkEnabled()`. No feature gating yet. |
| `config.yml`                      | `network:` block added (disabled by default). `server_type`, `server_id`, `server_name`, `redis.*` keys present but inert until the overhaul.             |
| `JebaitedCore.java`               | `NetworkManager.init(getConfig())` called early in `onEnable`                                                                                             |


The scaffold means every future session can call `NetworkManager.getInstance().isHub()` without building infrastructure mid-feature. The full gating, Redis, cross-server commands, and V016 migration all land together in the overhaul session.

---

### Full overhaul scope (all done together when Velocity is ready)

#### Feature behaviour by server type


| Feature                     | HUB | SMP | Other |
| --------------------------- | --- | --- | ----- |
| Cosmetics lounge / wardrobe | ✅   | ❌   | ❌     |
| Hotbar navigator            | ✅   | ❌   | ❌     |
| Toybox / preview pedestal   | ✅   | ❌   | ❌     |
| NPC quests / player shops   | ✅   | ❌   | ❌     |
| Events system               | ❌   | ✅   | ❌     |
| Graves / RTP / combat tag   | ❌   | ✅   | ❌     |
| Homes / warps               | ❌   | ✅   | ❌     |
| Friends (core)              | ✅   | ✅   | ✅     |
| Party (core)                | ✅   | ✅   | ✅     |
| Achievements                | ✅   | ✅   | ✅     |
| Economy / private vaults    | ✅   | ✅   | ✅     |
| Ranks / permissions         | ✅   | ✅   | ✅     |


Commands on the wrong server return: `§cThis command is only available on the Hub server.` (or `…SMP server.` as appropriate).

#### New components added during the overhaul


| Component                                                            | Purpose                                                                                                                                |
| -------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| Feature gating in every hub/smp-only manager, listener, command, GUI | Calls `NetworkManager.getInstance().isHub()` / `isSmp()` — one line per gate                                                           |
| Updated `/hub`, `/smp` commands                                      | Real Velocity server-switch when `network.enabled=true`; fall-through to world routing when disabled                                   |
| Friends: server location + "Join Server" button                      | Cross-server Velocity send; shows friend's current server name in friends list                                                         |
| `V016__server_instances.sql`                                         | New table: `server_instances(id, server_type, server_id, server_name, player_count, max_players, last_heartbeat_at)`                   |
| Redis integration (optional)                                         | Real-time friends online status, party invites, cross-server events. DB-only fallback included. Config toggle: `network.redis.enabled` |
| `PanelConnectorService` network push                                 | Heartbeat every 30s to `server_instances`, pushes `player_count`. Panel `/admin/network` reads this.                                   |
| Full Velocity plugin message channel `jebaited:network`              | Server-switch, cross-server party invites, real-time online status                                                                     |


#### Config additions (added together during overhaul)

```yaml
feature_flags:
  server_mode:
    hub:
      cosmetics: true
      hotbar_navigator: true
      toybox: true
      preview_pedestal: true
      npcs: true
      player_shops: true
    smp:
      events: true
      graves: true
      rtp: true
      combat_tag: true
      homes: true
      warps: true
```

#### V016 migration (added during overhaul)

```sql
CREATE TABLE IF NOT EXISTS server_instances (
    id SERIAL PRIMARY KEY,
    server_id   VARCHAR(64) NOT NULL UNIQUE,
    server_type VARCHAR(32) NOT NULL,
    server_name VARCHAR(64) NOT NULL,
    player_count INT NOT NULL DEFAULT 0,
    max_players  INT NOT NULL DEFAULT 0,
    online       BOOLEAN NOT NULL DEFAULT FALSE,
    last_heartbeat_at BIGINT DEFAULT NULL
);
```

---

### Backwards compatibility

- Until `network.enabled: true` is set, **nothing changes** from current single-server behaviour.
- `network.enabled: false` (default) → all feature gates pass through, `/hub` and `/smp` continue world-routing as today.
- V016 migration is idempotent (`CREATE TABLE IF NOT EXISTS`).
- No existing features (friends, parties, achievements, graves, etc.) break — they become network-aware only when the flag flips.

---

### Panel surface

```
[PANEL SURFACE]
Feature: Network Dashboard
DB tables added: server_instances
Suggested panel page: /admin/network
Data shape: array of { id, server_type, server_name, player_count, max_players, online, last_heartbeat_at }
```

---

### Implementation order (for the overhaul session)


| Step | What                                                                                                     | Risk                     |
| ---- | -------------------------------------------------------------------------------------------------------- | ------------------------ |
| 1    | Set `network.enabled: true` in config + provision Velocity                                               | Zero code risk           |
| 2    | `V016__server_instances.sql` migration + `PanelConnectorService` heartbeat                               | Low                      |
| 3    | Feature gating in hub-only managers/listeners (cosmetics, hotbar, toybox, pedestal, NPC, player shops)   | Low — one-line gate each |
| 4    | Feature gating in smp-only managers/listeners (events, graves, RTP, combat tag, homes, warps)            | Low                      |
| 5    | `feature_flags` config block parsed in `NetworkManager` for per-feature overrides                        | Low                      |
| 6    | Update `/hub` + `/smp` commands to send Velocity plugin message when `isNetworkEnabled()`                | Medium                   |
| 7    | Friends: add server location column read + "Join Server" ClickEvent in `FriendsMenu` / `FriendChatUI`    | Medium                   |
| 8    | Redis integration (if enabled): real-time online status, cross-server party invites                      | High                     |
| 9    | Full manual test cycle: hub features absent on SMP, SMP features absent on hub, cross-server friend join | Critical                 |


---

## 23. Discord Platform (Bot + Plugin + Panel contracts)

### Goal

Ship a first-party Discord platform that feels native to Jebaited: identity linking, live network status, event announcements, moderation sync, and staff bridge controls with strict auditability.

### Session A status (in progress)

- Added `bot-service` module scaffold (JDA + HikariCP + Redis + webhook security primitives + health endpoints).
- Added `V008__discord_platform_core.sql` with `discord_links`, `discord_link_codes`, `integration_audit_log`, `webhook_nonces`.
- Added config scaffolding in plugin `config.yml` under `integrations.discord`, `integrations.redis`, `integrations.webhooks`.
- Added tablist config scaffold under `scoreboard.tablist` for the rotating footer manager follow-up implementation.
- Added Docker orchestration for local dev: `discord-bot` service + `redis` in `docker-compose.yml`; bot container reads env overrides and can run in no-token local standby mode.
- Phase B kickoff: `/link` command and `DiscordLinkService` now issue one-time codes backed by `discord_link_codes`.
- Phase B progression: bot slash `/link <code>` now consumes pending codes, marks consumed rows, writes active `discord_links`, and runs first-pass rank-to-role sync from `bot-config.yml` `discord.role_ids`.
- Plugin `DiscordIntegrationService` posts signed JSON to bot `/webhooks/panel` for moderation + event start; bot `DiscordIntegrationDispatchService` mirrors to `#mod-logs` / announcements; Redis subscriber runs the same dispatch for cross-service pub/sub.
- Phase C: bot `/status` shows registered players, active Discord links, overall_stats aggregates, summed playtime hours; plugin posts **event end / KOTH tie / staff cancel** (`event.announce` phases `end` | `tie` | `cancelled`); opt-in **chat relay** (global / staff / faction out) with rate limits; bot **presence** line refreshes from DB; HTTP webhooks that pass verify insert **`integration_audit_log`** rows (type + correlation + payload).
- Phase D+: **Plugin inbound** `DiscordInboundHttpService` on configurable bind (`integrations.discord.inbound.*`, Bearer `api_token`); **Discord→MC** via `PluginBridgeClient` + `DiscordGatewayListener` (per-channel bridge + console `>` commands with `console_developer_role_id`); **MC→Discord** extended payload `bridge` on `relay.chat`; **`console.line`** type for log mirror; **`V009__discord_activity_and_bridge.sql`** `discord_activity_sample` for `/activity`; slash **`/ping`**, **`/server`**, **`/player`**, **`/activity`** + **`StatusEmbedUpdater`** channel message.

### Milestones


| Phase | Scope                                                                                         | Status      |
| ----- | --------------------------------------------------------------------------------------------- | ----------- |
| A     | Infrastructure: bot bootstrap, DB/Redis wiring, signed webhook verification, health endpoints | Shipped     |
| B     | `/link` one-time code flow + rank-to-role sync                                                | Shipped (plugin `/link` + bot slash + `DiscordIntegrationService` webhook signing) |
| C     | `/status` + event lifecycle + multi-bridge relay + presence + webhook audit + monitoring slash + activity chart | Shipped (**tablist live Discord member count** optional follow-up) |
| D     | Moderation mirror; bidirectional staff/global/faction bridge; console mirror + dev remote cmds | Shipped (plugin inbound HTTP + bot gateway listener; payments **not** included) |
| E     | Challenges / bounties / vote bridge integrations                                              | Planned     |
| F     | Store checkout orchestration (payments deferred until core is stable)                         | Planned     |


### Security invariants

- All cross-service calls are signed (`HMAC-SHA256`) with timestamp and nonce replay protection.
- Privileged Discord bridge actions fail closed unless explicitly allowlisted.
- Every integration action writes an audit row with correlation ID in `integration_audit_log`.
- Idempotency keys are required for all grant/mod side effects (follow-up DAO wiring in Phase B/D).

### Panel surface

```
[PANEL SURFACE]
Feature: Discord Platform Control Surface
DB tables added: discord_links, discord_link_codes, integration_audit_log, webhook_nonces, discord_activity_sample (V009)
Suggested panel pages/endpoints: /admin/integrations/discord, /api/integrations/discord/link-codes, /api/integrations/events
Data shape: link records, pending code stats, audit timeline entries with correlation_id and source_service
```

