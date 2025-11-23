package com.darkniightz.core.commands;

import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class StatsCommand implements CommandExecutor {

    private final ProfileStore profiles;
    private final RankManager ranks;

    public StatsCommand(ProfileStore profiles, RankManager ranks) {
        this.profiles = profiles;
        this.ranks = ranks;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        OfflinePlayer target;
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§cConsole must specify a player: §e/" + label + " <player>");
                return true;
            }
            target = p;
        } else {
            target = Bukkit.getOfflinePlayer(args[0]);
            if (target.getUniqueId() == null) {
                sender.sendMessage("§cPlayer not found: §e" + args[0]);
                return true;
            }
        }

        PlayerProfile prof = profiles.getOrCreate(target, ranks.getDefaultGroup());
        String name = target.getName() != null ? target.getName() : target.getUniqueId().toString().substring(0, 8);
        sender.sendMessage("§6–– Hub Stats for §e" + name + " §6––");
        sender.sendMessage("§7Rank: §b" + prof.getPrimaryRank());
        sender.sendMessage("§7Messages Sent: §a" + prof.getMessagesSent());
        sender.sendMessage("§7Commands Sent: §a" + prof.getCommandsSent());
        sender.sendMessage("§7Cosmetic Tickets: §e" + prof.getCosmeticTickets());
        // Cosmetics & games
        try {
            sender.sendMessage("§7Wardrobe Opens: §a" + prof.getWardrobeOpens());
            sender.sendMessage("§7Cosmetics Equipped: §a" + prof.getCosmeticEquips());
        } catch (Throwable ignored) {}
        return true;
    }
}
