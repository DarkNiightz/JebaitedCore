package com.darkniightz.core.system;

import com.darkniightz.main.database.DatabaseManager;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Issues one-time /link codes used to connect a Minecraft account to Discord.
 */
public final class DiscordLinkService {
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final int MAX_RETRIES = 8;

    private final DatabaseManager databaseManager;
    private final Logger logger;
    private final SecureRandom random = new SecureRandom();

    public DiscordLinkService(DatabaseManager databaseManager, Logger logger) {
        this.databaseManager = databaseManager;
        this.logger = logger;
    }

    public String issueCode(UUID playerUuid, long ttlSeconds) {
        if (playerUuid == null) {
            throw new IllegalArgumentException("playerUuid cannot be null");
        }
        long safeTtl = Math.max(300L, Math.min(1800L, ttlSeconds));
        Instant expiresAt = Instant.now().plusSeconds(safeTtl);

        String cleanupSql = "DELETE FROM discord_link_codes WHERE expires_at < NOW() OR consumed_at IS NOT NULL;";
        String deletePlayerActiveSql = "DELETE FROM discord_link_codes WHERE player_uuid = ?;";
        String insertSql = "INSERT INTO discord_link_codes (code, player_uuid, issued_at, expires_at) VALUES (?, ?, NOW(), ?);";

        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement cleanup = conn.prepareStatement(cleanupSql);
                 PreparedStatement deleteActive = conn.prepareStatement(deletePlayerActiveSql);
                 PreparedStatement insert = conn.prepareStatement(insertSql)) {
                cleanup.executeUpdate();
                deleteActive.setString(1, playerUuid.toString());
                deleteActive.executeUpdate();

                for (int i = 0; i < MAX_RETRIES; i++) {
                    String code = nextCode();
                    insert.setString(1, code);
                    insert.setString(2, playerUuid.toString());
                    insert.setTimestamp(3, Timestamp.from(expiresAt));
                    try {
                        insert.executeUpdate();
                        conn.commit();
                        return code;
                    } catch (java.sql.SQLException duplicate) {
                        if (!"23505".equals(duplicate.getSQLState())) {
                            throw duplicate;
                        }
                    }
                }
                throw new IllegalStateException("Could not generate unique Discord link code after retries.");
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            logger.warning("Failed to issue Discord link code for " + playerUuid + ": " + e.getMessage());
            return null;
        }
    }

    public boolean hasActiveLink(UUID playerUuid) {
        String sql = "SELECT 1 FROM discord_links WHERE player_uuid = ? AND unlinked_at IS NULL LIMIT 1;";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            logger.warning("Failed to check Discord link status for " + playerUuid + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Returns the number of active (not unlinked) Discord connections.
     * Used by lightweight surfaces such as tablist/footer status.
     */
    public int countActiveLinks() {
        String sql = "SELECT COUNT(*) FROM discord_links WHERE unlinked_at IS NULL;";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return Math.max(0, rs.getInt(1));
            }
        } catch (Exception e) {
            logger.warning("Failed to count active Discord links: " + e.getMessage());
        }
        return -1;
    }
    private String nextCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString().toUpperCase(Locale.ROOT);
    }
}
