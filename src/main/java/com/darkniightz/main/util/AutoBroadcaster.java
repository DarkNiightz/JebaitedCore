package com.darkniightz.main.util;

import com.darkniightz.main.Core;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class AutoBroadcaster extends BukkitRunnable {

    private final List<String> messages = Core.getInstance().getConfig().getStringList("broadcaster.messages");
    private int index = 0;

    @Override
    public void run() {
        if (messages.isEmpty()) return;
        String msg = messages.get(index);
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg));
        index = (index + 1) % messages.size();  // Cycle
    }
}