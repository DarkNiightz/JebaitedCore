package com.darkniightz.main.listeners;

import com.darkniightz.main.Core;
import com.darkniightz.main.managers.LogManager;
import com.darkniightz.main.managers.SpawnManager;
import com.darkniightz.main.util.RankUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;

import static com.darkniightz.main.listeners.InventoryManagerListener.*;

public class PlayerEventListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Set tablist name
        player.setPlayerListName(RankUtil.getColoredTabName(player));
        player.teleport(SpawnManager.getInstance().getSpawn());
        event.getPlayer().setGameMode(GameMode.ADVENTURE);
        player.sendMessage(ChatColor.GREEN + "Welcome back - teleported to spawn!");

        // Log join if enabled
        if (Core.getInstance().getConfig().getBoolean("logging.log-joins", true)) {
            String type = player.hasPlayedBefore() ? "Return" : "First-time";
            LogManager.getInstance(Core.getInstance()).log("JOIN: " + player.getName() + " (" + type + ") IP: " + player.getAddress().getAddress().getHostAddress());
        }

        // Custom join message
        String joinMsg = Core.getInstance().getConfig().getString("join-quit.join", "&a{player} ({rank}) joined!");
        joinMsg = joinMsg.replace("{player}", player.getName())
                .replace("{rank}", RankUtil.getRankName(player));
        event.setJoinMessage(ChatColor.translateAlternateColorCodes('&', joinMsg));

        // Player MOTD if enabled
        if (Core.getInstance().getConfig().getBoolean("motd.enabled", true)) {
            for (String line : Core.getInstance().getConfig().getStringList("motd.player-join")) {
                line = line.replace("{player}", player.getName())
                        .replace("{rank}", RankUtil.getRankName(player))
                        .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()));
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = event.getPlayer().getUniqueId();
        doubleJumpEnabled.remove(uuid);
        particlesEnabled.remove(uuid);
        activeGadget.remove(uuid);
        // Custom quit message
        String quitMsg = Core.getInstance().getConfig().getString("join-quit.quit", "&c{player} ({rank}) left!");
        quitMsg = quitMsg.replace("{player}", player.getName())
                .replace("{rank}", RankUtil.getRankName(player));
        event.setQuitMessage(ChatColor.translateAlternateColorCodes('&', quitMsg));
    }
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        event.setRespawnLocation(SpawnManager.getInstance().getSpawn());
        event.getPlayer().setGameMode(GameMode.ADVENTURE);
        event.getPlayer().sendMessage(ChatColor.GREEN + "Respawned at hub spawn!");

    }
}