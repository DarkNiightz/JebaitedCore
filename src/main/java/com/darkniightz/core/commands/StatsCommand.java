package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.achievements.AchievementManager;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.gui.StatsMenu;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class StatsCommand implements CommandExecutor, TabCompleter {

    private final JebaitedCore plugin;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;
    private final AchievementManager achievements;

    public StatsCommand(JebaitedCore plugin, ProfileStore profiles, RankManager ranks, DevModeManager devMode, AchievementManager achievements) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
        this.achievements = achievements;
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
            target = Bukkit.getOfflinePlayer(args[0]);
            if (target.getUniqueId() == null) {
                viewer.sendMessage(Messages.prefixed("§cPlayer not found: §e" + args[0]));
                return true;
            }
        }

        new StatsMenu(plugin, profiles, ranks, achievements, target).open(viewer);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1 || !(sender instanceof Player viewer)) {
            return List.of();
        }
        PlayerProfile viewerProfile = profiles.getOrCreate(viewer, ranks.getDefaultGroup());
        boolean bypass = devMode != null && devMode.isActive(viewer.getUniqueId());
        boolean staffTier = bypass || ranks.isAtLeast(viewerProfile.getPrimaryRank(), "helper");
        String partial = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!staffTier && !viewer.canSee(p)) {
                continue;
            }
            String name = p.getName();
            if (name == null || name.isBlank()) {
                continue;
            }
            if (partial.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(partial)) {
                out.add(name);
            }
        }
        Collections.sort(out);
        return out;
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
