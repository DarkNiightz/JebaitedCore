# Build, deploy, and restart (Windows)

You do **not** need a global `mvn` command. This repo ships **Maven Wrapper** as `mvnw.cmd` one folder **above** the plugin module.

## Where to run commands

The **module** folder is the one that contains the main `pom.xml` and `src/main/java` — usually:

`...\IdeaProjects\JebaitedCore\JebaitedCore`

The wrapper lives here:

`...\IdeaProjects\JebaitedCore\mvnw.cmd`

## Docker (single stack)

The **only** production Compose file for Minecraft + DB + bot + Redis is:

**`Vibe Code\MC Server\docker-compose.yml`**

This repo’s `docker-compose.yml` **includes** that file so you can run Compose from here too. Full port map, `.env` location, and cleanup of old stacks: **[docs/DOCKER.md](DOCKER.md)**.

## One script (build + replace JAR for Docker / local Paper)

From `Vibe Code\scripts\`:

```powershell
cd "C:\Users\jamie\Documents\Vibe Code\scripts"
.\build-jebaitedcore-docker.ps1
```

**No config required:** the JAR is copied to  
`IdeaProjects\JebaitedCore\JebaitedCore\local-paper-server\plugins\JebaitedCore.jar`  
Point Docker at that `plugins` folder, **or** override:

- `-PluginsPath "D:\your\paper\plugins"`, or
- optional file `docker-plugins.path` (one line, full path), or
- env `JEBAITED_PLUGINS_DIR` / `VIBE_PAPER_PLUGINS`

Then restart the container in Docker Desktop.

## Build the plugin JAR (manual)

In **PowerShell**:

```powershell
cd "C:\Users\jamie\Documents\Vibe Code\IdeaProjects\JebaitedCore\JebaitedCore"
..\mvnw.cmd clean package
```

Output: `target\JebaitedCore.jar`.

## Copy into your Paper `plugins` folder (choose one)

### Option 1 — Environment variable (recommended)

Set your real server plugins path, then build; the POM runs Ant and copies the JAR automatically when the variable is set:

```powershell
$env:JEBAITED_PLUGINS_DIR = "C:\Path\To\Your\PaperServer\plugins"
..\mvnw.cmd clean package
```

Watch the log line: `Copying ... -> ...\JebaitedCore.jar`.

### Option 2 — `jebaited-deploy.properties`

1. Copy `jebaited-deploy.properties.example` to `jebaited-deploy.properties` (same folder as `pom.xml`).
2. Set one line (use **forward slashes**):
  `jebaited.deploy.plugins.dir=C:/Path/To/PaperServer/plugins`
3. Run `..\mvnw.cmd clean package` again.

(`jebaited-deploy.properties` is gitignored so your path stays local.)

### Option 3 — Script

```powershell
.\scripts\deploy-jebaited.ps1 -PluginsDir "C:\Path\To\PaperServer\plugins"
```

### Option 4 — Manual copy

Copy `target\JebaitedCore.jar` to the server’s `plugins\` folder (overwrite the old one).

## Restart Paper (required for JAR changes)

Replacing the JAR while the server is running is unreliable.

1. Stop the Paper server (console `stop`, or close the window).
2. Copy/rebuild as above.
3. Start Paper again.

`/jreload` reloads config in the **already loaded** plugin; it does **not** load a new JAR. Full restart is required after replacing `JebaitedCore.jar`.

## Build the Discord bot service (optional)

From the same machine, in a second terminal:

```powershell
cd "C:\Users\jamie\Documents\Vibe Code\IdeaProjects\JebaitedCore\JebaitedCore"
..\mvnw.cmd -f bot-service\pom.xml -q -DskipTests package
```

## What must be configured for store + Discord

- **Stripe:** set `STRIPE_SECRET_KEY` and `STRIPE_WEBHOOK_SECRET` in the environment (or fill `store.stripe` in `plugins/JebaitedCore/config.yml`). Without keys, `/donate` stays disabled.
- **Discord inbound:** `integrations.discord.inbound.api_token` must match the bot’s Paper API token (not `CHANGE_ME` on a real server).
- **Database:** Postgres must be reachable if `database.enabled: true` (docker host `postgres` matches a typical compose network hostname).

See also: [STRIPE_AND_STORE_TESTING.md](STRIPE_AND_STORE_TESTING.md).