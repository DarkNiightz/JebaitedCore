package com.darkniightz.core.party;

import com.darkniightz.main.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Async-safe DAO for {@code player_party_stats}.
 * All public methods expect to be called from an async context
 * (via {@code Bukkit.getScheduler().runTaskAsynchronously}).
 */
public final class PartyStatDAO {

    /** Allowed column names — guard against SQL injection from dynamic column param. */
    private static final Set<String> VALID_COLS = Set.of(
            "parties_created",
            "parties_joined",
            "party_kills",
            "party_playtime_ms",
            "party_blocks_broken",
            "party_fish_caught",
            "party_bosses_killed",
            "party_xp_shared"
    );

    private final DatabaseManager db;
    private final Logger log;

    public PartyStatDAO(DatabaseManager db, Logger log) {
        this.db  = db;
        this.log = log;
    }

    /**
     * Upserts a delta increment into a single stat column.
     * Call from an async task — never on the main thread.
     *
     * @param uuid   player UUID
     * @param column column name (must be in {@link #VALID_COLS})
     * @param delta  amount to add (positive)
     */
    public void increment(UUID uuid, String column, long delta) {
        if (!VALID_COLS.contains(column)) {
            log.warning("[PartyStatDAO] Rejected unknown column: " + column);
            return;
        }
        String sql = "INSERT INTO player_party_stats (uuid, " + column + ") VALUES (?, ?) " +
                     "ON CONFLICT (uuid) DO UPDATE SET " + column +
                     " = player_party_stats." + column + " + EXCLUDED." + column;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, delta);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warning("[PartyStatDAO] increment(" + column + "): " + e.getMessage());
        }
    }

    /**
     * Convenience: schedules an async increment via the plugin scheduler.
     * Safe to call from the main thread.
     */
    public static void incrementAsync(org.bukkit.plugin.Plugin plugin,
                                      UUID uuid, String column, long delta) {
        // Retrieve DAO from JebaitedCore singleton to avoid passing it everywhere
        com.darkniightz.main.JebaitedCore core = com.darkniightz.main.JebaitedCore.getInstance();
        if (core == null) return;
        PartyStatDAO dao = core.getPartyStatDAO();
        if (dao == null) return;
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> dao.increment(uuid, column, delta));
    }
}
