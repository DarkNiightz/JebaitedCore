package com.darkniightz.core.system;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

public class BossBarManager {
    private final Plugin plugin;
    private BukkitTask task;
    private BossBar bossBar;
    private int nextIndex = 0;

    public BossBarManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("bossbar.enabled", true)) {
            return;
        }
        List<String> messages = cfg.getStringList("bossbar.messages");
        if (messages == null || messages.isEmpty()) {
            return;
        }

        String first = translate(messages.get(0));
        bossBar = Bukkit.createBossBar(first, BarColor.YELLOW, BarStyle.SOLID);
        bossBar.setVisible(true);
        syncPlayers();
        recordFeed("Bossbar started", java.util.List.of("§7Title: §f" + bossBar.getTitle()));

        long intervalTicks = Math.max(20L, cfg.getLong("bossbar.interval-seconds", 30L) * 20L);
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            List<String> current = plugin.getConfig().getStringList("bossbar.messages");
            if (current == null || current.isEmpty()) {
                return;
            }
            if (nextIndex >= current.size()) {
                nextIndex = 0;
            }
            bossBar.setTitle(translate(current.get(nextIndex++)));
            syncPlayers();
            recordFeed("Bossbar rotated", java.util.List.of("§7Title: §f" + bossBar.getTitle()));
        }, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar.setVisible(false);
            bossBar = null;
        }
    }

    public void rotateNow() {
        if (bossBar == null) {
            return;
        }
        List<String> current = plugin.getConfig().getStringList("bossbar.messages");
        if (current == null || current.isEmpty()) {
            return;
        }
        if (nextIndex >= current.size()) {
            nextIndex = 0;
        }
        bossBar.setTitle(translate(current.get(nextIndex++)));
        syncPlayers();
        recordFeed("Bossbar rotated now", java.util.List.of("§7Title: §f" + bossBar.getTitle()));
    }

    public String getCurrentTitle() {
        return bossBar == null ? "" : bossBar.getTitle();
    }

    private void syncPlayers() {
        if (bossBar == null) return;
        bossBar.removeAll();
        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }
    }

    private String translate(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw == null ? "" : raw);
    }

    private void recordFeed(String title, List<String> details) {
        if (plugin instanceof com.darkniightz.main.JebaitedCore core && core.getDebugFeedManager() != null) {
            core.getDebugFeedManager().recordSystem(title, details);
        }
    }
}
