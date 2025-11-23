package com.darkniightz.main.database.dao;

import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.main.database.DatabaseManager;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlayerProfileDAO {

    private final DatabaseManager dbManager;
    private final Logger logger;

    public PlayerProfileDAO(DatabaseManager dbManager, Logger logger) {
        this.dbManager = dbManager;
        this.logger = logger;
    }

    /**
     * Logs a moderation action to the database.
     *
     * @param targetUuid The UUID of the player the action is against.
     * @param entry      A map containing details of the moderation action.
     */
    public void logModerationAction(UUID targetUuid, Map<String, Object> entry) {
        String sql = "INSERT INTO moderation_history (target_uuid, type, actor, actor_uuid, reason, duration_ms, expires_at, timestamp) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?);";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, targetUuid.toString());
            pstmt.setString(2, (String) entry.get("type"));
            pstmt.setString(3, (String) entry.get("actor"));
            pstmt.setString(4, (String) entry.get("actorUuid"));
            pstmt.setString(5, (String) entry.get("reason"));

            // Handle nullable Long values
            Object duration = entry.get("durationMs");
            if (duration instanceof Long) {
                pstmt.setLong(6, (Long) duration);
            } else {
                pstmt.setNull(6, Types.BIGINT);
            }

            Object expires = entry.get("expiresAt");
            if (expires instanceof Long) {
                pstmt.setLong(7, (Long) expires);
            } else {
                pstmt.setNull(7, Types.BIGINT);
            }

            pstmt.setLong(8, (Long) entry.get("ts"));

            pstmt.executeUpdate();

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not log moderation action for " + targetUuid, e);
        }
    }

    /**
     * Retrieves the active ban details for a player.
     *
     * @param uuid The player's UUID.
     * @return A ResultSet containing ban details (expires_at, reason), or null if no active ban is found.
     * The caller is responsible for closing the connection from the ResultSet.
     */
    public ResultSet getActiveBan(UUID uuid) {
        String sql = "SELECT expires_at, reason FROM moderation_history " +
                     "WHERE target_uuid = ? AND type = 'ban' AND (expires_at IS NULL OR expires_at > ?) " +
                     "ORDER BY timestamp DESC LIMIT 1;";

        Connection conn = null;
        try {
            conn = dbManager.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, uuid.toString());
            pstmt.setLong(2, System.currentTimeMillis());
            return pstmt.executeQuery(); // The connection will be closed in the calling method
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not check active ban for " + uuid, e);
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Failed to close connection after error", ex);
                }
            }
            return null;
        }
    }

    public PlayerProfile loadPlayerProfile(UUID uuid) {
        String playerSql = "SELECT * FROM players WHERE uuid = ?;";
        String statsSql = "SELECT * FROM player_stats WHERE uuid = ?;";
        String cosmeticsSql = "SELECT cosmetic_id, cosmetic_type, is_active FROM player_cosmetics WHERE player_uuid = ?;";

        try (Connection conn = dbManager.getConnection()) {
            PlayerProfile profile = null;

            // Load main profile data
            try (PreparedStatement pstmt = conn.prepareStatement(playerSql)) {
                pstmt.setString(1, uuid.toString());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    profile = new PlayerProfile(uuid, rs.getString("username"));
                    profile.setRank(rs.getString("rank"));
                    profile.setFirstJoined(rs.getLong("first_joined"));
                    profile.setLastJoined(rs.getLong("last_joined"));
                }
            }

            if (profile == null) {
                return null; // Player not found
            }

            // Load stats
            try (PreparedStatement pstmt = conn.prepareStatement(statsSql)) {
                pstmt.setString(1, uuid.toString());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    profile.setCommandsSent(rs.getInt("commands_sent"));
                }
            }

            // Load cosmetics
            try (PreparedStatement pstmt = conn.prepareStatement(cosmeticsSql)) {
                pstmt.setString(1, uuid.toString());
                ResultSet rs = pstmt.executeQuery();
                Set<String> unlockedCosmetics = new HashSet<>();
                while (rs.next()) {
                    String cosmeticId = rs.getString("cosmetic_id");
                    unlockedCosmetics.add(cosmeticId);
                    if (rs.getBoolean("is_active")) {
                        String type = rs.getString("cosmetic_type");
                        if ("particle".equalsIgnoreCase(type)) {
                            profile.setActiveParticle(cosmeticId);
                        } else if ("trail".equalsIgnoreCase(type)) {
                            profile.setActiveTrail(cosmeticId);
                        }
                    }
                }
                profile.setUnlockedCosmetics(unlockedCosmetics);
            }

            return profile;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load player profile for " + uuid, e);
            return null;
        }
    }

    public void savePlayerProfile(PlayerProfile profile) {
        String playerUpsertSql = "INSERT INTO players (uuid, username, rank, first_joined, last_joined) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT (uuid) DO UPDATE SET " +
                "username = EXCLUDED.username, rank = EXCLUDED.rank, last_joined = EXCLUDED.last_joined;";

        String statsUpsertSql = "INSERT INTO player_stats (uuid, commands_sent) " +
                "VALUES (?, ?) " +
                "ON CONFLICT (uuid) DO UPDATE SET " +
                "commands_sent = EXCLUDED.commands_sent;";

        // We handle cosmetics separately by clearing and re-inserting active state
        String cosmeticsUpdateSql = "UPDATE player_cosmetics SET is_active = false WHERE player_uuid = ?;";
        String cosmeticsActivateSql = "UPDATE player_cosmetics SET is_active = true WHERE player_uuid = ? AND cosmetic_id = ?;";

        try (Connection conn = dbManager.getConnection()) {
            conn.setAutoCommit(false); // Start transaction

            try (PreparedStatement psPlayer = conn.prepareStatement(playerUpsertSql);
                 PreparedStatement psStats = conn.prepareStatement(statsUpsertSql)) {

                // Player data
                psPlayer.setString(1, profile.getUuid().toString());
                psPlayer.setString(2, profile.getName());
                psPlayer.setString(3, profile.getRank());
                psPlayer.setLong(4, profile.getFirstJoined());
                psPlayer.setLong(5, profile.getLastJoined());
                psPlayer.executeUpdate();

                // Stats data
                psStats.setString(1, profile.getUuid().toString());
                psStats.setInt(2, profile.getCommandsSent());
                psStats.executeUpdate();

                // Cosmetics data
                try (PreparedStatement psCosmeticsUpdate = conn.prepareStatement(cosmeticsUpdateSql);
                     PreparedStatement psCosmeticsActivate = conn.prepareStatement(cosmeticsActivateSql)) {
                    psCosmeticsUpdate.setString(1, profile.getUuid().toString());
                    psCosmeticsUpdate.executeUpdate();

                    if (profile.getActiveParticle() != null) {
                        psCosmeticsActivate.setString(1, profile.getUuid().toString());
                        psCosmeticsActivate.setString(2, profile.getActiveParticle());
                        psCosmeticsActivate.addBatch();
                    }
                    if (profile.getActiveTrail() != null) {
                        psCosmeticsActivate.setString(1, profile.getUuid().toString());
                        psCosmeticsActivate.setString(2, profile.getActiveTrail());
                        psCosmeticsActivate.addBatch();
                    }
                    psCosmeticsActivate.executeBatch();
                }

                conn.commit(); // Commit transaction
            } catch (SQLException e) {
                conn.rollback(); // Rollback on error
                throw e; // Re-throw to be logged
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to save player profile for " + profile.getUuid(), e);
        }
    }
    }
}