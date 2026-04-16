package com.darkniightz.core.system;

import com.darkniightz.main.JebaitedCore;
import com.darkniightz.main.database.DatabaseManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Supported Paper range: 1.21.9-1.21.11.
 */
public class AuditLogService implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Set<String> SENSITIVE_COMMANDS = Set.of(
            "login", "l", "register", "reg", "password", "changepassword", "changepass", "cpass"
    );

    private final JebaitedCore plugin;
    private final DatabaseManager databaseManager;
    private int cleanupTaskId = -1;

    public AuditLogService(JebaitedCore plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public void start() {
        if (databaseManager == null || !databaseManager.isEnabled()) {
            return;
        }
        cleanupAsync();
        if (cleanupTaskId != -1) {
            Bukkit.getScheduler().cancelTask(cleanupTaskId);
        }
        cleanupTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupNow, 20L, 20L * 60L * 60L).getTaskId();
    }

    public void stop() {
        if (cleanupTaskId != -1) {
            Bukkit.getScheduler().cancelTask(cleanupTaskId);
            cleanupTaskId = -1;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        if (databaseManager == null || !databaseManager.isEnabled()) {
            return;
        }
        String message = PLAIN.serialize(event.message()).trim();
        if (message.isEmpty()) {
            return;
        }
        logChat(event.getPlayer().getUniqueId(), event.getPlayer().getName(), message);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (databaseManager == null || !databaseManager.isEnabled()) {
            return;
        }
        String raw = event.getMessage();
        if (raw == null || raw.isBlank() || raw.charAt(0) != '/') {
            return;
        }
        String label = raw.substring(1).split("\\s+")[0].toLowerCase(Locale.ROOT);
        int idx = label.indexOf(':');
        if (idx >= 0) {
            label = label.substring(idx + 1);
        }
        if (SENSITIVE_COMMANDS.contains(label)) {
            return;
        }
        logCommand(event.getPlayer().getUniqueId(), event.getPlayer().getName(), raw);
    }

    public void logChat(UUID playerUuid, String playerName, String message) {
        queueInsert("INSERT INTO chat_logs (player_uuid, player_name, message) VALUES (?, ?, ?);", playerUuid, playerName, message);
    }

    public void logCommand(UUID playerUuid, String playerName, String rawCommand) {
        if (databaseManager == null || !databaseManager.isEnabled() || playerUuid == null || rawCommand == null || rawCommand.isBlank()) return;
        final long ts = System.currentTimeMillis();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO player_command_log (uuid, username, command, timestamp) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING;")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, playerName == null ? "unknown" : playerName);
                ps.setString(3, rawCommand);
                ps.setLong(4, ts);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to insert command log row", e);
            }
        });
    }

    private void queueInsert(String sql, UUID playerUuid, String playerName, String payload) {
        if (databaseManager == null || !databaseManager.isEnabled() || playerUuid == null || payload == null || payload.isBlank()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = databaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, playerUuid);
                ps.setString(2, playerName == null ? "unknown" : playerName);
                ps.setString(3, payload);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to insert audit log row", e);
            }
        });
    }

    private void cleanupAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::cleanupNow);
    }

    private void cleanupNow() {
        if (databaseManager == null || !databaseManager.isEnabled()) {
            return;
        }
        cleanupTable("chat_logs", "created_at", false);
        cleanupTable("player_command_log", "timestamp", true);
    }

    /**
     * @param timestampCol  column name holding the time value
     * @param epochMs       true if the column is a BIGINT epoch-ms value, false if it's a TIMESTAMPTZ
     */
    private void cleanupTable(String tableName, String timestampCol, boolean epochMs) {
        String deleteOld = epochMs
            ? "DELETE FROM " + tableName + " WHERE " + timestampCol + " < (EXTRACT(EPOCH FROM NOW() - INTERVAL '30 days') * 1000)::bigint;"
            : "DELETE FROM " + tableName + " WHERE " + timestampCol + " < NOW() - INTERVAL '30 days';";
        String capRows = "DELETE FROM " + tableName + " WHERE id NOT IN (SELECT id FROM " + tableName + " ORDER BY " + timestampCol + " DESC, id DESC LIMIT 10000);";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement psOld = conn.prepareStatement(deleteOld);
             PreparedStatement psCap = conn.prepareStatement(capRows)) {
            psOld.executeUpdate();
            psCap.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to clean up audit table " + tableName, e);
        }
    }
}
