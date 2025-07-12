package com.darkniightz.main.listeners;

import com.darkniightz.main.Core;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

public class ServerListPingListener implements Listener {

    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        if (!Core.getInstance().getConfig().getBoolean("motd.enabled", true)) return;

        String motd = Core.getInstance().getConfig().getString("motd.server-list", "&6Jebaited Server\n&aOnline: {online}/{max}");
        motd = motd.replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{max}", String.valueOf(Bukkit.getMaxPlayers()));
        event.setMotd(ChatColor.translateAlternateColorCodes('&', motd));
    }
}