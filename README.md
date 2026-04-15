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

Stack and entry point
- Language: Java
- Build tool: Maven (pom.xml)
- Paper API: io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT (scope provided)
- Main plugin class (entry point): com.darkniightz.main.JebaitedCore (declared in src/main/resources/plugin.yml)

Setup and build
1) Clone the repository
   git clone <this-repo-url>
   cd JebaitedCore

2) Build the plugin JAR
   mvn clean package

3) The artifact will be produced at
   target/JebaitedCore.jar

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
- Ranks & stats: /rank, /setrank, /stats, /tickets
- Moderation: /kick, /warn, /mute, /tempmute, /unmute, /ban, /tempban, /unban, /freeze, /vanish, /staffchat, /clearchat, /slowmode, /history
- Hub menus: /menu, /servers, /navigator
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
  - Validates Docker Compose configuration
  - Validates src/main/resources/plugin.yml and src/main/resources/config.yml with scripts/validate_yaml.py
- Dev server with Docker (Paper + PostgreSQL)
  - Prerequisites: Docker Desktop
  - Included files: Dockerfile, docker-compose.yml, .dockerignore
  - What you get:
    - A PostgreSQL 16 container named postgres with database "jebaited"
    - A Paper server container built from the packaged plugin jar
  - First time setup:
    1) Build the plugin jar:
       .\\mvnw.cmd -DskipTests package
    2) Start the stack:
       docker compose up -d --build
    3) Connect your Minecraft client to localhost:25565
  - Iterating on the plugin:
    - Rebuild after changes: .\\mvnw.cmd -DskipTests package
    - Rebuild the container image: docker compose up -d --build
  - Stopping and cleanup:
    - Stop containers: docker compose down
    - Remove volumes too if you want a full reset: docker compose down -v

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
