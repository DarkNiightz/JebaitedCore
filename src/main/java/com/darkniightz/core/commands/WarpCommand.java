package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.system.EconomyManager;
import com.darkniightz.core.system.TeleportWarmupManager;
import com.darkniightz.core.system.WarpsManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

public class WarpCommand implements CommandExecutor {

    private final Plugin plugin;
    private final WarpsManager warps;
    private final EconomyManager economy;

    public WarpCommand(Plugin plugin, WarpsManager warps, EconomyManager economy) {
        this.plugin = plugin;
        this.warps = warps;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can use this command."));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " <name>"));
            return true;
        }

        String name = args[0].toLowerCase();
        Location target = warps.getWarp(name);
        if (target == null) {
            player.sendMessage(Messages.prefixed("§cWarp not found: §e" + name));
            return true;
        }

        double cost = plugin.getConfig().getBoolean("warps.allow_cost", true) ? warps.getCost(name) : 0D;
        if (cost > 0D) {
            if (!economy.removeBalance(player, cost)) {
                player.sendMessage(Messages.prefixed("§cYou need §f" + economy.format(cost) + " §cto use this warp."));
                return true;
            }
            player.sendMessage(Messages.prefixed("§7Warp cost paid: §f" + economy.format(cost)));
        }

        long delayTicks = Math.max(0L, Math.round(plugin.getConfig().getDouble("warps.teleport_delay_seconds", 0D) * 20D));
        if (delayTicks <= 0L) {
            player.teleport(target);
            player.sendMessage(Messages.prefixed("§aTeleported to warp §e" + name + "§a."));
            return true;
        }

        player.sendMessage(Messages.prefixed("§7Teleporting to warp §e" + name + " §7in §f" + (delayTicks / 20L) + "s§7..."));
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            TeleportWarmupManager.complete(player.getUniqueId());
            if (!player.isOnline()) return;
            player.teleport(target);
            player.sendMessage(Messages.prefixed("§aTeleported to warp §e" + name + "§a."));
        }, delayTicks);
        TeleportWarmupManager.register(player.getUniqueId(), task);
        return true;
    }
}
