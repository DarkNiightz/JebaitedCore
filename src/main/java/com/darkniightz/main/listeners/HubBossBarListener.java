package com.darkniightz.main.listeners;

import com.darkniightz.main.Core;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class HubBossBarListener implements Listener {

    private BossBar bossBar;
    private final String[] rainbowColors = {"§4", "§c", "§6", "§e", "§2", "§a", "§b", "§3", "§1", "§9", "§d", "§5"};
    private int colorIndex = 0;
    private int messageIndex = 0;
    private final List<String> messages = Core.getInstance().getConfig().getStringList("bossbar.messages");

    public HubBossBarListener() {
        bossBar = Bukkit.createBossBar(rainbowTitle("Jebaited"), BarColor.PURPLE, BarStyle.SOLID);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (messages.isEmpty()) {
                    bossBar.setTitle(rainbowTitle("Welcome to Jebaited!"));  // Fallback if config empty
                } else {
                    bossBar.setTitle(rainbowTitle(messages.get(messageIndex)));
                    messageIndex = (messageIndex + 1) % messages.size();  // Cycle messages
                }
                bossBar.setProgress(1.0);  // Full bar - reduce for countdowns, e.g., 0.5 for half
            }
        }.runTaskTimer(Core.getInstance(), 0, 100);  // Update every 5 secs
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        bossBar.addPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        bossBar.removePlayer(event.getPlayer());
    }

    private String rainbowTitle(String title) {
        StringBuilder rainbow = new StringBuilder();
        for (int i = 0; i < title.length(); i++) {
            rainbow.append(rainbowColors[(colorIndex + i) % rainbowColors.length]).append(title.charAt(i));
        }
        colorIndex = (colorIndex + 1) % rainbowColors.length;  // Cycle for dynamic
        return rainbow.toString();
    }
}