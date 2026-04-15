package com.darkniightz.core.system;

import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.main.JebaitedCore;
import com.darkniightz.main.database.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@SuppressWarnings({"deprecation", "removal"})
public class EventModeManager {
    public record ActionResult(boolean ok, String message) {}

    private enum EventKind {
        CHAT_MATH,
        CHAT_SCRABBLE,
        CHAT_QUIZ,
        KOTH,
        FFA,
        DUELS,
        HARDCORE,       // legacy alias → treated as HARDCORE_FFA
        HARDCORE_FFA,
        HARDCORE_DUELS,
        HARDCORE_KOTH,
        OTHER
    }

    private static final Set<EventKind> SIGNUP_EVENTS = Set.of(
            EventKind.KOTH,
            EventKind.FFA,
            EventKind.DUELS,
            EventKind.HARDCORE,
            EventKind.HARDCORE_FFA,
            EventKind.HARDCORE_DUELS,
            EventKind.HARDCORE_KOTH
    );

    private final Plugin plugin;
    private final BroadcasterManager broadcasterManager;
    private final BossBarManager bossBarManager;

    private volatile EventSpec activeSpec;
    private volatile boolean waitingForQueue;

    private volatile String chatAnswer;
    private volatile long eventEndsAtMs;

    private BukkitTask autoTask;
    private BukkitTask kothTask;

    // In-memory spawn cache keyed by normalised arena key (ffa / duels / hardcore)
    private final Map<String, List<Location>> spawnCache = new ConcurrentHashMap<>();

    private final Set<UUID> queuedParticipants = ConcurrentHashMap.newKeySet();
    private final Set<UUID> activeParticipants = ConcurrentHashMap.newKeySet();
    private final Set<UUID> eliminated = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingReturnAfterDeath = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> adminEditAccessUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> kothSecondsByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, InventorySnapshot> inventorySnapshots = new ConcurrentHashMap<>();
    private final List<ItemStack> hardcoreLootPool = new ArrayList<>();

    public EventModeManager(Plugin plugin, BroadcasterManager broadcasterManager, BossBarManager bossBarManager) {
        this.plugin = plugin;
        this.broadcasterManager = broadcasterManager;
        this.bossBarManager = bossBarManager;
    }

    public synchronized void start() {
        stopAutoTask();
        long everyTicks = Math.max(20L, plugin.getConfig().getLong("event_mode.interval-seconds", 300L) * 20L);
        autoTask = Bukkit.getScheduler().runTaskTimer(plugin, this::autoTick, everyTicks, everyTicks);
        // Bootstrap DB table and warm the in-memory spawn cache
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            initSpawnsTable();
            loadSpawnsFromDB();
        });
    }

    public synchronized void stop() {
        stopAutoTask();
        stopEvent("plugin shutdown");
    }

    public synchronized boolean isActive() {
        return activeSpec != null;
    }

    public synchronized String getStatusLine() {
        if (activeSpec == null) {
            return "§7No active event.";
        }
        if (waitingForQueue) {
            return "§dQueue open: §f" + activeSpec.displayName + " §8| §7joined: §f" + queuedParticipants.size() + "§7/" + activeSpec.maxPlayers;
        }
        if (activeSpec.kind == EventKind.KOTH || activeSpec.kind == EventKind.HARDCORE_KOTH) {
            long remain = Math.max(0L, (eventEndsAtMs - System.currentTimeMillis()) / 1000L);
            return "§dActive: §f" + activeSpec.displayName + " §8| §7remaining: §f" + remain + "s";
        }
        return "§dActive: §f" + activeSpec.displayName + " §8| §7participants: §f" + activeParticipants.size();
    }

    public synchronized List<String> getConfiguredEventKeys() {
        var sec = plugin.getConfig().getConfigurationSection("event_mode.events");
        if (sec == null) return List.of("koth", "ffa", "duels", "chat_math", "chat_scrabble", "chat_quiz");
        Set<String> seen = new HashSet<>();
        List<String> out = new ArrayList<>();
        for (String key : sec.getKeys(false)) {
            String normalized = normalizeEventKey(key);
            if (sec.getBoolean(key + ".enabled", true) && seen.add(normalized)) {
                out.add(normalized);
            }
        }
        return out;
    }

    public synchronized List<String> getConfiguredEventDisplayNames() {
        List<String> out = new ArrayList<>();
        for (String key : getConfiguredEventKeys()) {
            EventSpec spec = loadSpec(key);
            if (spec != null && spec.enabled) {
                out.add(spec.displayName);
            }
        }
        return out;
    }

    public synchronized ActionResult startEvent(String requestedKey) {
        if (activeSpec != null) {
            return new ActionResult(false, "§cAn event is already active: §f" + activeSpec.displayName);
        }
        EventSpec spec = loadSpec(requestedKey);
        if (spec == null || !spec.enabled) {
            return new ActionResult(false, "§cUnknown or disabled event: §e" + requestedKey);
        }

        beginEvent(spec, false);
        return new ActionResult(true, "§aStarted event: §f" + spec.displayName);
    }

    public synchronized ActionResult stopEvent(String actorReason) {
        if (activeSpec == null) {
            return new ActionResult(false, "§7No active event to stop.");
        }
        String ended = activeSpec.displayName;
        clearState();
        broadcast("&7Event stopped: &f" + ended + "&7 (" + actorReason + ")");
        return new ActionResult(true, "§7Event stopped.");
    }

    public synchronized ActionResult completeEvent(Player winner, Integer rewardOverride, String reason) {
        if (activeSpec == null) {
            return new ActionResult(false, "§7No active event.");
        }
        if (winner == null) {
            return new ActionResult(false, "§cWinner is required.");
        }
        activeParticipants.add(winner.getUniqueId());
        int reward = rewardOverride != null ? Math.max(0, rewardOverride) : activeSpec.coinReward;
        finalizeEvent(winner.getUniqueId(), reward, reason == null ? "completed" : reason);
        return new ActionResult(true, "§aEvent completed. Winner: §f" + winner.getName());
    }

    public synchronized ActionResult setupKothPosition(Player player, boolean firstPos) {
        if (player == null) {
            return new ActionResult(false, "§cOnly players can set KOTH positions.");
        }
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
        if (player == null) {
            return new ActionResult(false, "§cOnly players can set arena spawns.");
        }
        String arenaKey = normalizeArenaKey(arenaKeyRaw);
        if (!isValidArenaKey(arenaKey)) {
            return new ActionResult(false, "§cArena setup only supports ffa/hardcore_ffa and duels/hardcore_duels. Use /event setup koth for KOTH.");
        }
        Location loc = player.getLocation();
        List<Location> cached = spawnCache.computeIfAbsent(arenaKey, k -> new ArrayList<>());
        cached.add(loc.clone());
        int spawnNum = cached.size();
        // Persist to DB asynchronously
        final String encoded = encodeLocation(loc);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!isSpawnDBAvailable()) return;
            try (Connection conn = ((JebaitedCore) plugin).getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO event_spawns (arena_key, world_name, x, y, z, yaw, pitch) VALUES (?,?,?,?,?,?,?)")) {
                ps.setString(1, arenaKey);
                String[] parts = encoded.split(":");
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
            return new ActionResult(false, "§cArena setup only supports ffa/hardcore_ffa and duels/hardcore_duels. Use /event setup koth for KOTH.");
        }
        spawnCache.put(arenaKey, new ArrayList<>());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!isSpawnDBAvailable()) return;
            try (Connection conn = ((JebaitedCore) plugin).getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM event_spawns WHERE arena_key = ?")) {
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
            lines.add(String.format("#%d §7%s §fx=%.1f y=%.1f z=%.1f",
                    i + 1, world, loc.getX(), loc.getY(), loc.getZ()));
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
        org.bukkit.Material glassType = org.bukkit.Material.WHITE_STAINED_GLASS_PANE;
        org.bukkit.block.data.BlockData glassData = glassType.createBlockData();

        // Collect all fake block locations to restore later
        List<Location> fakeLocations = new ArrayList<>();
        for (Location base : spawns) {
            if (base.getWorld() == null) continue;
            for (int dy = 0; dy <= 2; dy++) {
                Location fakeLoc = base.clone().add(0, dy, 0);
                fakeLocations.add(fakeLoc);
                player.sendBlockChange(fakeLoc, glassData);
            }
        }

        // Schedule restore
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

    public synchronized ActionResult joinQueue(Player player, boolean confirmedHardcore) {
        if (activeSpec == null || !waitingForQueue || !SIGNUP_EVENTS.contains(activeSpec.kind)) {
            return new ActionResult(false, "§7No joinable signup event is active.");
        }
        if (player == null || !isSmpPlayer(player)) {
            return new ActionResult(false, "§cYou must be in SMP to join this event.");
        }
        if (queuedParticipants.contains(player.getUniqueId())) {
            return new ActionResult(false, "§7You are already in the queue.");
        }
        if (queuedParticipants.size() >= activeSpec.maxPlayers) {
            return new ActionResult(false, "§cQueue is full.");
        }
        if ((activeSpec.kind == EventKind.HARDCORE || activeSpec.kind == EventKind.HARDCORE_FFA
                || activeSpec.kind == EventKind.HARDCORE_DUELS || activeSpec.kind == EventKind.HARDCORE_KOTH)
                && !confirmedHardcore) {
            player.sendMessage(Component.text("§4§lWARNING: §cThis hardcore event can make you lose your real items on death."));
            player.sendMessage(Component.text("[CLICK TO PROCEED]", NamedTextColor.RED, TextDecoration.BOLD)
                    .clickEvent(ClickEvent.runCommand("/event join confirm"))
                    .hoverEvent(HoverEvent.showText(Component.text("Join the hardcore event anyway", NamedTextColor.RED))));
            return new ActionResult(false, "§cHardcore warning sent. Click the button or run §f/event join confirm§c.");
        }

        adminEditAccessUntil.remove(player.getUniqueId());
        queuedParticipants.add(player.getUniqueId());
        broadcast("&f" + player.getName() + " joined " + activeSpec.displayName + " (&a" + queuedParticipants.size() + "&7/" + activeSpec.maxPlayers + ")");

        if (queuedParticipants.size() >= activeSpec.minPlayers) {
            launchSignupEvent();
        }
        return new ActionResult(true, "§aJoined the event queue.");
    }

    public synchronized ActionResult leaveQueue(Player player) {
        if (!waitingForQueue || player == null) {
            return new ActionResult(false, "§7You are not in a queue.");
        }
        if (!queuedParticipants.remove(player.getUniqueId())) {
            return new ActionResult(false, "§7You are not in the queue.");
        }
        return new ActionResult(true, "§7You left the event queue.");
    }

    public synchronized boolean submitChatAnswer(Player player, String rawAnswer) {
        if (player == null || activeSpec == null || waitingForQueue) return false;
        if (activeSpec.kind != EventKind.CHAT_MATH && activeSpec.kind != EventKind.CHAT_SCRABBLE && activeSpec.kind != EventKind.CHAT_QUIZ) {
            return false;
        }
        String answer = rawAnswer == null ? "" : rawAnswer.trim();
        if (answer.isBlank() || chatAnswer == null) return false;

        String guess = normalizeAnswer(answer);
        String expected = normalizeAnswer(chatAnswer);
        if (!matchesAnswer(guess, expected)) {
            return false;
        }

        activeParticipants.add(player.getUniqueId());
        finalizeEvent(player.getUniqueId(), activeSpec.coinReward, "answered correctly");
        return true;
    }

    public synchronized void handleParticipantDeath(Player player) {
        if (player == null || activeSpec == null || waitingForQueue) return;
        if (!activeParticipants.contains(player.getUniqueId())) return;

        if (activeSpec.kind == EventKind.KOTH || activeSpec.kind == EventKind.FFA || activeSpec.kind == EventKind.DUELS
                || activeSpec.kind == EventKind.HARDCORE || activeSpec.kind == EventKind.HARDCORE_FFA
                || activeSpec.kind == EventKind.HARDCORE_DUELS) {
            eliminated.add(player.getUniqueId());
            pendingReturnAfterDeath.add(player.getUniqueId());
            maybeFinishEliminationEvent();
        } else if (activeSpec.kind == EventKind.HARDCORE_KOTH) {
            // HC_KOTH: not eliminated (KOTH is timer-based), but pendingReturn so they respawn at world spawn
            pendingReturnAfterDeath.add(player.getUniqueId());
        }
    }

    public synchronized boolean isParticipantInHardcore(Player player) {
        if (player == null || activeSpec == null || waitingForQueue) return false;
        return (activeSpec.kind == EventKind.HARDCORE || activeSpec.kind == EventKind.HARDCORE_FFA
                || activeSpec.kind == EventKind.HARDCORE_DUELS || activeSpec.kind == EventKind.HARDCORE_KOTH)
                && activeParticipants.contains(player.getUniqueId());
    }

    public synchronized void collectHardcoreLoot(Player player, List<ItemStack> drops) {
        if (drops == null || drops.isEmpty()) return;
        for (ItemStack item : drops) {
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                hardcoreLootPool.add(item.clone());
            }
        }
    }

    public synchronized boolean shouldKeepInventoryOnDeath(Player player) {
        if (player == null || activeSpec == null || waitingForQueue) return false;
        if (!(activeSpec.kind == EventKind.KOTH || activeSpec.kind == EventKind.FFA || activeSpec.kind == EventKind.DUELS
                || activeSpec.kind == EventKind.HARDCORE || activeSpec.kind == EventKind.HARDCORE_FFA
                || activeSpec.kind == EventKind.HARDCORE_DUELS || activeSpec.kind == EventKind.HARDCORE_KOTH)) {
            return false;
        }
        boolean isHardcoreVariant = activeSpec.kind == EventKind.HARDCORE || activeSpec.kind == EventKind.HARDCORE_FFA
                || activeSpec.kind == EventKind.HARDCORE_DUELS || activeSpec.kind == EventKind.HARDCORE_KOTH;
        return activeParticipants.contains(player.getUniqueId()) && !isHardcoreVariant;
    }

    public synchronized void handleParticipantRespawn(Player player) {
        if (player == null) return;
        UUID id = player.getUniqueId();
        if (!pendingReturnAfterDeath.remove(id)) {
            return;
        }
        InventorySnapshot snapshot = inventorySnapshots.remove(id);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (snapshot != null) {
                if (activeSpec != null && (activeSpec.kind == EventKind.HARDCORE || activeSpec.kind == EventKind.HARDCORE_FFA
                        || activeSpec.kind == EventKind.HARDCORE_DUELS || activeSpec.kind == EventKind.HARDCORE_KOTH)) {
                    double targetHealth = Math.max(1.0D, Math.min(player.getMaxHealth(), 20.0D));
                    player.setHealth(targetHealth);
                    player.setFoodLevel(20);
                    if (snapshot.returnLocation() != null && snapshot.returnLocation().getWorld() != null) {
                        player.teleport(snapshot.returnLocation());
                    } else {
                        Location fallback = getWorldSpawn();
                        if (fallback != null) player.teleport(fallback);
                    }
                } else {
                    snapshot.restore(player);
                }
            } else {
                Location fallback = getWorldSpawn();
                if (fallback != null) {
                    player.teleport(fallback);
                }
            }
        });
    }

    private synchronized void autoTick() {
        if (activeSpec != null) return;
        if (!plugin.getConfig().getBoolean("event_mode.automation.enabled", true)) return;

        List<String> keys = getConfiguredEventKeys();
        if (keys.isEmpty()) return;
        String random = keys.get(ThreadLocalRandom.current().nextInt(keys.size()));
        EventSpec spec = loadSpec(random);
        if (spec == null || !spec.enabled) return;
        beginEvent(spec, true);
    }

    private synchronized void beginEvent(EventSpec spec, boolean automated) {
        activeSpec = spec;
        waitingForQueue = false;
        chatAnswer = null;
        eventEndsAtMs = 0L;
        queuedParticipants.clear();
        activeParticipants.clear();
        eliminated.clear();
        kothSecondsByPlayer.clear();

        if (SIGNUP_EVENTS.contains(spec.kind)) {
            waitingForQueue = true;
            queuedParticipants.clear();
            announceSignup(spec, automated);
            return;
        }

        if (spec.kind == EventKind.CHAT_MATH) {
            int a = ThreadLocalRandom.current().nextInt(4, 35);
            int b = ThreadLocalRandom.current().nextInt(4, 35);
            chatAnswer = Integer.toString(a + b);
            broadcast("&dMath Event: &fFirst to answer &e" + a + " + " + b + "&f wins &6" + spec.coinReward + " coins.");
            return;
        }

        if (spec.kind == EventKind.CHAT_SCRABBLE) {
            String word = pickScrabbleWord();
            String scrambled = scramble(word);
            chatAnswer = word;
            broadcast("&dScrabble Event: &fUnscramble this word: &e" + scrambled + " &7(First correct answer wins)");
            return;
        }

        if (spec.kind == EventKind.CHAT_QUIZ) {
            Quiz qa = pickQuiz();
            chatAnswer = qa.answer;
            broadcast("&dQuiz Event: &f" + qa.question + " &7(First correct answer wins)");
            return;
        }

        broadcast("&dEvent started: &f" + spec.displayName);
    }

    private synchronized void announceSignup(EventSpec spec, boolean automated) {
        // Unicode: \u25ac=blk-bar, \u2620=skull, \u25ba=arrow, \u2694=swords, \u26a0=warning, \u2717=cross, \u2605=star, \u2014=em-dash
        String bar = "\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac"
                   + "\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac"
                   + "\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac";

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin instanceof JebaitedCore core) {
                var profile = core.getProfileStore().getOrCreate(player, core.getRankManager().getDefaultGroup());
                if (profile != null && !profile.isEventNotificationsEnabled()) continue;
                if (profile != null && !profile.isEventCategoryEnabled(spec.key)) continue;
            }

            if (spec.kind == EventKind.HARDCORE || spec.kind == EventKind.HARDCORE_FFA
                    || spec.kind == EventKind.HARDCORE_DUELS || spec.kind == EventKind.HARDCORE_KOTH) {
                player.sendMessage(Component.text(bar, NamedTextColor.DARK_RED));
                player.sendMessage(
                    Component.text("  \u2620 " + spec.displayName.toUpperCase(Locale.ROOT) + " ", NamedTextColor.RED)
                        .decorate(TextDecoration.BOLD)
                        .append(Component.text("\u2014 Fight to the last breath").color(NamedTextColor.DARK_RED)
                                .decoration(TextDecoration.BOLD, false)));
                player.sendMessage(Component.empty());
                player.sendMessage(
                    Component.text("  \u25ba ", NamedTextColor.DARK_GRAY)
                        .append(Component.text("Die and you ", NamedTextColor.GRAY))
                        .append(Component.text("lose everything").color(NamedTextColor.RED).decorate(TextDecoration.BOLD))
                        .append(Component.text(" you're carrying.", NamedTextColor.GRAY)));
                player.sendMessage(
                    Component.text("  \u25ba ", NamedTextColor.DARK_GRAY)
                        .append(Component.text("Last survivor ", NamedTextColor.GRAY))
                        .append(Component.text("claims the loot pool").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                        .append(Component.text(" of every fallen player.", NamedTextColor.GRAY)));
                player.sendMessage(
                    Component.text("  \u25ba ", NamedTextColor.DARK_GRAY)
                        .append(Component.text("High risk. High reward. No second chances.", NamedTextColor.YELLOW)));
                player.sendMessage(Component.empty());
                if (isHubPlayer(player)) {
                    player.sendMessage(
                        Component.text("  \u26a0 ", NamedTextColor.YELLOW)
                            .append(Component.text("Must be in SMP to join. Run ", NamedTextColor.GRAY))
                            .append(Component.text("/smp", NamedTextColor.GREEN))
                            .append(Component.text(" first.", NamedTextColor.GRAY)));
                }
                player.sendMessage(
                    Component.text("  ")
                        .append(Component.text(" \u2694 JOIN \u2694 ").color(NamedTextColor.RED).decorate(TextDecoration.BOLD)
                            .clickEvent(ClickEvent.runCommand("/event join"))
                            .hoverEvent(HoverEvent.showText(
                                Component.text("Enter the hardcore arena.\n", NamedTextColor.RED)
                                    .append(Component.text("Your items are NOT protected.").color(NamedTextColor.DARK_RED)
                                        .decorate(TextDecoration.BOLD)))))
                        .append(Component.text("  "))
                        .append(Component.text(" \u2717 SKIP ").color(NamedTextColor.DARK_GRAY)
                            .clickEvent(ClickEvent.runCommand("/event leave"))
                            .hoverEvent(HoverEvent.showText(Component.text("Dismiss this event.", NamedTextColor.GRAY)))));
                player.sendMessage(Component.text(bar, NamedTextColor.DARK_RED));
            } else {
                String eventDesc = getEventDescription(spec);
                player.sendMessage(Component.text(bar, NamedTextColor.DARK_PURPLE));
                player.sendMessage(
                    Component.text("  \u2605 ", NamedTextColor.GOLD)
                        .append(Component.text(spec.displayName).color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD))
                        .append(Component.text(" \u2014 ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(eventDesc, NamedTextColor.GRAY)));
                player.sendMessage(
                    Component.text("  ")
                        .append(Component.text(" JOIN ").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                            .clickEvent(ClickEvent.runCommand("/event join"))
                            .hoverEvent(HoverEvent.showText(
                                Component.text("Click to join " + spec.displayName + "!", NamedTextColor.GREEN))))
                        .append(Component.text("  "))
                        .append(Component.text(" SKIP ").color(NamedTextColor.DARK_GRAY)
                            .clickEvent(ClickEvent.runCommand("/event leave"))
                            .hoverEvent(HoverEvent.showText(Component.text("Dismiss this event.", NamedTextColor.GRAY)))));
                if (isHubPlayer(player)) {
                    player.sendMessage(
                        Component.text("  \u26a0 ", NamedTextColor.YELLOW)
                            .append(Component.text("SMP event \u2014 run ", NamedTextColor.GRAY))
                            .append(Component.text("/smp", NamedTextColor.GREEN))
                            .append(Component.text(" first.", NamedTextColor.GRAY)));
                }
                player.sendMessage(Component.text(bar, NamedTextColor.DARK_PURPLE));
            }
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f, 1.8f);
        }
    }

    private String getEventDescription(EventSpec spec) {
        return switch (spec.kind) {
            case KOTH -> "Hold the hill the longest to win.";
            case FFA -> "Free-for-all. Last team standing wins. Gear is restored on death.";
            case DUELS -> "1v1 duel. Best of one. Gear is restored after the match.";
            case CHAT_MATH -> "Solve the math problem in chat first to win coins.";
            case CHAT_SCRABBLE -> "Unscramble the word in chat first to win coins.";
            case CHAT_QUIZ -> "Answer the trivia question in chat first to win coins.";
            case HARDCORE, HARDCORE_FFA -> "Hardcore FFA — die and lose your items, last survivor claims the pool.";
            case HARDCORE_DUELS -> "Hardcore duel — the loser drops everything to the winner.";
            case HARDCORE_KOTH -> "Hardcore KOTH — die and lose your items. Hill-holder wins claims the loot pool.";
            default -> plugin.getConfig().getString("event_mode.events." + spec.key + ".description", "Compete to win " + spec.coinReward + " coins.");
        };
    }

    private void giveLootToWinner(UUID winnerId) {
        if (hardcoreLootPool.isEmpty()) return;
        Player online = Bukkit.getPlayer(winnerId);
        if (online == null) {
            hardcoreLootPool.clear();
            return;
        }
        List<ItemStack> pool = new ArrayList<>(hardcoreLootPool);
        hardcoreLootPool.clear();
        int stacks = 0;
        for (ItemStack item : pool) {
            if (item == null || item.getType() == org.bukkit.Material.AIR) continue;
            stacks++;
            Map<Integer, ItemStack> overflow = online.getInventory().addItem(item);
            for (ItemStack leftover : overflow.values()) {
                online.getWorld().dropItemNaturally(online.getLocation(), leftover);
            }
        }
        online.updateInventory();
        if (stacks > 0) {
            online.sendMessage(
                Component.text("⚔ ", NamedTextColor.GOLD)
                    .append(Component.text("You claimed ", NamedTextColor.YELLOW))
                    .append(Component.text(stacks + " loot stack" + (stacks == 1 ? "" : "s"), NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(" from fallen players!", NamedTextColor.YELLOW)));
            online.playSound(online.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
        }
    }

    private synchronized void launchSignupEvent() {
        if (!waitingForQueue || activeSpec == null) return;
        activeParticipants.clear();
        for (UUID id : queuedParticipants) {
            activeParticipants.add(id);
        }
        queuedParticipants.clear();
        waitingForQueue = false;

        if (activeSpec.kind == EventKind.KOTH || activeSpec.kind == EventKind.HARDCORE_KOTH) {
            if (!isKothConfigured()) {
                broadcast("&cKOTH is not configured. Event aborted.");
                clearState();
                return;
            }
            int durationSeconds = Math.max(30, plugin.getConfig().getInt("event_mode.koth.duration_seconds", 120));
            eventEndsAtMs = System.currentTimeMillis() + durationSeconds * 1000L;
            startKothTicker();
            broadcast("&d" + activeSpec.displayName + " started with &f" + activeParticipants.size() + "&d players.");
            return;
        }

        List<Location> spawns = getArenaSpawns(activeSpec.key);
        if (spawns.isEmpty()) {
            Location eventSpawn = getEventWorldSpawn();
            if (eventSpawn != null) {
                spawns = List.of(eventSpawn);
            }
        }
        if (spawns.isEmpty()) {
            broadcast("&cNo arena spawns configured for " + activeSpec.key + ". Event aborted.");
            clearState();
            return;
        }

        int i = 0;
        for (UUID id : activeParticipants) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            saveInventorySnapshotIfAbsent(p);
            Location spawn = spawns.get(i % spawns.size());
            p.teleport(spawn);
            i++;
        }

        broadcast("&d" + activeSpec.displayName + " started with &f" + activeParticipants.size() + "&d players.");
        maybeFinishEliminationEvent();
    }

    private synchronized void maybeFinishEliminationEvent() {
        if (activeSpec == null) return;
        if (!(activeSpec.kind == EventKind.FFA || activeSpec.kind == EventKind.DUELS
                || activeSpec.kind == EventKind.HARDCORE || activeSpec.kind == EventKind.HARDCORE_FFA
                || activeSpec.kind == EventKind.HARDCORE_DUELS)) return;

        List<UUID> alive = new ArrayList<>();
        for (UUID id : activeParticipants) {
            if (!eliminated.contains(id)) {
                alive.add(id);
            }
        }

        if (alive.size() <= 1 && !activeParticipants.isEmpty()) {
            UUID winner = alive.isEmpty() ? activeParticipants.iterator().next() : alive.get(0);
            finalizeEvent(winner, activeSpec.coinReward, "won " + activeSpec.displayName);
        }
    }

    private synchronized void startKothTicker() {
        if (kothTask != null) {
            kothTask.cancel();
        }
        kothTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tickKoth();
            if (System.currentTimeMillis() >= eventEndsAtMs) {
                finishKoth();
            }
        }, 20L, 20L);
    }

    private synchronized void tickKoth() {
        Cuboid hill = getKothCuboid();
        if (hill == null) return;
        for (UUID id : activeParticipants) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !isSmpPlayer(p)) continue;
            if (!hill.contains(p.getLocation())) continue;
            kothSecondsByPlayer.merge(id, 1, Integer::sum);
        }
    }

    private synchronized void finishKoth() {
        UUID winner = kothSecondsByPlayer.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);
        if (winner == null && !activeParticipants.isEmpty()) {
            winner = activeParticipants.iterator().next();
        }
        if (winner == null) {
            broadcast("&7" + activeSpec.displayName + " ended with no winner.");
            clearState();
            return;
        }
        finalizeEvent(winner, activeSpec.coinReward, "won " + activeSpec.displayName);
    }

    private synchronized void finalizeEvent(UUID winner, int reward, String reason) {
        if (activeSpec == null) return;
        EventSpec ended = activeSpec;

        rewardWinner(winner, reward, ended.displayName);
        writeEventStats(ended.key, winner);
        restoreSnapshots();
        if (ended.kind == EventKind.HARDCORE || ended.kind == EventKind.HARDCORE_FFA
                || ended.kind == EventKind.HARDCORE_DUELS || ended.kind == EventKind.HARDCORE_KOTH) {
            giveLootToWinner(winner);
        }

        OfflinePlayer w = Bukkit.getOfflinePlayer(winner);
        String winnerName = w.getName() == null ? winner.toString().substring(0, 8) : w.getName();
        broadcast("&dEvent complete: &f" + ended.displayName + "&7. Winner: &a" + winnerName + " &7(+" + reward + " coins)");
        if (reason != null && !reason.isBlank()) {
            broadcast("&7Reason: &f" + reason);
        }

        clearState();
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
        if (store == null) return;

        boolean chatCategory = eventKey != null && eventKey.toLowerCase(Locale.ROOT).startsWith("chat_");
        for (UUID id : activeParticipants) {
            boolean won = id.equals(winner);
            store.updateEventStats(id, eventKey, 1, won ? 1 : 0, won ? 0 : 1);
            if (won) {
                OfflinePlayer participant = Bukkit.getOfflinePlayer(id);
                PlayerProfile profile = store.getOrCreate(participant, core.getRankManager().getDefaultGroup());
                if (profile != null) {
                    if (chatCategory) {
                        profile.incEventWinsChat();
                    } else if (activeSpec != null && (activeSpec.kind == EventKind.HARDCORE
                            || activeSpec.kind == EventKind.HARDCORE_FFA || activeSpec.kind == EventKind.HARDCORE_DUELS
                            || activeSpec.kind == EventKind.HARDCORE_KOTH)) {
                        profile.incEventWinsHardcore();
                    } else {
                        profile.incEventWinsCombat();
                    }
                    store.saveDeferred(id);
                }
            }
        }
    }

    private EventSpec loadSpec(String rawKey) {
        String key = normalizeEventKey(rawKey);
        var sec = plugin.getConfig().getConfigurationSection("event_mode.events." + key);
        if (sec == null) {
            return null;
        }

        String display = compactDisplayName(key, sec.getString("display_name", key.toUpperCase(Locale.ROOT)));
        boolean enabled = sec.getBoolean("enabled", true);
        int reward = Math.max(0, sec.getInt("coin_reward", 10));
        int minPlayers = Math.max(1, sec.getInt("min_players", defaultMinPlayers(key)));
        int maxPlayers = Math.max(minPlayers, sec.getInt("max_players", defaultMaxPlayers(key)));
        EventKind kind = kindForKey(key);

        return new EventSpec(key, display, enabled, reward, minPlayers, maxPlayers, kind);
    }

    private int defaultMinPlayers(String key) {
        return switch (normalizeEventKey(key)) {
            case "duels", "hardcore_duels" -> 2;
            case "koth", "ffa", "hardcore", "hardcore_ffa", "hardcore_koth" -> 2;
            default -> 1;
        };
    }

    private int defaultMaxPlayers(String key) {
        return switch (normalizeEventKey(key)) {
            case "duels", "hardcore_duels" -> 2;
            case "koth", "ffa", "hardcore", "hardcore_ffa", "hardcore_koth" -> 32;
            default -> 1;
        };
    }

    private EventKind kindForKey(String key) {
        return switch (key) {
            case "chat_math" -> EventKind.CHAT_MATH;
            case "chat_scrabble", "chat_anagram", "chat_word" -> EventKind.CHAT_SCRABBLE;
            case "chat_quiz" -> EventKind.CHAT_QUIZ;
            case "koth" -> EventKind.KOTH;
            case "ffa" -> EventKind.FFA;
            case "duels" -> EventKind.DUELS;
            case "hardcore" -> EventKind.HARDCORE_FFA;  // legacy alias
            case "hardcore_ffa" -> EventKind.HARDCORE_FFA;
            case "hardcore_duels" -> EventKind.HARDCORE_DUELS;
            case "hardcore_koth" -> EventKind.HARDCORE_KOTH;
            default -> EventKind.OTHER;
        };
    }

    private String normalizeEventKey(String raw) {
        if (raw == null) return "";
        String key = raw.toLowerCase(Locale.ROOT).trim();
        if ("math".equals(key) || "chatmath".equals(key)) return "chat_math";
        if ("scrabble".equals(key)) return "chat_scrabble";
        if ("anagram".equals(key) || "chat_anagram".equals(key) || "chat_word".equals(key) || "word".equals(key)) return "chat_scrabble";
        if ("quiz".equals(key) || "question".equals(key)) return "chat_quiz";
        if ("lms".equals(key)) return "ffa";
        if ("hardcoreffa".equals(key)) return "hardcore_ffa";
        if ("hardcoreduels".equals(key)) return "hardcore_duels";
        if ("hardcorekoth".equals(key)) return "hardcore_koth";
        return key;
    }

    private String pickScrabbleWord() {
        List<String> words = plugin.getConfig().getStringList("event_mode.chat.scrabble_words");
        if (words == null || words.isEmpty()) {
            words = List.of("minecraft", "diamond", "creeper", "survival", "jebaited");
        }
        String selected = words.get(ThreadLocalRandom.current().nextInt(words.size()));
        return selected.toLowerCase(Locale.ROOT).trim();
    }

    private String scramble(String word) {
        List<Character> chars = new ArrayList<>();
        for (char c : word.toCharArray()) chars.add(c);
        java.util.Collections.shuffle(chars);
        StringBuilder sb = new StringBuilder(chars.size());
        for (char c : chars) sb.append(c);
        String out = sb.toString();
        if (out.equalsIgnoreCase(word) && out.length() > 1) {
            return new StringBuilder(out).reverse().toString();
        }
        return out;
    }

    private record Quiz(String question, String answer) {}

    private Quiz pickQuiz() {
        var sec = plugin.getConfig().getConfigurationSection("event_mode.chat.quiz");
        if (sec == null || sec.getKeys(false).isEmpty()) {
            return new Quiz("What dimension do you need Eyes of Ender for?", "end");
        }
        List<String> keys = new ArrayList<>(sec.getKeys(false));
        String pick = keys.get(ThreadLocalRandom.current().nextInt(keys.size()));
        String q = sec.getString(pick + ".question", "What item summons the Wither?");
        String a = sec.getString(pick + ".answer", "soul sand");
        return new Quiz(q, normalizeAnswer(a));
    }

    private boolean isKothConfigured() {
        return getKothCuboid() != null;
    }

    private Cuboid getKothCuboid() {
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
        return new Cuboid(world, x1, y1, z1, x2, y2, z2);
    }

    private List<Location> getArenaSpawns(String eventKey) {
        String key = normalizeArenaKey(eventKey);
        return spawnCache.getOrDefault(key, List.of());
    }

    private String normalizeArenaKey(String arenaKeyRaw) {
        String key = normalizeEventKey(arenaKeyRaw);
        // Duels and its HC variant share the same spawn pool
        if ("duels".equals(key) || "hardcore_duels".equals(key)) return "duels";
        // FFA and HC_FFA share the same spawn pool; legacy "hardcore" also maps here
        if ("ffa".equals(key) || "hardcore_ffa".equals(key) || "hardcore".equals(key)) return "ffa";
        // HC_KOTH uses pos1/pos2, not arena spawns — reject gracefully
        return key;
    }

    private boolean isValidArenaKey(String normalised) {
        return "ffa".equals(normalised) || "duels".equals(normalised);
    }

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
             PreparedStatement ps = conn.prepareStatement("SELECT arena_key, world_name, x, y, z, yaw, pitch FROM event_spawns ORDER BY id ASC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String key = rs.getString("arena_key");
                String worldName = rs.getString("world_name");
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                float yaw = rs.getFloat("yaw");
                float pitch = rs.getFloat("pitch");
                org.bukkit.World w = org.bukkit.Bukkit.getWorld(worldName);
                // World may not be loaded yet at this point — store decoded lazily via encoded string for now
                if (w != null) {
                    loaded.computeIfAbsent(key, k -> new ArrayList<>()).add(new Location(w, x, y, z, yaw, pitch));
                } else {
                    // World not loaded; re-attempt resolve on main thread
                    final String fKey = key; final double fx = x, fy = y, fz = z; final float fyaw = yaw, fpitch = pitch; final String fWorld = worldName;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        org.bukkit.World resolved = org.bukkit.Bukkit.getWorld(fWorld);
                        if (resolved != null) {
                            spawnCache.computeIfAbsent(fKey, k -> new ArrayList<>()).add(new Location(resolved, fx, fy, fz, fyaw, fpitch));
                        }
                    });
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[EventSpawns] Failed to load spawns from DB: " + e.getMessage());
            return;
        }
        // Merge loaded results into cache (main-thread worlds already resolved)
        for (Map.Entry<String, List<Location>> entry : loaded.entrySet()) {
            spawnCache.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        if (!loaded.isEmpty()) {
            plugin.getLogger().info("[EventSpawns] Loaded " + loaded.values().stream().mapToInt(List::size).sum() + " arena spawn(s) from database.");
        }
    }

    private String encodeLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "";
        }
        return location.getWorld().getName() + ":"
                + location.getX() + ":"
                + location.getY() + ":"
                + location.getZ() + ":"
                + location.getYaw() + ":"
                + location.getPitch();
    }

    private Location decodeLocation(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] p = raw.split(":");
        if (p.length < 6) return null;
        var world = Bukkit.getWorld(p[0]);
        if (world == null) return null;
        try {
            double x = Double.parseDouble(p[1]);
            double y = Double.parseDouble(p[2]);
            double z = Double.parseDouble(p[3]);
            float yaw = Float.parseFloat(p[4]);
            float pitch = Float.parseFloat(p[5]);
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Location getWorldSpawn() {
        String smp = plugin.getConfig().getString("worlds.smp", "smp");
        var world = Bukkit.getWorld(smp);
        if (world != null) return world.getSpawnLocation();
        if (!Bukkit.getWorlds().isEmpty()) return Bukkit.getWorlds().get(0).getSpawnLocation();
        return null;
    }

    public Location getAdminEditSpawn() {
        Location spawn = getEventWorldSpawn();
        return spawn == null ? getWorldSpawn() : spawn.clone();
    }

    public void grantAdminEditAccess(Player player) {
        if (player == null) {
            return;
        }
        long seconds = Math.max(60L, plugin.getConfig().getLong("event_mode.admin_edit_window_seconds", 600L));
        adminEditAccessUntil.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000L));
    }

    public boolean canAdminEdit(Player player) {
        if (player == null) {
            return false;
        }
        UUID id = player.getUniqueId();
        if (queuedParticipants.contains(id) || activeParticipants.contains(id)) {
            adminEditAccessUntil.remove(id);
            return false;
        }
        Long until = adminEditAccessUntil.get(id);
        if (until == null) {
            return false;
        }
        if (until < System.currentTimeMillis()) {
            adminEditAccessUntil.remove(id);
            return false;
        }
        return true;
    }

    public synchronized ActionResult rebuildEventWorld(boolean confirmed) {
        if (!confirmed) {
            return new ActionResult(false, "§cThis deletes the current event world. Run §f/event rebuildworld confirm§c.");
        }
        if (activeSpec != null) {
            return new ActionResult(false, "§cStop the current event before rebuilding the event world.");
        }

        String eventWorldName = plugin.getConfig().getString("event_mode.world", "events");
        if (eventWorldName == null || eventWorldName.isBlank()) {
            return new ActionResult(false, "§cNo event world is configured.");
        }

        Location fallback = getWorldSpawn();
        World existing = Bukkit.getWorld(eventWorldName);
        if (existing != null) {
            for (Player player : new ArrayList<>(existing.getPlayers())) {
                if (fallback != null) {
                    player.teleport(fallback);
                }
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
        if (rebuilt == null) {
            return new ActionResult(false, "§cFailed to recreate the event world.");
        }
        rebuilt.setTime(1000L);
        rebuilt.setStorm(false);
        rebuilt.setThundering(false);
        rebuilt.setClearWeatherDuration(20 * 60 * 20);
        return new ActionResult(true, "§aEvent world rebuilt as a fresh superflat map: §f" + rebuilt.getName());
    }

    private Location getEventWorldSpawn() {
        String eventWorldName = plugin.getConfig().getString("event_mode.world", "events");
        if (eventWorldName == null || eventWorldName.isBlank()) {
            return getWorldSpawn();
        }
        World world = ensureEventWorld(eventWorldName);
        if (world != null) {
            return world.getSpawnLocation();
        }
        return getWorldSpawn();
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
            WorldBorder border = world.getWorldBorder();
            border.setCenter(0.0, 0.0);
            border.setSize(Math.max(1000.0, plugin.getConfig().getDouble("event_mode.world_border_size", 10000.0)));
        }
        return world;
    }

    private String normalizeAnswer(String value) {
        if (value == null) {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT).trim();
        return lower.replaceAll("[^a-z0-9]", "");
    }

    private boolean matchesAnswer(String guess, String expected) {
        if (guess.isBlank() || expected.isBlank()) {
            return false;
        }
        if (guess.equals(expected)) {
            return true;
        }
        if (expected.startsWith(guess) && guess.length() >= 3) {
            return true;
        }
        return guess.contains(expected);
    }

    private String compactDisplayName(String key, String configured) {
        return switch (normalizeEventKey(key)) {
            case "chat_scrabble" -> "Scrabble";
            case "chat_math" -> "Math";
            case "chat_quiz" -> "Quiz";
            default -> configured;
        };
    }

    private void saveInventorySnapshotIfAbsent(Player player) {
        if (player == null || inventorySnapshots.containsKey(player.getUniqueId())) {
            return;
        }
        inventorySnapshots.put(player.getUniqueId(), InventorySnapshot.capture(player));
    }

    private void restoreSnapshots() {
        for (Map.Entry<UUID, InventorySnapshot> entry : new HashMap<>(inventorySnapshots).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            entry.getValue().restore(player);
        }
        inventorySnapshots.clear();
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

    private boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                if (!deleteRecursively(child)) {
                    return false;
                }
            }
        }
        return file.delete();
    }

    private void clearState() {
        activeSpec = null;
        waitingForQueue = false;
        chatAnswer = null;
        eventEndsAtMs = 0L;
        queuedParticipants.clear();
        activeParticipants.clear();
        eliminated.clear();
        pendingReturnAfterDeath.clear();
        kothSecondsByPlayer.clear();
        hardcoreLootPool.clear();
        restoreSnapshots();
        if (kothTask != null) {
            kothTask.cancel();
            kothTask = null;
        }
    }

    private void stopAutoTask() {
        if (autoTask != null) {
            autoTask.cancel();
            autoTask = null;
        }
    }

    private void broadcast(String raw) {
        String prefix = plugin.getConfig().getString("event_mode.broadcast_prefix", "&9[&dEVENT&9] &f");
        String message = ChatColor.translateAlternateColorCodes('&', prefix + raw);
        if (plugin instanceof JebaitedCore core) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerProfile profile = core.getProfileStore().getOrCreate(player, core.getRankManager().getDefaultGroup());
                if (profile != null && !profile.isEventNotificationsEnabled()) {
                    continue;
                }
                if (profile != null && activeSpec != null && !profile.isEventCategoryEnabled(activeSpec.key)) {
                    continue;
                }
                player.sendMessage(message);
            }
            return;
        }
        Bukkit.broadcastMessage(message);
    }

    private static final class EventSpec {
        private final String key;
        private final String displayName;
        private final boolean enabled;
        private final int coinReward;
        private final int minPlayers;
        private final int maxPlayers;
        private final EventKind kind;

        private EventSpec(String key, String displayName, boolean enabled, int coinReward, int minPlayers, int maxPlayers, EventKind kind) {
            this.key = key;
            this.displayName = displayName;
            this.enabled = enabled;
            this.coinReward = coinReward;
            this.minPlayers = minPlayers;
            this.maxPlayers = maxPlayers;
            this.kind = kind;
        }
    }

    private static final class Cuboid {
        private final String world;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        private Cuboid(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
            this.world = world;
            this.minX = Math.min(x1, x2);
            this.minY = Math.min(y1, y2);
            this.minZ = Math.min(z1, z2);
            this.maxX = Math.max(x1, x2);
            this.maxY = Math.max(y1, y2);
            this.maxZ = Math.max(z1, z2);
        }

        private boolean contains(Location loc) {
            if (loc == null || loc.getWorld() == null) return false;
            if (!world.equalsIgnoreCase(loc.getWorld().getName())) return false;
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    private record InventorySnapshot(
            ItemStack[] contents,
            ItemStack[] armor,
            ItemStack offHand,
            int level,
            float exp,
            int food,
            double health,
            Location returnLocation
    ) {
        private static InventorySnapshot capture(Player player) {
            ItemStack[] contents = player.getInventory().getContents().clone();
            ItemStack[] armor = player.getInventory().getArmorContents().clone();
            ItemStack offHand = player.getInventory().getItemInOffHand() == null ? null : player.getInventory().getItemInOffHand().clone();
            int level = player.getLevel();
            float exp = player.getExp();
            int food = player.getFoodLevel();
            double health = player.getHealth();
            Location location = player.getLocation().clone();
            return new InventorySnapshot(contents, armor, offHand, level, exp, food, health, location);
        }

        private void restore(Player player) {
            player.getInventory().setContents(contents == null ? new ItemStack[0] : contents.clone());
            player.getInventory().setArmorContents(armor == null ? new ItemStack[0] : armor.clone());
            player.getInventory().setItemInOffHand(offHand == null ? null : offHand.clone());
            player.setLevel(level);
            player.setExp(exp);
            player.setFoodLevel(food);
            double targetHealth = Math.max(1.0D, Math.min(player.getMaxHealth(), health));
            player.setHealth(targetHealth);
            if (returnLocation != null && returnLocation.getWorld() != null) {
                player.teleport(returnLocation);
            }
            player.updateInventory();
        }
    }
}
