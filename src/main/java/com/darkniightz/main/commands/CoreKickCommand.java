package com.darkniightz.main.commands;

import com.darkniightz.main.Core;
import com.darkniightz.main.managers.LogManager;
import com.darkniightz.main.util.RankUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CoreKickCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("core.staff")) {
            sender.sendMessage(ChatColor.RED + "No permission, sorry!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /corekick <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not online!");
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

        target.kickPlayer("Kicked for chat issue by staff.");
        sender.sendMessage(ChatColor.GREEN + "Kicked " + target.getName() + ".");
        if (Core.getInstance().getConfig().getBoolean("logging.log-moderation", true)) {
            LogManager.getInstance(Core.getInstance()).log("KICKED: " + sender.getName() + " kicked " + target.getName());
        }
        return true;
    }
}