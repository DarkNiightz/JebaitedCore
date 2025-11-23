package com.darkniightz.core.moderation;

import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
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

    public ModerationListener(ProfileStore profiles, RankManager ranks, ModerationManager moderation, Plugin plugin) {
        this.profiles = profiles;
        this.ranks = ranks;
        this.moderation = moderation;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String name = event.getName();
        PlayerProfile p = profiles.getOrCreate(uuid, name, ranks.getDefaultGroup());
        Long banUntil = p.getBanUntil();
        if (banUntil != null && banUntil > System.currentTimeMillis()) {
            String reason = p.getBanReason() == null ? "Banned" : p.getBanReason();
            String untilTxt = (banUntil == Long.MAX_VALUE) ? "Permanent" : (TimeUtil.formatDurationShort(banUntil - System.currentTimeMillis()));
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    "§cYou are banned.\n§7Reason: §e" + reason + "\n§7Duration: §e" + untilTxt);
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
