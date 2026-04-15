# JebaitedCore — RCON & Panel API Reference

> **For web panel developers.** This document describes every RCON command
> exposed by JebaitedCore and the webhook events the plugin posts to the panel.
> Keep this in sync as new commands are added.

---

## RCON Setup

1. In `server.properties` set:
   ```
   enable-rcon=true
   rcon.port=25575
   rcon.password=<your-secret>
   ```
2. In `spigot.yml` set:
   ```yaml
   restart-script: ./restart.sh
   ```
3. The panel connects via RCON to run commands just as a console operator would.

---

## Restart System

### `/restart [<time>] [<reason...>]`
Schedule a server restart with a countdown.

| Argument | Examples | Description |
|---|---|---|
| *(none)* | | 5-minute countdown, no reason |
| `<time>` | `30s` `5m` `1h` `90` | Duration until restart. Bare number = seconds. |
| `<reason>` | `Applying hotfix` | Free-text displayed in broadcast |

**Examples via RCON:**
```
restart 5m Hotfix deployment
restart 30s Critical bug fix – back momentarily
restart 1h Nightly maintenance
```

**Announcements fired automatically at:** 30 min → 15 → 10 → 5 → 3 → 2 → 1 → 30 s → 10, 9 … 1 → RESTARTING!

---

### `/restart cancel`
Cancels a pending restart. Broadcasts cancellation globally.

```
restart cancel
```

---

### `/restart status`
Returns whether a restart is pending and how many seconds remain.

```
restart status
```

Sample output:
```
[Jebaited] Restart pending — 4m32s remaining.
```
or
```
[Jebaited] No restart is currently scheduled.
```

---

## Moderation Commands (RCON-safe)

All of these work from RCON / console without a rank check.

| Command | Usage |
|---|---|
| `/kick <player> [reason]` | Kick an online player |
| `/ban <player> [reason]` | Permanent ban |
| `/tempban <player> <time> [reason]` | Temp ban (`30s` / `5m` / `1h`) |
| `/unban <player>` | Remove ban |
| `/mute <player> [reason]` | Permanent mute |
| `/tempmute <player> <time> [reason]` | Temp mute |
| `/unmute <player>` | Remove mute |
| `/warn <player> <reason>` | Issue a warning |
| `/freeze <player>` | Toggle freeze |
| `/history <player>` | View moderation log |
| `/clearchat` | Clear public chat |
| `/slowmode <seconds\|off>` | Set chat slowmode |

---

## Event System

| Command | Usage |
|---|---|
| `/event status` | Current event state |
| `/event list` | Available event types |
| `/event start <type>` | Start event |
| `/event stop` | Stop active event |
| `/event complete <winner>` | End and announce winner |
| `/event setup <ffa\|duels\|hardcore> addspawn` | Add spawn at current pos (requires online player) |
| `/event setup <ffa\|duels\|hardcore> listspawns` | List saved spawns |
| `/event setup <ffa\|duels\|hardcore> clearspawns` | Delete all spawns |

---

## Economy

| Command | Usage |
|---|---|
| `/eco give <player> <amount>` | Grant coins |
| `/eco take <player> <amount>` | Remove coins |
| `/eco set <player> <amount>` | Set balance |
| `/balance <player>` | Check balance |

---

## Ranks

| Command | Usage |
|---|---|
| `/setrank <player> <group>` | Set player's rank |
| `/rank get <player>` | Get current rank |
| `/rank set <player> <group>` | Set rank |

---

## Maintenance

| Command | Usage |
|---|---|
| `/maintenance on` | Enable maintenance mode |
| `/maintenance off` | Disable maintenance mode |
| `/maintenance status` | Check state |
| `/maintenance add <player>` | Whitelist a player |
| `/maintenance remove <player>` | Remove from whitelist |

---

## Webhook Events (Plugin → Panel)

The plugin posts JSON to `POST {webpanel.internal_url}/api/server/restart-event`
with header `X-Provision-Secret: <provision_secret>`.

### `restart_scheduled`
```json
{
  "event": "restart_scheduled",
  "countdown_seconds": "300",
  "reason": "Hotfix deployment",
  "initiator": "DarkNiightz"
}
```

### `restart_executing`
Sent immediately before kicking players.
```json
{
  "event": "restart_executing",
  "reason": "Hotfix deployment",
  "initiator": "DarkNiightz",
  "timestamp": "1713139200000"
}
```

### `restart_cancelled`
```json
{
  "event": "restart_cancelled",
  "canceller": "DarkNiightz"
}
```

### Panel-side flow
1. On `restart_scheduled` → show countdown banner in panel, disable "Start Restart" button.
2. On `restart_executing` → set server status to `RESTARTING`, begin RCON reconnect polling.
3. On successful RCON reconnect → set server status to `ONLINE`, hide banner.
4. On `restart_cancelled` → hide countdown banner, re-enable "Start Restart" button.

---

## Suggested Panel "Restart" UI Flow

```
User clicks "Restart" in panel
  → show confirmation dialog: "Restart with countdown?" / custom time input
  → POST /api/admin/restart (panel internal route)
    → panel sends RCON: restart 5m Initiated from web panel
  → panel receives restart_scheduled webhook
    → show countdown banner
  → panel receives restart_executing webhook
    → set status RESTARTING, begin polling
  → RCON reconnects after ~30–60s
    → set status ONLINE, clear banner, log event
```

---

## Web Panel Roles

This section defines the permission model for each staff tier in the web panel.
Panel roles mirror the in-game rank ladder but use named permission strings rather than rank gates.

---

### Role: `srmod` — Sr. Mod

Sr. Mod is the first elevated staff tier above Moderator. They have moderation oversight
but cannot approve rank changes, modify server settings, or configure events.

#### Permissions — GRANT

| Permission | Notes |
|---|---|
| `moderation.view_logs` | Full punishment log including the **Issued By** column. Moderators below cannot see the issuer column. |
| `moderation.view_all_history` | View punishment history for any player, not just their own cases. |
| `players.search` | Search for any player by name or UUID. |
| `players.view_profile` | Open any player's profile page. |
| `rank_requests.view` | See the pending rank request queue (read-only). Cannot approve or deny. |
| `rank_requests.create` | Submit a rank change request for a player. **Target rank must be `helper` or below.** Requests for `moderator`+ must come from admin. |
| `chat.view_staff_channel` | Read and post in the staff chat channel. |

#### Permissions — DENY (explicit)

| Permission | Reason |
|---|---|
| `rank_requests.approve` | Approval is admin+ only. |
| `rank_requests.deny` | Denial is admin+ only. |
| `server.restart` | Owner / developer only. |
| `server.maintenance` | Admin+ only. |
| `server.config` | Admin+ only. |
| `economy.modify` | Admin+ only. |
| `events.configure` | Admin+ only. Sr. Mods can start/stop events in-game but cannot configure them. |
| `moderation.import` | Admin+ only. |

#### Key distinctions vs Moderator

- A **Moderator** sees punishment log entries but the "Issued By" column is hidden.  
  A **Sr. Mod** sees the full "Issued By" column — they can audit which mod issued what.
- A **Moderator** cannot submit rank requests.  
  A **Sr. Mod** can submit requests up to `helper`. They still cannot approve their own requests.
- Neither Moderators nor Sr. Mods can approve rank requests — that floor is **Admin**.

#### In-game command access (reference)

Granted via the rank ladder automatically:
```
/kick  /warn  /ban  /mute  /tempban  /tempmute  /unban  /unmute
/freeze  /slowmode  /staffchat  /history  /vanish  /notes  /whois
/generatepassword
/event start <event>  /event stop  /event complete <winner>  /event status  /event list
```

Denied (admin+ gate in `CommandSecurityListener`):
```
/event setup      — admin+ only
/event rebuildworld — admin+ only
```

`/setrank` is accessible but capped: Sr. Mod can only assign `helper` or below.
Attempts to assign `moderator` or above are rejected by the command.

---

## System Prompt Snippet (for AI/LLM integrations)

Copy this block into any AI assistant system prompt that needs to interact with
the server via RCON:

```
You are connected to a JebaitedCore Paper Minecraft server via RCON.
Available commands:

RESTART:
  restart [<time>] [<reason>]   – schedule restart (30s / 5m / 1h, or bare seconds)
  restart cancel                – cancel pending restart
  restart status                – check if restart is pending

MODERATION:
  kick <player> [reason]
  ban <player> [reason]
  tempban <player> <time> [reason]
  unban <player>
  mute <player> [reason]
  tempmute <player> <time> [reason]
  unmute <player>
  warn <player> <reason>
  freeze <player>
  history <player>
  clearchat
  slowmode <seconds|off>

ECONOMY:
  eco give|take|set <player> <amount>
  balance <player>

RANKS:
  setrank <player> <rank>
  rank get <player>
  rank set <player> <rank>

MAINTENANCE:
  maintenance on|off|status
  maintenance add|remove|list <player>

EVENTS:
  event status|list
  event start <type>
  event stop
  event complete <winner>

All commands return the same chat output they would send to a player.
Strip § colour codes from output before displaying to users.
RCON output may contain ANSI escape codes — strip those too.
```
