---
name: grafter
description: Maintains and evolves the JebaitedCore plugin with roadmap-aware wiring, migrations, and cleanup discipline. Use when adding or editing commands, features, stats, events, DB schema, Settings/Debug surfaces, or when reviewing dead code and roadmap progress.
---

# Grafter

Grafter is the project skill for building and maintaining `JebaitedCore` end-to-end.

## Source of truth

- Treat `ROADMAP.md` as the authoritative project state and intent.
- **ROADMAP hygiene (same session as the code change):** When you ship or materially change a feature, update `ROADMAP.md` in the same pass — at minimum: the **top blurb** if focus or shipped surface changes, **Feature Showcase** (player/staff tables), **Upcoming Features** (remove or rewrite rows that are no longer “planned” the same way), **Feature Index** status column for the relevant §, and the **detailed §** (files, wiring, implementation status). Ending a multi-file session: refresh the copy-paste block in [`SESSION_HANDOFF.md`](../../../SESSION_HANDOFF.md) (repo root).
- **mcMMO upgrades:** On each new mcMMO release, re-run the staging checklist in ROADMAP §14 (`/compat` bridge line + `mcmmo_level` + optional `integrations.mcmmo.bridge_self_test`), diff upstream `plugin.yml` commands against the inventory table, and extend `MCMMO_OWNED_COMMANDS` / wrappers only when adding newly overlapping labels (today: `party`, `pa`, `p`, `inspect` + aliases, `mcrank`, `mcstats`, `mctop`).
- If docs conflict with `ROADMAP.md`, follow `ROADMAP.md` and update stale docs.
- Verify reality from code before making roadmap claims.

## Web admin panel — out of scope

- **Do not edit the web-panel codebase** (Node/Express `web-admin` or any repo outside **this** plugin). Grafter work is **JebaitedCore only**.
- When a feature needs panel UI, HTTP APIs, auth, or live relay beyond what the plugin already does (`PanelConnectorService`, shared DB, etc.), **document the contract** in `ROADMAP.md` (tables, JSON shape, suggested routes) and **tell Jamie** to implement or wire the web side — do not open or patch the panel project yourself.

## Core operating rules

1. Complete full wiring for any feature change in one pass.
2. Keep behavior safe for live servers (no risky assumptions, no silent breakage).
3. Prefer removing dead paths only when confidence is high and usage is verified.
4. Keep command and permission surfaces consistent across code and config.
5. Treat **Settings** and **Debug** as first-class surfaces: every feature or fix pass should answer whether either needs an update (see below).

## Player and developer surfaces

Review these on every meaningful change—not only when wiring commands.

### Settings surface (player)

- If the change adds or changes **per-player behavior** that players should control: use or extend `SettingKey` and the category registry, wire into the correct **Settings** category (`SettingsMenu` / `SettingsCategoryMenu`), and persist via existing profile/preferences paths. Do not add ad-hoc toggles without a `SettingKey` unless Jamie explicitly wants a non-settings exception.
- If a **new command** is mainly for settings: still satisfy the command checklist below (permissions, `CommandSecurityListener`, tab completion).

### Debug cockpit surface (developer / ops)

`/debug` opens `DebugMenu` when devmode is on; rank gate is in `CommandSecurityListener`. Wire diagnostics here the same way you wire player toggles into Settings.

- **Menu rows:** If the work adds a **staff/dev command**, **diagnostic**, or **repeatable admin action** other devs will want from the cockpit, add a row in `DebugMenu` in the matching section (follow existing grouping: commands, listeners, events, etc.).
- **Live feed:** If the feature emits **high-signal, frequent, or failure-prone** activity worth watching in-game, record lines with `DebugFeedManager` using the closest existing `Category` (`SYSTEM`, `COMMAND`, `LISTENER`, `JOIN`, `MODERATION`, `COSMETIC`, `GADGET`, `PREVIEW`, `EVENT`). Add a new `Category` only when nothing fits **and** the feed filter UI must distinguish it—then update `DebugMenu` feed filter controls to match.
- **State:** If the feature needs **dev-only toggles** surfaced in the cockpit, prefer extending `DebugStateManager` (or existing `DebugMenu` patterns) instead of scattered static flags.

**Intentional skip:** If Debug would be noisy or pointless for this change, say so in the commit/PR (e.g. internal refactor only) so skipping is explicit, not forgotten.

## Command change checklist

When adding or changing a command, verify all of:

1. Command class implementation exists in `src/main/java`.
2. Entry is present in `src/main/resources/plugin.yml` with usage/aliases.
3. Binding exists in `JebaitedCore.registerCommands()`.
4. Permission constant exists in `PermissionConstants`.
5. Security/rank gate is enforced in `CommandSecurityListener` where required.
6. Tab completion is implemented and returns filtered lists (never `null`).

## Data and migration checklist

For any persistent feature/stat/schema change:

1. Add a new SQL migration in `src/main/resources/db/`.
2. Append migration filename to `migrations.index` in order.
3. Update DAO read/write paths.
4. Update tracking/listeners that produce the data.
5. Update UI/leaderboard surfaces that display the data.

## Dead code and redundancy policy

Before deleting:

1. Check command/listener wiring in `JebaitedCore`.
2. Check `plugin.yml` declarations and aliases.
3. Check constructor side-effects (self-registered listeners/tasks).
4. Search references across Java and resources.

Classify findings as:

- **High confidence remove**: unreachable and unreferenced.
- **Medium confidence cleanup**: redundant alias wiring, duplicate paths, stale docs.
- **Do not remove**: low-signal classes used indirectly or via runtime hooks.

## Response format for audits

When asked to audit the repo:

1. List high-confidence unused files/classes.
2. List medium-confidence cleanup opportunities.
3. List upgrade opportunities (API/tooling/quality checks).
4. List risky deletions to avoid.

Each finding must include concrete file paths.

## Scope discipline

- **Execution:** Apply `.env`, `docker-compose.yml`, and config edits in the workspace yourself; follow [`.cursor/rules/jebaited-execution.mdc`](../../rules/jebaited-execution.mdc). Do not offload trivial "add this line" steps to Jamie unless a secret is not in the repo.
- Keep changes focused on requested outcomes.
- Do not rewrite large systems unless explicitly requested.
- After edits, run available lint/build checks relevant to touched files.
- **Web panel:** never in scope unless Jamie explicitly pastes panel files into the chat or asks for a panel change in this workspace — default is plugin + DB migrations + `ROADMAP.md` handoff notes only.
