package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.world.SmpReturnManager;
import com.darkniightz.core.world.SpawnManager;
import com.darkniightz.core.world.WorldManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class HubCommand implements CommandExecutor {
    private final JebaitedCore plugin;
    private final WorldManager worldManager;
    private final SpawnManager spawnManager;

    public HubCommand(JebaitedCore plugin, WorldManager worldManager, SpawnManager spawnManager) {
        this.plugin = plugin;
        this.worldManager = worldManager;
        this.spawnManager = spawnManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can use /hub."));
            return true;
        }
        World hub = Bukkit.getWorld(worldManager.getHubWorldName());
        if (hub == null) {
            player.sendMessage(Messages.prefixed("§cHub world is unavailable right now."));
            return true;
        }
        if (worldManager.isSmp(player)) {
            SmpReturnManager.remember(player);
        }
        Location target = spawnManager.getSpawnForWorld(hub.getName());
        if (target == null) {
            target = hub.getSpawnLocation();
        }

        if (isStaff(player)) {
            if (player.teleport(target)) {
                player.sendMessage(Messages.prefixed("§aTeleported to Hub."));
            } else {
                player.sendMessage(Messages.prefixed("§cCould not teleport you to Hub."));
            }
            return true;
        }

        if (player.getWorld() != null && target.getWorld() != null && player.getWorld().equals(target.getWorld())) {
            if (player.teleport(target)) {
                player.sendMessage(Messages.prefixed("§aTeleported to Hub."));
            } else {
                player.sendMessage(Messages.prefixed("§cCould not teleport you to Hub."));
            }
            return true;
        }

        Location start = player.getLocation().clone();
        Location finalTarget = target.clone();
        player.sendMessage(Messages.prefixed("§7Teleporting to hub in §f3s§7. Movement cancels."));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (moved(start, player.getLocation())) {
                player.sendMessage(Messages.prefixed("§cTeleport cancelled because you moved."));
                return;
            }
            if (player.teleport(finalTarget)) {
                player.sendMessage(Messages.prefixed("§aTeleported to Hub."));
            } else {
                player.sendMessage(Messages.prefixed("§cCould not teleport you to Hub."));
            }
        }, 60L);
        return true;
    }

    private boolean isStaff(Player player) {
        if (plugin.getDevModeManager() != null && plugin.getDevModeManager().isActive(player.getUniqueId())) {
            return true;
        }
        var profile = plugin.getProfileStore().getOrCreate(player, plugin.getRankManager().getDefaultGroup());
        return plugin.getRankManager().isAtLeast(profile.getPrimaryRank(), "helper");
    }

    private boolean moved(Location from, Location now) {
        if (from == null || now == null || from.getWorld() == null || now.getWorld() == null) return true;
        if (!from.getWorld().equals(now.getWorld())) return true;
        return from.getBlockX() != now.getBlockX()
                || from.getBlockY() != now.getBlockY()
                || from.getBlockZ() != now.getBlockZ();
    }
}
