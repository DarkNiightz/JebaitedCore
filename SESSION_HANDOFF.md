# Session handoff (for the next Cursor chat)

Copy the block below into the first message when starting a **new** chat so context stays small and tokens cheap.

```
## Handoff
- **Repo:** JebaitedCore (Paper 1.21 plugin). ROADMAP.md is source of truth for intent.
- **Branch:** `cursor/next-steps-2026-04-18`
- **Last commit / PR:** `8fa4f15` (server-side parity + persistence hardening). PR open link: https://github.com/DarkNiightz/JebaitedCore/compare/main...cursor/next-steps-2026-04-18?expand=1
- **Handoff saved:** 2026-04-18 — panel/server parity tranche shipped, plus Docker restart hardening notes captured.
- **Shipped this session (server-side only):**
  - Command parity: `setrank` console/RCON path, `/unfreeze` label, `maintenance allow` alias, cosmetics admin RCON commands (`give|take|wipe`).
  - Hardcore flow: no join wipe, death-to-pool, winner claim via `/loot` GUI.
  - DB migrations: `V011` moderation lifecycle, `V012` server id columns, `V013` booster/quest tables.
  - Write-path hardening: moderation status transitions + server stamping, audit log server stamping, immediate rank DB persist fallback logging.
- **Docker/ops learnings (critical):**
  - Keep one compose project name: `name: jebaitednetwork` (avoid duplicate stacks/port collisions).
  - Canonical stack path: `Vibe Code/JebaitedNetwork/docker-compose.yml` and `.env` there.
  - Windows path with spaces: use `JebaitedNetwork/up-build.ps1` (BuildKit off).
  - mcMMO MySQL host in `MC Server/plugins/mcMMO/config.yml` must be `mysql` (not `jebaited-mysql`).
- **Quick restart verify:**
  - `docker restart jnet-mc-hub jnet-mc-smp`
  - `docker ps --format "table {{.Names}}\t{{.Status}}"` (hub/smp should become healthy)
  - `docker logs jnet-mc-hub --since 90s` + `docker logs jnet-mc-smp --since 90s` (look for Paper done + JebaitedCore startup report)
- **Working on next:** §21 KOTH polish / party-aware TeamEngine + HC-CTF; then I2 Player Shops; parallel mcMMO wrappers.
- **Next 1–3 steps:** 1) Finish §21 event architecture cleanup (KOTH/CTF flow). 2) Start I2 schema + minimal write paths. 3) Continue mcMMO wrapper parity (`/mcability`, `/mccooldown`, `/ptp`).
- **Files to open first:** [ROADMAP.md](ROADMAP.md), `src/main/java/com/darkniightz/core/eventmode/`, [docs/DOCKER.md](docs/DOCKER.md), [PlayerProfileDAO](src/main/java/com/darkniightz/main/PlayerProfileDAO.java), [AuditLogService](src/main/java/com/darkniightz/core/system/AuditLogService.java).
- **Verify:** `.\mvnw.cmd -DskipTests compile`; `docker compose -f docker-compose.ci.yml config`; runtime container health checks above.
```

Erase or overwrite the bullets each session so the next run does not inherit stale tasks.

---

### New session or stay?

- **Start a new Cursor chat** for large tracks: §21 `TeamEngine`/events, I2 Player Shops, Discord/Stripe if un-parking, or multi-file refactors.
- **Stay** for tiny fixes (single file, one migration, config tweaks).

The handoff block above is enough for a fresh session to resume without re-reading the whole implementation.