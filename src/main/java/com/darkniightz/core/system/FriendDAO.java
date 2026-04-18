package com.darkniightz.core.system;

import com.darkniightz.main.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Database access for the friends system (friendships + friend_requests tables).
 * All methods execute on the calling thread — always call from an async task.
 */
public class FriendDAO {

    /** Immutable snapshot of a friendship's extra stats. */
    public record FriendshipStats(long createdAt, long xpTogether, int killsTogether) {}

    private final DatabaseManager db;
    private final Logger log;

    public FriendDAO(DatabaseManager db, Logger log) {
        this.db = db;
        this.log = log;
    }

    // ── Load ─────────────────────────────────────────────────────────────────

    /** Returns the set of friend UUIDs for the given player. */
    public Set<UUID> loadFriends(UUID player) {
        Set<UUID> result = new HashSet<>();
        String uuid = player.toString();
        String sql = "SELECT player_a, player_b FROM friendships WHERE player_a = ? OR player_b = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String a = rs.getString("player_a");
                    String b = rs.getString("player_b");
                    result.add(UUID.fromString(uuid.equals(a) ? b : a));
                }
            }
        } catch (SQLException e) {
            log.warning("[FriendDAO] loadFriends failed for " + uuid + ": " + e.getMessage());
        }
        return result;
    }

    /** Returns the set of UUIDs who have sent a pending request to the given player. */
    public Set<UUID> loadInboundRequests(UUID receiver) {
        Set<UUID> result = new HashSet<>();
        String sql = "SELECT sender_uuid FROM friend_requests WHERE receiver_uuid = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, receiver.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(UUID.fromString(rs.getString("sender_uuid")));
                }
            }
        } catch (SQLException e) {
            log.warning("[FriendDAO] loadInboundRequests failed: " + e.getMessage());
        }
        return result;
    }

    // ── Friend requests ───────────────────────────────────────────────────────

    /** Inserts a pending friend request. Returns false if it already exists. */
    public boolean insertRequest(UUID sender, UUID receiver) {
        String sql = "INSERT INTO friend_requests (sender_uuid, receiver_uuid) VALUES (?, ?) ON CONFLICT DO NOTHING";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sender.toString());
            ps.setString(2, receiver.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.warning("[FriendDAO] insertRequest failed: " + e.getMessage());
            return false;
        }
    }

    /** Deletes a pending friend request (accepted, denied, or cancelled). */
    public void deleteRequest(UUID sender, UUID receiver) {
        String sql = "DELETE FROM friend_requests WHERE sender_uuid = ? AND receiver_uuid = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sender.toString());
            ps.setString(2, receiver.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warning("[FriendDAO] deleteRequest failed: " + e.getMessage());
        }
    }

    // ── Friendships ───────────────────────────────────────────────────────────

    /** Inserts a friendship row (canonical order: a < b). */
    public void insertFriendship(UUID a, UUID b) {
        String ua = a.toString();
        String ub = b.toString();
        // Enforce canonical order
        if (ua.compareTo(ub) > 0) {
            String tmp = ua;
            ua = ub;
            ub = tmp;
        }
        String sql = "INSERT INTO friendships (player_a, player_b) VALUES (?, ?) ON CONFLICT DO NOTHING";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ua);
            ps.setString(2, ub);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warning("[FriendDAO] insertFriendship failed: " + e.getMessage());
        }
    }

    /** Deletes a friendship row. */
    public void deleteFriendship(UUID a, UUID b) {
        String ua = a.toString();
        String ub = b.toString();
        if (ua.compareTo(ub) > 0) {
            String tmp = ua;
            ua = ub;
            ub = tmp;
        }
        String sql = "DELETE FROM friendships WHERE player_a = ? AND player_b = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ua);
            ps.setString(2, ub);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warning("[FriendDAO] deleteFriendship failed: " + e.getMessage());
        }
    }

    // ── Friendship stats ──────────────────────────────────────────────────────

    /**
     * Returns the {@link FriendshipStats} for the pair, or null if the row doesn't exist.
     * Also reads {@code created_at} from the friendships table.
     */
    public FriendshipStats loadFriendshipStats(UUID a, UUID b) {
        String ua = a.toString();
        String ub = b.toString();
        if (ua.compareTo(ub) > 0) { String t = ua; ua = ub; ub = t; }
        // LEFT JOIN so we get the friendship even if stats row doesn't exist yet
        String sql = """
                SELECT f.created_at, COALESCE(s.xp_together, 0) AS xp_together,
                       COALESCE(s.kills_together, 0) AS kills_together
                  FROM friendships f
                  LEFT JOIN friendship_stats s
                         ON s.player_a = f.player_a AND s.player_b = f.player_b
                 WHERE f.player_a = ? AND f.player_b = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ua);
            ps.setString(2, ub);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new FriendshipStats(
                            rs.getLong("created_at"),
                            rs.getLong("xp_together"),
                            rs.getInt("kills_together"));
                }
            }
        } catch (SQLException e) {
            log.warning("[FriendDAO] loadFriendshipStats failed: " + e.getMessage());
        }
        return null;
    }

    /** Increments xp_together for both sides of a friendship (async-safe upsert). */
    public void addXpTogether(UUID a, UUID b, long xp) {
        String ua = a.toString();
        String ub = b.toString();
        if (ua.compareTo(ub) > 0) { String t = ua; ua = ub; ub = t; }
        String sql = """
                INSERT INTO friendship_stats (player_a, player_b, xp_together, kills_together)
                VALUES (?, ?, ?, 0)
                ON CONFLICT (player_a, player_b)
                DO UPDATE SET xp_together = friendship_stats.xp_together + EXCLUDED.xp_together
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ua);
            ps.setString(2, ub);
            ps.setLong(3, xp);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warning("[FriendDAO] addXpTogether failed: " + e.getMessage());
        }
    }

    /**
     * Adds co-present party time for a friend pair (V006 {@code friendship_stats.party_time_ms}).
     * No-op if the pair is not in {@code friendships}. Call from an async task only.
     */
    public void addPartyTimeTogether(UUID a, UUID b, long deltaMs) {
        if (deltaMs <= 0) return;
        String ua = a.toString();
        String ub = b.toString();
        if (ua.compareTo(ub) > 0) {
            String t = ua;
            ua = ub;
            ub = t;
        }
        String sql = """
                INSERT INTO friendship_stats (player_a, player_b, xp_together, kills_together, party_time_ms)
                SELECT ?, ?, 0, 0, ?
                WHERE EXISTS (SELECT 1 FROM friendships f WHERE f.player_a = ? AND f.player_b = ?)
                ON CONFLICT (player_a, player_b)
                DO UPDATE SET party_time_ms = friendship_stats.party_time_ms + EXCLUDED.party_time_ms
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ua);
            ps.setString(2, ub);
            ps.setLong(3, deltaMs);
            ps.setString(4, ua);
            ps.setString(5, ub);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warning("[FriendDAO] addPartyTimeTogether failed: " + e.getMessage());
        }
    }

    /** Increments kills_together (one player killed the other). */
    public void addKillTogether(UUID a, UUID b) {
        String ua = a.toString();
        String ub = b.toString();
        if (ua.compareTo(ub) > 0) { String t = ua; ua = ub; ub = t; }
        String sql = """
                INSERT INTO friendship_stats (player_a, player_b, xp_together, kills_together)
                VALUES (?, ?, 0, 1)
                ON CONFLICT (player_a, player_b)
                DO UPDATE SET kills_together = friendship_stats.kills_together + 1
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ua);
            ps.setString(2, ub);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warning("[FriendDAO] addKillTogether failed: " + e.getMessage());
        }
    }
}
