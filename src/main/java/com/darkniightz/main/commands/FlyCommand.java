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

public class FlyCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Player target;
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Specify a player!");
                return true;
            }
            target = (Player) sender;
            if (!sender.hasPermission("core.fly.self")) {
                sender.sendMessage(ChatColor.RED + "No permission!");
                return true;
            }
        } else {
            if (!sender.hasPermission("core.fly.others")) {
                sender.sendMessage(ChatColor.RED + "No permission to toggle for others!");
                return true;
            }
            target = Bukkit.getPlayer(args[0]);
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
        }

        boolean flying = !target.getAllowFlight();
        target.setAllowFlight(flying);
        target.setFlying(flying);  // Enable immediately if true
        String msg = flying ? ChatColor.GREEN + "Flight enabled" : ChatColor.RED + "Flight disabled";
        target.sendMessage(msg + " for you.");
        if (sender != target) {
            sender.sendMessage(msg + " for " + target.getName() + ".");
        }

        // Log if enabled
        if (Core.getInstance().getConfig().getBoolean("logging.log-moderation", true)) {
            LogManager.getInstance(Core.getInstance()).log("FLY: " + sender.getName() + " set fly " + flying + " for " + target.getName());
        }

        return true;
    }
}