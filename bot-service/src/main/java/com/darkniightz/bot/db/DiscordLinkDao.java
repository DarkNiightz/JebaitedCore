package com.darkniightz.bot.db;

import com.darkniightz.bot.observability.AuditLogger;
import com.darkniightz.bot.observability.CorrelationId;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;
import java.util.UUID;

public final class DiscordLinkDao {
    private final DataSource dataSource;

    public DiscordLinkDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record LinkResult(boolean success, String message, UUID playerUuid, String primaryRank, String donorRank) {}

    /** True when this Discord account has an active Minecraft link (no unlinked row). */
    public boolean isLinked(String discordUserId) {
        if (discordUserId == null || discordUserId.isBlank()) {
            return false;
        }
        String sql = "SELECT 1 FROM discord_links WHERE discord_user_id = ? AND unlinked_at IS NULL LIMIT 1";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, discordUserId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            AuditLogger.info(
                    "discord.link.is_linked_failed",
                    CorrelationId.next(),
                    e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage()));
            return false;
        }
    }

    public LinkResult consumeCodeAndLink(String code, String discordUserId) {
        if (code == null || code.isBlank()) {
            return new LinkResult(false, "Missing link code.", null, null, null);
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);

        String selectCode = "SELECT player_uuid FROM discord_link_codes WHERE code = ? AND consumed_at IS NULL AND expires_at > NOW() LIMIT 1;";
        String consumeCode = "UPDATE discord_link_codes SET consumed_at = NOW(), consumed_by = ? WHERE code = ?;";
        String unlinkPriorDiscord = "UPDATE discord_links SET unlinked_at = NOW() WHERE discord_user_id = ? AND unlinked_at IS NULL;";
        String unlinkPriorPlayer = "UPDATE discord_links SET unlinked_at = NOW() WHERE player_uuid = ? AND unlinked_at IS NULL;";
        String insertLink = "INSERT INTO discord_links (player_uuid, discord_user_id, linked_at, link_source) VALUES (?, ?, NOW(), 'DISCORD_SLASH_LINK');";
        String selectRank = "SELECT rank, donor_rank FROM players WHERE uuid = ? LIMIT 1;";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                UUID playerUuid;
                try (PreparedStatement ps = conn.prepareStatement(selectCode)) {
                    ps.setString(1, normalized);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return new LinkResult(false, "That code is invalid or expired. Run /link in Minecraft again.", null, null, null);
                        }
                        playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(consumeCode)) {
                    ps.setString(1, discordUserId);
                    ps.setString(2, normalized);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(unlinkPriorDiscord)) {
                    ps.setString(1, discordUserId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(unlinkPriorPlayer)) {
                    ps.setString(1, playerUuid.toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(insertLink)) {
                    ps.setString(1, playerUuid.toString());
                    ps.setString(2, discordUserId);
                    ps.executeUpdate();
                }

                String primary = null;
                String donor = null;
                try (PreparedStatement ps = conn.prepareStatement(selectRank)) {
                    ps.setString(1, playerUuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            primary = rs.getString("rank");
                            donor = rs.getString("donor_rank");
                        }
                    }
                }
                conn.commit();
                return new LinkResult(true, "Linked successfully.", playerUuid, primary, donor);
            } catch (Exception e) {
                conn.rollback();
                AuditLogger.info(
                        "discord.link.db_error",
                        CorrelationId.next(),
                        e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage()));
                return new LinkResult(false, "Link failed due to a database error.", null, null, null);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            return new LinkResult(false, "Link failed due to a connection error.", null, null, null);
        }
    }
}
