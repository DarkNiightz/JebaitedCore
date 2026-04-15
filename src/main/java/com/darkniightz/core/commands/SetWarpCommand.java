package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.system.WarpsManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class SetWarpCommand implements CommandExecutor {

    private final Plugin plugin;
    private final WarpsManager warps;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;

    public SetWarpCommand(Plugin plugin, WarpsManager warps, ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
        this.plugin = plugin;
        this.warps = warps;
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can use this command."));
            return true;
        }

        PlayerProfile actor = profiles.getOrCreate(player, ranks.getDefaultGroup());
        boolean bypass = devMode != null && devMode.isActive(player.getUniqueId());
        if (!bypass && !ranks.isAtLeast(actor.getPrimaryRank(), "admin")) {
            player.sendMessage(Messages.noPerm());
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " <name> [cost]"));
            return true;
        }

        double cost = 0D;
        if (args.length >= 2) {
            try {
                cost = Double.parseDouble(args[1]);
            } catch (NumberFormatException ex) {
                player.sendMessage(Messages.prefixed("§cCost must be a number."));
                return true;
            }
            if (cost < 0D) {
                player.sendMessage(Messages.prefixed("§cCost cannot be negative."));
                return true;
            }
        }

        if (!plugin.getConfig().getBoolean("warps.allow_cost", true)) {
            cost = 0D;
        }

        final double finalCost = cost;
        final String warpName = args[0];
        final org.bukkit.Location loc = player.getLocation(); // capture on main thread
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            warps.setWarp(warpName, loc, finalCost);
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                player.sendMessage(Messages.prefixed("§aWarp set: §e" + warpName.toLowerCase() + " §7(cost: §f" + finalCost + "§7)"));
            });
        });
        return true;
    }
}
