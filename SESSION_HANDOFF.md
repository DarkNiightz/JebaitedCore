# Session handoff (for the next Cursor chat)

Copy the block below into the first message when starting a **new** chat so context stays small and tokens cheap.

```
## Handoff
- **Repo:** JebaitedCore (Paper 1.21 plugin). ROADMAP.md is source of truth for intent.
- **Branch:** (fill)
- **Last commit / PR:** (fill)
- **Handoff saved:** 2026-04-17 — ready to resume tomorrow.
- **Shipped (recent plugin):** §21 CTF — YAML `red_kit`/`blue_kit` + strip/reapply (`CtfKitUtil`); KOTH scoreboard **unc. leader**; **ground flag** pickup (`CtfGroundFlagListener` + PDC item). §17 — server shop MVP (`ShopManager`, V007, `/shop`, staging checklist + `docs/PANEL_SERVER_SHOP_HANDOFF.md`).
- **Not done yet (pick one track to start):** (A) **§17** — run staging checklist on a real server; Jamie builds panel prices + `shop_transactions` UI from handoff doc. (B) **§21** — party-aware CTF teams in [`TeamEngine`](src/main/java/com/darkniightz/core/eventmode/team/TeamEngine.java) (currently random shuffle). (C) **I2** — after §17 stable; design stub in ROADMAP §17 “I2 Player Shops”.
- **Working on:** (fill tomorrow — e.g. “§17 staging pass” or “TeamEngine parties”)
- **Next 1–3 steps:** 1) If ops first: §17 [Staging verification](ROADMAP.md#staging-verification-checklist) on staging PostgreSQL + SMP. 2) If plugin first: implement `TeamEngine.assignCtfTeams` party cohesion (see ROADMAP Current focus row 3). 3) Panel remains Jamie / `web-admin` — paste [docs/PANEL_SERVER_SHOP_HANDOFF.md](docs/PANEL_SERVER_SHOP_HANDOFF.md).
- **Files to open first:** [`ROADMAP.md`](ROADMAP.md) (Current focus + §17), [`TeamEngine.java`](src/main/java/com/darkniightz/core/eventmode/team/TeamEngine.java), [`ShopManager.java`](src/main/java/com/darkniightz/core/shop/ShopManager.java), [`config.yml`](src/main/resources/config.yml) (`server_shop:` / `event_mode:`)
- **Verify:** `.\mvnw.cmd -DskipTests compile`
```

Erase or overwrite the bullets each session so the next run does not inherit stale tasks.

---

### New session or stay?

- **Start a new Cursor chat** for a large track: §21 `TeamEngine`, **web-admin** shop UI, **I2** player shops, or multi-file refactors.
- **Stay** for tiny fixes (single file, config tweak).

The handoff block above is enough for a fresh session to resume without re-reading the whole implementation.
