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

public class SmpCommand implements CommandExecutor {
    private final JebaitedCore plugin;
    private final WorldManager worldManager;
    private final SpawnManager spawnManager;

    public SmpCommand(JebaitedCore plugin, WorldManager worldManager, SpawnManager spawnManager) {
        this.plugin = plugin;
        this.worldManager = worldManager;
        this.spawnManager = spawnManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can use /smp."));
            return true;
        }
        World smp = worldManager.ensureSmpWorldLoaded();
        if (smp == null) {
            player.sendMessage(Messages.prefixed("§cSMP world is unavailable right now."));
            return true;
        }
        Location target = SmpReturnManager.get(player);
        if (target != null && target.getWorld() != null && !smp.getName().equalsIgnoreCase(target.getWorld().getName())) {
            target = null;
        }
        if (target == null) {
            target = spawnManager.getSpawnForWorld(smp.getName());
        }
        if (target == null) {
            target = smp.getSpawnLocation();
        }

        if (isStaff(player)) {
            if (player.teleport(target)) {
                player.sendMessage(Messages.prefixed("§2Teleported to SMP."));
            } else {
                player.sendMessage(Messages.prefixed("§cCould not teleport you to SMP."));
            }
            return true;
        }

        if (player.getWorld() != null && target.getWorld() != null && player.getWorld().equals(target.getWorld())) {
            if (player.teleport(target)) {
                player.sendMessage(Messages.prefixed("§2Teleported to SMP."));
            } else {
                player.sendMessage(Messages.prefixed("§cCould not teleport you to SMP."));
            }
            return true;
        }

        Location start = player.getLocation().clone();
        Location finalTarget = target.clone();
        player.sendMessage(Messages.prefixed("§7Teleporting to SMP in §f3s§7. Movement cancels."));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (moved(start, player.getLocation())) {
                player.sendMessage(Messages.prefixed("§cTeleport cancelled because you moved."));
                return;
            }
            if (player.teleport(finalTarget)) {
                player.sendMessage(Messages.prefixed("§2Teleported to SMP."));
            } else {
                player.sendMessage(Messages.prefixed("§cCould not teleport you to SMP."));
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
