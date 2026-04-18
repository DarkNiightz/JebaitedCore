# Session handoff (for the next Cursor chat)

Copy the block below into the first message when starting a **new** chat so context stays small and tokens cheap.

```
## Handoff
- **Repo:** JebaitedCore (Paper 1.21 plugin). ROADMAP.md is source of truth for intent.
- **Branch:** (fill)
- **Last commit / PR:** (fill)
- **Handoff saved:** 2026-04-18 — Discord §23 ops + **Stripe `/donate` store** (V010, `StoreService`, webhook route, bot link-gates). **Stripe business onboarding not started** (see deferred list below).
- **Shipped this pass:** **Plugin** store: `StoreService`, `/donate` + `DonateMenu`, `POST /integrations/stripe/webhook`, `DiscordInboundHttpService` unified bind; **bot** link required for bridge + `/ping` `/server` `/player` `/activity`. **Deploy:** `Vibe Code\scripts\build-jebaitedcore-docker.ps1` defaults to `local-paper-server\plugins`. Prior Discord bridge/console/monitoring as before.
- **Not done yet:** (1) **Tablist** live Discord member count. (2) **§21** party-aware CTF / factions plugin integration if you replace permission-based “faction” bridge. (3) **Stripe — finish later (account & live):** complete Stripe Dashboard **business profile** (VAT if registered, **public website or social URL** matching business name). Add **production** `STRIPE_SECRET_KEY` / `STRIPE_WEBHOOK_SECRET`; **Dashboard webhook** `https://…/integrations/stripe/webhook` + event `checkout.session.completed` (or keep **Stripe CLI** `stripe listen` for dev). Replace placeholder **success/cancel URLs** in `config.yml` with real pages. (4) **Docker:** single stack **`Vibe Code/MC Server/docker-compose.yml`** (includes Redis + discord-bot; plugin env + **:8789**). Put secrets in **`MC Server/.env`**. Run `docker compose -p jebaitedcore down` to drop old duplicate project; see **[docs/DOCKER.md](docs/DOCKER.md)**.
- **Working on:** (fill)
- **Next 1–3 steps:** 1) Wire Docker/compose so **bot → Paper `8789`** (host.docker.internal or shared network) with matching **`JB_PLUGIN_API_TOKEN`** + plugin `integrations.discord.inbound.api_token`. 2) Enable **`Privileged Message Content Intent`** in Discord Developer Portal for bridge + console. 3) Assign `relay_*_in/out` and `console_*` channel IDs in `bot-config.yml` / `.env`.
- **Files to open first:** `[DiscordInboundHttpService](src/main/java/com/darkniightz/core/system/DiscordInboundHttpService.java)`, `[PluginBridgeClient](bot-service/src/main/java/com/darkniightz/bot/bridge/PluginBridgeClient.java)`, `[DiscordGatewayListener](bot-service/src/main/java/com/darkniightz/bot/discord/DiscordGatewayListener.java)`, `[BotApplication](bot-service/src/main/java/com/darkniightz/bot/BotApplication.java)`, `[bot-config.yml](bot-service/src/main/resources/bot-config.yml)`
- **Verify:** `.\mvnw.cmd -DskipTests compile`, `.\mvnw.cmd -f bot-service/pom.xml -DskipTests package`, `docker compose config`
- **Stage 3 config:** match **`integrations.discord.inbound.api_token`** (plugin) with **`JB_PLUGIN_API_TOKEN`** / `plugin_api_token` (bot); expose Paper **8789** to the bot host; enable **`integrations.discord.inbound.enabled`** and **`integrations.discord.relay_chat`** after channel mapping.
```

Erase or overwrite the bullets each session so the next run does not inherit stale tasks.

---

### New session or stay?

- **Start a new Cursor chat** for large tracks: Discord Phase B/C, §21 `TeamEngine`, or multi-file refactors.
- **Stay** for tiny fixes (single file, one migration, config tweaks).

The handoff block above is enough for a fresh session to resume without re-reading the whole implementation.