# Session handoff (for the next Cursor chat)

Copy the block below into the first message when starting a **new** chat so context stays small and tokens cheap.

```
## Handoff
- **Repo:** JebaitedCore (Paper 1.21 plugin + `bot-service`). ROADMAP.md is source of truth.
- **Branch:** (current workspace)
- **Handoff saved:** 2026-04-18 — §F StatsMenu UX pass (7 tabs + cosmetics fractions + SMP economy gate).
- **Shipped this session:**
  - **`StatsMenu`:** Seven tabs (**Profile · Hub · SMP · Hardcore · Arena · Chat games · Achievements**); **Hub** cosmetics show **unlocked/enabled totals** per `CosmeticsManager` category + active particle/gadget; **SMP** economy ingot only when **`WorldManager.isSmp(viewer)`**; **events** merged map filtered into three buckets (**`ChatGameKeys`** / `hardcore` / arena rest); **Achievements** tab fills **`ACH_GRID`** with per-definition progress + summary on **28**, book **`AchievementsMenu`** still slot **43** (handler **`tab == 6`**); skull chat transcript — balance line only when viewer on SMP worlds.
  - **§F v2 (prior):** **`plugin.yml`** aliases **`profile`**, **`pp`**; **`StatsCommand`** public **`/stats <player>`**; **`CommandSecurityListener`** throttle for `profile`/`pp`.
  - **§F v1 (prior):** Tabbed `StatsMenu` + Discord `/player` rich embed (`PlayerLookupDao`, `MonitoringSlashCommandListener`, `BotApplication`).
  - **`ROADMAP.md` hygiene (rule: `jebaited-plugin-standards.mdc`):** top blurb + **Feature Showcase** (`/stats`, Economy rows) + **Upcoming F** + **Feature Index §6** aligned with the above — not only §6 body text.
- **Working on next:** §F remainder per ROADMAP §6 — Adventure/`profile.labels.*`, bottom row (friend / party / settings / refresh), optional per-bucket event overflow polish **or** §21 / I2 per focus.
- **Next 1–3 steps:** 1) **`.\mvnw.cmd -DskipTests package`** → copy **`target/JebaitedCore.jar`** → **`MC Server/plugins/`** (or `build-jebaitedcore-docker.ps1`). 2) **`docker compose restart mc-hub mc-smp`** from this repo root (`name: jebaitednetwork` include). 3) In-game: all **seven tabs**, skull transcript from **hub vs SMP**, cosmetics fractions, event buckets.
- **Files to open first:** `StatsMenu.java`, `StatsCommand.java`, `AchievementManager.java`, ROADMAP §6.
- **Verify:** `.\mvnw.cmd -DskipTests compile` + `.\mvnw.cmd -f bot-service\pom.xml compile`; `docker compose build discord-bot` from `JebaitedNetwork` after pulling.
```

Erase or overwrite the bullets each session so the next run does not inherit stale tasks.

---

### New session or stay?

- **Start a new Cursor chat** for large tracks: §21 `TeamEngine`/events, I2 Player Shops, Discord/Stripe if un-parking, or multi-file refactors.
- **Stay** for tiny fixes (single file, one migration, config tweaks).

The handoff block above is enough for a fresh session to resume without re-reading the whole implementation.
