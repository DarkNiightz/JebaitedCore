package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.system.WarpsManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class DelWarpCommand implements CommandExecutor {

    private final Plugin plugin;
    private final WarpsManager warps;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;

    public DelWarpCommand(Plugin plugin, WarpsManager warps, ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
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
            player.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " <name>"));
            return true;
        }

        String name = args[0].toLowerCase();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean deleted = warps.deleteWarp(name);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (!deleted) {
                    player.sendMessage(Messages.prefixed("§cWarp not found: §e" + name));
                } else {
                    player.sendMessage(Messages.prefixed("§aDeleted warp: §e" + name));
                }
            });
        });
        return true;
    }
}
