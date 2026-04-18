# Docker — JebaitedNetwork (single stack)

## One Compose project

Everything lives under **`Vibe Code/JebaitedNetwork/docker-compose.yml`** with **`name: jebaitednetwork`**:

- **postgres**, **mysql**, **redis**, **discord-bot**, **web-admin**
- **mc-hub** — Paper bound to **`../MC Server`** (your existing hub world + `plugins/`)
- **mc-smp** — second Paper instance, data in **`JebaitedNetwork/servers/smp`** (for network / cross-server tests)

`MC Server/docker-compose.yml` and this repo’s **`docker-compose.yml`** only **`include`** that file — no second stack.

## Commands

```powershell
cd "C:\Users\jamie\Documents\Vibe Code\JebaitedNetwork"
docker compose up -d --build
```

If **`docker compose` build** errors with **`invalid file request package.json`** or **`package-lock.json`**, you are likely on **Windows** with a path that contains **spaces** (for example `...\Vibe Code\...`). Docker BuildKit mishandles that. Run once per shell:

```powershell
$env:DOCKER_BUILDKIT='0'
docker compose up -d --build
```

Or put the stack under a path **without** spaces.

Env: **`JebaitedNetwork/.env`** (see **`.env.example`**). If you used **`MC Server/.env`** before, merge those values here.

## Ports (defaults)

| Host | Service |
|------|---------|
| 25565 | mc-hub (main MC) |
| 25566 | mc-smp (second server) |
| 8789 | Hub plugin HTTP (Discord inbound + Stripe) |
| 8790 | SMP plugin HTTP |
| 8788 | Discord bot |
| 3001 | Web admin |
| 5432 | Postgres |
| 6379 | Redis |

## JebaitedCore.jar

Build the plugin, then copy **`JebaitedCore.jar`** into:

- Hub: **`MC Server/plugins/`**
- SMP (when you care): **`JebaitedNetwork/servers/smp/plugins/`** (create `plugins` if missing)

Or use **`Vibe Code\scripts\build-jebaitedcore-docker.ps1`** (defaults to **`MC Server\plugins`**).

## Discord bot → Paper

Default **`JB_PLUGIN_INBOUND_BASE_URL=http://mc-hub:8789`**. To point the bot at **smp**, set that env to **`http://mc-smp:8789`** in **`.env`**.
