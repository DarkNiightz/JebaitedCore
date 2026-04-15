package com.darkniightz.core.system;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

public class BroadcasterManager {
    private final Plugin plugin;
    private BukkitTask task;
    private int nextIndex = 0;

    public BroadcasterManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("broadcaster.enabled", true)) {
            return;
        }
        List<String> messages = cfg.getStringList("broadcaster.messages");
        if (messages == null || messages.isEmpty()) {
            return;
        }
        long intervalTicks = Math.max(20L, cfg.getLong("broadcaster.interval-seconds", 300L) * 20L);
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            List<String> current = plugin.getConfig().getStringList("broadcaster.messages");
            if (current == null || current.isEmpty()) {
                return;
            }
            if (nextIndex >= current.size()) {
                nextIndex = 0;
            }
            String raw = current.get(nextIndex++);
            String message = ChatColor.translateAlternateColorCodes('&', raw);
            Bukkit.broadcastMessage(message);
            recordFeed("Auto broadcast fired", java.util.List.of("§7Message: §f" + ChatColor.stripColor(message)));
        }, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void broadcastNow() {
        List<String> current = plugin.getConfig().getStringList("broadcaster.messages");
        if (current == null || current.isEmpty()) {
            return;
        }
        if (nextIndex >= current.size()) {
            nextIndex = 0;
        }
        String raw = current.get(nextIndex++);
        String message = ChatColor.translateAlternateColorCodes('&', raw);
        Bukkit.broadcastMessage(message);
        recordFeed("Broadcast fired", java.util.List.of("§7Message: §f" + ChatColor.stripColor(message)));
    }

    private void recordFeed(String title, List<String> details) {
        if (plugin instanceof com.darkniightz.main.JebaitedCore core && core.getDebugFeedManager() != null) {
            core.getDebugFeedManager().recordSystem(title, details);
        }
    }
}
