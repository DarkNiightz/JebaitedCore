package com.darkniightz.main;

import com.darkniightz.main.managers.LogManager;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CoreUnbanCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("core.staff")) {
            sender.sendMessage(ChatColor.RED + "No permission, sorry!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /unban <player>");
            return true;
        }

        String targetName = args[0];
        BanList banList = Bukkit.getBanList(BanList.Type.NAME);
        if (banList.isBanned(targetName)) {
            banList.pardon(targetName);
            sender.sendMessage(ChatColor.GREEN + "Unbanned " + targetName + ".");
            // Log
            if (Core.getInstance().getConfig().getBoolean("logging.log-moderation", true)) {
                LogManager.getInstance(Core.getInstance()).log("UNBAN: " + sender.getName() + " unbanned " + targetName);
            }
        } else {
            sender.sendMessage(ChatColor.RED + targetName + " isn't banned!");
        }

        return true;
    }
}