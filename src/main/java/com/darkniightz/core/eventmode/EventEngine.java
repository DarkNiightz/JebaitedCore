package com.darkniightz.core.eventmode;

import com.darkniightz.core.eventmode.handler.*;
import com.darkniightz.core.eventmode.team.TeamEngine;
import com.darkniightz.core.party.PartyManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.system.BossBarManager;
import com.darkniightz.core.system.BroadcasterManager;
import com.darkniightz.main.JebaitedCore;
import com.darkniightz.main.database.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core events brain. Owns the full lifecycle:
 *   IDLE → OPEN → LOBBY_COUNTDOWN → RUNNING → ENDING → IDLE
 *
 * EventModeManager is a thin façade that delegates every call here.
 * External callers (commands, listeners) should always go through EventModeManager.
 * Combat / signup events only. Chat mini-games use {@link ChatGameManager}.
 */
@SuppressWarnings({"deprecation", "removal"})
public final class EventEngine {

    /** Result type returned by every engine action. */
    public record ActionResult(boolean ok, String message) {}

    /** Immutable snapshot for V006 async DB writes after an event ends or is stopped. */
    /**
     * @param coWinners empty for a single winner ({@code winnerOrNull} only); non-empty means
     *                  cosmetic coin reward is split evenly across this set (KOTH ties).
     */
    private record EventPersistenceSnap(
            int sessionId,
            String eventType,
            String arenaKey,
            Set<UUID> active,
            Set<UUID> eliminated,
            Set<UUID> spectating,
            UUID winnerOrNull,
            Set<UUID> coWinners,
            int rewardCoins,
            Map<UUID, Integer> kills,
            Map<UUID, Integer> deaths
    ) {}

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final Plugin plugin;
    private final BroadcasterManager broadcasterManager;
    private final BossBarManager bossBarManager;
    private final PartyManager partyManager;

    // ── Live session ─────────────────────────────────────────────────────────
    private volatile EventSession session;
    /** Winner claim bucket persisted across event teardown until player claims via /loot. */
    private final Map<UUID, List<ItemStack>> pendingHardcoreLootClaims = new HashMap<>();

    // ── Handler instances ─────────────────────────────────────────────────────
    private FfaHandler  ffaHandler;
    private KothHandler kothHandler;
    private DuelsHandler duelsHandler;
    private CtfHandler   ctfHandler;

    // ── Bukkit tasks ──────────────────────────────────────────────────────────
    private BukkitTask autoTask;
    private BukkitTask tickTask;         // 1 s tick (KOTH + lobby countdown)
    private BukkitTask countdownTask;    // lobby countdown boss bar update

    // ── Arena spawn cache (DB-backed) ─────────────────────────────────────────
    private final Map<String, List<Location>> spawnCache = new ConcurrentHashMap<>();

    // ── Admin edit access ─────────────────────────────────────────────────────
    private final Map<UUID, Long> adminEditAccessUntil = new ConcurrentHashMap<>();

    // ── Lobby countdown config ─────────────────────────────────────────────────
    private BossBar countdownBar;

    private final EventArenaRegistry arenaRegistry = new EventArenaRegistry();

    public EventEngine(Plugin plugin, BroadcasterManager broadcasterManager, BossBarManager bossBarManager, PartyManager partyManager) {
        this.plugin              = plugin;
        this.broadcasterManager  = broadcasterManager;
        this.bossBarManager      = bossBarManager;
        this.partyManager        = partyManager;
        initHandlers();
    }

    // ── Public lifecycle ──────────────────────────────────────────────────────

    public void reloadArenasFromConfig() {
        arenaRegistry.reload(plugin.getConfig(), plugin.getLogger());
    }

    public List<String> listArenaKeysForKind(String eventKindKey) {
        return arenaRegistry.listArenaKeysForKind(eventKindKey);
    }

    public synchronized void start() {
        arenaRegistry.reload(plugin.getConfig(), plugin.getLogger());
        stopAutoTask();
        long everyTicks = Math.max(20L, plugin.getConfig().getLong("event_mode.interval-seconds", 300L) * 20L);
        autoTask = Bukkit.getScheduler().runTaskTimer(plugin, this::autoTick, everyTicks, everyTicks);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            initSpawnsTable();
            loadSpawnsFromDB();
        });
    }

    public synchronized void stop() {
        stopAutoTask();
        doStopEvent("plugin shutdown");
    }

    public synchronized boolean isActive() {
        return session != null && session.state != EventState.IDLE;
    }

    public synchronized String getStatusLine() {
        if (session == null || session.state == EventState.IDLE) {
            return "§7No active event.";
        }
        EventSession s = session;
        return switch (s.state) {
            case OPEN -> "§dQueue open: §f" + s.spec.displayName + " §8| §7joined: §f"
                    + s.queued.size() + "§7/" + s.spec.maxPlayers;
            case LOBBY_COUNTDOWN -> "§dStarting: §f" + s.spec.displayName + " §8| §7in §f"
                    + s.countdownSecondsLeft + "s §8| §7" + s.queued.size() + " players";
            case RUNNING -> {
                if (s.spec.kind.isKoth()) {
                    long remain = Math.max(0L, (s.endsAtMs - System.currentTimeMillis()) / 1000L);
                    yield "§dActive: §f" + s.spec.displayName + " §8| §7remaining: §f" + remain + "s";
                }
                yield "§dActive: §f" + s.spec.displayName + " §8| §7participants: §f" + s.active.size();
            }
            default -> "§dEvent: §f" + s.spec.displayName + " §8| §7" + s.state;
        };
    }

    // ── Command actions ───────────────────────────────────────────────────────

    public synchronized ActionResult startEvent(String requestedKey) {
        return startEvent(requestedKey, null);
    }

    public synchronized ActionResult startEvent(String requestedKey, String arenaKeyOrNull) {
        if (session != null && session.state != EventState.IDLE) {
            return new ActionResult(false, "§cAn event is already active: §f" + session.spec.displayName);
        }
        String norm = normalizeEventKey(requestedKey);
        if (ChatGameKeys.isChatGameConfigKey(norm)) {
            return new ActionResult(false, "§cChat games use §f/chatgame start " + norm + " §c(not /event).");
        }
        EventSpec spec = loadSpec(requestedKey);
        if (spec == null || !spec.enabled) {
            return new ActionResult(false, "§cUnknown or disabled event: §e" + requestedKey);
        }
        if (arenaKeyOrNull != null && !arenaKeyOrNull.isBlank()) {
            String ak = arenaKeyOrNull.toLowerCase(Locale.ROOT).trim();
            if (arenaRegistry.get(spec.key, ak) == null) {
                return new ActionResult(false, "§cUnknown arena key §e" + ak + " §cfor §e" + spec.key);
            }
        }
        beginEvent(spec, false, arenaKeyOrNull);
        return new ActionResult(true, "§aStarted event: §f" + spec.displayName);
    }

    public synchronized ActionResult stopEvent(String actorReason) {
        if (session == null || session.state == EventState.IDLE) {
            return new ActionResult(false, "§7No active event to stop.");
        }
        String ended = session.spec.displayName;
        String catKey = session.spec.key;
        doStopEvent(actorReason);
        EventNotifications.broadcastCategoryOptional(plugin, catKey, "§7Event stopped: §f" + ended + " §7(" + actorReason + ")");
        return new ActionResult(true, "§7Event stopped.");
    }

    public synchronized ActionResult forceStart() {
        if (session == null || session.state != EventState.LOBBY_COUNTDOWN) {
            return new ActionResult(false, "§7No lobby countdown is active.");
        }
        stopCountdownTask();
        launchEvent();
        return new ActionResult(true, "§aForce-started.");
    }

    public synchronized ActionResult completeEvent(Player winner, Integer rewardOverride, String reason) {
        if (session == null || session.state != EventState.RUNNING) {
            return new ActionResult(false, "§7No running event.");
        }
        if (winner == null) return new ActionResult(false, "§cWinner is required.");
        session.active.add(winner.getUniqueId());
        int reward = rewardOverride != null ? Math.max(0, rewardOverride) : session.runtimeCoinReward;
        finalizeEvent(winner.getUniqueId(), reward, reason == null ? "completed" : reason);
        return new ActionResult(true, "§aEvent completed. Winner: §f" + winner.getName());
    }

    // ── Queue ─────────────────────────────────────────────────────────────────

    public synchronized ActionResult joinQueue(Player player, boolean confirmedHardcore) {
        if (session == null
                || (session.state != EventState.OPEN && session.state != EventState.LOBBY_COUNTDOWN)
                || !session.spec.kind.isSignup()) {
            return new ActionResult(false, "§7No joinable signup event is active.");
        }
        if (player == null || !isSmpPlayer(player)) {
            return new ActionResult(false, "§cYou must be in SMP to join this event.");
        }
        if (session.queued.contains(player.getUniqueId())) {
            return new ActionResult(false, "§7You are already in the queue.");
        }
        if (session.queued.size() >= session.spec.maxPlayers) {
            return new ActionResult(false, "§cQueue is full.");
        }
        if (session.spec.kind.isHardcore() && !confirmedHardcore) {
            sendHardcoreWarning(player);
            return new ActionResult(false, "§cHardcore warning sent. Click above or run §f/event join confirm§c.");
        }
        adminEditAccessUntil.remove(player.getUniqueId());
        session.queued.add(player.getUniqueId());
        broadcast("§f" + player.getName() + " §7joined §d" + session.spec.displayName
                + " §8(§a" + session.queued.size() + "§8/§7" + session.spec.maxPlayers + "§8)");

        // Transition: OPEN → LOBBY_COUNTDOWN when minPlayers met
        if (session.state == EventState.OPEN && session.queued.size() >= session.spec.minPlayers) {
            startLobbyCountdown();
        }
        int needed = session.spec.minPlayers - session.queued.size();
        if (needed > 0) {
            return new ActionResult(true, "§aYou joined §d" + session.spec.displayName
                    + "§a! Waiting for §f" + needed + "§a more player" + (needed == 1 ? "" : "s")
                    + " §7(" + session.queued.size() + "/" + session.spec.maxPlayers + ")");
        }
        return new ActionResult(true, "§aYou joined §d" + session.spec.displayName + "§a! Starting soon...");
    }

    public synchronized ActionResult leaveQueue(Player player) {
        if (session == null
                || (session.state != EventState.OPEN && session.state != EventState.LOBBY_COUNTDOWN)
                || player == null) {
            return new ActionResult(false, "§7You are not in a queue.");
        }
        if (!session.queued.remove(player.getUniqueId())) {
            return new ActionResult(false, "§7You are not in the queue.");
        }
        // If countdown was running and we've dropped below minPlayers, revert to OPEN
        if (session.state == EventState.LOBBY_COUNTDOWN
                && session.queued.size() < session.spec.minPlayers) {
            session.state = EventState.OPEN;
            stopCountdownTask();
            removeCountdownBar();
            broadcast("§7Countdown cancelled — not enough players.");
        }
        return new ActionResult(true, "§7You left the event queue.");
    }

    // ── Participant lifecycle (called by EventModeCombatListener) ─────────────

    /**
     * Called when a participant receives damage that would be fatal.
     * The damage event has already been cancelled — the player is alive.
     * For FFA/Duels: mark eliminated, switch to SPECTATOR, schedule 5s end if last alive.
     * For KOTH: strip HC loot if applicable, teleport to arena spawns or SMP world spawn, stays active.
     */
    public synchronized void handleParticipantFatalDamage(Player player, Player killerOrNull) {
        if (player == null || session == null || session.state != EventState.RUNNING) return;
        UUID id = player.getUniqueId();
        if (!session.active.contains(id)) return;

        session.eventDeaths.computeIfAbsent(id, u -> new AtomicInteger()).incrementAndGet();
        if (killerOrNull != null) {
            UUID kid = killerOrNull.getUniqueId();
            if (!kid.equals(id) && session.active.contains(kid)) {
                session.eventKills.computeIfAbsent(kid, u -> new AtomicInteger()).incrementAndGet();
            }
        }

        EventKind kind   = session.spec.kind;
        boolean isHC     = kind.isHardcore();
        boolean isKoth   = kind == EventKind.KOTH || kind == EventKind.HARDCORE_KOTH;

        // HC: collect whatever is in inventory into the loot pool, then strip it
        if (isHC) {
            collectHardcoreLootFromInventory(player);
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.getInventory().setItemInOffHand(null);
            player.updateInventory();
        }

        if (kind == EventKind.CTF) {
            session.eventDeaths.computeIfAbsent(id, u -> new AtomicInteger()).incrementAndGet();
            if (killerOrNull != null) {
                UUID kid = killerOrNull.getUniqueId();
                if (!kid.equals(id) && session.active.contains(kid)) {
                    session.eventKills.computeIfAbsent(kid, u -> new AtomicInteger()).incrementAndGet();
                }
            }
            ctfHandler.onParticipantDowned(session, player);
            return;
        }

        if (isKoth) {
            boolean haveSpawns = !getArenaSpawnsForSession(session).isEmpty();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                player.setHealth(player.getMaxHealth());
                player.setFoodLevel(20);
                Location spawn = pickKothRespawnLocation(session);
                if (spawn != null) player.teleport(spawn);
                String dest = haveSpawns ? "a ring spawn." : "world spawn.";
                player.sendMessage(Component.text(
                        "☠ Knocked down! Respawning at " + dest,
                        NamedTextColor.RED));
            });
        } else {
            // FFA / Duels / HC variants: elimination — convert to spectator
            session.eliminated.add(id);
            // Move snapshot to spectator bucket so restoreSnapshots restores gamemode to SURVIVAL
            InventorySnapshot snap = session.snapshots.remove(id);
            session.spectatorSnapshots.put(id, snap);
            session.spectating.add(id);

            // Check if the event should end (schedules 5s finalise if only 1 alive)
            checkAndScheduleEnd();

            // Switch to spectator next tick (can't safely change gamemode mid-damage-event)
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                player.setHealth(player.getMaxHealth());
                player.setFoodLevel(20);
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage(Component.text("✗ Eliminated! Spectating the rest of the event.",
                        NamedTextColor.RED));
            });
        }
    }

    /** Collects whatever is currently in a player's inventory into the HC loot pool. */
    private void collectHardcoreLootFromInventory(Player player) {
        if (session == null) return;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) session.hardcoreLootPool.add(item.clone());
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null && item.getType() != Material.AIR) session.hardcoreLootPool.add(item.clone());
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off != null && off.getType() != Material.AIR) session.hardcoreLootPool.add(off.clone());
    }

    /**
     * Checks whether the FFA/Duels event is down to 1 survivor and, if so, transitions
     * to ENDING and schedules finaliseEvent after a 5-second grace period.
     * No-op if called while state != RUNNING (prevents double-fire).
     */
    private synchronized void checkAndScheduleEnd() {
        if (session == null || session.state != EventState.RUNNING) return;
        List<UUID> alive = new ArrayList<>();
        for (UUID uid : session.active) {
            if (!session.eliminated.contains(uid)) alive.add(uid);
        }
        if (alive.size() <= 1 && !session.active.isEmpty()) {
            UUID winner = alive.isEmpty() ? session.active.iterator().next() : alive.get(0);
            session.state = EventState.ENDING; // prevents re-entry from any concurrent path
            broadcast("§d★ §f" + session.spec.displayName + " §7ending in §c5 §7seconds...");
            final UUID finalWinner = winner;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                synchronized (EventEngine.this) {
                    if (session == null || session.state != EventState.ENDING) return;
                    finalizeEvent(finalWinner, session.runtimeCoinReward,
                            "won " + session.spec.displayName);
                }
            }, 100L);
        }
    }

    public synchronized void handleParticipantDeath(Player player) {
        if (player == null || session == null || session.state != EventState.RUNNING) return;
        if (!session.active.contains(player.getUniqueId())) return;
        getHandler(session.spec.kind).onDeath(session, player);
    }

    public synchronized boolean isParticipant(Player player) {
        if (player == null || session == null) return false;
        UUID uuid = player.getUniqueId();
        return session.active.contains(uuid) || session.queued.contains(uuid);
    }

    /** Active combatants in a {@link EventState#RUNNING} session (excludes lobby queue). */
    public synchronized boolean isActiveEventParticipant(Player player) {
        if (player == null || session == null || session.state != EventState.RUNNING) return false;
        return session.active.contains(player.getUniqueId());
    }

    /**
     * True when both are on the same CTF team. Used so party friendly-fire rules can still
     * block damage between party members on the same side (until dedicated team FF exists).
     */
    public synchronized boolean areCtfTeammates(Player a, Player b) {
        if (a == null || b == null || session == null || session.state != EventState.RUNNING) return false;
        if (session.spec.kind != EventKind.CTF) return false;
        UUID ua = a.getUniqueId(), ub = b.getUniqueId();
        if (!session.active.contains(ua) || !session.active.contains(ub)) return false;
        var ta = TeamEngine.teamOf(session, ua);
        var tb = TeamEngine.teamOf(session, ub);
        return ta != null && ta == tb;
    }

    public synchronized boolean isParticipantInHardcore(Player player) {
        if (player == null || session == null || session.state != EventState.RUNNING) return false;
        return session.spec.kind.isHardcore() && session.active.contains(player.getUniqueId());
    }

    public synchronized void collectHardcoreLoot(Player player, List<ItemStack> drops) {
        if (drops == null || drops.isEmpty()) return;
        if (session == null) return;
        for (ItemStack item : drops) {
            if (item != null && item.getType() != Material.AIR) {
                session.hardcoreLootPool.add(item.clone());
            }
        }
    }

    public synchronized boolean shouldKeepInventoryOnDeath(Player player) {
        if (player == null || session == null || session.state != EventState.RUNNING) return false;
        EventKind kind = session.spec.kind;
        boolean isCombatEvent = kind == EventKind.KOTH || kind == EventKind.FFA || kind == EventKind.DUELS
                || kind.isHardcore();
        if (!isCombatEvent) return false;
        return session.active.contains(player.getUniqueId()) && !kind.isHardcore();
    }

    public synchronized void handleParticipantRespawn(Player player) {
        if (player == null || session == null) return;
        getHandler(session.spec.kind).onRespawn(session, player);
    }

    // ── Arena setup ───────────────────────────────────────────────────────────

    public synchronized ActionResult setupKothPosition(Player player, boolean firstPos) {
        if (player == null) return new ActionResult(false, "§cOnly players can set KOTH positions.");
        String base = firstPos ? "event_mode.koth.pos1" : "event_mode.koth.pos2";
        Location loc = player.getLocation();
        plugin.getConfig().set("event_mode.koth.world", loc.getWorld() == null ? "smp" : loc.getWorld().getName());
        plugin.getConfig().set(base + ".x", loc.getBlockX());
        plugin.getConfig().set(base + ".y", loc.getBlockY());
        plugin.getConfig().set(base + ".z", loc.getBlockZ());
        plugin.saveConfig();
        return new ActionResult(true, "§aSet KOTH " + (firstPos ? "pos1" : "pos2") + ".");
    }

    public synchronized ActionResult setupArenaSpawn(Player player, String arenaKeyRaw) {
        if (player == null) return new ActionResult(false, "§cOnly players can set arena spawns.");
        String arenaKey = normalizeArenaKey(arenaKeyRaw);
        if (!isValidArenaKey(arenaKey)) {
            return new ActionResult(false, "§cArena setup only supports ffa, duels, and koth (hardcore variants map to ffa/duels/koth).");
        }
        Location loc = player.getLocation();
        List<Location> cached = spawnCache.computeIfAbsent(arenaKey, k -> new ArrayList<>());
        cached.add(loc.clone());
        int spawnNum = cached.size();
        final String encoded = encodeLocation(loc);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!isSpawnDBAvailable()) return;
            try (Connection conn = ((JebaitedCore) plugin).getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO event_spawns (arena_key, world_name, x, y, z, yaw, pitch) VALUES (?,?,?,?,?,?,?)")) {
                String[] parts = encoded.split(":");
                ps.setString(1, arenaKey);
                ps.setString(2, parts[0]);
                ps.setDouble(3, Double.parseDouble(parts[1]));
                ps.setDouble(4, Double.parseDouble(parts[2]));
                ps.setDouble(5, Double.parseDouble(parts[3]));
                ps.setFloat(6, Float.parseFloat(parts[4]));
                ps.setFloat(7, Float.parseFloat(parts[5]));
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[EventSpawns] Failed to save spawn: " + e.getMessage());
            }
        });
        return new ActionResult(true, "§aAdded " + arenaKey.toUpperCase(Locale.ROOT) + " spawn #" + spawnNum + ".");
    }

    public synchronized ActionResult clearArenaSpawns(String arenaKeyRaw) {
        String arenaKey = normalizeArenaKey(arenaKeyRaw);
        if (!isValidArenaKey(arenaKey)) {
            return new ActionResult(false, "§cArena setup only supports ffa, duels, and koth (hardcore variants map to ffa/duels/koth).");
        }
        spawnCache.put(arenaKey, new ArrayList<>());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!isSpawnDBAvailable()) return;
            try (Connection conn = ((JebaitedCore) plugin).getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM event_spawns WHERE arena_key = ?")) {
                ps.setString(1, arenaKey);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[EventSpawns] Failed to clear spawns: " + e.getMessage());
            }
        });
        return new ActionResult(true, "§7Cleared all " + arenaKey.toUpperCase(Locale.ROOT) + " spawns.");
    }

    public synchronized List<String> listArenaSpawns(String arenaKeyRaw) {
        String arenaKey = normalizeArenaKey(arenaKeyRaw);
        List<Location> spawns = spawnCache.getOrDefault(arenaKey, List.of());
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < spawns.size(); i++) {
            Location loc = spawns.get(i);
            String world = loc.getWorld() != null ? loc.getWorld().getName() : "?";
            lines.add(String.format("#%d §7%s §fx=%.1f y=%.1f z=%.1f", i + 1, world, loc.getX(), loc.getY(), loc.getZ()));
        }
        return lines;
    }

    public synchronized ActionResult viewArenaSpawns(Player player, String arenaKeyRaw, int seconds) {
        String arenaKey = normalizeArenaKey(arenaKeyRaw);
        List<Location> spawns = spawnCache.getOrDefault(arenaKey, List.of());
        if (spawns.isEmpty()) {
            return new ActionResult(false, "§7No spawns configured for §f" + arenaKey.toUpperCase(Locale.ROOT) + "§7.");
        }
        int duration = Math.max(5, Math.min(120, seconds));
        Material glassType = Material.WHITE_STAINED_GLASS_PANE;
        org.bukkit.block.data.BlockData glassData = glassType.createBlockData();
        List<Location> fakeLocations = new ArrayList<>();
        for (Location base : spawns) {
            if (base.getWorld() == null) continue;
            for (int dy = 0; dy <= 2; dy++) {
                Location fakeLoc = base.clone().add(0, dy, 0);
                fakeLocations.add(fakeLoc);
                player.sendBlockChange(fakeLoc, glassData);
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            for (Location fakeLoc : fakeLocations) {
                if (fakeLoc.getWorld() == null) continue;
                player.sendBlockChange(fakeLoc, fakeLoc.getWorld().getBlockAt(fakeLoc).getBlockData());
            }
        }, duration * 20L);
        return new ActionResult(true, "§aShowing §f" + spawns.size() + " §a" + arenaKey.toUpperCase(Locale.ROOT)
                + " spawn" + (spawns.size() == 1 ? "" : "s") + " §7(" + duration + "s preview).");
    }

    public synchronized Location getFirstArenaSpawn(String arenaKeyRaw) {
        String arenaKey = normalizeArenaKey(arenaKeyRaw);
        List<Location> spawns = spawnCache.getOrDefault(arenaKey, List.of());
        return spawns.isEmpty() ? null : spawns.get(0).clone();
    }

    public synchronized int getArenaSpawnCount(String arenaKeyRaw) {
        return spawnCache.getOrDefault(normalizeArenaKey(arenaKeyRaw), List.of()).size();
    }

    // ── Config / event list ───────────────────────────────────────────────────

    public synchronized List<String> getConfiguredEventKeys() {
        var sec = plugin.getConfig().getConfigurationSection("event_mode.events");
        if (sec == null) return List.of("koth", "ffa", "duels");
        Set<String> seen = new HashSet<>();
        List<String> out = new ArrayList<>();
        for (String key : sec.getKeys(false)) {
            String normalized = normalizeEventKey(key);
            if (ChatGameKeys.isChatGameConfigKey(normalized)) {
                continue;
            }
            if (sec.getBoolean(key + ".enabled", true) && seen.add(normalized)) out.add(normalized);
        }
        return out;
    }

    public synchronized List<String> getConfiguredEventDisplayNames() {
        List<String> out = new ArrayList<>();
        for (String key : getConfiguredEventKeys()) {
            EventSpec spec = loadSpec(key);
            if (spec != null && spec.enabled) out.add(spec.displayName);
        }
        return out;
    }

    // ── Admin edit access ─────────────────────────────────────────────────────

    public void grantAdminEditAccess(Player player) {
        if (player == null) return;
        long secs = Math.max(60L, plugin.getConfig().getLong("event_mode.admin_edit_window_seconds", 600L));
        adminEditAccessUntil.put(player.getUniqueId(), System.currentTimeMillis() + secs * 1000L);
    }

    public boolean canAdminEdit(Player player) {
        if (player == null) return false;
        UUID id = player.getUniqueId();
        if (session != null && (session.queued.contains(id) || session.active.contains(id))) {
            adminEditAccessUntil.remove(id);
            return false;
        }
        Long until = adminEditAccessUntil.get(id);
        if (until == null) return false;
        if (until < System.currentTimeMillis()) {
            adminEditAccessUntil.remove(id);
            return false;
        }
        return true;
    }

    public Location getAdminEditSpawn() {
        Location spawn = getEventWorldSpawn();
        return spawn == null ? getWorldSpawn() : spawn.clone();
    }

    // ── Event world ───────────────────────────────────────────────────────────

    public synchronized ActionResult rebuildEventWorld(boolean confirmed) {
        if (!confirmed) {
            return new ActionResult(false, "§cThis deletes the current event world. Run §f/event rebuildworld confirm§c.");
        }
        if (session != null && session.state != EventState.IDLE) {
            return new ActionResult(false, "§cStop the current event before rebuilding the event world.");
        }
        String eventWorldName = plugin.getConfig().getString("event_mode.world", "events");
        if (eventWorldName == null || eventWorldName.isBlank()) {
            return new ActionResult(false, "§cNo event world is configured.");
        }
        Location fallback = getWorldSpawn();
        World existing = Bukkit.getWorld(eventWorldName);
        if (existing != null) {
            for (Player p : new ArrayList<>(existing.getPlayers())) {
                if (fallback != null) p.teleport(fallback);
            }
            if (!Bukkit.unloadWorld(existing, false)) {
                return new ActionResult(false, "§cCould not unload the current event world.");
            }
        }
        File folder = new File(Bukkit.getWorldContainer(), eventWorldName);
        if (folder.exists() && !deleteRecursively(folder)) {
            return new ActionResult(false, "§cCould not delete the old event world folder. Remove it manually and restart.");
        }
        World rebuilt = ensureEventWorld(eventWorldName);
        if (rebuilt == null) return new ActionResult(false, "§cFailed to recreate the event world.");
        rebuilt.setTime(1000L);
        rebuilt.setStorm(false);
        rebuilt.setThundering(false);
        rebuilt.setClearWeatherDuration(20 * 60 * 20);
        return new ActionResult(true, "§aEvent world rebuilt as a fresh superflat map: §f" + rebuilt.getName());
    }

    // ── Scoreboard integration ────────────────────────────────────────────────

    public synchronized List<String> getEventScoreboardLines() {
        if (session == null || session.state != EventState.RUNNING) return List.of();
        return getHandler(session.spec.kind).getScoreboardLines(session);
    }

    public synchronized List<String> listArenaRegistryLines() {
        return arenaRegistry.describeAll();
    }

    public synchronized String getEventInfoSummary() {
        if (session == null || session.state == EventState.IDLE) {
            return "§7No active event.";
        }
        EventSession s = session;
        String arena = s.selectedArenaKey != null ? s.selectedArenaKey : "§8legacy";
        return "§d" + s.spec.displayName + " §8| §7" + s.state + " §8| §7kind §f" + s.spec.kind
                + " §8| §7arena §f" + arena
                + " §8| §7queue §f" + s.queued.size() + " §8| §7active §f" + s.active.size();
    }

    public synchronized ActionResult setRuntimeCoinReward(int coins) {
        if (session == null || session.state == EventState.IDLE) {
            return new ActionResult(false, "§7No active event.");
        }
        session.runtimeCoinReward = Math.max(0, coins);
        return new ActionResult(true, "§aCoin reward set to §f" + session.runtimeCoinReward);
    }

    public synchronized ActionResult staffSpectateEnter(Player player) {
        if (player == null || !player.isOnline()) {
            return new ActionResult(false, "§cPlayer required.");
        }
        if (session == null || session.state != EventState.RUNNING) {
            return new ActionResult(false, "§7No running event to spectate.");
        }
        UUID id = player.getUniqueId();
        if (session.active.contains(id) || session.queued.contains(id)) {
            return new ActionResult(false, "§7Leave the event first.");
        }
        if (session.spectatorVisitors.contains(id)) {
            return new ActionResult(false, "§7You are already spectating.");
        }
        Location ret = player.getLocation().clone();
        GameMode prev = player.getGameMode();
        session.spectatorVisitorState.put(id, new SpectatorVisitState(ret, prev));
        session.spectatorVisitors.add(id);
        Location dest = getSpectatorDestination();
        if (dest != null) player.teleport(dest);
        player.setGameMode(GameMode.SPECTATOR);
        return new ActionResult(true, "§aSpectating §f" + session.spec.displayName + "§a. Run §f/event spectate leave§a to exit.");
    }

    public synchronized ActionResult staffSpectateLeave(Player player) {
        if (player == null) return new ActionResult(false, "§cPlayer required.");
        UUID id = player.getUniqueId();
        if (session == null || !session.spectatorVisitors.contains(id)) {
            return new ActionResult(false, "§7You are not event-spectating.");
        }
        SpectatorVisitState st = session.spectatorVisitorState.remove(id);
        session.spectatorVisitors.remove(id);
        if (player.isOnline()) {
            player.setGameMode(st != null && st.previousMode() != null ? st.previousMode() : GameMode.SURVIVAL);
            if (st != null && st.returnLocation() != null && st.returnLocation().getWorld() != null) {
                player.teleport(st.returnLocation());
            }
        }
        return new ActionResult(true, "§7Returned from event spectate.");
    }

    public synchronized void handleCtfFlagInteract(Player player, org.bukkit.block.Block block) {
        if (session == null || session.state != EventState.RUNNING || session.spec.kind != EventKind.CTF) return;
        if (!session.active.contains(player.getUniqueId())) return;
        ctfHandler.handleInteract(player, block, session);
    }

    /** @return true if the pickup should be cancelled (CTF ground flag item). */
    public synchronized boolean handleCtfGroundFlagPickup(Player player, org.bukkit.entity.Item item) {
        if (session == null || session.state != EventState.RUNNING || session.spec.kind != EventKind.CTF) {
            return false;
        }
        if (!session.active.contains(player.getUniqueId())) return false;
        return ctfHandler.handleGroundFlagPickup(player, item, session);
    }

    private Location getSpectatorDestination() {
        if (session == null) return getEventWorldSpawn();
        List<Location> sp = getArenaSpawnsForSession(session);
        if (!sp.isEmpty()) return sp.get(0).clone();
        return getEventWorldSpawn();
    }

    private void restoreStaffSpectators() {
        if (session == null) return;
        for (Map.Entry<UUID, SpectatorVisitState> e : new HashMap<>(session.spectatorVisitorState).entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || !p.isOnline()) continue;
            SpectatorVisitState st = e.getValue();
            p.setGameMode(st.previousMode() != null ? st.previousMode() : GameMode.SURVIVAL);
            if (st.returnLocation() != null && st.returnLocation().getWorld() != null) {
                p.teleport(st.returnLocation());
            }
        }
        session.spectatorVisitorState.clear();
        session.spectatorVisitors.clear();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ── Private implementation ────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    private void initHandlers() {
        FfaHandler sharedFfa = new FfaHandler(plugin,
                this::onEliminationTrigger,
                this::getWorldSpawn);
        ffaHandler   = sharedFfa;
        duelsHandler = new DuelsHandler(sharedFfa);
        kothHandler  = new KothHandler(plugin,
                this::getKothCuboidForSession,
                this::pickKothRespawnLocation,
                this::onKothExpired);
        ctfHandler   = new CtfHandler(plugin);
    }

    private EventHandler getHandler(EventKind kind) {
        return switch (kind) {
            case KOTH, HARDCORE_KOTH    -> kothHandler;
            case DUELS, HARDCORE_DUELS  -> duelsHandler;
            case CTF                    -> ctfHandler;
            default                     -> ffaHandler;
        };
    }

    // Called by FfaHandler when elimination triggers an event-end check
    private synchronized void onEliminationTrigger(EventSession s) {
        if (s != session || session.state != EventState.RUNNING) return;
        List<UUID> alive = new ArrayList<>();
        for (UUID id : session.active) {
            if (!session.eliminated.contains(id)) alive.add(id);
        }
        if (alive.size() <= 1 && !session.active.isEmpty()) {
            UUID winner = alive.isEmpty() ? session.active.iterator().next() : alive.get(0);
            session.state = EventState.ENDING;
            broadcast("§d★ §f" + session.spec.displayName + " §7ending in §c5 §7seconds...");
            final UUID finalWinner = winner;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                synchronized (EventEngine.this) {
                    if (session == null || session.state != EventState.ENDING) return;
                    finalizeEvent(finalWinner, session.runtimeCoinReward,
                            "won " + session.spec.displayName);
                }
            }, 100L);
        }
    }

    // Called by KothHandler when its timer expires
    private synchronized void onKothExpired(EventSession s) {
        if (s != session || session.state != EventState.RUNNING) return;
        int maxSec = session.kothSeconds.values().stream().max(Integer::compareTo).orElse(0);
        List<UUID> tops = session.kothSeconds.entrySet().stream()
                .filter(e -> e.getValue() == maxSec && maxSec > 0)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (tops.isEmpty()) {
            UUID winner = session.active.isEmpty() ? null : session.active.iterator().next();
            if (winner == null) {
                broadcast("§7" + session.spec.displayName + " ended with no winner.");
                EventPersistenceSnap snap = null;
                if (session.persistenceSessionId > 0) {
                    snap = takePersistenceSnapshot(session, null, 0);
                }
                clearState();
                persistFinalizeFromSnapshotAsync(snap);
                return;
            }
            finalizeEvent(winner, session.runtimeCoinReward, "won " + session.spec.displayName);
            return;
        }
        if (tops.size() == 1) {
            finalizeEvent(tops.get(0), session.runtimeCoinReward, "won " + session.spec.displayName);
            return;
        }
        finalizeKothTiedWinners(new ArrayList<>(tops), session.runtimeCoinReward);
    }

    private synchronized void beginEvent(EventSpec spec, boolean automated, String arenaKeyOrNull) {
        session = new EventSession(spec);
        session.runtimeCoinReward = spec.coinReward;
        String arenaNorm = null;
        if (arenaKeyOrNull != null && !arenaKeyOrNull.isBlank()) {
            String ak = arenaKeyOrNull.toLowerCase(Locale.ROOT).trim();
            if (arenaRegistry.get(spec.key, ak) != null) {
                arenaNorm = ak;
            }
        }
        if (arenaNorm == null) {
            ArenaConfig def = arenaRegistry.defaultArena(spec.key);
            arenaNorm = def != null ? def.key().toLowerCase(Locale.ROOT) : null;
        }
        session.selectedArenaKey = arenaNorm;

        if (spec.kind.isSignup()) {
            session.state = EventState.OPEN;
            announceSignup(spec, automated);
            return;
        }

        session.state = EventState.RUNNING;
        broadcast("§dEvent started: §f" + spec.displayName);
    }

    /** Transitions OPEN → LOBBY_COUNTDOWN and starts the boss bar countdown. */
    private void startLobbyCountdown() {
        if (session == null) return;
        int seconds = Math.max(5, plugin.getConfig().getInt("event_mode.lobby_countdown_seconds", 30));
        session.state = EventState.LOBBY_COUNTDOWN;
        session.countdownSecondsLeft = seconds;

        broadcast("§d" + session.spec.displayName + " §7starts in §f" + seconds + "s§7! Run §f/event join§7 to sign up.");
        createCountdownBar();
        updateCountdownBossBar();

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            synchronized (EventEngine.this) {
                if (session == null || session.state != EventState.LOBBY_COUNTDOWN) {
                    stopCountdownTask();
                    return;
                }
                session.countdownSecondsLeft--;
                updateCountdownBossBar();
                if (session.countdownSecondsLeft <= 0) {
                    stopCountdownTask();
                    launchEvent();
                } else if (session.countdownSecondsLeft <= 5
                        || session.countdownSecondsLeft % 10 == 0) {
                    broadcast("§d" + session.spec.displayName + " §7starting in §f"
                            + session.countdownSecondsLeft + "§7s...");
                }
            }
        }, 20L, 20L);
    }

    private void createCountdownBar() {
        if (countdownBar == null) {
            countdownBar = Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SOLID);
        }
        countdownBar.removeAll();
        syncCountdownBarPlayers();
        countdownBar.setVisible(true);
    }

    /** Per-player opt-in via {@link com.darkniightz.core.system.PresentationPreference}. */
    private void syncCountdownBarPlayers() {
        if (countdownBar == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (com.darkniightz.core.system.PresentationPreference.showEventCountdownBossBar(p)) {
                countdownBar.addPlayer(p);
            } else {
                countdownBar.removePlayer(p);
            }
        }
    }

    private void updateCountdownBossBar() {
        if (session == null || countdownBar == null) return;
        int total   = Math.max(1, plugin.getConfig().getInt("event_mode.lobby_countdown_seconds", 30));
        int left    = session.countdownSecondsLeft;
        float prog  = Math.max(0f, Math.min(1f, (float) left / total));
        countdownBar.setTitle(ChatColor.translateAlternateColorCodes('&',
                "&d" + session.spec.displayName + " &7starting in &f" + left + "s"));
        countdownBar.setProgress(prog);
        syncCountdownBarPlayers();
    }

    /** Transitions LOBBY_COUNTDOWN → RUNNING. Teleports players and takes snapshots. */
    private synchronized void launchEvent() {
        if (session == null || session.state != EventState.LOBBY_COUNTDOWN) return;

        session.active.clear();
        session.active.addAll(session.queued);
        session.queued.clear();
        session.state = EventState.RUNNING;

        removeCountdownBar();

        session.resolvedArenaConfig = resolveArenaConfig(session);

        EventKind kind = session.spec.kind;

        if (kind.isKoth()) {
            if (getKothCuboidForSession(session) == null) {
                broadcast("§cKOTH is not configured (arena_registry hill or /event setup koth). Event aborted.");
                clearState();
                return;
            }
            int durationSeconds = Math.max(30, plugin.getConfig().getInt("event_mode.koth.duration_seconds", 120));
            if (session.resolvedArenaConfig != null && session.resolvedArenaConfig.kothDurationSeconds() != null) {
                durationSeconds = Math.max(30, session.resolvedArenaConfig.kothDurationSeconds());
            }
            session.endsAtMs = System.currentTimeMillis() + durationSeconds * 1000L;

            boolean isHC = kind.isHardcore();
            List<Location> ring = getArenaSpawnsForSession(session);
            int si = 0;
            for (UUID id : session.active) {
                Player p = Bukkit.getPlayer(id);
                if (p == null) continue;
                if (isHC) {
                    if (!session.snapshots.containsKey(id)) {
                        session.snapshots.put(id, InventorySnapshot.capture(p));
                    }
                    p.getInventory().clear();
                    p.getInventory().setArmorContents(new ItemStack[4]);
                    p.getInventory().setItemInOffHand(null);
                    p.updateInventory();
                } else {
                    if (!session.snapshots.containsKey(id)) {
                        session.snapshots.put(id, InventorySnapshot.capture(p));
                    }
                }
                if (!ring.isEmpty()) {
                    Location dest = ring.get(si % ring.size());
                    if (dest != null) p.teleport(dest.clone());
                    si++;
                }
            }

            getHandler(kind).onStart(session);
            startTickTask();
            // KOTH players stay in SMP — set DO_IMMEDIATE_RESPAWN there so the death screen is suppressed.
            setImmediateRespawn(true);
            schedulePersistEventSessionStart(session);
            broadcast("§d" + session.spec.displayName + " started with §f" + session.active.size() + "§d players.");
            discordNotifyEventRunning();
            return;
        }

        if (kind == EventKind.CTF) {
            ArenaConfig.CtfLayout lay = session.resolvedArenaConfig != null
                    ? session.resolvedArenaConfig.ctf() : ArenaConfig.CtfLayout.empty();
            if (!lay.isComplete()) {
                broadcast("§cCTF requires arena_registry." + session.spec.key + " with ctf.red_spawn, blue_spawn, red_flag, blue_flag.");
                clearState();
                return;
            }
            int durationSeconds = Math.max(120, plugin.getConfig().getInt("event_mode.ctf.duration_seconds", 600));
            session.endsAtMs = System.currentTimeMillis() + durationSeconds * 1000L;
            String eventWorldName = plugin.getConfig().getString("event_mode.world", "events");
            ensureEventWorld(eventWorldName);
            TeamEngine.assignCtfTeams(session, partyManager);
            for (UUID id : session.active) {
                Player p = Bukkit.getPlayer(id);
                if (p == null) continue;
                if (!session.snapshots.containsKey(id)) {
                    session.snapshots.put(id, InventorySnapshot.capture(p));
                }
                p.getInventory().clear();
                p.getInventory().setArmorContents(new ItemStack[4]);
                p.getInventory().setItemInOffHand(null);
                boolean red = session.ctfTeamRed.contains(id);
                CtfKitUtil.apply(p, red ? lay.redKit() : lay.blueKit());
                Location dest = red ? lay.redSpawn() : lay.blueSpawn();
                if (dest != null) p.teleport(dest.clone());
            }
            getHandler(kind).onStart(session);
            startTickTask();
            setImmediateRespawn(true);
            schedulePersistEventSessionStart(session);
            broadcast("§d" + session.spec.displayName + " (CTF) started with §f" + session.active.size() + "§d players.");
            return;
        }

        // Always ensure the event world is loaded before any arena logic.
        // ensureEventWorld is idempotent — if already loaded it just sets gamerules.
        // This is what actually sets DO_IMMEDIATE_RESPAWN=true on the events world.
        String eventWorldName = plugin.getConfig().getString("event_mode.world", "events");
        ensureEventWorld(eventWorldName);

        List<Location> spawns = getArenaSpawnsForSession(session);
        if (spawns.isEmpty()) {
            Location eventSpawn = getEventWorldSpawn();
            if (eventSpawn != null) spawns = List.of(eventSpawn);
        }
        if (spawns.isEmpty()) {
            broadcast("§cNo arena spawns configured for " + session.spec.key + ". Event aborted.");
            clearState();
            return;
        }

        int i = 0;
        for (UUID id : session.active) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            // Keep inventory on hardcore join. Hardcore loss happens on elimination/death.
            if (!session.snapshots.containsKey(id)) {
                session.snapshots.put(id, InventorySnapshot.capture(p));
            }
            Location spawn = spawns.get(i % spawns.size());
            p.teleport(spawn);
            i++;
        }

        getHandler(kind).onStart(session);
        if (kind.isKoth()) startTickTask(); // covered above, but safety check
        // Arena events teleport players to events world (DO_IMMEDIATE_RESPAWN already permanent there).
        // Also set on SMP in case any death happens while still transitioning.
        setImmediateRespawn(true);
        schedulePersistEventSessionStart(session);
        broadcast("§d" + session.spec.displayName + " started with §f" + session.active.size() + "§d players.");
        discordNotifyEventRunning();
        checkImmediateElimination();
    }

    private void discordNotifyEventRunning() {
        if (session == null) {
            return;
        }
        if (!(plugin instanceof JebaitedCore core)) {
            return;
        }
        var svc = core.getDiscordIntegrationService();
        if (svc == null || !svc.isEnabled()) {
            return;
        }
        svc.notifyEventStarted(session.spec.key, session.spec.displayName, session.active.size(), session.spec.kind.name());
    }

    private void checkImmediateElimination() {
        if (session == null || !session.spec.kind.isElimination()) return;
        onEliminationTrigger(session);
    }

    private void startTickTask() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            synchronized (EventEngine.this) {
                if (session == null || session.state != EventState.RUNNING) return;
                getHandler(session.spec.kind).onTick(session);
                if (session.spec.kind == EventKind.CTF && session.ctfPendingWinnerUuid != null) {
                    UUID w = session.ctfPendingWinnerUuid;
                    session.ctfPendingWinnerUuid = null;
                    finalizeEvent(w, session.runtimeCoinReward, "ctf score limit");
                }
            }
        }, 20L, 20L);
    }

    private synchronized void finalizeEvent(UUID winner, int reward, String reason) {
        if (session == null) return;
        EventSpec ended = session.spec;
        session.state = EventState.ENDING;

        EventPersistenceSnap pSnap = takePersistenceSnapshot(session, winner, reward);

        rewardWinner(winner, reward, ended.displayName);
        writeEventStats(ended.key, winner);
        restoreSnapshots();
        restoreStaffSpectators();
        if (ended.kind.isHardcore()) queueLootForWinner(winner);
        getHandler(ended.kind).onEnd(session);

        OfflinePlayer w = Bukkit.getOfflinePlayer(winner);
        String winnerName = w.getName() == null ? winner.toString().substring(0, 8) : w.getName();
        broadcast("§d★ §fEvent Over: §d" + ended.displayName + " §7— Winner: §a" + winnerName + " §8(+§6" + reward + " coins§8)");
        if (reason != null && !reason.isBlank()) broadcast("§7Reason: §f" + reason);

        if (plugin instanceof JebaitedCore core) {
            var svc = core.getDiscordIntegrationService();
            if (svc != null && svc.isEnabled()) {
                svc.notifyEventEnded(
                        ended.key,
                        ended.displayName,
                        ended.kind.name(),
                        winnerName,
                        reward,
                        reason == null ? "" : reason);
            }
        }

        clearState();
        persistFinalizeFromSnapshotAsync(pSnap);
    }

    /** KOTH timer expiry with multiple leaders on uncontested time (coins + HC loot split). */
    private synchronized void finalizeKothTiedWinners(List<UUID> winners, int rewardTotal) {
        if (session == null || winners == null || winners.isEmpty()) return;
        Collections.sort(winners);
        EventSpec ended = session.spec;
        session.state = EventState.ENDING;

        LinkedHashSet<UUID> co = new LinkedHashSet<>(winners);
        EventPersistenceSnap pSnap = takePersistenceSnapshot(session, winners.get(0), rewardTotal, co);

        int n = winners.size();
        int base = rewardTotal / n;
        int rem = rewardTotal % n;
        for (int i = 0; i < n; i++) {
            int coins = base + (i < rem ? 1 : 0);
            rewardWinner(winners.get(i), coins, ended.displayName + " tie");
        }
        writeEventStatsCoWinners(ended.key, winners);
        restoreSnapshots();
        restoreStaffSpectators();
        if (ended.kind.isHardcore()) giveLootSplitToWinners(winners);
        getHandler(ended.kind).onEnd(session);

        StringBuilder names = new StringBuilder();
        for (int i = 0; i < winners.size(); i++) {
            if (i > 0) names.append("§7, §a");
            OfflinePlayer ow = Bukkit.getOfflinePlayer(winners.get(i));
            String nm = ow.getName() != null ? ow.getName() : winners.get(i).toString().substring(0, 8);
            names.append(nm);
        }
        broadcast("§d★ §fEvent Over: §d" + ended.displayName + " §7— §eTie §7— §a" + names
                + " §8(+§6" + rewardTotal + " coins split§8)");

        StringBuilder tiePlain = new StringBuilder();
        for (int i = 0; i < winners.size(); i++) {
            if (i > 0) {
                tiePlain.append(", ");
            }
            OfflinePlayer ow = Bukkit.getOfflinePlayer(winners.get(i));
            String nm = ow.getName() != null ? ow.getName() : winners.get(i).toString().substring(0, 8);
            tiePlain.append(nm);
        }
        if (plugin instanceof JebaitedCore core) {
            var svc = core.getDiscordIntegrationService();
            if (svc != null && svc.isEnabled()) {
                svc.notifyEventTie(ended.key, ended.displayName, ended.kind.name(), tiePlain.toString(), rewardTotal);
            }
        }

        clearState();
        persistFinalizeFromSnapshotAsync(pSnap);
    }

    private synchronized void doStopEvent(String reason) {
        EventSpec specForDiscordCancel = null;
        if (session != null && session.state == EventState.RUNNING) {
            specForDiscordCancel = session.spec;
        }
        EventPersistenceSnap snap = null;
        if (session != null) {
            if (session.persistenceSessionId > 0 && session.state == EventState.RUNNING) {
                snap = takePersistenceSnapshot(session, null, 0);
            }
            restoreSnapshots();
            restoreStaffSpectators();
            removeCountdownBar();
        }
        clearState();
        if (specForDiscordCancel != null
                && reason != null
                && !reason.toLowerCase(Locale.ROOT).contains("plugin shutdown")
                && plugin instanceof JebaitedCore core) {
            var svc = core.getDiscordIntegrationService();
            if (svc != null && svc.isEnabled()) {
                svc.notifyEventCancelled(specForDiscordCancel.key, specForDiscordCancel.displayName, reason);
            }
        }
        persistFinalizeFromSnapshotAsync(snap);
    }

    private void restoreSnapshots() {
        if (session == null) return;
        // Restore active participants
        for (Map.Entry<UUID, InventorySnapshot> entry : new HashMap<>(session.snapshots).entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null) continue;
            entry.getValue().restore(p);
        }
        session.snapshots.clear();
        // Restore spectating (eliminated) players — full snapshot restore (inventory + stats + teleport)
        for (Map.Entry<UUID, InventorySnapshot> entry : new HashMap<>(session.spectatorSnapshots).entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null) continue;
            InventorySnapshot snap = entry.getValue();
            p.setGameMode(GameMode.SURVIVAL);
            if (snap != null) {
                snap.restore(p); // restores inventory, armor, offhand, health, food, level, exp, and teleports
            } else {
                Location fallback = getWorldSpawn();
                if (fallback != null) p.teleport(fallback);
                p.updateInventory();
            }
        }
        session.spectatorSnapshots.clear();
        session.spectating.clear();
    }

    private void clearState() {
        if (session != null) {
            session.active.clear();
            session.queued.clear();
            session.eliminated.clear();
            session.pendingReturn.clear();
            session.hardcoreLootPool.clear();
            session.kothSeconds.clear();
        }
        session = null;
        // Restore normal death screen behaviour outside of events.
        setImmediateRespawn(false);
        stopTickTask();
        stopCountdownTask();
    }

    /** Sets DO_IMMEDIATE_RESPAWN on all event-relevant worlds for the duration of an event. */
    private void setImmediateRespawn(boolean enabled) {
        String smpName = plugin.getConfig().getString("worlds.smp", "smp");
        World smpWorld = Bukkit.getWorld(smpName);
        if (smpWorld != null) smpWorld.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, enabled);
        String eventWorldName = plugin.getConfig().getString("event_mode.world", "events");
        World eventWorld = Bukkit.getWorld(eventWorldName);
        if (eventWorld != null) eventWorld.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, enabled);
    }

    private void queueLootForWinner(UUID winnerId) {
        if (session == null || session.hardcoreLootPool.isEmpty()) return;
        List<ItemStack> pool = new ArrayList<>(session.hardcoreLootPool);
        session.hardcoreLootPool.clear();
        List<ItemStack> cleaned = sanitizeLoot(pool);
        if (cleaned.isEmpty()) return;
        pendingHardcoreLootClaims.computeIfAbsent(winnerId, k -> new ArrayList<>()).addAll(cleaned);
        Player online = Bukkit.getPlayer(winnerId);
        if (online != null && online.isOnline()) {
            online.sendMessage(Component.text("⚔ ", NamedTextColor.GOLD)
                    .append(Component.text("Hardcore loot pool ready: ", NamedTextColor.YELLOW))
                    .append(Component.text(cleaned.size() + " stack" + (cleaned.size() == 1 ? "" : "s"),
                            NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(". Claim with ", NamedTextColor.YELLOW))
                    .append(Component.text("/loot", NamedTextColor.AQUA))
                    .append(Component.text(".", NamedTextColor.YELLOW)));
            online.playSound(online.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
        }
    }

    /** Round-robin hardcore loot pool across tied KOTH winners (§21). */
    private void giveLootSplitToWinners(List<UUID> winnerIds) {
        if (session == null || session.hardcoreLootPool.isEmpty() || winnerIds == null || winnerIds.isEmpty()) {
            return;
        }
        List<UUID> eligible = new ArrayList<>(winnerIds);
        if (eligible.isEmpty()) return;
        List<ItemStack> pool = new ArrayList<>(session.hardcoreLootPool);
        session.hardcoreLootPool.clear();
        List<List<ItemStack>> split = new ArrayList<>();
        for (int i = 0; i < eligible.size(); i++) split.add(new ArrayList<>());
        int wi = 0;
        for (ItemStack item : pool) {
            if (item == null || item.getType() == Material.AIR) continue;
            List<ItemStack> bucket = split.get(wi % split.size());
            wi++;
            bucket.add(item.clone());
        }
        for (int i = 0; i < eligible.size(); i++) {
            UUID id = eligible.get(i);
            List<ItemStack> cleaned = sanitizeLoot(split.get(i));
            if (!cleaned.isEmpty()) {
                pendingHardcoreLootClaims.computeIfAbsent(id, k -> new ArrayList<>()).addAll(cleaned);
            }
        }
        for (UUID id : eligible) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            int count = pendingHardcoreLootClaims.getOrDefault(id, List.of()).size();
            p.sendMessage(Component.text("⚔ ", NamedTextColor.GOLD)
                    .append(Component.text("Tie win — your hardcore loot share is ready (", NamedTextColor.YELLOW))
                    .append(Component.text(count + " stack" + (count == 1 ? "" : "s"), NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text("). Use ", NamedTextColor.YELLOW))
                    .append(Component.text("/loot", NamedTextColor.AQUA))
                    .append(Component.text(" to claim.", NamedTextColor.YELLOW)));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
        }
    }

    private List<ItemStack> sanitizeLoot(List<ItemStack> raw) {
        List<ItemStack> cleaned = new ArrayList<>();
        if (raw == null) return cleaned;
        for (ItemStack item : raw) {
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) continue;
            cleaned.add(item.clone());
        }
        return cleaned;
    }

    public synchronized int getPendingHardcoreLootCount(UUID playerId) {
        if (playerId == null) return 0;
        return pendingHardcoreLootClaims.getOrDefault(playerId, List.of()).size();
    }

    public synchronized List<ItemStack> getPendingHardcoreLootPreview(UUID playerId) {
        if (playerId == null) return List.of();
        List<ItemStack> list = pendingHardcoreLootClaims.get(playerId);
        if (list == null || list.isEmpty()) return List.of();
        List<ItemStack> copy = new ArrayList<>(list.size());
        for (ItemStack item : list) {
            if (item != null && item.getType() != Material.AIR) {
                copy.add(item.clone());
            }
        }
        return copy;
    }

    public synchronized int claimPendingHardcoreLoot(Player player) {
        if (player == null) return 0;
        UUID id = player.getUniqueId();
        List<ItemStack> list = pendingHardcoreLootClaims.get(id);
        if (list == null || list.isEmpty()) return 0;
        int delivered = 0;
        for (ItemStack item : list) {
            if (item == null || item.getType() == Material.AIR) continue;
            delivered++;
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        player.updateInventory();
        pendingHardcoreLootClaims.remove(id);
        return delivered;
    }
    private void rewardWinner(UUID winner, int reward, String eventDisplay) {
        if (!(plugin instanceof JebaitedCore core)) return;
        ProfileStore store = core.getProfileStore();
        if (store == null || reward <= 0) return;
        Player online = Bukkit.getPlayer(winner);
        if (online != null) {
            store.grantCosmeticCoins(online, reward, eventDisplay + " winner");
            return;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(winner);
        PlayerProfile profile = store.getOrCreate(offline, core.getRankManager().getDefaultGroup());
        if (profile == null) return;
        profile.addCosmeticCoins(reward);
        store.save(offline.getUniqueId());
    }

    private void writeEventStats(String eventKey, UUID winner) {
        if (!(plugin instanceof JebaitedCore core)) return;
        ProfileStore store = core.getProfileStore();
        if (store == null || session == null) return;
        for (UUID id : session.active) {
            boolean won = id.equals(winner);
            store.updateEventStats(id, eventKey, 1, won ? 1 : 0, won ? 0 : 1);
            if (won) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                PlayerProfile profile = store.getOrCreate(op, core.getRankManager().getDefaultGroup());
                if (profile != null) {
                    if (session.spec.kind.isHardcore()) profile.incEventWinsHardcore();
                    else profile.incEventWinsCombat();
                    store.saveDeferred(id);
                }
            }
        }
    }

    private void writeEventStatsCoWinners(String eventKey, Collection<UUID> winners) {
        if (!(plugin instanceof JebaitedCore core)) return;
        ProfileStore store = core.getProfileStore();
        if (store == null || session == null) return;
        Set<UUID> win = new HashSet<>(winners);
        for (UUID id : session.active) {
            boolean won = win.contains(id);
            store.updateEventStats(id, eventKey, 1, won ? 1 : 0, won ? 0 : 1);
            if (won) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                PlayerProfile profile = store.getOrCreate(op, core.getRankManager().getDefaultGroup());
                if (profile != null) {
                    if (session.spec.kind.isHardcore()) profile.incEventWinsHardcore();
                    else profile.incEventWinsCombat();
                    store.saveDeferred(id);
                }
            }
        }
    }

    private EventPersistenceSnap takePersistenceSnapshot(EventSession s, UUID winnerOrNull, int rewardCoins) {
        return takePersistenceSnapshot(s, winnerOrNull, rewardCoins, Set.of());
    }

    private EventPersistenceSnap takePersistenceSnapshot(EventSession s, UUID winnerOrNull, int rewardCoins,
                                                       Set<UUID> coWinners) {
        int sid = s.persistenceSessionId;
        Map<UUID, Integer> kills = new HashMap<>();
        for (Map.Entry<UUID, AtomicInteger> e : s.eventKills.entrySet()) {
            kills.put(e.getKey(), e.getValue().get());
        }
        Map<UUID, Integer> deaths = new HashMap<>();
        for (Map.Entry<UUID, AtomicInteger> e : s.eventDeaths.entrySet()) {
            deaths.put(e.getKey(), e.getValue().get());
        }
        String arenaKey = s.spec.key;
        if (arenaKey != null && arenaKey.isBlank()) {
            arenaKey = null;
        }
        Set<UUID> tie = coWinners == null ? Set.of() : Set.copyOf(coWinners);
        return new EventPersistenceSnap(
                sid,
                s.spec.kind.name(),
                arenaKey,
                new HashSet<>(s.active),
                new HashSet<>(s.eliminated),
                new HashSet<>(s.spectating),
                winnerOrNull,
                tie,
                rewardCoins,
                kills,
                deaths);
    }

    private void schedulePersistEventSessionStart(EventSession forSameSession) {
        if (!(plugin instanceof JebaitedCore core)) return;
        if (!core.getDatabaseManager().isEnabled()) return;
        EventParticipantDAO dao = core.getEventParticipantDAO();
        if (dao == null) return;
        final EventSession anchor = forSameSession;
        String eventType = anchor.spec.kind.name();
        String arenaKey = anchor.spec.key;
        long startedAt = System.currentTimeMillis();
        EventParticipantDAO.runAsync(plugin, () -> {
            int id = dao.insertSessionRunning(eventType, arenaKey, startedAt);
            if (id <= 0) return;
            EventParticipantDAO.runOnMainWhenPossible(plugin, () -> {
                synchronized (EventEngine.this) {
                    if (session != anchor) return;
                    session.persistenceSessionId = id;
                }
            });
        });
    }

    private List<EventParticipantDAO.ParticipantRow> buildParticipantRows(EventPersistenceSnap snap) {
        List<EventParticipantDAO.ParticipantRow> rows = new ArrayList<>();
        Set<UUID> tie = snap.coWinners();
        boolean split = tie != null && !tie.isEmpty();
        List<UUID> tieSorted = split ? tie.stream().sorted().toList() : List.of();
        int nWin = tieSorted.size();
        int totalReward = snap.rewardCoins();
        int baseEach = split && nWin > 0 ? totalReward / nWin : totalReward;
        int remExtra = split && nWin > 0 ? totalReward % nWin : 0;

        for (UUID pid : snap.active()) {
            int k = snap.kills().getOrDefault(pid, 0);
            int d = snap.deaths().getOrDefault(pid, 0);
            String result;
            boolean won = split ? tie.contains(pid)
                    : snap.winnerOrNull() != null && pid.equals(snap.winnerOrNull());
            if (won) {
                result = "WIN";
            } else if (snap.spectating().contains(pid) || snap.eliminated().contains(pid)) {
                result = "SPECTATE";
            } else {
                result = "LOSS";
            }
            int coins = 0;
            if (won) {
                if (split) {
                    int idx = tieSorted.indexOf(pid);
                    coins = baseEach + (idx >= 0 && idx < remExtra ? 1 : 0);
                } else {
                    coins = totalReward;
                }
            }
            rows.add(new EventParticipantDAO.ParticipantRow(pid, k, d, result, coins, 0));
        }
        return rows;
    }

    private void persistFinalizeFromSnapshotAsync(EventPersistenceSnap snap) {
        if (snap == null || snap.sessionId() <= 0) return;
        if (!(plugin instanceof JebaitedCore core)) return;
        if (!core.getDatabaseManager().isEnabled()) return;
        EventParticipantDAO dao = core.getEventParticipantDAO();
        if (dao == null) return;
        List<EventParticipantDAO.ParticipantRow> rows = buildParticipantRows(snap);
        EventParticipantDAO.runAsync(plugin, () -> {
            long endedAt = System.currentTimeMillis();
            dao.updateSessionEnd(snap.sessionId(), endedAt, snap.winnerOrNull(), null, rows.size());
            if (!rows.isEmpty()) {
                dao.upsertParticipantsBatch(snap.sessionId(), rows);
            }
        });
    }

    // ── Auto-tick ────────────────────────────────────────────────────────────

    private synchronized void autoTick() {
        if (session != null) return;
        if (!plugin.getConfig().getBoolean("event_mode.automation.enabled", false)) return;
        List<String> keys = getConfiguredEventKeys();
        if (keys.isEmpty()) return;
        String random = keys.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(keys.size()));
        EventSpec spec = loadSpec(random);
        if (spec == null || !spec.enabled) return;
        beginEvent(spec, true, null);
    }

    // ── Announce helpers ──────────────────────────────────────────────────────

    private void announceSignup(EventSpec spec, boolean automated) {
        String bar = "█".repeat(47);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin instanceof JebaitedCore core) {
                var profile = core.getProfileStore().getOrCreate(player, core.getRankManager().getDefaultGroup());
                if (profile != null && !profile.isEventNotificationsEnabled()) continue;
                if (profile != null && !profile.isEventCategoryEnabled(spec.key)) continue;
            }
            if (spec.kind.isHardcore()) {
                sendHardcoreAnnouncement(player, spec);
            } else {
                sendNormalAnnouncement(player, spec);
            }
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f, 1.8f);
        }
    }

    private void sendHardcoreAnnouncement(Player player, EventSpec spec) {
        player.sendMessage(Component.text("█".repeat(47), NamedTextColor.DARK_RED));
        player.sendMessage(Component.text("  ☠ " + spec.displayName.toUpperCase(Locale.ROOT) + " ",
                NamedTextColor.RED, TextDecoration.BOLD)
                .append(Component.text("— Fight to the last breath").color(NamedTextColor.DARK_RED)
                        .decoration(TextDecoration.BOLD, false)));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("  ▸ Die and you ", NamedTextColor.GRAY)
                .append(Component.text("lose everything", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(" you're carrying.", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  ▸ Last survivor ", NamedTextColor.GRAY)
                .append(Component.text("claims the loot pool", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" of every fallen player.", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  ▸ High risk. High reward. No second chances.", NamedTextColor.YELLOW));
        player.sendMessage(Component.empty());
        if (isHubPlayer(player)) {
            player.sendMessage(Component.text("  ⚠ ", NamedTextColor.YELLOW)
                    .append(Component.text("Must be in SMP to join. Run ", NamedTextColor.GRAY))
                    .append(Component.text("/smp", NamedTextColor.GREEN))
                    .append(Component.text(" first.", NamedTextColor.GRAY)));
        }
        player.sendMessage(Component.text("  ")
                .append(Component.text(" ⚔ JOIN ⚔ ").color(NamedTextColor.RED).decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/event join"))
                        .hoverEvent(HoverEvent.showText(
                                Component.text("Enter the hardcore arena.\n", NamedTextColor.RED)
                                        .append(Component.text("Your items are NOT protected.")
                                                .color(NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD)))))
                .append(Component.text("  "))
                .append(Component.text(" ✗ SKIP ").color(NamedTextColor.DARK_GRAY)
                        .clickEvent(ClickEvent.runCommand("/event leave"))
                        .hoverEvent(HoverEvent.showText(Component.text("Dismiss this event.", NamedTextColor.GRAY)))));
        player.sendMessage(Component.text("█".repeat(47), NamedTextColor.DARK_RED));
    }

    private void sendNormalAnnouncement(Player player, EventSpec spec) {
        String desc = getEventDescription(spec);
        player.sendMessage(Component.text("█".repeat(47), NamedTextColor.DARK_PURPLE));
        player.sendMessage(Component.text("  ★ ", NamedTextColor.GOLD)
                .append(Component.text(spec.displayName).color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD))
                .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                .append(Component.text(desc, NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  ")
                .append(Component.text(" JOIN ").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/event join"))
                        .hoverEvent(HoverEvent.showText(
                                Component.text("Click to join " + spec.displayName + "!", NamedTextColor.GREEN))))
                .append(Component.text("  "))
                .append(Component.text(" SKIP ").color(NamedTextColor.DARK_GRAY)
                        .clickEvent(ClickEvent.runCommand("/event leave"))
                        .hoverEvent(HoverEvent.showText(Component.text("Dismiss this event.", NamedTextColor.GRAY)))));
        if (isHubPlayer(player)) {
            player.sendMessage(Component.text("  ⚠ ", NamedTextColor.YELLOW)
                    .append(Component.text("SMP event — run ", NamedTextColor.GRAY))
                    .append(Component.text("/smp", NamedTextColor.GREEN))
                    .append(Component.text(" first.", NamedTextColor.GRAY)));
        }
        player.sendMessage(Component.text("█".repeat(47), NamedTextColor.DARK_PURPLE));
    }

    private void sendHardcoreWarning(Player player) {
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("  ☠ HARDCORE EVENT WARNING", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        player.sendMessage(Component.text("  ─────────────────────────────────────", NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.text("  • You KEEP inventory on join.", NamedTextColor.GREEN));
        player.sendMessage(Component.text("  • If you die, your full inventory is added to the hardcore loot pool.", NamedTextColor.RED));
        player.sendMessage(Component.text("  • Only winner(s) can claim that pool at the end via /loot.", NamedTextColor.RED));
        player.sendMessage(Component.text("  • Grave Insurance does NOT apply in hardcore events.", NamedTextColor.RED));
        player.sendMessage(Component.text("  ─────────────────────────────────────", NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.text("  [CLICK TO CONFIRM AND JOIN]", NamedTextColor.RED, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/event join confirm"))
                .hoverEvent(HoverEvent.showText(Component.text("I understand — join the hardcore event", NamedTextColor.RED))));
        player.sendMessage(Component.empty());
    }

    private String getEventDescription(EventSpec spec) {
        return switch (spec.kind) {
            case KOTH          -> "Earn the most uncontested hill time (sole player in zone) to win.";
            case FFA           -> "Free-for-all. Last player standing wins. Gear is restored on death.";
            case DUELS         -> "1v1 duel. Best of one. Gear is restored after the match.";
            case HARDCORE, HARDCORE_FFA  -> "Hardcore FFA — keep gear on join; die and lose items to the loot pool. Winner claims via /loot.";
            case HARDCORE_DUELS          -> "Hardcore duel — keep gear on join; loser inventory goes to the loot pool. Winner claims via /loot.";
            case HARDCORE_KOTH           -> "Hardcore KOTH — keep gear on join; deaths feed loot pool. Most uncontested hill time wins; ties split claimable pool.";
            case CTF           -> "Capture the Flag — steal the enemy flag and bring it to your base.";
            default            -> plugin.getConfig().getString("event_mode.events." + spec.key + ".description",
                                       "Compete to win " + spec.coinReward + " coins.");
        };
    }

    // ── Config loading ────────────────────────────────────────────────────────

    private EventSpec loadSpec(String rawKey) {
        String key = normalizeEventKey(rawKey);
        if (ChatGameKeys.isChatGameConfigKey(key)) {
            return null;
        }
        var sec = plugin.getConfig().getConfigurationSection("event_mode.events." + key);
        if (sec == null) return null;
        String display = compactDisplayName(key, sec.getString("display_name", key.toUpperCase(Locale.ROOT)));
        boolean enabled = sec.getBoolean("enabled", true);
        int reward = Math.max(0, sec.getInt("coin_reward", 10));
        int minPlayers = Math.max(1, sec.getInt("min_players", defaultMinPlayers(key)));
        int maxPlayers = Math.max(minPlayers, sec.getInt("max_players", defaultMaxPlayers(key)));
        EventKind kind = EventKind.fromKey(key);
        return new EventSpec(key, display, enabled, reward, minPlayers, maxPlayers, kind);
    }

    private int defaultMinPlayers(String key) {
        return switch (normalizeEventKey(key)) {
            case "duels", "hardcore_duels" -> 2;
            case "ctf" -> 4;
            default -> 2;
        };
    }

    private int defaultMaxPlayers(String key) {
        return switch (normalizeEventKey(key)) {
            case "duels", "hardcore_duels" -> 2;
            case "ctf" -> 16;
            default -> 32;
        };
    }

    private static String normalizeEventKey(String raw) {
        if (raw == null) return "";
        String key = raw.toLowerCase(Locale.ROOT).trim();
        return switch (key) {
            case "math", "chatmath"      -> "chat_math";
            case "scrabble"              -> "chat_scrabble";
            case "anagram", "chat_anagram", "chat_word", "word" -> "chat_scrabble";
            case "quiz", "question"      -> "chat_quiz";
            case "lms"                   -> "ffa";
            case "hardcoreffa"           -> "hardcore_ffa";
            case "hardcoreduels"         -> "hardcore_duels";
            case "hardcorekoth"          -> "hardcore_koth";
            default                      -> key;
        };
    }

    private static String compactDisplayName(String key, String configured) {
        return switch (normalizeEventKey(key)) {
            case "chat_scrabble" -> "Scrabble";
            case "chat_math"     -> "Math";
            case "chat_quiz"     -> "Quiz";
            default              -> configured;
        };
    }

    // ── Arena registry + KOTH helpers ────────────────────────────────────────

    private ArenaConfig resolveArenaConfig(EventSession s) {
        if (s == null) return null;
        if (s.selectedArenaKey != null) {
            ArenaConfig a = arenaRegistry.get(s.spec.key, s.selectedArenaKey);
            if (a != null) return a;
        }
        return arenaRegistry.defaultArena(s.spec.key);
    }

    private KothHandler.Cuboid getKothCuboidForSession(EventSession s) {
        ArenaConfig ac = resolveArenaConfig(s);
        if (ac != null && ac.hill() != null) {
            return ac.hill().toHandlerCuboid();
        }
        return getLegacyKothCuboid();
    }

    /** Legacy {@code event_mode.koth.pos1/pos2} corners when registry has no hill. */
    private KothHandler.Cuboid getLegacyKothCuboid() {
        String world = plugin.getConfig().getString("event_mode.koth.world", "smp");
        if (world == null || world.isBlank()) return null;
        if (!plugin.getConfig().isSet("event_mode.koth.pos1.x") || !plugin.getConfig().isSet("event_mode.koth.pos2.x")) {
            return null;
        }
        int x1 = plugin.getConfig().getInt("event_mode.koth.pos1.x");
        int y1 = plugin.getConfig().getInt("event_mode.koth.pos1.y");
        int z1 = plugin.getConfig().getInt("event_mode.koth.pos1.z");
        int x2 = plugin.getConfig().getInt("event_mode.koth.pos2.x");
        int y2 = plugin.getConfig().getInt("event_mode.koth.pos2.y");
        int z2 = plugin.getConfig().getInt("event_mode.koth.pos2.z");
        return new KothHandler.Cuboid(world, x1, y1, z1, x2, y2, z2);
    }

    private List<Location> getArenaSpawnsForSession(EventSession s) {
        if (s == null) return List.of();
        String norm = normalizeArenaKey(s.spec.key);
        List<Location> db = spawnCache.getOrDefault(norm, List.of());
        if (!db.isEmpty()) return db;
        if (s.resolvedArenaConfig != null && !s.resolvedArenaConfig.spawns().isEmpty()) {
            return s.resolvedArenaConfig.spawns();
        }
        return List.of();
    }

    /** Next KOTH respawn: DB/YAML arena spawns round-robin, else SMP {@link #getWorldSpawn()}. */
    private Location pickKothRespawnLocation(EventSession s) {
        if (s == null) return getWorldSpawn();
        List<Location> spawns = getArenaSpawnsForSession(s);
        if (spawns.isEmpty()) return getWorldSpawn();
        int idx = Math.floorMod(s.kothSpawnCursor.getAndIncrement(), spawns.size());
        Location loc = spawns.get(idx);
        return loc != null ? loc.clone() : getWorldSpawn();
    }

    private static String normalizeArenaKey(String arenaKeyRaw) {
        String key = normalizeEventKey(arenaKeyRaw);
        if ("duels".equals(key) || "hardcore_duels".equals(key)) return "duels";
        if ("ffa".equals(key) || "hardcore_ffa".equals(key) || "hardcore".equals(key)) return "ffa";
        if ("hardcore_koth".equals(key)) return "koth";
        return key;
    }

    private static boolean isValidArenaKey(String normalised) {
        return "ffa".equals(normalised) || "duels".equals(normalised) || "koth".equals(normalised);
    }

    // ── World helpers ─────────────────────────────────────────────────────────

    private Location getWorldSpawn() {
        String smp = plugin.getConfig().getString("worlds.smp", "smp");
        World world = Bukkit.getWorld(smp);
        if (world != null) return world.getSpawnLocation();
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    private Location getEventWorldSpawn() {
        String name = plugin.getConfig().getString("event_mode.world", "events");
        if (name == null || name.isBlank()) return getWorldSpawn();
        World world = ensureEventWorld(name);
        return world != null ? world.getSpawnLocation() : getWorldSpawn();
    }

    private World ensureEventWorld(String eventWorldName) {
        World world = Bukkit.getWorld(eventWorldName);
        if (world == null) {
            WorldCreator creator = new WorldCreator(eventWorldName);
            if (plugin.getConfig().getBoolean("event_mode.use_superflat_world", true)) {
                creator.type(WorldType.FLAT);
            }
            world = Bukkit.createWorld(creator);
        }
        if (world != null) {
            world.setGameRule(GameRule.DO_FIRE_TICK, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            // Suppress the "You Died!" screen for event participants — they auto-respawn
            world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
            WorldBorder border = world.getWorldBorder();
            border.setCenter(0.0, 0.0);
            border.setSize(Math.max(1000.0, plugin.getConfig().getDouble("event_mode.world_border_size", 10000.0)));
        }
        return world;
    }

    private boolean isSmpPlayer(Player player) {
        if (player == null || player.getWorld() == null) return false;
        String smp = plugin.getConfig().getString("worlds.smp", "smp");
        return smp.equalsIgnoreCase(player.getWorld().getName());
    }

    private boolean isHubPlayer(Player player) {
        if (player == null || player.getWorld() == null) return false;
        String hub = plugin.getConfig().getString("worlds.hub", "world");
        return hub.equalsIgnoreCase(player.getWorld().getName());
    }

    // ── DB spawn persistence ──────────────────────────────────────────────────

    private boolean isSpawnDBAvailable() {
        if (!(plugin instanceof JebaitedCore core)) return false;
        DatabaseManager db = core.getDatabaseManager();
        return db != null && db.isEnabled() && db.ensureConnected();
    }

    private void initSpawnsTable() {
        if (!isSpawnDBAvailable()) return;
        String sql = """
                CREATE TABLE IF NOT EXISTS event_spawns (
                    id SERIAL PRIMARY KEY,
                    arena_key VARCHAR(32) NOT NULL,
                    world_name VARCHAR(64) NOT NULL,
                    x DOUBLE PRECISION NOT NULL,
                    y DOUBLE PRECISION NOT NULL,
                    z DOUBLE PRECISION NOT NULL,
                    yaw REAL NOT NULL,
                    pitch REAL NOT NULL
                )""";
        try (Connection conn = ((JebaitedCore) plugin).getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
        } catch (SQLException e) {
            plugin.getLogger().warning("[EventSpawns] Failed to create event_spawns table: " + e.getMessage());
        }
    }

    private void loadSpawnsFromDB() {
        if (!isSpawnDBAvailable()) return;
        Map<String, List<Location>> loaded = new HashMap<>();
        try (Connection conn = ((JebaitedCore) plugin).getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT arena_key, world_name, x, y, z, yaw, pitch FROM event_spawns ORDER BY id ASC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String key = rs.getString("arena_key");
                String worldName = rs.getString("world_name");
                double x = rs.getDouble("x"), y = rs.getDouble("y"), z = rs.getDouble("z");
                float yaw = rs.getFloat("yaw"), pitch = rs.getFloat("pitch");
                // If the world isn't loaded yet, force-load it on the main thread.
                // Paper doesn't auto-load custom worlds (e.g. "events") — ensureEventWorld handles that.
                World w = Bukkit.getWorld(worldName);
                if (w != null) {
                    loaded.computeIfAbsent(key, k -> new ArrayList<>()).add(new Location(w, x, y, z, yaw, pitch));
                } else {
                    final String fKey = key; final double fx = x, fy = y, fz = z;
                    final float fyaw = yaw, fpitch = pitch; final String fWorld = worldName;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        // ensureEventWorld must run on main thread (Bukkit.createWorld requirement).
                        World resolved = ensureEventWorld(fWorld);
                        if (resolved != null) {
                            spawnCache.computeIfAbsent(fKey, k -> new ArrayList<>())
                                    .add(new Location(resolved, fx, fy, fz, fyaw, fpitch));
                        } else {
                            plugin.getLogger().warning("[EventSpawns] Could not load world '" + fWorld + "' — spawn skipped.");
                        }
                    });
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[EventSpawns] Failed to load spawns from DB: " + e.getMessage());
            return;
        }
        for (Map.Entry<String, List<Location>> entry : loaded.entrySet()) {
            spawnCache.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        if (!loaded.isEmpty()) {
            plugin.getLogger().info("[EventSpawns] Loaded "
                    + loaded.values().stream().mapToInt(List::size).sum() + " arena spawn(s).");
        }
    }

    private static String encodeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return "";
        return loc.getWorld().getName() + ":" + loc.getX() + ":" + loc.getY() + ":" + loc.getZ()
                + ":" + loc.getYaw() + ":" + loc.getPitch();
    }

    // ── Task management ───────────────────────────────────────────────────────

    private void stopAutoTask() {
        if (autoTask != null) { autoTask.cancel(); autoTask = null; }
    }

    private void stopTickTask() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
    }

    private void stopCountdownTask() {
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
    }

    private void removeCountdownBar() {
        if (countdownBar != null) {
            countdownBar.removeAll();
            countdownBar.setVisible(false);
            countdownBar = null;
        }
    }

    // ── Misc ─────────────────────────────────────────────────────────────────

    private boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) return true;
        File[] children = file.listFiles();
        if (children != null) { for (File child : children) if (!deleteRecursively(child)) return false; }
        return file.delete();
    }

    private void broadcast(String legacy) {
        if (session != null) {
            EventNotifications.broadcastCategory(plugin, session.spec.key, legacy);
        } else {
            EventNotifications.broadcastCategoryOptional(plugin, null, legacy);
        }
    }
}
