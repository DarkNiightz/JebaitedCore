package com.darkniightz.main;

import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.main.database.DatabaseManager;

import java.sql.*;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@SuppressWarnings("deprecation")
public class PlayerProfileDAO {

    public record BanRecord(Long expiresAt, String reason) {}
    public record RankRequestApproval(long id, String targetUuid, String targetName, String requestedRank) {}
    public record EventStatRecord(int participated, int won, int lost) {}
    public record BalanceTopRecord(UUID uuid, String username, double balance) {}
    public record StatTopRecord(UUID uuid, String username, long value) {}
    public record NoteRecord(long id, String targetName, String note, String author, long createdAtMs) {}

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
        String sql = "INSERT INTO moderation_history (target_uuid, target_name, type, actor, actor_uuid, reason, duration_ms, expires_at, timestamp) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, targetUuid.toString());
            pstmt.setString(2, resolveTargetName(conn, targetUuid, entry));
            pstmt.setString(3, (String) entry.get("type"));
            pstmt.setString(4, (String) entry.get("actor"));
            pstmt.setString(5, (String) entry.get("actorUuid"));
            pstmt.setString(6, (String) entry.get("reason"));

            // Handle nullable Long values
            Object duration = entry.get("durationMs");
            if (duration instanceof Long) {
                pstmt.setLong(7, (Long) duration);
            } else {
                pstmt.setNull(7, Types.BIGINT);
            }

            Object expires = entry.get("expiresAt");
            if (expires instanceof Long) {
                pstmt.setLong(8, (Long) expires);
            } else {
                pstmt.setNull(8, Types.BIGINT);
            }

            pstmt.setLong(9, (Long) entry.get("ts"));

            pstmt.executeUpdate();

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not log moderation action for " + targetUuid, e);
        }
    }

    /**
     * Retrieves the active ban details for a player.
     * Honors both permanent/temp bans and invalidates bans if an unban action exists after them.
     */
    public BanRecord getActiveBan(UUID uuid) {
        String sql = "SELECT mh.expires_at, mh.reason FROM moderation_history mh " +
                "WHERE mh.target_uuid = ? " +
                "AND mh.type IN ('ban', 'tempban') " +
                "AND (mh.expires_at IS NULL OR mh.expires_at > ?) " +
                "AND NOT EXISTS (" +
                "  SELECT 1 FROM moderation_history u " +
                "  WHERE u.target_uuid = mh.target_uuid " +
                "    AND u.type = 'unban' " +
                "    AND u.timestamp > mh.timestamp" +
                ") " +
                "ORDER BY mh.timestamp DESC LIMIT 1;";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            pstmt.setLong(2, System.currentTimeMillis());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) return null;
                Long expires = null;
                long rawExpires = rs.getLong("expires_at");
                if (!rs.wasNull()) expires = rawExpires;
                String reason = rs.getString("reason");
                return new BanRecord(expires, reason);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not check active ban for " + uuid, e);
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
                    // Use special setter that does not mark the rank as dirty on load
                    profile.setRankLoaded(rs.getString("rank"));
                    try {
                        profile.setDonorRankLoaded(rs.getString("donor_rank"));
                    } catch (SQLException ignore) {
                        profile.setDonorRankLoaded(null);
                    }
                    try {
                        profile.setRankDisplayModeLoaded(rs.getString("rank_display_pref"));
                    } catch (SQLException ignore) {
                        profile.setRankDisplayModeLoaded("primary");
                    }
                    profile.setFirstJoined(rs.getLong("first_joined"));
                    profile.setLastJoined(rs.getLong("last_joined"));
                    try {
                        profile.setActiveTag(rs.getString("active_tag"));
                    } catch (SQLException ignore) {
                        profile.setActiveTag(null);
                    }
                    try {
                        profile.setFavoriteCosmetics(parseKeySet(rs.getString("favorite_cosmetics")));
                    } catch (SQLException ignore) {
                        profile.setFavoriteCosmetics(new java.util.LinkedHashSet<>());
                    }
                    try {
                        profile.setPreviewedCosmetics(parseKeySet(rs.getString("previewed_cosmetics")));
                    } catch (SQLException ignore) {
                        profile.setPreviewedCosmetics(new java.util.LinkedHashSet<>());
                    }
                    try {
                        profile.setCosmeticLoadouts(parseLoadouts(rs.getString("cosmetic_loadouts")));
                    } catch (SQLException ignore) {
                        profile.setCosmeticLoadouts(new LinkedHashMap<>());
                    }
                    try {
                        profile.loadPreferences(rs.getString("settings_blob"));
                    } catch (SQLException ignore) {
                        profile.loadPreferences("");
                    }
                    try {
                        profile.setKitCooldownsLoaded(parseKitCooldowns(rs.getString("kit_cooldowns")));
                    } catch (SQLException ignore) {
                        // column not yet present on older DBs — V004 migration adds it
                    }
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
                    try {
                        profile.setMessagesSent(rs.getInt("messages_sent"));
                    } catch (SQLException ignore) { /* older schema */ }
                    try {
                        profile.setKills(rs.getInt("kills"));
                    } catch (SQLException ignore) { /* older schema */ }
                    try {
                        profile.setDeaths(rs.getInt("deaths"));
                    } catch (SQLException ignore) { /* older schema */ }
                    try {
                        profile.setMobsKilled(rs.getInt("mobs_killed"));
                    } catch (SQLException ignore) { /* older schema */ }
                    try {
                        profile.setBossesKilled(rs.getInt("bosses_killed"));
                    } catch (SQLException ignore) { /* older schema */ }
                    try {
                        profile.setBlocksBroken(rs.getInt("blocks_broken"));
                    } catch (SQLException ignore) { /* older schema */ }
                    try {
                        profile.setCropsBroken(rs.getInt("crops_broken"));
                    } catch (SQLException ignore) { /* older schema */ }
                    try {
                        profile.setFishCaught(rs.getInt("fish_caught"));
                    } catch (SQLException ignore) { /* older schema */ }
                    try {
                        profile.setMcMMOLevel(rs.getInt("mcmmo_level"));
                    } catch (SQLException ignore) { /* older schema */ }
                    try {
                        profile.setEventWinsCombat(rs.getInt("event_wins_combat"));
                    } catch (SQLException ignore) { /* older schema */ }
                    try {
                        profile.setEventWinsChat(rs.getInt("event_wins_chat"));
                    } catch (SQLException ignore) { /* older schema */ }
                    try {
                        profile.setEventWinsHardcore(rs.getInt("event_wins_hardcore"));
                    } catch (SQLException ignore) { /* older schema */ }
                    try {
                        long ms = rs.getLong("playtime_ms");
                        if (!rs.wasNull()) {
                            profile.setPlaytimeMs(ms);
                        } else {
                            long seconds = rs.getLong("playtime_seconds");
                            if (!rs.wasNull()) profile.setPlaytimeMs(seconds * 1000L);
                        }
                    } catch (SQLException ignore) {
                        try {
                            long seconds = rs.getLong("playtime_seconds");
                            if (!rs.wasNull()) profile.setPlaytimeMs(seconds * 1000L);
                        } catch (SQLException ignoredAgain) { /* older schema */ }
                    }
                    // Optional column: cosmetic_coins (migration adds it). Handle absence safely
                    try {
                        int coins = rs.getInt("cosmetic_coins");
                        if (!rs.wasNull()) profile.setCosmeticCoins(coins);
                    } catch (SQLException ignore) { /* older schema */ }
                    try {
                        double balance = rs.getDouble("balance");
                        if (!rs.wasNull()) profile.setBalance(balance);
                    } catch (SQLException ignore) { /* older schema */ }
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
                        } else if ("gadget".equalsIgnoreCase(type)) {
                            profile.setEquippedGadget(cosmeticId);
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
        // If the rank hasn't been changed in-memory, avoid overwriting the DB value.
        // Use two variants: one that updates rank (when dirty or inserting new), and one that leaves it untouched.
        String playerUpsertWithRank = "INSERT INTO players (uuid, username, rank, donor_rank, rank_display_pref, first_joined, last_joined, favorite_cosmetics, previewed_cosmetics, cosmetic_loadouts, active_tag, active_tag_display, settings_blob) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (uuid) DO UPDATE SET " +
            "username = EXCLUDED.username, rank = EXCLUDED.rank, donor_rank = EXCLUDED.donor_rank, rank_display_pref = EXCLUDED.rank_display_pref, last_joined = EXCLUDED.last_joined, favorite_cosmetics = EXCLUDED.favorite_cosmetics, previewed_cosmetics = EXCLUDED.previewed_cosmetics, cosmetic_loadouts = EXCLUDED.cosmetic_loadouts, active_tag = EXCLUDED.active_tag, active_tag_display = EXCLUDED.active_tag_display, settings_blob = EXCLUDED.settings_blob;";

        // Note: In the no-rank-update path, we still provide a rank value for INSERTs,
        // but on conflict we do NOT update the rank. This avoids referencing EXCLUDED
        // in the VALUES clause (which is not allowed in PostgreSQL) and fixes the
        // "missing FROM-clause entry for table 'excluded'" error.
        String playerUpsertNoRank = "INSERT INTO players (uuid, username, rank, donor_rank, rank_display_pref, first_joined, last_joined, favorite_cosmetics, previewed_cosmetics, cosmetic_loadouts, active_tag, active_tag_display, settings_blob) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (uuid) DO UPDATE SET " +
            "username = EXCLUDED.username, donor_rank = EXCLUDED.donor_rank, rank_display_pref = EXCLUDED.rank_display_pref, last_joined = EXCLUDED.last_joined, favorite_cosmetics = EXCLUDED.favorite_cosmetics, previewed_cosmetics = EXCLUDED.previewed_cosmetics, cosmetic_loadouts = EXCLUDED.cosmetic_loadouts, active_tag = EXCLUDED.active_tag, active_tag_display = EXCLUDED.active_tag_display, settings_blob = EXCLUDED.settings_blob;";

        String statsUpsertSql = "INSERT INTO player_stats (uuid, commands_sent, messages_sent, kills, deaths, mobs_killed, bosses_killed, blocks_broken, crops_broken, fish_caught, mcmmo_level, event_wins_combat, event_wins_chat, event_wins_hardcore, playtime_ms, playtime_seconds, cosmetic_coins, balance) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (uuid) DO UPDATE SET " +
            "commands_sent = EXCLUDED.commands_sent, messages_sent = EXCLUDED.messages_sent, " +
            "kills = EXCLUDED.kills, deaths = EXCLUDED.deaths, mobs_killed = EXCLUDED.mobs_killed, " +
            "bosses_killed = EXCLUDED.bosses_killed, blocks_broken = EXCLUDED.blocks_broken, crops_broken = EXCLUDED.crops_broken, fish_caught = EXCLUDED.fish_caught, " +
            "mcmmo_level = EXCLUDED.mcmmo_level, event_wins_combat = EXCLUDED.event_wins_combat, event_wins_chat = EXCLUDED.event_wins_chat, event_wins_hardcore = EXCLUDED.event_wins_hardcore, " +
            "playtime_ms = EXCLUDED.playtime_ms, playtime_seconds = EXCLUDED.playtime_seconds, cosmetic_coins = EXCLUDED.cosmetic_coins, balance = EXCLUDED.balance;";

        // We handle cosmetics separately by clearing and re-inserting active state
        String cosmeticsUpdateSql = "UPDATE player_cosmetics SET is_active = false WHERE player_uuid = ?;";
        String cosmeticsActivateSql = "UPDATE player_cosmetics SET is_active = true WHERE player_uuid = ? AND cosmetic_id = ?;";

        try (Connection conn = dbManager.getConnection()) {
            conn.setAutoCommit(false); // Start transaction

            boolean updateRank = profile.isRankDirty();
            String playerSql = updateRank ? playerUpsertWithRank : playerUpsertNoRank;
            try (PreparedStatement psPlayer = conn.prepareStatement(playerSql);
                 PreparedStatement psStats = conn.prepareStatement(statsUpsertSql)) {

                // Player data
                String activeTagDisplay = resolveActiveTagDisplay(profile);
                if (updateRank) {
                    psPlayer.setString(1, profile.getUuid().toString());
                    psPlayer.setString(2, profile.getName());
                    psPlayer.setString(3, profile.getRank());
                    psPlayer.setString(4, profile.getDonorRank());
                    psPlayer.setString(5, profile.getRankDisplayMode());
                    psPlayer.setLong(6, profile.getFirstJoined());
                    psPlayer.setLong(7, profile.getLastJoined());
                    psPlayer.setString(8, joinKeySet(profile.getFavoriteCosmetics()));
                    psPlayer.setString(9, joinKeySet(profile.getPreviewedCosmetics()));
                    psPlayer.setString(10, joinLoadouts(profile.getCosmeticLoadouts()));
                    psPlayer.setString(11, profile.getActiveTag());
                    psPlayer.setString(12, activeTagDisplay);
                    psPlayer.setString(13, profile.serializePreferences());
                } else {
                    psPlayer.setString(1, profile.getUuid().toString());
                    psPlayer.setString(2, profile.getName());
                    psPlayer.setString(3, profile.getRank());
                    psPlayer.setString(4, profile.getDonorRank());
                    psPlayer.setString(5, profile.getRankDisplayMode());
                    psPlayer.setLong(6, profile.getFirstJoined());
                    psPlayer.setLong(7, profile.getLastJoined());
                    psPlayer.setString(8, joinKeySet(profile.getFavoriteCosmetics()));
                    psPlayer.setString(9, joinKeySet(profile.getPreviewedCosmetics()));
                    psPlayer.setString(10, joinLoadouts(profile.getCosmeticLoadouts()));
                    psPlayer.setString(11, profile.getActiveTag());
                    psPlayer.setString(12, activeTagDisplay);
                    psPlayer.setString(13, profile.serializePreferences());
                }
                psPlayer.executeUpdate();
                // Clear rank dirty flag after successful write
                if (updateRank) profile.clearRankDirty();
                if (profile.isDonorRankDirty()) profile.clearDonorRankDirty();
                if (profile.isRankDisplayModeDirty()) profile.clearRankDisplayModeDirty();

                // Stats data
                Integer power = org.bukkit.Bukkit.getOfflinePlayer(profile.getUuid()) != null
                        ? com.darkniightz.core.system.McMMOIntegration.getPowerLevel(org.bukkit.Bukkit.getOfflinePlayer(profile.getUuid()))
                        : null;
                if (power != null) {
                    profile.setMcMMOLevel(power);
                }
                psStats.setString(1, profile.getUuid().toString());
                psStats.setInt(2, profile.getCommandsSent());
                psStats.setInt(3, profile.getMessagesSent());
                psStats.setInt(4, profile.getKills());
                psStats.setInt(5, profile.getDeaths());
                psStats.setInt(6, profile.getMobsKilled());
                psStats.setInt(7, profile.getBossesKilled());
                psStats.setInt(8, profile.getBlocksBroken());
                psStats.setInt(9, profile.getCropsBroken());
                psStats.setInt(10, profile.getFishCaught());
                psStats.setInt(11, profile.getMcMMOLevel());
                psStats.setInt(12, profile.getEventWinsCombat());
                psStats.setInt(13, profile.getEventWinsChat());
                psStats.setInt(14, profile.getEventWinsHardcore());
                psStats.setLong(15, profile.getPlaytimeMs());
                psStats.setLong(16, Math.max(0L, profile.getPlaytimeMs() / 1000L));
                psStats.setInt(17, profile.getCosmeticCoins());
                psStats.setDouble(18, profile.getBalance());
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
                    if (profile.getEquippedGadget() != null) {
                        psCosmeticsActivate.setString(1, profile.getUuid().toString());
                        psCosmeticsActivate.setString(2, profile.getEquippedGadget());
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

    /** Unlocks a cosmetic for the given player if not already present. */
    public void unlockCosmetic(UUID uuid, String cosmeticId, String cosmeticType) {
        String sql = "INSERT INTO player_cosmetics (player_uuid, cosmetic_id, cosmetic_type, is_active) VALUES (?,?,?,false) " +
                "ON CONFLICT (player_uuid, cosmetic_id, cosmetic_type) DO NOTHING;";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, cosmeticId);
            ps.setString(3, cosmeticType);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to unlock cosmetic '" + cosmeticId + "' for " + uuid, e);
        }
    }

    public java.util.List<StatTopRecord> getTopByStat(String statColumn, int limit) {
        final java.util.Set<String> allowed = java.util.Set.of(
                "kills", "deaths", "mobs_killed", "bosses_killed", "commands_sent", "messages_sent",
                "playtime_ms", "playtime_seconds", "cosmetic_coins", "balance", "blocks_broken", "crops_broken", "fish_caught",
                "mcmmo_level", "event_wins_combat", "event_wins_chat", "event_wins_hardcore"
        );
        if (statColumn == null || !allowed.contains(statColumn)) {
            return java.util.List.of();
        }

        String sql = "SELECT p.uuid, p.username, ps." + statColumn + " AS value " +
                "FROM player_stats ps JOIN players p ON p.uuid = ps.uuid " +
                "ORDER BY ps." + statColumn + " DESC, p.username ASC LIMIT ?;";

        java.util.List<StatTopRecord> list = new java.util.ArrayList<>();
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, Math.min(100, limit)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new StatTopRecord(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("username"),
                            rs.getLong("value")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to fetch top stats for " + statColumn, e);
        }
        return list;
    }

    /**
     * Aggregates wins from player_event_stats for event keys matching the given prefix.
     * Single query — O(1) DB round trip regardless of player count.
     *
     * @param keyPrefix e.g. "chat_" for chat events, "" to match all keys
     * @param limit     max rows to return
     */
    public java.util.List<StatTopRecord> getTopEventWinsByKeyPrefix(String keyPrefix, int limit) {
        // Use a LIKE pattern: if prefix is empty match everything, else "prefix%"
        String pattern = (keyPrefix == null || keyPrefix.isEmpty()) ? "%" : keyPrefix + "%";
        String sql =
            "SELECT p.uuid, p.username, SUM(pes.won) AS total_wins " +
            "FROM player_event_stats pes " +
            "JOIN players p ON p.uuid = pes.player_uuid " +
            "WHERE pes.event_key LIKE ? " +
            "GROUP BY p.uuid, p.username " +
            "HAVING SUM(pes.won) > 0 " +
            "ORDER BY total_wins DESC, p.username ASC " +
            "LIMIT ?;";

        java.util.List<StatTopRecord> list = new java.util.ArrayList<>();
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pattern);
            ps.setInt(2, Math.max(1, Math.min(100, limit)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new StatTopRecord(
                        java.util.UUID.fromString(rs.getString("uuid")),
                        rs.getString("username"),
                        rs.getLong("total_wins")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to fetch top event wins for prefix '" + keyPrefix + "'", e);
        }
        return list;
    }

    public java.util.List<java.util.UUID> listAllPlayerUuids() {
        String sql = "SELECT uuid FROM players";
        java.util.List<java.util.UUID> out = new java.util.ArrayList<>();
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(java.util.UUID.fromString(rs.getString("uuid")));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to list player uuids", e);
        }
        return out;
    }

    public String loadStoredRank(UUID uuid, String username) {
        String sql = "SELECT rank FROM players WHERE uuid = ? OR username ILIKE ? LIMIT 1;";
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid == null ? "" : uuid.toString());
            ps.setString(2, username == null ? "" : username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("rank");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to load stored rank for " + username, e);
        }
        return null;
    }

    public int countWatchlistEntries(UUID uuid, String username) {
        String sql = "SELECT COUNT(*) FROM watchlist_entries WHERE target_uuid::text = ? OR target_name ILIKE ?;";
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid == null ? "" : uuid.toString());
            ps.setString(2, username == null ? "" : username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            if (!"42P01".equals(e.getSQLState()) && !"42703".equals(e.getSQLState())) {
                logger.log(Level.WARNING, "Failed to query watchlist entries for " + username, e);
            }
            return 0;
        }
    }

    public java.util.List<NoteRecord> loadPlayerNotes(UUID uuid, String username, int limit) {
        String sql = "SELECT id, target_name, note, author, created_at FROM player_notes WHERE target_uuid::text = ? OR target_name ILIKE ? ORDER BY created_at DESC LIMIT ?;";
        java.util.List<NoteRecord> out = new java.util.ArrayList<>();
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid == null ? "" : uuid.toString());
            ps.setString(2, username == null ? "" : username);
            ps.setInt(3, Math.max(1, Math.min(200, limit)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("created_at");
                    out.add(new NoteRecord(
                            rs.getLong("id"),
                            rs.getString("target_name"),
                            rs.getString("note"),
                            rs.getString("author"),
                            ts == null ? 0L : ts.getTime()
                    ));
                }
            }
        } catch (SQLException e) {
            if (!"42P01".equals(e.getSQLState()) && !"42703".equals(e.getSQLState())) {
                logger.log(Level.WARNING, "Failed to load player notes for " + username, e);
            }
        }
        return out;
    }

    private String resolveActiveTagDisplay(PlayerProfile profile) {
        if (profile == null || profile.getActiveTag() == null || profile.getActiveTag().isBlank()) {
            return null;
        }
        try {
            var core = com.darkniightz.main.JebaitedCore.getInstance();
            if (core != null) {
                if (com.darkniightz.core.system.TagCustomizationManager.CUSTOM_TAG_KEY.equalsIgnoreCase(profile.getActiveTag())
                        && core.getTagCustomizationManager() != null) {
                    String custom = core.getTagCustomizationManager().getCustomTag(profile.getUuid());
                    if (custom != null && !custom.isBlank()) {
                        return org.bukkit.ChatColor.stripColor(custom);
                    }
                }
                if (core.getCosmeticsManager() != null) {
                    var cosmetic = core.getCosmeticsManager().get(profile.getActiveTag());
                    if (cosmetic != null && cosmetic.name != null && !cosmetic.name.isBlank()) {
                        return org.bukkit.ChatColor.stripColor(cosmetic.name);
                    }
                }
            }
        } catch (Exception ignore) {
        }
        return org.bukkit.ChatColor.stripColor(profile.getActiveTag());
    }

    private String resolveTargetName(Connection conn, UUID targetUuid, Map<String, Object> entry) {
        Object provided = entry == null ? null : entry.get("targetName");
        if (provided instanceof String name && !name.isBlank()) {
            return name.length() > 16 ? name.substring(0, 16) : name;
        }
        if (targetUuid == null) {
            return null;
        }
        String sql = "SELECT username FROM players WHERE uuid = ? LIMIT 1;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, targetUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("username");
                    if (name != null && !name.isBlank()) {
                        return name.length() > 16 ? name.substring(0, 16) : name;
                    }
                }
            }
        } catch (SQLException ignored) {
        }
        return null;
    }

    /**
     * Fetches moderation history entries for a target player, newest first.
     *
     * @param target The target player's UUID
     * @param limit  Max number of rows to return (use a sane upper bound like 100)
     * @return List of rows as maps compatible with ModerationLogger.entry() keys
     */
    public java.util.List<java.util.Map<String, Object>> getModerationHistory(UUID target, int limit) {
        String sql = "SELECT type, actor, actor_uuid, reason, duration_ms, expires_at, timestamp FROM moderation_history " +
                "WHERE target_uuid = ? ORDER BY timestamp DESC LIMIT ?;";
        java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, target.toString());
            ps.setInt(2, Math.max(1, Math.min(500, limit)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("ts", rs.getLong("timestamp"));
                    m.put("type", rs.getString("type"));
                    String actor = rs.getString("actor");
                    if (actor != null && !actor.isEmpty()) m.put("actor", actor);
                    String actorUuid = rs.getString("actor_uuid");
                    if (actorUuid != null && !actorUuid.isEmpty()) m.put("actorUuid", actorUuid);
                    String reason = rs.getString("reason");
                    if (reason != null && !reason.isEmpty()) m.put("reason", reason);
                    long duration = rs.getLong("duration_ms");
                    if (!rs.wasNull()) m.put("durationMs", duration);
                    long expires = rs.getLong("expires_at");
                    if (!rs.wasNull()) m.put("expiresAt", expires);
                    rows.add(m);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to fetch moderation history for " + target, e);
        }
        return rows;
    }

    public java.util.Set<UUID> loadUuidStateByPrefix(String prefix) {
        String sql = "SELECT state_key FROM moderation_state WHERE state_key LIKE ?;";
        java.util.Set<UUID> uuids = new java.util.HashSet<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, prefix + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("state_key");
                    if (key == null || !key.startsWith(prefix)) continue;
                    try {
                        uuids.add(UUID.fromString(key.substring(prefix.length())));
                    } catch (IllegalArgumentException ignore) {
                        // Skip malformed rows rather than failing startup.
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to load moderation state for prefix " + prefix, e);
        }
        return uuids;
    }

    public int loadSlowmodeSeconds() {
        String sql = "SELECT state_value FROM moderation_state WHERE state_key = 'slowmode:global' LIMIT 1;";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return 0;
            String raw = rs.getString("state_value");
            if (raw == null || raw.isBlank()) return 0;
            try {
                return Math.max(0, Integer.parseInt(raw.trim()));
            } catch (NumberFormatException ignore) {
                return 0;
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to load slowmode state", e);
            return 0;
        }
    }

    public void saveModerationState(String key, String value) {
        String sql = "INSERT INTO moderation_state (state_key, state_value, updated_at) VALUES (?, ?, ?) " +
                "ON CONFLICT (state_key) DO UPDATE SET state_value = EXCLUDED.state_value, updated_at = EXCLUDED.updated_at;";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to save moderation state for " + key, e);
        }
    }

    public void removeModerationState(String key) {
        String sql = "DELETE FROM moderation_state WHERE state_key = ?;";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to remove moderation state for " + key, e);
        }
    }

    public void updateEventStats(UUID playerUuid, String eventKey, int participatedDelta, int wonDelta, int lostDelta) {
        if (playerUuid == null || eventKey == null || eventKey.isBlank()) return;
        String sql = "INSERT INTO player_event_stats (player_uuid, event_key, participated, won, lost) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT (player_uuid, event_key) DO UPDATE SET " +
                "participated = player_event_stats.participated + EXCLUDED.participated, " +
                "won = player_event_stats.won + EXCLUDED.won, " +
                "lost = player_event_stats.lost + EXCLUDED.lost;";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, eventKey.toLowerCase(java.util.Locale.ROOT));
            ps.setInt(3, Math.max(0, participatedDelta));
            ps.setInt(4, Math.max(0, wonDelta));
            ps.setInt(5, Math.max(0, lostDelta));
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to update event stats for " + playerUuid + " on " + eventKey, e);
        }
    }

    public Map<String, EventStatRecord> loadEventStats(UUID playerUuid) {
        Map<String, EventStatRecord> out = new LinkedHashMap<>();
        if (playerUuid == null) return out;
        String sql = "SELECT event_key, participated, won, lost FROM player_event_stats WHERE player_uuid = ? ORDER BY event_key ASC;";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("event_key");
                    out.put(key, new EventStatRecord(
                            rs.getInt("participated"),
                            rs.getInt("won"),
                            rs.getInt("lost")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to load event stats for " + playerUuid, e);
        }
        return out;
    }

    public java.util.List<BalanceTopRecord> loadTopBalances(int limit) {
        String sql = "SELECT p.uuid, p.username, s.balance FROM players p " +
                "JOIN player_stats s ON p.uuid = s.uuid " +
                "ORDER BY s.balance DESC, p.username ASC LIMIT ?;";
        java.util.List<BalanceTopRecord> out = new java.util.ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, Math.min(100, limit)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = java.util.UUID.fromString(rs.getString("uuid"));
                    String username = rs.getString("username");
                    double balance = rs.getDouble("balance");
                    out.add(new BalanceTopRecord(uuid, username, balance));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to load top balances", e);
        }
        return out;
    }

    public void saveVanishState(UUID id, boolean active) {
        String key = "vanish:" + id;
        if (active) saveModerationState(key, "true");
        else removeModerationState(key);
    }

    public void saveFreezeState(UUID id, boolean active) {
        String key = "freeze:" + id;
        if (active) saveModerationState(key, "true");
        else removeModerationState(key);
    }

    public void saveSlowmodeSeconds(int seconds) {
        if (seconds <= 0) {
            removeModerationState("slowmode:global");
            return;
        }
        saveModerationState("slowmode:global", Integer.toString(seconds));
    }

    public RankRequestApproval findApprovedPendingRankRequest(UUID uuid, String username) {
        String sql = "SELECT id, target_uuid, target_name, requested_rank " +
                "FROM rank_change_requests " +
                "WHERE LOWER(COALESCE(status, '')) = 'approved' AND applied_at IS NULL " +
                "AND (target_uuid = ? OR target_name ILIKE ?) " +
                "ORDER BY decided_at ASC NULLS LAST, created_at ASC " +
                "LIMIT 1;";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid != null ? uuid.toString() : "");
            ps.setString(2, username != null ? username : "");
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new RankRequestApproval(
                        rs.getLong("id"),
                        rs.getString("target_uuid"),
                        rs.getString("target_name"),
                        rs.getString("requested_rank")
                );
            }
        } catch (SQLException e) {
            if ("42P01".equals(e.getSQLState()) || "42703".equals(e.getSQLState())) {
                return null;
            }
            logger.log(Level.WARNING, "Failed to load approved rank requests", e);
            return null;
        }
    }

    public void markRankRequestApplied(long id) {
        String sql = "UPDATE rank_change_requests SET applied_at = NOW() WHERE id = ?;";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (!"42P01".equals(e.getSQLState()) && !"42703".equals(e.getSQLState())) {
                logger.log(Level.WARNING, "Failed to mark rank request " + id + " as applied", e);
            }
        }
    }

    private Set<String> parseKeySet(String raw) {
        Set<String> keys = new java.util.LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return keys;
        }
        for (String part : raw.split(",")) {
            String key = part == null ? "" : part.trim();
            if (!key.isEmpty()) {
                keys.add(key);
            }
        }
        return keys;
    }

    private String joinKeySet(Set<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return "";
        }
        return String.join(",", keys);
    }

    private Map<String, String> parseLoadouts(String raw) {
        Map<String, String> loadouts = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return loadouts;
        }
        for (String line : raw.split("\\r?\\n")) {
            if (line == null || line.isBlank()) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String name = decode(line.substring(0, eq));
            String value = line.substring(eq + 1);
            if (!name.isBlank()) {
                loadouts.put(name, decode(value));
            }
        }
        return loadouts;
    }

    private String joinLoadouts(Map<String, String> loadouts) {
        if (loadouts == null || loadouts.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : loadouts.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) continue;
            if (out.length() > 0) out.append('\n');
            out.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return out.toString();
    }

    private String encode(String raw) {
        return URLEncoder.encode(raw == null ? "" : raw, StandardCharsets.UTF_8);
    }

    private String decode(String raw) {
        return URLDecoder.decode(raw == null ? "" : raw, StandardCharsets.UTF_8);
    }

    // ── Private Vaults ────────────────────────────────────────────────────────

    /** Loads raw vault data for a given player UUID + page (0-based). Sync — call from async context. */
    public byte[] loadVaultPage(java.util.UUID uuid, int page) {
        String sql = "SELECT items FROM player_vaults WHERE uuid = ? AND page = ?";
        try (Connection c = dbManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, page);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBytes("items");
            }
        } catch (SQLException e) {
            logger.warning("loadVaultPage failed: " + e.getMessage());
        }
        return null;
    }

    /** Upserts vault page data. Call from an async context. */
    public void saveVaultPageAsync(java.util.UUID uuid, int page, byte[] data) {
        String sql = "INSERT INTO player_vaults (uuid, page, items, updated_at) VALUES (?,?,?,?) " +
                     "ON CONFLICT (uuid, page) DO UPDATE SET items = EXCLUDED.items, updated_at = EXCLUDED.updated_at";
        try (Connection c = dbManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, page);
            ps.setBytes(3, data);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("saveVaultPageAsync failed: " + e.getMessage());
        }
    }

    // --- Kit cooldown persistence ---

    /**
     * Asynchronously persists the entire kit_cooldowns map for the given player.
     * Call from the main thread after updating profile.setKitLastUsed().
     */
    public void saveKitCooldownsAsync(UUID uuid, java.util.Map<String, Long> cooldowns) {
        String json = serializeKitCooldowns(cooldowns);
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(
            org.bukkit.Bukkit.getPluginManager().getPlugin("JebaitedCore"),
            () -> {
                try (java.sql.Connection conn = dbManager.getConnection();
                     java.sql.PreparedStatement ps = conn.prepareStatement(
                             "UPDATE players SET kit_cooldowns = ?::jsonb WHERE uuid = ?")) {
                    ps.setString(1, json);
                    ps.setString(2, uuid.toString());
                    ps.executeUpdate();
                } catch (java.sql.SQLException e) {
                    logger.warning("saveKitCooldownsAsync failed: " + e.getMessage());
                }
            });
    }

    /** Serialises kit cooldowns as a JSON object string for JSONB storage. */
    private String serializeKitCooldowns(java.util.Map<String, Long> cooldowns) {
        if (cooldowns == null || cooldowns.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (java.util.Map.Entry<String, Long> e : cooldowns.entrySet()) {
            if (!first) sb.append(',');
            sb.append('"').append(e.getKey().replace("\"", "")).append('"');
            sb.append(':').append(e.getValue());
            first = false;
        }
        sb.append('}');
        return sb.toString();
    }

    /** Parses a JSONB JSON string (e.g. {"gold":1234567890}) into a Map. */
    private java.util.Map<String, Long> parseKitCooldowns(String json) {
        java.util.Map<String, Long> map = new java.util.LinkedHashMap<>();
        if (json == null || json.isBlank() || "{}".equals(json.trim())) return map;
        // Simple hand-rolled parser for flat {"key":longVal,...} — avoids JSON library dependency
        String inner = json.trim();
        if (inner.startsWith("{")) inner = inner.substring(1);
        if (inner.endsWith("}")) inner = inner.substring(0, inner.length() - 1);
        for (String token : inner.split(",")) {
            token = token.trim();
            int colon = token.indexOf(':');
            if (colon <= 0) continue;
            String key = token.substring(0, colon).trim().replace("\"", "");
            String val = token.substring(colon + 1).trim();
            try { map.put(key, Long.parseLong(val)); } catch (NumberFormatException ignored) {}
        }
        return map;
    }
}
