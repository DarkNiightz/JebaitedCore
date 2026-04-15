package com.darkniightz.core.playerjoin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.Plugin;

public class ServerListMotdListener implements Listener {
    private final Plugin plugin;

    public ServerListMotdListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPing(ServerListPingEvent event) {
        if (!plugin.getConfig().getBoolean("motd.enabled", true)) {
            return;
        }
        String raw = plugin.getConfig().getString("motd.server-list", "");
        if (raw == null || raw.isBlank()) {
            return;
        }
        String motd = ChatColor.translateAlternateColorCodes('&', raw
                .replace("{online}", Integer.toString(Bukkit.getOnlinePlayers().size()))
                .replace("{max}", Integer.toString(Bukkit.getMaxPlayers())));
        event.setMotd(motd);
    }
}
