package com.darkniightz.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

public final class PlayerLookupDao {
    private final DataSource dataSource;

    public PlayerLookupDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record PlayerRow(
            String uuid,
            String username,
            String rank,
            long firstJoinedMs,
            long playtimeMs,
            int kills,
            int deaths,
            int messagesSent) {}

    public Optional<PlayerRow> findByUsername(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String sql =
                "SELECT p.uuid, p.username, p.rank, p.first_joined, COALESCE(ps.playtime_ms,0) AS playtime_ms, "
                        + "COALESCE(ps.kills,0) AS kills, COALESCE(ps.deaths,0) AS deaths, COALESCE(ps.messages_sent,0) AS messages_sent "
                        + "FROM players p LEFT JOIN player_stats ps ON p.uuid = ps.uuid "
                        + "WHERE LOWER(p.username) = LOWER(?) LIMIT 1;";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(
                        new PlayerRow(
                                rs.getString("uuid"),
                                rs.getString("username"),
                                rs.getString("rank"),
                                rs.getLong("first_joined"),
                                rs.getLong("playtime_ms"),
                                rs.getInt("kills"),
                                rs.getInt("deaths"),
                                rs.getInt("messages_sent")));
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
