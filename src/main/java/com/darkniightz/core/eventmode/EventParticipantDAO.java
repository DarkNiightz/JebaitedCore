package com.darkniightz.core.eventmode;

import com.darkniightz.main.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Async-safe persistence for {@code event_sessions} / {@code event_participants} (V006).
 * Callers schedule JDBC via {@link #runAsync(Plugin, Runnable)}; DAO mutating methods are
 * safe from any thread when invoked inside that runnable.
 */
public final class EventParticipantDAO {

    public record ParticipantRow(
            UUID playerUuid,
            int kills,
            int deaths,
            String result,
            int coinsEarned,
            int xpEarned
    ) {}

    private final DatabaseManager db;
    private final Logger log;

    public EventParticipantDAO(DatabaseManager db, Logger log) {
        this.db = db;
        this.log = log;
    }

    /**
     * Runs {@code task} on the async scheduler when the plugin can schedule work.
     * During {@link org.bukkit.plugin.java.JavaPlugin#onDisable()} (or whenever the plugin is
     * already disabled), Bukkit rejects new tasks; in that case {@code task} runs immediately on
     * the caller thread so session finalization can still reach the DB before disconnect.
     * Callers must only submit work safe to run on the current thread (typically JDBC only).
     */
    public static void runAsync(Plugin plugin, Runnable task) {
        if (task == null) return;
        if (plugin != null && plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            return;
        }
        try {
            task.run();
        } catch (Throwable t) {
            Logger lg = plugin != null ? plugin.getLogger() : Bukkit.getLogger();
            lg.log(Level.WARNING, "[EventParticipantDAO] runAsync (sync fallback): " + t.getMessage(), t);
        }
    }

    /**
     * After async DB work, apply a main-thread update. If the plugin cannot schedule,
     * runs synchronously only when already on the main thread; otherwise skips (session teardown).
     */
    public static void runOnMainWhenPossible(Plugin plugin, Runnable task) {
        if (task == null) return;
        Logger logOut = plugin != null ? plugin.getLogger() : Bukkit.getLogger();
        if (plugin != null && plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            try {
                task.run();
            } catch (Throwable t) {
                logOut.log(Level.WARNING, "[EventParticipantDAO] runOnMainWhenPossible sync: " + t.getMessage(), t);
            }
            return;
        }
        logOut.fine("[EventParticipantDAO] Skipping main callback (plugin not schedulable, async thread)");
    }

    /**
     * Inserts a running session row. Returns generated id, or -1 on failure.
     * Call from async thread only.
     */
    public int insertSessionRunning(String eventType, String arenaKeyOrNull, long startedAtMs) {
        String sql = """
                INSERT INTO event_sessions (event_type, arena_key, started_at, participant_count)
                VALUES (?, ?, ?, 0)
                RETURNING id
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventType);
            if (arenaKeyOrNull == null || arenaKeyOrNull.isBlank()) {
                ps.setNull(2, java.sql.Types.VARCHAR);
            } else {
                ps.setString(2, arenaKeyOrNull);
            }
            ps.setLong(3, startedAtMs);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            log.warning("[EventParticipantDAO] insertSessionRunning: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Updates session end metadata. Call from async thread only.
     */
    public void updateSessionEnd(int sessionId, long endedAtMs, UUID winnerUuidOrNull,
                                 String winningTeamOrNull, int participantCount) {
        if (sessionId <= 0) return;
        String sql = """
                UPDATE event_sessions
                   SET ended_at = ?,
                       winner_uuid = ?,
                       winning_team = ?,
                       participant_count = ?
                 WHERE id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, endedAtMs);
            if (winnerUuidOrNull == null) {
                ps.setNull(2, java.sql.Types.VARCHAR);
            } else {
                ps.setString(2, winnerUuidOrNull.toString());
            }
            if (winningTeamOrNull == null || winningTeamOrNull.isBlank()) {
                ps.setNull(3, java.sql.Types.VARCHAR);
            } else {
                ps.setString(3, winningTeamOrNull);
            }
            ps.setInt(4, participantCount);
            ps.setInt(5, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warning("[EventParticipantDAO] updateSessionEnd: " + e.getMessage());
        }
    }

    /**
     * Upserts one participant row. Call from async thread only.
     */
    public void upsertParticipant(int sessionId, ParticipantRow row) {
        if (sessionId <= 0) return;
        String sql = """
                INSERT INTO event_participants (session_id, player_uuid, kills, deaths, result, coins_earned, xp_earned)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (session_id, player_uuid)
                DO UPDATE SET
                    kills = EXCLUDED.kills,
                    deaths = EXCLUDED.deaths,
                    result = EXCLUDED.result,
                    coins_earned = EXCLUDED.coins_earned,
                    xp_earned = EXCLUDED.xp_earned
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            ps.setString(2, row.playerUuid().toString());
            ps.setInt(3, row.kills());
            ps.setInt(4, row.deaths());
            ps.setString(5, row.result());
            ps.setInt(6, row.coinsEarned());
            ps.setInt(7, row.xpEarned());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warning("[EventParticipantDAO] upsertParticipant: " + e.getMessage());
        }
    }

    /**
     * Batch upsert in one transaction. Call from async thread only.
     */
    public void upsertParticipantsBatch(int sessionId, List<ParticipantRow> rows) {
        if (sessionId <= 0 || rows == null || rows.isEmpty()) return;
        String sql = """
                INSERT INTO event_participants (session_id, player_uuid, kills, deaths, result, coins_earned, xp_earned)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (session_id, player_uuid)
                DO UPDATE SET
                    kills = EXCLUDED.kills,
                    deaths = EXCLUDED.deaths,
                    result = EXCLUDED.result,
                    coins_earned = EXCLUDED.coins_earned,
                    xp_earned = EXCLUDED.xp_earned
                """;
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (ParticipantRow row : rows) {
                    ps.setInt(1, sessionId);
                    ps.setString(2, row.playerUuid().toString());
                    ps.setInt(3, row.kills());
                    ps.setInt(4, row.deaths());
                    ps.setString(5, row.result());
                    ps.setInt(6, row.coinsEarned());
                    ps.setInt(7, row.xpEarned());
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.warning("[EventParticipantDAO] upsertParticipantsBatch: " + e.getMessage());
        }
    }
}
