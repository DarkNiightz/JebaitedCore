package com.darkniightz.core.system;

import com.darkniightz.main.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OverallStatsManager {
    public static final String UNIQUE_LOGINS = "unique_logins";
    public static final String TOTAL_JOINS = "total_joins";
    public static final String TOTAL_MESSAGES = "total_messages_sent";
    public static final String TOTAL_COMMANDS = "total_commands_sent";
    public static final String TOTAL_KILLS = "total_kills";
    public static final String TOTAL_DEATHS = "total_deaths";
    public static final String TOTAL_MOBS = "total_mobs_killed";
    public static final String TOTAL_BOSSES = "total_bosses_killed";
    public static final String TOTAL_BLOCKS = "total_blocks_broken";
    public static final String TOTAL_CROPS = "total_crops_broken";
    public static final String TOTAL_FISH = "total_fish_caught";
    public static final String TOTAL_PLAYTIME_MS = "total_playtime_ms";
    public static final String TOTAL_TRADES = "total_trades_completed";
    public static final String TOTAL_GRAVES = "total_graves_created";
    public static final String TOTAL_COMBAT_LOG_GRAVES = "total_combatlog_graves";

    private final DatabaseManager database;
    private final Logger logger;

    public OverallStatsManager(DatabaseManager database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    public void increment(String statKey, long amount) {
        if (statKey == null || statKey.isBlank() || amount == 0L) return;
        if (database == null || !database.isEnabled()) return;

        String sql = "INSERT INTO overall_stats(stat_key, stat_value, updated_at) VALUES (?, ?, ?) " +
                "ON CONFLICT (stat_key) DO UPDATE SET stat_value = overall_stats.stat_value + EXCLUDED.stat_value, updated_at = EXCLUDED.updated_at;";

        try (Connection conn = database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, statKey);
            ps.setLong(2, amount);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to increment overall stat '" + statKey + "'", e);
        }
    }

    public Map<String, Long> loadAll() {
        Map<String, Long> out = new HashMap<>();
        if (database == null || !database.isEnabled()) return out;

        String sql = "SELECT stat_key, stat_value FROM overall_stats";
        try (Connection conn = database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getString("stat_key"), rs.getLong("stat_value"));
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to load overall stats", e);
        }
        return out;
    }
}
