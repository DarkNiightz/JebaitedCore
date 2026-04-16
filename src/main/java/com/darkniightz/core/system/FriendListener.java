package com.darkniightz.core.system;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Populates and clears the friend cache on player join/quit.
 */
public class FriendListener implements Listener {

    private final FriendManager friendManager;
    private final Plugin plugin;

    public FriendListener(FriendManager friendManager, Plugin plugin) {
        this.friendManager = friendManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        final var uuid = event.getPlayer().getUniqueId();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> friendManager.loadPlayer(uuid));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        friendManager.unloadPlayer(event.getPlayer().getUniqueId());
        com.darkniightz.core.chat.ChatInputService.cancel(event.getPlayer().getUniqueId());
    }
}
