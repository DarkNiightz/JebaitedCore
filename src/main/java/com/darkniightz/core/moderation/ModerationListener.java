package com.darkniightz.core.moderation;

import com.darkniightz.core.dev.DebugFeedManager;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.main.JebaitedCore;
import com.darkniightz.main.PlayerProfileDAO;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public class ModerationListener implements Listener {
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final ModerationManager moderation;
    private final Plugin plugin;
    private final DebugFeedManager feed;

    public ModerationListener(ProfileStore profiles, RankManager ranks, ModerationManager moderation, Plugin plugin, DebugFeedManager feed) {
        this.profiles = profiles;
        this.ranks = ranks;
        this.moderation = moderation;
        this.plugin = plugin;
        this.feed = feed;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        JebaitedCore core = JebaitedCore.getInstance();

        if (!core.getDatabaseManager().isEnabled()) {
            return;
        }

        profiles.preloadProfile(uuid, event.getName(), ranks.getDefaultGroup());

        PlayerProfileDAO dao = core.getPlayerProfileDAO();
        PlayerProfileDAO.BanRecord ban = dao.getActiveBan(uuid);
        if (ban != null) {
            Long expiresAt = ban.expiresAt();
            String reason = ban.reason();

            boolean isPermanent = expiresAt == null || expiresAt == 0L || expiresAt == Long.MAX_VALUE;
            String untilTxt = isPermanent ? "Permanent" : TimeUtil.formatDurationShort(Math.max(0, expiresAt - System.currentTimeMillis()));
            String displayReason = (reason == null || reason.isEmpty()) ? "Banned" : reason;

            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    "§cYou are banned.\n§7Reason: §e" + displayReason + "\n§7Duration: §e" + untilTxt);
            if (feed != null) {
                feed.recordModeration(null, "Ban enforced during login", java.util.List.of(
                        "§7Target UUID: §f" + uuid,
                        "§7Reason: §f" + displayReason,
                        "§7Duration: §f" + untilTxt
                ));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getX() == event.getTo().getX() &&
            event.getFrom().getY() == event.getTo().getY() &&
            event.getFrom().getZ() == event.getTo().getZ()) return;
        var p = event.getPlayer();
        if (moderation.isFrozen(p.getUniqueId())) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var joining = event.getPlayer();
        for (UUID id : moderation.getVanished()) {
            if (!joining.getUniqueId().equals(id)) {
                var vanished = event.getPlayer().getServer().getPlayer(id);
                if (vanished != null) joining.hidePlayer(plugin, vanished);
            }
        }
        if (feed != null) {
            feed.recordModeration(joining, "Moderation join hook", java.util.List.of("§7Vanished players hidden from the joiner."));
        }
    }
}
