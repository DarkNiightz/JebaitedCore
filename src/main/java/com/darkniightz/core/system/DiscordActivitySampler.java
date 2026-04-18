package com.darkniightz.core.system;

import com.darkniightz.main.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.logging.Level;

/**
 * Records online player counts for Discord /activity charts.
 */
public final class DiscordActivitySampler {
    private final Plugin plugin;
    private final DatabaseManager databaseManager;
    private int taskId = -1;

    public DiscordActivitySampler(Plugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public void start() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        long interval = Math.max(600L, plugin.getConfig().getLong("integrations.discord.activity_sample_interval_ticks", 6000L));
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::sample, interval, interval);
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    private void sample() {
        if (databaseManager == null) {
            return;
        }
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String insert =
                    "INSERT INTO discord_activity_sample (online_count, max_players) VALUES (?, ?);";
            String prune = "DELETE FROM discord_activity_sample WHERE sampled_at < NOW() - INTERVAL '48 hours';";
            try (Connection conn = databaseManager.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(insert)) {
                    ps.setInt(1, online);
                    ps.setInt(2, max);
                    ps.executeUpdate();
                }
                try (Statement st = conn.createStatement()) {
                    st.execute(prune);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE, "discord activity sample failed", e);
            }
        });
    }
}
