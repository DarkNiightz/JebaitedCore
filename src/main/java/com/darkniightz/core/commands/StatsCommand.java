package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.gui.StatsMenu;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class StatsCommand implements CommandExecutor {

    private final Plugin plugin;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;

    public StatsCommand(Plugin plugin, ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player viewer)) {
            if (args.length == 0) {
                sender.sendMessage(Messages.prefixed("§cConsole usage: §f/" + label + " <player>"));
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (target.getUniqueId() == null) {
                sender.sendMessage(Messages.prefixed("§cPlayer not found: §e" + args[0]));
                return true;
            }
            printQuickConsoleStats(sender, target);
            return true;
        }

        OfflinePlayer target = viewer;
        if (args.length >= 1) {
            PlayerProfile viewerProfile = profiles.getOrCreate(viewer, ranks.getDefaultGroup());
            boolean bypass = devMode != null && devMode.isActive(viewer.getUniqueId());
            if (!bypass && !ranks.isAtLeast(viewerProfile.getPrimaryRank(), "admin")) {
                viewer.sendMessage(Messages.noPerm());
                return true;
            }
            target = Bukkit.getOfflinePlayer(args[0]);
            if (target.getUniqueId() == null) {
                viewer.sendMessage(Messages.prefixed("§cPlayer not found: §e" + args[0]));
                return true;
            }
        }

        new StatsMenu(plugin, profiles, ranks, target).open(viewer);
        return true;
    }

    private void printQuickConsoleStats(CommandSender sender, OfflinePlayer target) {
        PlayerProfile prof = profiles.getOrCreate(target, ranks.getDefaultGroup());
        if (prof == null) {
            sender.sendMessage(Messages.prefixed("§cCould not load player profile."));
            return;
        }
        String name = target.getName() != null ? target.getName() : target.getUniqueId().toString().substring(0, 8);
        sender.sendMessage(Messages.prefixed("§6–– Stats for §e" + name + " §6––"));
        sender.sendMessage(Messages.prefixed("§7Rank: §b" + prof.getPrimaryRank()));
        sender.sendMessage(Messages.prefixed("§7Messages Sent: §a" + prof.getMessagesSent()));
        sender.sendMessage(Messages.prefixed("§7Commands Sent: §a" + prof.getCommandsSent()));
        sender.sendMessage(Messages.prefixed("§7Cosmetic Coins: §6" + prof.getCosmeticCoins()));
        sender.sendMessage(Messages.prefixed("§7Playtime: §f" + (prof.getPlaytimeMs() / 1000L) + "s"));
    }
}
