package com.darkniightz.core.achievements;

import com.darkniightz.main.database.DatabaseManager;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Database access for the achievement system.
 * All methods are blocking — always call from an async task.
 */
public final class AchievementDAO {

    /**
     * Represents one row in {@code player_achievements}.
     */
    public record AchievementRow(
        String achievementId,
        long   progress,
        int    tierReached,
        long   firstUnlockAt
    ) {
        public AchievementRow withProgress(long newProgress) {
            return new AchievementRow(achievementId, newProgress, tierReached, firstUnlockAt);
        }

        public AchievementRow withTier(int newTier, long unlockAt) {
            long firstUnlock = firstUnlockAt > 0 ? firstUnlockAt : unlockAt;
            return new AchievementRow(achievementId, progress, newTier, firstUnlock);
        }
    }

    private final DatabaseManager db;
    private final Logger          log;

    public AchievementDAO(DatabaseManager db, Logger log) {
        this.db  = db;
        this.log = log;
    }

    /** Loads all achievement rows for this player. Key = achievementId. */
    public Map<String, AchievementRow> loadAll(UUID uuid) {
        Map<String, AchievementRow> result = new HashMap<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT achievement_id, progress, tier_reached, first_unlock_at " +
                 "FROM player_achievements WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id    = rs.getString("achievement_id");
                    long   prog  = rs.getLong("progress");
                    int    tier  = rs.getInt("tier_reached");
                    long   first = rs.getLong("first_unlock_at");
                    result.put(id, new AchievementRow(id, prog, tier, first));
                }
            }
        } catch (SQLException e) {
            log.warning("[AchievementDAO] loadAll failed for " + uuid + ": " + e.getMessage());
        }
        return result;
    }

    /** Upsert a single row. */
    public void upsert(UUID uuid, AchievementRow row) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO player_achievements (uuid, achievement_id, progress, tier_reached, first_unlock_at, last_updated) " +
                 "VALUES (?, ?, ?, ?, ?, ?) " +
                 "ON CONFLICT (uuid, achievement_id) DO UPDATE SET " +
                 "  progress = EXCLUDED.progress, " +
                 "  tier_reached = EXCLUDED.tier_reached, " +
                 "  first_unlock_at = COALESCE(player_achievements.first_unlock_at, EXCLUDED.first_unlock_at), " +
                 "  last_updated = EXCLUDED.last_updated")) {
            long now = System.currentTimeMillis();
            ps.setString(1, uuid.toString());
            ps.setString(2, row.achievementId());
            ps.setLong(3, row.progress());
            ps.setInt(4, row.tierReached());
            ps.setLong(5, row.firstUnlockAt() > 0 ? row.firstUnlockAt() : now);
            ps.setLong(6, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warning("[AchievementDAO] upsert failed for " + uuid + "/" + row.achievementId() + ": " + e.getMessage());
        }
    }

    /** Batch upsert all rows in the map. */
    public void upsertAll(UUID uuid, Map<String, AchievementRow> rows) {
        if (rows.isEmpty()) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO player_achievements (uuid, achievement_id, progress, tier_reached, first_unlock_at, last_updated) " +
                 "VALUES (?, ?, ?, ?, ?, ?) " +
                 "ON CONFLICT (uuid, achievement_id) DO UPDATE SET " +
                 "  progress = EXCLUDED.progress, " +
                 "  tier_reached = EXCLUDED.tier_reached, " +
                 "  first_unlock_at = COALESCE(player_achievements.first_unlock_at, EXCLUDED.first_unlock_at), " +
                 "  last_updated = EXCLUDED.last_updated")) {
            long now = System.currentTimeMillis();
            for (AchievementRow row : rows.values()) {
                ps.setString(1, uuid.toString());
                ps.setString(2, row.achievementId());
                ps.setLong(3, row.progress());
                ps.setInt(4, row.tierReached());
                ps.setLong(5, row.firstUnlockAt() > 0 ? row.firstUnlockAt() : now);
                ps.setLong(6, now);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            log.warning("[AchievementDAO] upsertAll failed for " + uuid + ": " + e.getMessage());
        }
    }

    /** Record a one-time reward voucher for a tier unlock. */
    public void grantVoucher(UUID uuid, String achievementId, int tier, String rewardType, String rewardValue) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO achievement_vouchers (uuid, achievement_id, tier, reward_type, reward_value, granted_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, achievementId);
            ps.setInt(3, tier);
            ps.setString(4, rewardType);
            ps.setString(5, rewardValue);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warning("[AchievementDAO] grantVoucher failed for " + uuid + ": " + e.getMessage());
        }
    }
}
