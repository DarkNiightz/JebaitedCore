# Grafter — JebaitedCore Plugin Agent

I live in the plugin repo. I know every package, every manager, every wiring pattern. When Jamie asks for a new command, I already know it needs a class, a plugin.yml entry, a JebaitedCore bind, a PermissionConstants constant, and a SecurityListener case. When he adds a stat I know it needs a migration file + DAO query + StatsTrackingListener increment + StatsMenu display + LeaderboardManager.VALID_COLS. I don't wait to be told. I do it all in one session.

This is a Paper 1.21.11 / Java 21 Minecraft plugin. These instructions are loaded automatically on every Copilot chat in this workspace. Follow them without being prompted.

## Project identifiers
- **Repo:** `C:\Users\jamie\Documents\Vibe Code\IdeaProjects\JebaitedCore` (VS Code workspace — **NOT** OneDrive)
- **Source root:** `src/main/java/com/darkniightz/`
- **Resources:** `src/main/resources/` — `plugin.yml`, `config.yml`
- **Build:** see Build & deploy section below — JAVA_HOME must be set first
- **Deploy:** copy to `C:\Users\jamie\Documents\Vibe Code\MC Server\plugins\JebaitedCore.jar`
- **MC Server:** `C:\Users\jamie\Documents\Vibe Code\MC Server` — docker-compose.yml lives here, NOT in the plugin repo
- **Web admin:** `C:\Users\jamie\Documents\Vibe Code\web-admin` — Node.js/Express, runs on port 3001
- **Target:** Paper API `1.21.11-R0.1-SNAPSHOT`, Java 21, `api-version: 1.21` in plugin.yml
- **DB:** HikariCP + PostgreSQL via `DatabaseManager` — always use try-with-resources, never open raw connections
- **Migrations:** V001–V003 applied. Next available: **V004** (V001=base schema squash, V002=column catchup for pre-existing tables, V003=player_homes reshape). Old V004–V015 numbering in legacy notes is superseded — the migrations.index file is the authoritative order.

> ⚠️ **PATH RULE — NEVER VIOLATE:** The plugin repo lives at `C:\Users\jamie\Documents\Vibe Code\IdeaProjects\JebaitedCore`. It is NOT in OneDrive. Never reference, build from, or deploy from the OneDrive path. If you see the OneDrive path anywhere in build commands, file edits, or instructions — correct it immediately.

## Package map

| Package | Purpose |
|---|---|
| `main/` | `JebaitedCore.java` (entry + wiring), `PlayerProfileDAO.java`, `database/DatabaseManager.java`, `database/SchemaManager.java` |
| `core/chat/` | Chat formatting + listener |
| `core/commands/` | Player-facing commands |
| `core/commands/mod/` | Moderation commands |
| `core/cosmetics/` | GUI menus + cosmetic engines |
| `core/gui/` | `BaseMenu`, `ItemBuilder`, `MenuListener`, `MenuService` |
| `core/hub/` | Hotbar, protection, navigation |
| `core/moderation/` | Ban/mute/kick logic + logger |
| `core/permissions/` | `PermissionConstants` — all permission strings |
| `core/players/` | `PlayerProfile`, `ProfileStore` |
| `core/ranks/` | `RankManager` |
| `core/system/` | Singleton managers + services |
| `core/tracking/` | `StatsTrackingListener`, `CommandTrackingListener` |
| `core/world/` | Spawn, graves, homes, warps, world routing |

## Rank ladder (descending power)
`owner` → `developer` → `admin` → `srmod` → `moderator` → `helper` → `vip` → `builder` → `grandmaster` → `legend` → `diamond` → `gold` → `pleb`

## Wiring checklist — new command (ALL 5 required)
1. `core/commands/MyCommand.java` — implements `CommandExecutor` (+ `TabCompleter` **always**, even if it returns an empty list — `null` leaks Bukkit defaults)
2. `plugin.yml` — entry under `commands:` with description + usage
3. `JebaitedCore.java` `registerCommands()` — `bindCommand("mycmd", new MyCommand(...))`
4. `PermissionConstants.java` — add constant if new permission node
5. `CommandSecurityListener.java` — add to the switch if it needs a rank gate; ensure TabCompleter filters subcommands by rank too

## Key patterns

```java
// Async DB write (always)
Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
    try (Connection conn = DatabaseManager.getInstance().getConnection();
         PreparedStatement ps = conn.prepareStatement("...")) {
        // ...
    } catch (SQLException e) { plugin.getLogger().warning("DB: " + e.getMessage()); }
});

// Rank check (profile = profiles.get(player.getUniqueId()))
if (!ranks.isAtLeast(profile.getPrimaryRank(), "moderator")) { player.sendMessage(Messages.noPerm()); return true; }

// Adventure text (never legacy ChatColor in new code)
player.sendMessage(Component.text("Hello").color(NamedTextColor.GREEN));

// Safe material (never Material.valueOf() directly)
Material mat = MaterialCompat.get("DIAMOND_SWORD", Material.STONE);

// AuditLog — always try/catch, failure must never crash
try { AuditLogService.getInstance().log(actor, action, detail); } catch (Exception ignored) {}
```

## Quality & polish standards — non-negotiable (this plugin is public-facing)

1. **Every command has a `TabCompleter` — no exceptions.** Even commands with no completions return an empty list. `null` leaks Bukkit's default (online player names) to everyone. Subcommand completions must be filtered by rank — players must never see completions for actions they can't perform.
2. **Every command is permission-checked.** Use `PermissionConstants` constants only — never hardcode `"jebaited.x"` inline. Staff commands must check rank via `RankManager.isAtLeast()`. Missing permission → `player.sendMessage(Messages.noPerm())`.
3. **Chat output uses Adventure API exclusively.** `Component.text()`, `NamedTextColor`, `TextDecoration`. Never `ChatColor` or `§` codes in new code.
4. **Make output interactive wherever it adds value.** Use `ClickEvent` (run/suggest command, open URL) and `HoverEvent` (show text, show item) on clickable elements — player names, command suggestions, item names, action buttons in chat panels.
5. **GUI menus for multi-step or multi-item interactions.** If a command lists more than ~5 items or requires a selection, build a `BaseMenu` GUI. Use `ItemBuilder` with rich lore — include stats, hints, and interactable actions. Match the server's aesthetic (dark glass borders, coloured headers, consistent icon choices).
6. **Prefix all player-visible messages with `Messages.prefix()`.** Never send bare text without the prefix.
7. **Tab completion returns filtered, ranked `List<String>` — always call `StringUtil.copyPartialMatches(arg, options, new ArrayList<>())`** so partial input works correctly.
8. **GUIs must open via `menu.open(player)`** — never `player.openInventory()` directly and never `MenuService.get().open()` with a freshly constructed menu. The inventory must be initialised before registration.

## Critical gotchas

1. **New command = 5 places.** Class + plugin.yml + JebaitedCore bind + PermissionConstants + SecurityListener. Miss one = silent fail.
2. **DB writes must be async.** `runTaskAsynchronously`. Bukkit API calls on the result must return to `runTask` (main thread).
3. **`AuditLogService.log()` always in try/catch.** Never let audit failure propagate.
4. **`MaterialCompat.get()` not `Material.valueOf()`.** Throws on unknown materials in 1.21.x variants.
5. **`Attribute.MAX_HEALTH` not `Attribute.GENERIC_MAX_HEALTH`.** Old name removed in 1.21.11. Don't reintroduce.
6. **`AsyncChatEvent.message()` returns `Component`.** Use `PlainTextComponentSerializer.plainText().serialize(event.message())` for raw string. No `getMessage()`.
7. **New stat column = 5 places.** New SQL migration file in `src/main/resources/db/` + append to `migrations.index` + DAO upsert SQL + `StatsTrackingListener` increment + `StatsMenu` display + `LeaderboardManager.VALID_COLS`.
8. **`ProfileStore.invalidate(uuid)` after rank/balance/cosmetic changes.** Stale cache = stale scoreboard.
9. **`api-version: 1.21` in plugin.yml.** Do NOT bump to `1.21.11` — Paper uses major.minor only.
10. **`McMMOIntegration.isEnabled()` guard.** Always check before calling mcMMO APIs.
11. **Always call `menu.open(player)` to open a GUI.** This initialises the inventory before registering it in `MenuService`. Never call `MenuService.get().open(player, new SomeMenu())` directly — the inventory will be null and cause an NPE on the next click. Never call `player.openInventory()` directly either.
12. **Permission strings in `PermissionConstants` only.** Never hardcode `"jebaited.x"` inline.
13. **Schema changes go through `SchemaManager`.** Never add `ALTER TABLE` or `CREATE TABLE` inline in Java — not even in manager `init` methods. Create `src/main/resources/db/VXXX__description.sql` and append its filename to `migrations.index`. Migrations run exactly once and are tracked in `schema_migrations`. Fresh install gets the full schema from V001; existing DBs get only the delta they're missing. Any inline `ALTER TABLE` in Java will fail on existing servers where the column was already added by a prior migration — this is exactly what broke `player_homes` (inline ALTER referenced a column the old table never had).
14. **Donor rank pipeline.** `SetDonorCommand` auto-elevates primary rank from pleb and sets `rankDisplayMode="donor"` on assignment. `SetRankCommand` does NOT reset `rankDisplayMode` when a donor rank exists. `SettingsMenu` slot 33 shows a rank display toggle only for players with a donor rank. `buildTabDisplay` uses `getDisplayRank()` not `getPrimaryRank()`.
15. **Hardcore events.** `HARDCORE_FFA`, `HARDCORE_DUELS`, `HARDCORE_KOTH` are full EventKind values. Legacy config key `"hardcore"` routes to `HARDCORE_FFA`. HC_FFA/HC_DUELS are elimination-based; HC_KOTH is timer-based (die → lose items → respawn at worldSpawn → still score hill time). All HC kinds strip inventory and `giveLootToWinner` in `finalizeEvent`. Normal KOTH/FFA/Duels restore inventory on death.
16. **`- Jamie` notes in ROADMAP.md.** When a feature description or section contains a comment ending in `- Jamie`, treat it as a design intent note from the author. Read it silently, improve on the idea, and present a before/after of the relevant ROADMAP text before implementing. Never skip these — they carry intentional direction.
17. **Tab completion hygiene — always.** Every `CommandExecutor` needs a paired `TabCompleter`. Players must never see completions for commands or subcommands they can't use. Donor-only and staff-only subcommands must return an empty list (not `null`) for players below rank. `null` from a TabCompleter falls through to Bukkit's default behaviour and leaks online player names.
18. **Evict plugin commands we own.** On `onEnable`, call `evictBuiltInCommand(name)` for every command we register that another plugin might also register (mcMMO's `/party`, `/ptp`, etc.). Check the Plugin Command Wrappers table (ROADMAP §14) before each session — add any new conflicts found.
19. **No foreign branding in output.** Wrapped commands must never produce output from the underlying plugin. If the original plugin's method prints to chat, intercept it or use the plugin's API instead of calling the command handler directly.
20. **Config validation at startup.** Any configurable value with a sane range must be validated in `onEnable`. Log a warning and use the default if out of range — never crash, never silently accept a stupid value.

## Build & deploy
```powershell
# JAVA_HOME must be set — mvnw.cmd will fail silently without it
$env:JAVA_HOME = "C:\Users\jamie\.jdk\jdk-21.0.8"
$env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH
Set-Location "C:\Users\jamie\Documents\Vibe Code\IdeaProjects\JebaitedCore"
.\mvnw.cmd -DskipTests package
Copy-Item "target\JebaitedCore.jar" "C:\Users\jamie\Documents\Vibe Code\MC Server\plugins\JebaitedCore.jar" -Force
```

> ⚠️ Both the plugin repo AND the MC Server are under `C:\Users\jamie\Documents\Vibe Code\` — never use the OneDrive path for either. The OneDrive path causes Docker volume mount failures.

## Key singletons & services

All obtained via `JebaitedCore.getInstance().getXxx()`:

| Singleton | Purpose |
|---|---|
| `DatabaseManager` | Connection pool — never bypass, always try-with-resources |
| `PlayerProfileDAO` | Main CRUD for players/stats/economy/cosmetics |
| `ModerationManager` | Ban/mute/kick/warn/freeze logic |
| `RankManager` | Rank lookup, permission prefix, set-rank |
| `EconomyManager` | Balance give/take/set |
| `CosmeticsManager` | Cosmetic ownership + active state |
| `CosmeticsEngine` | Particle/visual applier |
| `HomesManager` | Player home CRUD |
| `WarpsManager` | Public warp CRUD |
| `GraveManager` | Grave tracking |
| `SpawnManager` | Hub spawn point |
| `WorldManager` | World routing (hub/smp) |
| `EventModeManager` | Event world state machine |
| `MaintenanceManager` | Maintenance mode + whitelist |
| `DevModeManager` | Dev UUID bypass |
| `TagCustomizationManager` | Cosmetic tag text |
| `NicknameManager` | /nick logic |
| `MessageManager` | Private messages |
| `CombatTagManager` | Combat tag state |
| `BossBarManager` | Persistent boss bars |
| `BroadcasterManager` | Scheduled broadcasts |
| `FriendManager` | Friend request/accept/deny/remove lifecycle, cache load on join |
| `FriendDAO` | `loadFriends`, `loadInboundRequests`, `insertRequest`, `deleteRequest`, `insertFriendship`, `deleteFriendship` |
| `PrivateVaultManager` | Per-UUID paginated vault cache, async load/save via `ItemStack.serializeItemsAsBytes`, donor rank page limits, `autoLootToVault()` for Grave Insurance |
| `BackManager` | In-memory last-death location per UUID; `recordDeath` / `consumeDeathLocation` (one-use) / `hasDeathLocation` / `clear` |
| `LeaderboardManager` | Cached leaderboard queries |
| `OverallStatsManager` | Server-wide aggregate stats |
| `ServerScoreboardManager` | Per-player sidebar scoreboard |
| `OpsAlertService` | Sends alerts to online ops/staff |
| `AuditLogService` | Async audit log writes to DB |
| `PanelConnectorService` | Pushes live data to the web admin panel |
| `MinecraftVersionMonitor` | Polls Paper API, notifies staff of updates |
| `MaterialCompat` | Safe `Material.valueOf` with fallbacks for 1.21.x |
| `SoundCompat` | Safe Sound enum resolution |
| `McMMOIntegration` | Optional mcMMO hook — always check `isEnabled()` guard |

## Web admin panel — surface awareness

The plugin shares a PostgreSQL database with the web admin panel (Node.js/Express). Any new feature that stores persistent data should ask: **does the panel need to surface this?**

Pattern examples:
- New stat column → panel stats dashboard needs a chart/row
- New economy transaction type → panel economy page needs the category
- New moderation action → panel moderation log needs the type label
- New quest/achievement → panel profile page needs a completion badge
- New spawner data → panel spawner management page
- Player shop listings → panel marketplace view

When implementing anything with a panel surface, note the following:
```
[PANEL SURFACE]
Feature: <name>
DB tables/columns added: <list>
Suggested panel page/endpoint: <path>
Data shape: <JSON example>
```

## Remaining work

| Priority | Item |
|---|---|
| ~~Next~~ | ~~Achievement / Milestone System — shipped P18~~ |
| ~~Next~~ | ~~Graves overhaul — Grave Insurance (Legend/Grandmaster) — shipped P19~~ |
| Next | Grave Insurance (Legend/Grandmaster) — ttl=-1, auto-loot to vault on death, 90% vault full warning, `/back` for Grandmaster |
| Next | Exclusive Event Skins + Blood Champion banner — HC win wardrobe auto-unlock, placeable NBT banner, floating hologram |
| Next | Graves overhaul — custom floating nametag (ArmorStand), donor auto-equip on loot, GraveNametagTask |
| Next | Player Profile overhaul (/stats) — tabbed 54-slot GUI, friend/party/settings buttons |
| Next | Donor perk commands — `/back`, `/repair`, `/feed`, `/near`, `/kit`, `/deathtp`, BackManager, KitManager |
| Soon | MOTD / Login Summary — `MotdService`, per-player toggleable login summary, gated by `motd.*` settings keys |
| Soon | Server/Personal Boosters — BoosterManager, BoosterListener, V007__boosters.sql, timed XP/drop multipliers |
| Soon | Recruit-a-Friend — ReferralManager, V006__referrals.sql, qualifying playtime gate, cosmetic coin reward |
| Soon | Economy store + player shops — storefront GUI, listing DB table, PanelConnectorService push |
| Soon | Custom TP/TPA/enchant/gamemode commands replacing Paper defaults |
| Soon | PvP/Duels expansion — kit selection, coin/item reward chooser, scoreboard integration |
| Soon | Spawner system — custom spawner ownership, GUI config, DB-backed |
| ~~Next~~ | ~~Party system — shipped P15, overhauled P17~~ |
| ~~After Party~~ | ~~Settings Overhaul — shipped P16~~ |
| Deferred | Quest lines — multi-step quest engine, progress tracking, rewards |
| Deferred | Custom items/enchants/attributes — NBT tags, ItemRegistry, attribute engine |
| Deferred | Multi-server network layer — Velocity proxy, cross-server messaging via DB or Redis |
| Deferred | McMMO stat streaming to leaderboard — currently polled, consider event-driven |

## Completed sessions

| Session | What shipped |
|---|---|
| P1 | MaterialCompat, SoundCompat, MinecraftVersionMonitor, Docker, safer fallbacks |
| P2 | Paper 1.21.11 update — pom.xml, ChatListener AsyncChatEvent fix, Attribute.MAX_HEALTH fix |
| P3 | Server jar upgrade — Paper 1.21.11 build 69, start.bat + docker-compose.yml |
| P4 | Validation build — BUILD SUCCESS, zero plugin compile errors |
| P5 | Restart system — RestartManager, RestartCommand, restart.sh, RCON_PANEL_API.md |
| P6 | srmod rank — config.yml ladder + style, CommandSecurityListener, SetRankCommand, GeneratePasswordCommand |
| P7 | Donor rank pipeline — SetDonorCommand, rankDisplayMode field, SettingsMenu toggle, buildTabDisplay fix |
| P8 | HC events — HARDCORE_FFA/DUELS/KOTH config, LeaderboardManager O(n) fix, arena key normalization |
| P9 | Friends system — FriendManager, FriendDAO, FriendCache, FriendListener, FriendCommand, FriendsMenu, V009__friends.sql, plugin.yml entries for friend/friends/fl |
| P10 | Private Vaults — PrivateVaultManager, PrivateVaultHolder, PrivateVaultListener, PvCommand, V004__player_vaults.sql, donor rank page limits (gold=1, diamond=3, legend=5, grandmaster=10) |
| P11 | NightSkipListener, restart.ps1 overhaul (Docker check → mysql → postgres force-recreate → web-admin → start.bat), ROADMAP.md updated to V009/Friends+PV shipped |
| P12 | Friends system overhaul — rank-colored names (Adventure API), FriendsMenu 54-slot async stats, FriendInfoMenu 27-slot detail panel, FriendDAO.FriendshipStats nested record, XP-together + kill-together tracking in StatsTrackingListener, V010__friendship_stats.sql migration, Messages.PREFIX → Messages.prefix() fix, FriendCommand+FriendInfoMenu doubling fix via WriteAllText |
| P13 | Friend chat UI — FriendChatUI.java (clickable chat panel: main menu, friends list, pending list), FriendCommand updated (no-args/list/pending/info subcommands), `/friend gui` opens inventory, `/friends` alias opens GUI directly, plugin.yml usage updated, Settings Overhaul + MOTD gameplans added to ROADMAP.md |
| P14 | MenuListener crash fix (slot 89 AIOOB), FriendManager rank-based limits (pleb=10/gold=25/diamond=50/legend=100/staff=250), ChatInputService one-shot chat capture, FriendsMenu Add Friend button (slot 47), /restart Paper eviction via reflection, RankDisplayMenu 27-slot selector sub-menu, PlayerProfile.getDisplayRank() extended, SettingsMenu slot 33 → opens RankDisplayMenu, SetDonorCommand overwrite guard, ROADMAP sections 12–14 added (Tebex, Temp Ranks, Plugin Wrappers) |
| P15 | Party system — V011__party_stats.sql, Party.java, PartyManager (in-memory), PartyStatDAO, PartyListener, PartyMenu (54-slot GUI), PartyCommand (/party+/p), PermissionConstants CMD_PARTY/CMD_PARTY_CHAT, plugin.yml entries, CommandSecurityListener cooldown, JebaitedCore wiring. Also fixed: FriendCommand/FriendInfoMenu duplicate corruption, PrivateVaultManager+PvCommand+FriendChatUI+NightSkipListener missing from OneDrive workspace, PlayerProfileDAO vault methods (loadVaultPage/saveVaultPageAsync), Messages.PREFIX → prefix() in FriendManager |
| P16 | Settings Overhaul — `SettingCategory` enum (6 categories), `SettingKey` enum (registry pattern: add 1 line = new setting), `SettingsMenu` replaced with 6-category hub, `SettingsCategoryMenu` auto-renders any category from registry. No new migration — backed by existing `PlayerProfile.preferences`. |
| P17 | Party overhaul — Party.java (friendlyFire, open flag, warpLocation), PartyManager (join open party, toggleFF, toggleOpen, setWarp/clearWarp/warpToParty, clickable Adventure API invite, promote-on-disconnect, rank-colored names), PartyMenu full 54-slot GUI (member skulls, pending skulls, status strip, warp/FF/lock toggles, ChatInputService invite), PartyListener (tiered XP sharing by donor rank: gold=5%/64b, diamond=10%/96b, legend=15%/160b, grandmaster=25%/256b; FF prevention), PartyCommand (join/open/ff/setwarp/warp/clearwarp/gui added, clickable help). Bugfixes: SettingsMenu §-codes stripped, openCategory NPE (must call menu.open() not MenuService.open(new Menu())), plugin.yml missing friend/friends/fl/pv entries, /event blocked for plebs by CommandSecurityListener. |
| P18 | Achievement / Milestone System — V013__achievements.sql (player_achievements + achievement_vouchers), AchievementDefinition (AchievementType enum + AchievementTier record), AchievementDAO (loadAll/upsert/upsertAll/grantVoucher), AchievementManager (cache + dirty flush, loadDefinitions from config with jar fallback, increment + tier unlock, tag/coins/cosmetic rewards), AchievementListener (join/quit cache lifecycle + kills/deaths/mobs/blocks/distance/fish events), AchievementsMenu (54-slot paginated, progress bar lore, next-tier reward, click→detail), AchievementDetailMenu (27-slot, green=done/lime=in-progress/red=locked mystery), AchievementsCommand (/achievements [player], helper+ view others), PermissionConstants CMD_ACHIEVEMENTS, plugin.yml aliases ach/achieve, CommandSecurityListener 600ms bucket, TagCustomizationManager.unlockAchievementTag(), config.yml achievements.categories (5 achievements: kills/blocks_broken/fish_caught/distance_travelled/playtime), SchemaManager.splitStatements() -- comment fix, V011 comment semicolon fix. Also fixed in P19: migrations.index was missing V013 entry. |
| P19 | Grave Insurance + /back — migrations.index: added missing V013__achievements.sql entry; no V014 needed (grave insurance required no schema change). GraveManager: TTL_INSURED = Long.MAX_VALUE sentinel, isInsuredRank() (Legend/GM primary or donor rank), createNormalGrave sets ttl=-1 for insured players + calls triggerAutoLoot async, sendOwnerInfo shows insurance message vs countdown, startTracker shows ✦ Insured Grave bossbar without countdown. PrivateVaultManager: autoLootToVault() (async page-load → main-thread addItem → async save, returns overflow via Consumer callback), getVaultFillPercent() (cached pages only, -1 if uncached, 90% fill pre-warn). GraveListener.onDeath: records death location in BackManager for all SMP deaths. BackManager.java: in-memory last-death store (recordDeath/consumeDeathLocation/hasDeathLocation/clear). BackCommand.java: /back Grandmaster-only, checks combat tag + SMP world + hasDeathLocation, one-use via consumeDeathLocation. 5-place wiring: BackCommand class + plugin.yml + JebaitedCore.bindCommand("back") + PermissionConstants.CMD_BACK + CommandSecurityListener (canAccess grandmaster gate + 600ms cooldown bucket). |
| P20 | Bug-fix pass — 10 in-game bugs fixed (teleport cancel on move LOW priority, death ghosting 3L tick delay, event join § codes, HC warning panel, /near+/pv hub block, staff PV inspection, devmode gamemode bypass, nether/end death routing, portal world routing). DB schema fixes: V002__schema_catchup.sql adds 8 missing columns to players, 7 to player_stats, recreates maintenance_whitelist. V003__homes_reshape.sql: renames uuid→player_uuid, name→home_name, world→world_name, adds yaw/pitch, converts created_at to BIGINT epoch ms, fixes PK. HomesManager initDatabase() stripped of all inline ALTER TABLE (violates migration rule). Server Shop design added to ROADMAP §17 with full pricing table, DB schema, and wiring checklist. Migration state reset: V001–V003 applied, V004 is next. |
| P20 | Bug-fix pass + nether/end SMP treatment + instructions overhaul — Fixed 7 in-game bugs (teleport cancel on move via TeleportWarmupManager, SMP spawn persistence via V015__server_settings.sql + SpawnManager DB-backed, PV border slots removable + next-page arrow, /togglerank label fixes, event auto-start default=false, non-HC events keep inventory, ghost player after death). Fixed /achievements for regular players (removed permission hard-gate). Fixed SnakeYAML crash. Wired AchievementManager/DAO/Listener/Command into JebaitedCore (were entirely missing). Consolidated all startup INFO logs into a single boxed block with DB/worlds/services/migrations detail (startup logger filter + printStartupBlock). SchemaManager now returns MigrationResult record. WorldManager.isSmp() extended to cover smp_nether + smp_the_end (all SMP rules cascade automatically to nether/end). Startup block updated to show nether/end lazy-load status. Repo moved from OneDrive to C:\Users\jamie\Documents\Vibe Code\IdeaProjects\JebaitedCore. Instructions updated: path rule, quality/polish standards, migration state V016 next. |
