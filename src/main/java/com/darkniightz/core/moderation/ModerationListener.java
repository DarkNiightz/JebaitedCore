package com.darkniightz.core.moderation;

import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.main.JebaitedCore;
import com.darkniightz.main.database.dao.PlayerProfileDAO;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class ModerationListener implements Listener {
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final ModerationManager moderation;
    private final Plugin plugin;

    public ModerationListener(ProfileStore profiles, RankManager ranks, ModerationManager moderation, Plugin plugin) {
        this.profiles = profiles;
        this.ranks = ranks;
        this.moderation = moderation;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        JebaitedCore core = JebaitedCore.getInstance();

        // If database is disabled, we can't check for bans.
        if (!core.getDatabaseManager().isEnabled()) {
            return;
        }

        PlayerProfileDAO dao = core.getPlayerProfileDAO();
        ResultSet rs = dao.getActiveBan(uuid);

        if (rs != null) {
            try (Connection conn = rs.getStatement().getConnection(); ResultSet autoCloseRs = rs) { // Auto-closes connection and resultset
                if (autoCloseRs.next()) {
                    long expiresAt = autoCloseRs.getLong("expires_at");
                    String reason = autoCloseRs.getString("reason");

                    // If expires_at is 0 or null from DB, we treat it as permanent.
                    boolean isPermanent = autoCloseRs.wasNull() || expiresAt == 0;
                    String untilTxt = isPermanent ? "Permanent" : TimeUtil.formatDurationShort(expiresAt - System.currentTimeMillis());
                    String displayReason = (reason == null || reason.isEmpty()) ? "Banned" : reason;

                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                            "§cYou are banned.\n§7Reason: §e" + displayReason + "\n§7Duration: §e" + untilTxt);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Database error during login check for " + uuid);
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "§cAn error occurred while checking your account status.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        var p = event.getPlayer();
        if (moderation.isFrozen(p.getUniqueId())) {
            if (event.getFrom().getX() != event.getTo().getX() ||
                event.getFrom().getY() != event.getTo().getY() ||
                event.getFrom().getZ() != event.getTo().getZ()) {
                event.setTo(event.getFrom());
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Re-apply vanish state for all currently vanished players versus the joiner
        var joining = event.getPlayer();
        for (UUID id : moderation.getVanished()) {
            if (!joining.getUniqueId().equals(id)) {
                var vanished = event.getPlayer().getServer().getPlayer(id);
                if (vanished != null) joining.hidePlayer(plugin, vanished);
            }
        }
    }
}
