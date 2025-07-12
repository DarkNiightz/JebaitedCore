package com.darkniightz.main.commands;

import com.darkniightz.main.Core;
import com.darkniightz.main.managers.RankManager;
import com.darkniightz.main.util.RankUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetRankCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("core.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /setrank <player> <rank> (default/friend/vip/moderator/srmoderator/admin/developer)");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not online!");
            return true;
        }

        String rank = args[1].toLowerCase();
        if (!isValidRank(rank)) {
            sender.sendMessage(ChatColor.RED + "Invalid rank!");
            return true;
        }

        // Hierarchy check (console bypasses, OP players bypass)
        if (sender instanceof Player && !((Player) sender).isOp()) {
            Player actor = (Player) sender;
            int actorLevel = RankUtil.getRankLevel(actor);
            int targetLevel = RankUtil.getRankLevel(target);
            if (actorLevel < targetLevel || (actorLevel == targetLevel && actorLevel < 5)) {
                sender.sendMessage(ChatColor.RED + "You cannot moderate higher or equal ranks!");
                return true;
            }
        }

        RankManager.getInstance(Core.getInstance()).setRank(target, rank);
        sender.sendMessage(ChatColor.GREEN + "Set " + target.getName() + "'s rank to " + rank + ".");
        target.sendMessage(ChatColor.GREEN + "Your rank has been updated to " + rank + ".");

        // Update tablist immediately
        target.setPlayerListName(RankUtil.getColoredTabName(target));  // Assuming RankUtil updated to use RankManager

        return true;
    }

    private boolean isValidRank(String rank) {
        return rank.equals("default") || rank.equals("friend") || rank.equals("vip") ||
                rank.equals("moderator") || rank.equals("srmoderator") || rank.equals("admin") || rank.equals("developer");
    }
}