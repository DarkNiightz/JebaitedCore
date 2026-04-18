package com.darkniightz.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Read-only stats for Discord /status and presence.
 */
public final class BotStatsDao {
    private final DataSource dataSource;

    public BotStatsDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record NetworkStats(
            long totalJoins,
            long totalKills,
            long registeredPlayers,
            long linkedDiscordAccounts,
            long totalDeaths,
            long totalMessages,
            long totalPlaytimeHours) {}

    public NetworkStats loadNetworkStats() {
        Map<String, Long> stats = loadOverallStatsRow();
        long joins = stats.getOrDefault("total_joins", 0L);
        long kills = stats.getOrDefault("total_kills", 0L);
        long deaths = stats.getOrDefault("total_deaths", 0L);
        long messages = stats.getOrDefault("total_messages_sent", 0L);
        long playMs = stats.getOrDefault("total_playtime_ms", 0L);
        long players = 0;
        long linked = 0;

        String sqlCount = "SELECT COUNT(*) AS c FROM players;";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sqlCount);
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                players = rs.getLong("c");
            }
        } catch (Exception ignored) {
        }

        String sqlLinks = "SELECT COUNT(*) AS c FROM discord_links WHERE unlinked_at IS NULL;";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sqlLinks);
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                linked = rs.getLong("c");
            }
        } catch (Exception ignored) {
        }

        long playHours = playMs / 3_600_000L;
        return new NetworkStats(joins, kills, players, linked, deaths, messages, playHours);
    }

    private Map<String, Long> loadOverallStatsRow() {
        Map<String, Long> out = new HashMap<>();
        String sql =
                "SELECT stat_key, stat_value FROM overall_stats WHERE stat_key IN ("
                        + "'total_joins','total_kills','total_deaths','total_messages_sent','total_playtime_ms'"
                        + ");";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getString("stat_key"), rs.getLong("stat_value"));
            }
        } catch (Exception ignored) {
        }
        return out;
    }
}
