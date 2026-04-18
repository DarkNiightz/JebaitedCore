package com.darkniightz.bot.db;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

public final class PlayerLookupDao {
    private final DataSource dataSource;

    public PlayerLookupDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Superset of player_stats + rank columns for Discord /player and admin tools.
     */
    public record PlayerRow(
            String uuid,
            String username,
            String rank,
            String donorRank,
            long firstJoinedMs,
            long lastJoinedMs,
            long playtimeMs,
            int kills,
            int deaths,
            int messagesSent,
            int commandsSent,
            int cosmeticCoins,
            double balance,
            int mobsKilled,
            int bossesKilled,
            int blocksBroken,
            int cropsBroken,
            int fishCaught,
            int eventWinsCombat,
            int eventWinsChat,
            int eventWinsHardcore,
            int mcmmoLevel) {}

    public Optional<PlayerRow> findByUsername(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String sql =
                "SELECT p.uuid, p.username, p.rank, p.donor_rank, p.first_joined, p.last_joined, "
                        + "COALESCE(ps.playtime_ms,0) AS playtime_ms, "
                        + "COALESCE(ps.kills,0) AS kills, COALESCE(ps.deaths,0) AS deaths, "
                        + "COALESCE(ps.messages_sent,0) AS messages_sent, "
                        + "COALESCE(ps.commands_sent,0) AS commands_sent, "
                        + "COALESCE(ps.cosmetic_coins,0) AS cosmetic_coins, "
                        + "COALESCE(ps.balance,0) AS balance, "
                        + "COALESCE(ps.mobs_killed,0) AS mobs_killed, "
                        + "COALESCE(ps.bosses_killed,0) AS bosses_killed, "
                        + "COALESCE(ps.blocks_broken,0) AS blocks_broken, "
                        + "COALESCE(ps.crops_broken,0) AS crops_broken, "
                        + "COALESCE(ps.fish_caught,0) AS fish_caught, "
                        + "COALESCE(ps.event_wins_combat,0) AS event_wins_combat, "
                        + "COALESCE(ps.event_wins_chat,0) AS event_wins_chat, "
                        + "COALESCE(ps.event_wins_hardcore,0) AS event_wins_hardcore, "
                        + "COALESCE(ps.mcmmo_level,0) AS mcmmo_level "
                        + "FROM players p LEFT JOIN player_stats ps ON p.uuid = ps.uuid "
                        + "WHERE LOWER(p.username) = LOWER(?) LIMIT 1;";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                BigDecimal bal = rs.getBigDecimal("balance");
                double balance = bal == null ? 0.0 : bal.doubleValue();
                String donor = rs.getString("donor_rank");
                return Optional.of(
                        new PlayerRow(
                                rs.getString("uuid"),
                                rs.getString("username"),
                                rs.getString("rank"),
                                donor == null ? "" : donor,
                                rs.getLong("first_joined"),
                                rs.getLong("last_joined"),
                                rs.getLong("playtime_ms"),
                                rs.getInt("kills"),
                                rs.getInt("deaths"),
                                rs.getInt("messages_sent"),
                                rs.getInt("commands_sent"),
                                rs.getInt("cosmetic_coins"),
                                balance,
                                rs.getInt("mobs_killed"),
                                rs.getInt("bosses_killed"),
                                rs.getInt("blocks_broken"),
                                rs.getInt("crops_broken"),
                                rs.getInt("fish_caught"),
                                rs.getInt("event_wins_combat"),
                                rs.getInt("event_wins_chat"),
                                rs.getInt("event_wins_hardcore"),
                                rs.getInt("mcmmo_level")));
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
