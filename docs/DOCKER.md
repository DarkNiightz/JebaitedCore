# Docker — single stack (JebaitedNetwork)

## Windows + `Vibe Code` path (read this first)

If **`docker compose up --build`** fails with **`invalid file request package-lock.json`** (or `package.json`), Docker BuildKit is choking on **spaces in the path**. From **`JebaitedNetwork`**, run **`.\up-build.ps1`** (or **`$env:DOCKER_BUILDKIT='0'`** then **`docker compose up -d --build`**). See **`JebaitedNetwork/README.md`**.

---

## Canonical stack (one Compose file)

**Authoritative file:** `Vibe Code/JebaitedNetwork/docker-compose.yml`  
**Compose project name:** `jebaitednetwork`

Everything runs from that one file: **PostgreSQL**, **MySQL** (mcMMO), **Redis**, **mc-hub** (Paper hub), **mc-smp** (Paper SMP), **discord-bot**, **web-admin** (panel).

### Services (image, ports, env) — compose reference


| Service          | Image / build                        | Published ports (host→container defaults)     | Required / notable env (see `JebaitedNetwork/.env`)                                                                                       |
| ---------------- | ------------------------------------ | --------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| **postgres**     | `postgres:16-alpine`                 | `${POSTGRES_PORT:-5432}:5432`                 | `POSTGRES_ADMIN_USER`, `POSTGRES_ADMIN_PASSWORD`, `POSTGRES_DB`                                                                           |
| **db-bootstrap** | `postgres:16-alpine`                 | (none)                                        | Runs once: `PGPASSWORD`, uses `POSTGRES_*` to apply `db-bootstrap.sql`                                                                    |
| **mysql**        | `mysql:8.0`                          | `${MYSQL_PORT:-3306}:3306`                    | `MCMMO_PASSWORD`, `MYSQL_ROOT_PASSWORD`                                                                                                   |
| **redis**        | `redis:7-alpine`                     | `${REDIS_PORT:-6379}:6379`                    | —                                                                                                                                         |
| **mc-hub**       | `itzg/minecraft-server:java21`       | `25565`, `25575` (RCON), `8789` (plugin HTTP) | `env_file: .env`; `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `RCON_PASSWORD`, `MC_VERSION`, `MC_HUB_MEMORY`                            |
| **mc-smp**       | `itzg/minecraft-server:java21`       | `25566→25565`, `25576→25575`, `8790→8789`     | Same pattern as hub; `RCON_SMP_PASSWORD` optional                                                                                         |
| **discord-bot**  | build `.../JebaitedCore/bot-service` | `${DISCORD_BOT_PORT:-8788}:8787`              | `JB_DB_*`, `JB_REDIS_URI`, `JB_WEBHOOK_SECRET`, `JB_PLUGIN_INBOUND_BASE_URL`, `JB_PLUGIN_API_TOKEN`, Discord token in `.env` / bot config |
| **web-admin**    | build `../web-admin`                 | `${WEB_ADMIN_PORT:-3001}:3001`                | `WEB_ADMIN_DB_USER`, `WEB_ADMIN_DB_PASSWORD`, `WEB_ADMIN_SESSION_SECRET`, `WEB_ADMIN_PROVISION_SECRET`, `POSTGRES_DB`                     |


Host path binds (hub data, logs, panel): `../MC Server` → mc-hub `/data`; `MC Server/logs` and `plugins/JebaitedCore` → web-admin read-only; `./servers/smp` → mc-smp.

### Convenience entry points (same stack, not duplicates)


| Location                                                    | What it does                                                            |
| ----------------------------------------------------------- | ----------------------------------------------------------------------- |
| `JebaitedNetwork/docker-compose.yml`                        | **Use this** for `docker compose` day-to-day                            |
| `MC Server/docker-compose.yml`                              | Thin `include:` of `../JebaitedNetwork/docker-compose.yml` — same stack |
| `IdeaProjects/JebaitedCore/JebaitedCore/docker-compose.yml` | Thin `include:` so you can run Compose from the plugin repo             |


All three entry points set Compose **`name: jebaitednetwork`** so you do not spawn a second project (e.g. `mcserver` or `jebaitedcore`) when using a wrapper. If you see duplicate containers or port conflicts, you may have an **old** project from before this fix — `docker compose down` in each old directory, or prune (see below).

## Start / stop

**From the canonical directory:**

```powershell
cd "C:\Users\jamie\Documents\Vibe Code\JebaitedNetwork"
.\up-build.ps1
```

(`up-build.ps1` sets `DOCKER_BUILDKIT=0` on Windows paths with spaces. If BuildKit works on your machine, plain `docker compose up -d --build` is fine.)

Stop (keep volumes):

```powershell
docker compose down
```

Full reset (destructive — drops named volumes for this project, e.g. Postgres data):

```powershell
docker compose down -v
```

Then `up -d --build` again. Optional machine-wide cleanup (affects **all** unused Docker resources, not only Jebaited): `docker system prune -a` — use with care.

### BuildKit + paths with spaces

If `docker compose` build fails with missing `package.json` / `package-lock.json` or odd path errors on Windows under `...\Vibe Code\...`:

```powershell
$env:DOCKER_BUILDKIT='0'
docker compose up -d --build
```

Or move the repo tree to a path **without** spaces.

## Known footguns (do this every time)

- **Compose project drift:** keep `name: jebaitednetwork` on wrapper compose files. If old stacks exist (`mcserver`, `jebaitedcore`), shut them down first to prevent duplicate containers and port conflicts.
- **Windows BuildKit path issue:** on `Vibe Code` paths, prefer `.\up-build.ps1` from `JebaitedNetwork` so BuildKit is disabled.
- **mcMMO MySQL host wiring:** in `MC Server/plugins/mcMMO/config.yml`, use `MySQL.Server.Address: mysql` (or reachable alias `jnet-mysql`). `jebaited-mysql` is not a valid alias in the current network.
- **Post-restart health sequence:**
  - `docker restart jnet-mc-hub jnet-mc-smp`
  - `docker ps --format "table {{.Names}}\t{{.Status}}"`
  - `docker logs jnet-mc-hub --since 90s`
  - `docker logs jnet-mc-smp --since 90s`

### Next-session checklist

1. Start from `Vibe Code/JebaitedNetwork`.
2. Use `.\up-build.ps1` on Windows paths with spaces.
3. Confirm only one compose project is active (`jebaitednetwork`).
4. After jar/config changes, restart only required containers and verify health/logs.
5. If mcMMO errors on startup, validate `MC Server/plugins/mcMMO/config.yml` host is `mysql`.

## Environment file

Create **`JebaitedNetwork/.env`** (see **`JebaitedNetwork/.env.example`** if present).

**mc-hub**, **mc-smp**, and **discord-bot** use `env_file: .env` relative to **JebaitedNetwork**. Put stack-wide secrets here, including:

- `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` (passed into Paper containers for store)
- `JB_PLUGIN_API_TOKEN` — must match plugin `integrations.discord.inbound.api_token`
- `JB_PLUGIN_INBOUND_BASE_URL` — default `http://mc-hub:8789` (bot → Paper plugin HTTP)
- `WEB_ADMIN_SESSION_SECRET`, `WEB_ADMIN_PROVISION_SECRET`, DB passwords, etc.

If you previously used **MC Server/.env**, merge those values into **JebaitedNetwork/.env**.

## Ports (defaults)


| Host port | Service     | Notes                                         |
| --------- | ----------- | --------------------------------------------- |
| 25565     | mc-hub      | Main Paper (hub)                              |
| 25566     | mc-smp      | Second Paper                                  |
| 8789      | Hub         | Plugin HTTP (Discord inbound, Stripe webhook) |
| 8790      | SMP         | Plugin HTTP                                   |
| 8788      | discord-bot | Maps to container `8787` — see health below   |
| 3001      | web-admin   | Panel                                         |
| 5432      | PostgreSQL  |                                               |
| 6379      | Redis       |                                               |
| 3306      | MySQL       | mcMMO                                         |


## Wiring checklist (panel, bot, Paper, DB)


| Check               | Expected                                                                                                                                   |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| Postgres            | `web-admin` `DATABASE_URL` uses `postgres:5432` and same `POSTGRES_DB` as bootstrap. Bot uses `JB_DB_JDBC_URL` → `postgres:5432/jebaited`. |
| Panel               | After `up`, `http://localhost:3001` responds (session/env must be set).                                                                    |
| Bot → Paper         | `JB_PLUGIN_INBOUND_BASE_URL=http://mc-hub:8789` (default in compose). For SMP-only tests: `http://mc-smp:8789`.                            |
| Bot health          | In container, HTTP port is **8787**; on the host: `http://localhost:8788/health/live`                                                      |
| Plugin token        | `JB_PLUGIN_API_TOKEN` in `.env` = `integrations.discord.inbound.api_token` in `plugins/JebaitedCore/config.yml`                            |
| Hub world + plugins | `mc-hub` mounts **Vibe Code/MC Server** → `/data` — JAR goes to **MC Server/plugins/JebaitedCore.jar** |


## Repos on disk (typical layout)


| Piece                                      | Path                                                     |
| ------------------------------------------ | -------------------------------------------------------- |
| This plugin (Maven module)                 | `Vibe Code/IdeaProjects/JebaitedCore/JebaitedCore`       |
| Compose + `.env`                           | `Vibe Code/JebaitedNetwork`                              |
| Hub Paper data (world, `plugins/`, logs)   | `Vibe Code/MC Server`                                    |
| SMP Paper data                             | `Vibe Code/JebaitedNetwork/servers/smp`                  |
| Discord bot **build context** (Dockerfile) | `.../JebaitedCore/bot-service` (referenced by compose)   |
| Web-admin **build context**                | `Vibe Code/web-admin` (sibling folder; not in this repo) |


## JebaitedCore.jar — build and copy

1. **Script (recommended):**  
   `Vibe Code/scripts/build-jebaitedcore-docker.ps1`  
   Defaults to **`Vibe Code/MC Server/plugins`** (matches **mc-hub** bind mount).
2. **Maven + env:** set `JEBAITED_PLUGINS_DIR` to that same `plugins` folder, then `mvnw.cmd clean package` from the plugin module.
3. **SMP:** copy to `JebaitedNetwork/servers/smp/plugins/` when testing the second server.

Restart the **Paper** container after replacing the JAR (not `/jreload` for a new jar).

## Discord bot → Paper

Default: **`JB_PLUGIN_INBOUND_BASE_URL=http://mc-hub:8789`**.  
Change in **JebaitedNetwork/.env** if the bot should target **mc-smp** instead.