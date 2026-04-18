package com.darkniightz.core.eventmode;

import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.main.JebaitedCore;
import com.darkniightz.main.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Parallel mini-games in chat (math / scramble / quiz). Does not use {@link com.darkniightz.core.eventmode.EventEngine}
 * session — arena events may run at the same time.
 */
public final class ChatGameManager {

    public record ActionResult(boolean ok, String message) {}

    private final Plugin plugin;
    private final ChatGameEngine engine;

    private final List<ChatGameSpec> specs = new ArrayList<>();
    private volatile ChatGameSession session;
    private BukkitTask autoTask;

    public ChatGameManager(Plugin plugin) {
        this.plugin = plugin;
        this.engine = new ChatGameEngine(plugin);
    }

    public void reloadFromConfig() {
        maybeMigrateLegacyGamesYaml();
        specs.clear();
        var root = plugin.getConfig().getConfigurationSection("chat_games.games");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            String norm = ChatGameKeys.normalize(key);
            ChatGameKind kind = ChatGameKind.fromConfigKey(norm);
            if (kind == null) {
                continue;
            }
            var sec = root.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            String display = ChatGameKind.compactDisplayName(norm, sec.getString("display_name", norm));
            boolean enabled = sec.getBoolean("enabled", true);
            int reward = Math.max(0, sec.getInt("coin_reward", 10));
            String target = sec.getString("target_server", "SMP");
            specs.add(new ChatGameSpec(norm, display, enabled, reward, kind, target));
        }
    }

    /**
     * If {@code chat_games.games} is empty but legacy {@code event_mode.events.chat_*} blocks exist, copy once.
     */
    private void maybeMigrateLegacyGamesYaml() {
        var games = plugin.getConfig().getConfigurationSection("chat_games.games");
        boolean empty = games == null || games.getKeys(false).isEmpty();
        if (!empty) {
            return;
        }
        var legacy = plugin.getConfig().getConfigurationSection("event_mode.events");
        if (legacy == null) {
            return;
        }
        List<String> chatKeys = new ArrayList<>();
        for (String k : legacy.getKeys(false)) {
            if (ChatGameKeys.isChatGameConfigKey(k)) {
                chatKeys.add(ChatGameKeys.normalize(k));
            }
        }
        if (chatKeys.isEmpty()) {
            return;
        }
        plugin.getConfig().createSection("chat_games.games");
        for (String norm : chatKeys) {
            var from = legacy.getConfigurationSection(norm);
            if (from == null) {
                continue;
            }
            String base = "chat_games.games." + norm;
            plugin.getConfig().set(base + ".enabled", from.getBoolean("enabled", true));
            plugin.getConfig().set(base + ".display_name", from.getString("display_name", norm));
            plugin.getConfig().set(base + ".coin_reward", from.getInt("coin_reward", 10));
            plugin.getConfig().set(base + ".target_server", from.getString("target_server", "SMP"));
        }
        plugin.saveConfig();
        plugin.getLogger().info("[ChatGames] Migrated " + chatKeys.size()
                + " chat game block(s) from event_mode.events to chat_games.games.");
    }

    public void start() {
        reloadFromConfig();
        stopAutoTask();
        long everyTicks = Math.max(20L, plugin.getConfig().getLong("chat_games.automation.interval_seconds", 180L) * 20L);
        autoTask = Bukkit.getScheduler().runTaskTimer(plugin, this::autoTick, everyTicks, everyTicks);
    }

    public void stop() {
        stopAutoTask();
        synchronized (this) {
            if (session != null) {
                int sid = session.persistenceSessionId;
                long started = session.startedAtMs;
                String ck = session.spec.configKey;
                String dn = session.spec.displayName;
                session = null;
                persistSessionEndAsync(sid, null, List.of(), 0);
                ChatGamePanelNotifier.post(plugin, "stopped", ck, dn, sid, null, 0, started, System.currentTimeMillis());
            }
        }
    }

    private void stopAutoTask() {
        if (autoTask != null) {
            autoTask.cancel();
            autoTask = null;
        }
    }

    private void autoTick() {
        synchronized (this) {
            if (session != null) {
                return;
            }
            if (!plugin.getConfig().getBoolean("chat_games.automation.enabled", false)) {
                return;
            }
            List<ChatGameSpec> enabled = new ArrayList<>();
            for (ChatGameSpec s : specs) {
                if (s.enabled) {
                    enabled.add(s);
                }
            }
            if (enabled.isEmpty()) {
                return;
            }
            ChatGameSpec pick = enabled.get(ThreadLocalRandom.current().nextInt(enabled.size()));
            startRoundLocked(pick);
        }
    }

    public synchronized ActionResult startRound(String rawKey) {
        String key = ChatGameKeys.normalize(rawKey);
        ChatGameSpec spec = findSpec(key);
        if (spec == null || !spec.enabled) {
            return new ActionResult(false, "§cUnknown or disabled chat game: §e" + rawKey);
        }
        if (session != null) {
            return new ActionResult(false, "§cA chat game is already active: §f" + session.spec.displayName);
        }
        startRoundLocked(spec);
        return new ActionResult(true, "§aStarted chat game: §f" + spec.displayName);
    }

    public synchronized ActionResult stopRound(String reason) {
        if (session == null) {
            return new ActionResult(false, "§7No active chat game.");
        }
        int sid = session.persistenceSessionId;
        long started = session.startedAtMs;
        String ck = session.spec.configKey;
        String dn = session.spec.displayName;
        session = null;
        persistSessionEndAsync(sid, null, List.of(), 0);
        ChatGamePanelNotifier.post(plugin, "stopped", ck, dn, sid, null, 0, started, System.currentTimeMillis());
        return new ActionResult(true, "§7Chat game stopped" + (reason == null || reason.isBlank() ? "." : ": §f" + reason));
    }

    private void startRoundLocked(ChatGameSpec spec) {
        long now = System.currentTimeMillis();
        ChatGameSession s = new ChatGameSession(spec, now);
        this.session = s;
        engine.startRound(s, line -> EventNotifications.broadcastCategory(plugin, spec.configKey, line));
        schedulePersistSessionStart(s);
    }

    private ChatGameSpec findSpec(String normalizedKey) {
        for (ChatGameSpec s : specs) {
            if (s.configKey.equalsIgnoreCase(normalizedKey)) {
                return s;
            }
        }
        return null;
    }

    private void schedulePersistSessionStart(ChatGameSession anchor) {
        if (!(plugin instanceof JebaitedCore core)) {
            return;
        }
        DatabaseManager db = core.getDatabaseManager();
        if (db == null || !db.isEnabled()) {
            return;
        }
        EventParticipantDAO dao = core.getEventParticipantDAO();
        if (dao == null) {
            return;
        }
        long startedAt = anchor.startedAtMs;
        String eventType = anchor.spec.configKey;
        EventParticipantDAO.runAsync(plugin, () -> {
            int id = dao.insertSessionRunning(eventType, null, startedAt);
            if (id <= 0) {
                return;
            }
            EventParticipantDAO.runOnMainWhenPossible(plugin, () -> {
                synchronized (ChatGameManager.this) {
                    if (session != anchor) {
                        return;
                    }
                    anchor.persistenceSessionId = id;
                    ChatGamePanelNotifier.post(plugin, "started", anchor.spec.configKey, anchor.spec.displayName,
                            id, null, 0, startedAt, System.currentTimeMillis());
                }
            });
        });
    }

    public synchronized boolean submitAnswer(Player player, String rawAnswer) {
        if (player == null || session == null) {
            return false;
        }
        ChatGameSession snap = session;
        if (!engine.tryAcceptAnswer(snap, player, rawAnswer)) {
            return false;
        }
        UUID winner = player.getUniqueId();
        int reward = snap.spec.coinReward;
        int sid = snap.persistenceSessionId;
        long started = snap.startedAtMs;
        String configKey = snap.spec.configKey;
        String displayName = snap.spec.displayName;
        session = null;

        rewardAndStats(winner, reward, displayName, configKey);
        broadcastWin(winner, reward, displayName, configKey);

        List<EventParticipantDAO.ParticipantRow> rows = List.of(
                new EventParticipantDAO.ParticipantRow(winner, 0, 0, "WIN", reward, 0));
        persistSessionEndAsync(sid, winner, rows, 1);

        long ended = System.currentTimeMillis();
        ChatGamePanelNotifier.post(plugin, "solved", configKey, displayName, sid, winner, reward, started, ended);
        return true;
    }

    private void rewardAndStats(UUID winner, int reward, String displayName, String configKey) {
        if (!(plugin instanceof JebaitedCore core)) {
            return;
        }
        ProfileStore store = core.getProfileStore();
        if (store == null) {
            return;
        }
        if (reward > 0) {
            Player online = Bukkit.getPlayer(winner);
            if (online != null) {
                store.grantCosmeticCoins(online, reward, displayName + " winner");
            } else {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(winner);
                PlayerProfile profile = store.getOrCreate(offline, core.getRankManager().getDefaultGroup());
                if (profile != null) {
                    profile.addCosmeticCoins(reward);
                    store.save(offline.getUniqueId());
                }
            }
        }
        store.updateEventStats(winner, configKey, 1, 1, 0);
        OfflinePlayer op = Bukkit.getOfflinePlayer(winner);
        PlayerProfile profile = store.getOrCreate(op, core.getRankManager().getDefaultGroup());
        if (profile != null) {
            profile.incEventWinsChat();
            store.saveDeferred(winner);
        }
    }

    private void broadcastWin(UUID winner, int reward, String displayName, String categoryKey) {
        OfflinePlayer w = Bukkit.getOfflinePlayer(winner);
        String winnerName = w.getName() == null ? winner.toString().substring(0, 8) : w.getName();
        String line = "§d★ §fChat game over: §d" + displayName + " §7— Winner: §a" + winnerName
                + " §8(+§6" + reward + " coins§8)";
        EventNotifications.broadcastCategory(plugin, categoryKey, line);
    }

    private void persistSessionEndAsync(int sessionId, UUID winnerOrNull,
                                        List<EventParticipantDAO.ParticipantRow> rows, int participantCount) {
        if (sessionId <= 0) {
            return;
        }
        if (!(plugin instanceof JebaitedCore core)) {
            return;
        }
        if (!core.getDatabaseManager().isEnabled()) {
            return;
        }
        EventParticipantDAO dao = core.getEventParticipantDAO();
        if (dao == null) {
            return;
        }
        EventParticipantDAO.runAsync(plugin, () -> {
            long endedAt = System.currentTimeMillis();
            dao.updateSessionEnd(sessionId, endedAt, winnerOrNull, null, participantCount);
            if (rows != null && !rows.isEmpty()) {
                dao.upsertParticipantsBatch(sessionId, rows);
            }
        });
    }

    public synchronized String getStatusLine() {
        if (session == null) {
            return "§7No active chat game.";
        }
        return "§dChat: §f" + session.spec.displayName + " §8| §7" + session.spec.kind.scoreboardHint();
    }

    public synchronized List<String> getChatScoreboardLines() {
        if (session == null) {
            return List.of();
        }
        return List.of(
                "§dChat §8| §f" + session.spec.kind.shortLabel(),
                "§7" + session.spec.kind.scoreboardHint());
    }

    public synchronized boolean isActive() {
        return session != null;
    }

    public List<String> getConfiguredKeys() {
        List<String> out = new ArrayList<>();
        for (ChatGameSpec s : specs) {
            out.add(s.configKey);
        }
        return out;
    }

    public List<String> getConfiguredDisplayNames() {
        List<String> out = new ArrayList<>();
        for (ChatGameSpec s : specs) {
            out.add(s.displayName);
        }
        return out;
    }

    /** Keys for stats UI merge (stable order). */
    public static List<String> chatGameStatKeys(Plugin plugin) {
        List<String> out = new ArrayList<>();
        var root = plugin.getConfig().getConfigurationSection("chat_games.games");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                String norm = ChatGameKeys.normalize(key);
                if (ChatGameKind.fromConfigKey(norm) != null) {
                    out.add(norm);
                }
            }
        }
        return out;
    }

}
