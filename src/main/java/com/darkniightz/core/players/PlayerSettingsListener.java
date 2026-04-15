package com.darkniightz.core.players;

import com.darkniightz.main.JebaitedCore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerSettingsListener implements Listener {

    private final JebaitedCore plugin;

    public PlayerSettingsListener(JebaitedCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getScoreboardManager() != null) {
                plugin.getScoreboardManager().refreshPlayer(event.getPlayer());
            }
        }, 5L);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Component message = event.deathMessage();
        if (message == null) {
            return;
        }
        event.deathMessage(null);

        for (var viewer : Bukkit.getOnlinePlayers()) {
            PlayerProfile profile = plugin.getProfileStore().getOrCreate(viewer, plugin.getRankManager().getDefaultGroup());
            if (profile != null && !profile.isDeathMessagesEnabled()) {
                continue;
            }
            viewer.sendMessage(message);
        }
    }
}
