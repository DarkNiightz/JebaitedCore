# Session handoff (for the next Cursor chat)

Copy the block below into the first message when starting a **new** chat so context stays small and tokens cheap.

```
## Handoff
- **Repo:** JebaitedCore (Paper 1.21 plugin). ROADMAP.md is source of truth for intent.
- **Branch:** (fill)
- **Last commit / PR:** (fill)
- **Handoff saved:** 2026-04-18 — **§17 shop** operator-validated: in-game `/shop`, **web panel** connected, **no inventory space** + **insufficient balance** paths OK. **Discord bot + Stripe `/donate` intro shipped**; **Stripe not production-complete** (business profile, live keys, real success/cancel URLs, production webhook) — **parked**.
- **Intent this phase:** not continuing Discord/Stripe/Docker wiring unless something breaks; **prefer plugin gameplay** (§21 / I2 / parallel mcMMO).
- **Shipped (prior work, see ROADMAP):** `StoreService`, `/donate` + `DonateMenu`, webhook route, `DiscordInboundHttpService`, bot bridge + slash commands; `Vibe Code\scripts\build-jebaitedcore-docker.ps1` → **`MC Server\plugins`** (hub bind mount). Full §23/§17 detail in [ROADMAP.md](ROADMAP.md).
- **Optional backlog (when you return):** tablist live Discord member count; finish Stripe live; bot → Paper **8789** + matching **`JB_PLUGIN_API_TOKEN`** / `integrations.discord.inbound.api_token` — canonical Docker: **[docs/DOCKER.md](docs/DOCKER.md)** (`JebaitedNetwork/docker-compose.yml`, `.env` there).
- **Working on:** (fill)
- **Next 1–3 steps:** 1) **§21** — KOTH polish and/or **party-aware `TeamEngine`** + HC-CTF ([ROADMAP.md](ROADMAP.md) **Current focus**). 2) Or scope **I2 Player Shops** / §18 when economy/events APIs feel stable. 3) **Parallel:** mcMMO wrappers (`/mcability`, `/mccooldown`, `/ptp`) per roadmap “Next theme pick”.
- **Files to open first:** [ROADMAP.md](ROADMAP.md) §21; `[core/eventmode](src/main/java/com/darkniightz/core/eventmode/)`; for I2 later: `[ShopManager](src/main/java/com/darkniightz/core/shop/ShopManager.java)` + `src/main/resources/db/`. **Discord/Stripe ops only if un-parking:** `[DiscordInboundHttpService](src/main/java/com/darkniightz/core/system/DiscordInboundHttpService.java)`, `[PluginBridgeClient](bot-service/src/main/java/com/darkniightz/bot/bridge/PluginBridgeClient.java)`, `[bot-config.yml](bot-service/src/main/resources/bot-config.yml)`.
- **Verify:** `.\mvnw.cmd -DskipTests compile`; `.\mvnw.cmd -f bot-service/pom.xml -DskipTests package` only if touching bot.
```

Erase or overwrite the bullets each session so the next run does not inherit stale tasks.

---

### New session or stay?

- **Start a new Cursor chat** for large tracks: §21 `TeamEngine`/events, I2 Player Shops, Discord/Stripe if un-parking, or multi-file refactors.
- **Stay** for tiny fixes (single file, one migration, config tweaks).

The handoff block above is enough for a fresh session to resume without re-reading the whole implementation.