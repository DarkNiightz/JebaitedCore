# JebaitedCore

Minecraft hub/core plugin for Paper 1.21.x. Provides ranks, chat formatting, hub menus, cosmetics, and a lightweight moderation suite with optional PostgreSQL persistence.

Contents
- Overview
- Requirements
- Setup and Build
- Install and Run on a Paper server
- Configuration (config.yml) and environment
- Commands and permissions
- Database schema (auto-created)
- Project structure
- Roadmap and Cursor Grafter skill
- Tests
- License

Overview
JebaitedCore is a Paper plugin that centralizes common hub features:
- Chat formatting and staff chat
- Rank display and simple rank management
- Player profile and stats tracking
- Hub protections and hotbar navigator GUI
- Cosmetics (particles, trails, wardrobe) with a small rules/catalog
- Moderation tools: kick, warn, (temp)mute/(un)ban, freeze, vanish, slowmode, history
- Developer mode toggle for allowlisted UUIDs

The plugin can operate without a database, but enabling PostgreSQL unlocks persistence for players, stats, cosmetics, and moderation history.

Requirements
- Java 21
- Maven 3.8+ (or use the included wrapper)
- PaperMC server for Minecraft 1.21.x (plugin.yml api-version: 1.21)
- Optional: PostgreSQL (recommended 13+; tested with modern 14/15) when database.enabled = true
  - Note: The PostgreSQL driver is declared as a runtime dependency. If not shaded, ensure the driver is available to the server at runtime. TODO: Decide whether to shade the driver into the plugin JAR.
- Optional: [mcMMO](https://www.mcmmo.org) — JebaitedCore lists mcMMO as a soft dependency and evicts/rebinds overlapping commands (`/party`, `/p`, `/mcstats`, `/mctop`) so players see Jebaited formatting. Upgrade mcMMO on a staging server first; use `/compat` to confirm the detected mcMMO version, and set `integrations.mcmmo.bridge_self_test: true` in `config.yml` if you want a one-line startup log that probes `ExperienceAPI.getPowerLevel` after enable.

Stack and entry point
- Language: Java
- Build tool: Maven (pom.xml)
- Paper API: io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT (scope provided)
- Main plugin class (entry point): com.darkniightz.main.JebaitedCore (declared in src/main/resources/plugin.yml)

Setup and build
1) Clone the repository
   git clone <this-repo-url>
   cd JebaitedCore

2) Build the plugin JAR (Maven Wrapper — **no global `mvn` needed** on Windows):
   From the inner module folder (the one containing `pom.xml` and `src/`):
   ```
   ..\mvnw.cmd clean package
   ```
   Full step-by-step (deploy path, restart, env vars): [docs/BUILD_AND_RUN_WINDOWS.md](docs/BUILD_AND_RUN_WINDOWS.md)

3) The artifact will be produced at
   target/JebaitedCore.jar
   Optional: set `JEBAITED_PLUGINS_DIR` or `jebaited-deploy.properties` so `package` also copies the JAR to your Paper `plugins` folder (see Ant echo in build log).

Install and run on a Paper server
1) Copy target/JebaitedCore.jar into your Paper server's plugins directory.
2) Start the server once to generate the default configuration at plugins/JebaitedCore/config.yml.
3) Edit config.yml to match your needs (see "Configuration" below).
4) Restart or reload the server.

Configuration (config.yml) and environment
Configuration file: src/main/resources/config.yml (deployed to plugins/JebaitedCore/config.yml at runtime).

Key sections (not exhaustive):
- ranks: Color codes per rank label
- messages: Customizable user-facing strings (mute/ban/etc.)
- logging: Enable/disable logging for joins, chat, moderation
- chat: Formatting template, separator, and color policy
- devmode: allowed_uuids list, grant_op, enabled_by_default
- feature_flags, hotbar.navigator: Toggles for hub features and the navigator item setup
- menus: Definitions for Servers Navigator and Cosmetics GUIs
- hub.protection: Build/damage/hunger/respawn behavior in hub
- database: PostgreSQL connection and HikariCP pool settings
  database:
    enabled: true
    host: "localhost"
    port: 5432
    database: "jebaited"
    username: "user"
    password: "password"
    pool-settings:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
- cosmetics: Catalog and rules used by the cosmetics engine

Environment variables
- The plugin does not currently read environment variables directly. All configuration is managed via config.yml. TODO: Consider supporting environment expansion for database credentials in the future.

Commands and permissions
Commands are declared in plugin.yml and registered in the main class. Highlights:
- Core/help: /jebaited, /devmode
- Ranks & stats: /rank, /setrank, /stats
- Moderation: /kick, /warn, /mute, /tempmute, /unmute, /ban, /tempban, /unban, /freeze, /vanish, /staffchat, /clearchat, /slowmode, /history
- Hub menus: /menu, /servers, /navigator
- Economy & shop: /balance, /pay, /balancetop, /eco (staff), /shop (alias /market; SMP)
- Cosmetics: /cosmetics, /wardrobe

Permissions (from plugin.yml):
- jebaited.core.use (default: true)
- jebaited.menu.use (default: true)
- jebaited.cosmetics.open (default: true)

Note: Additional command access control is enforced by in-code rank checks (see RankManager and related command executors). TODO: Document all permission/rank requirements per command.

Database schema (auto-created)
When database.enabled = true, tables are created automatically on enable:
- players(uuid, username, rank, first_joined, last_joined)
- player_stats(uuid, kills, deaths, commands_sent)
- moderation_history(id, target_uuid, type, actor, actor_uuid, reason, duration_ms, expires_at, timestamp)
- player_cosmetics(id, player_uuid, cosmetic_id, cosmetic_type, is_active)

Project structure
- pom.xml — Maven project and dependencies (Paper API, HikariCP, PostgreSQL driver)
- src/main/java/com/darkniightz/main/JebaitedCore.java — Main plugin class (entry point)
- src/main/java/com/darkniightz/main/database/DatabaseManager.java — HikariCP + PostgreSQL configuration
- src/main/java/com/darkniightz/core/... — Chat, commands, moderation, cosmetics, hub GUI, players/ranks
- src/main/resources/plugin.yml — Bukkit/Paper plugin descriptor (name, main, api-version, commands)
- src/main/resources/config.yml — Default configuration populated on first run

Roadmap and Cursor Grafter skill
- [ROADMAP.md](ROADMAP.md) — feature index, upcoming work, and P1 backlog (source of truth for intent).
- [`.cursor/skills/grafter/SKILL.md`](.cursor/skills/grafter/SKILL.md) — agent checklist: command wiring, migrations, and **Settings + Debug** surfaces on every change.
- [docs/PANEL_SERVER_SHOP_HANDOFF.md](docs/PANEL_SERVER_SHOP_HANDOFF.md) — **web panel only:** §17 server shop DB contract + copy-paste prompt for `web-admin` (not edited from this repo per Grafter skill).

Tests
- There are currently no automated tests in this repository.
- TODOs:
  - Unit tests for utility classes (e.g., TimeUtil) with JUnit 5
  - Plugin behavior tests using MockBukkit
  - Database integration tests (if enabled) using Testcontainers for PostgreSQL

Development notes and scripts
- Build: mvn clean package
- Windows wrapper build: .\\mvnw.cmd -DskipTests package
- Clean only: mvn clean
- Install to local repo: mvn install
- CI pipeline
  - GitHub Actions workflow at .github/workflows/ci.yml
  - Runs Maven build on push/PR
  - Validates `docker-compose.ci.yml` (syntax only; real stack lives in JebaitedNetwork — see docs)
  - Validates src/main/resources/plugin.yml and src/main/resources/config.yml with scripts/validate_yaml.py
- Dev / prod Docker stack (Paper + Postgres + Redis + bot + web-admin)
  - **Canonical compose:** `Vibe Code/JebaitedNetwork/docker-compose.yml` (project `jebaitednetwork`). This repo’s `docker-compose.yml` only `include`s that file.
  - **Do not** treat the root `Dockerfile` here as the main server image — the stack uses `itzg/minecraft-server` with `Vibe Code/MC Server` bind-mounted for hub data and `plugins/`.
  - Full runbook (ports, `.env`, build script): [docs/DOCKER.md](docs/DOCKER.md)
  - Typical loop: build JAR → copy to `MC Server/plugins/` (or `Vibe Code/scripts/build-jebaitedcore-docker.ps1`) → `docker compose up -d --build` from **JebaitedNetwork** → client to `localhost:25565`.
  - Stopping / reset: `docker compose down` or `docker compose down -v` from that directory (see DOCKER.md).

Operations automation (MC Server folder)
- Backups:
  - backup-world.ps1: archives world folders (world, world_nether, world_the_end, smp) into backups/worlds-*.zip
  - backup-db.ps1: exports PostgreSQL dump into backups/db-*.sql
  - backup-all.ps1: runs both
- Restore:
  - restore-world.ps1: restores latest world zip and restarts minecraft service
  - restore-db.ps1: restores latest SQL dump and restarts minecraft service
  - restore-all.ps1: restores DB and worlds in sequence
- Notes:
  - restore scripts intentionally stop minecraft before restore to avoid file corruption
  - world restore keeps a rollback copy under backups/rollback-world-<timestamp>

License
- No license file is present. TODO: Add a LICENSE file (e.g., MIT/Apache-2.0) and update this section accordingly.
