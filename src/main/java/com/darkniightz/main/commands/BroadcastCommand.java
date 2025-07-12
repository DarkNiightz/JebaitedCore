package com.darkniightz.main.commands;

import com.darkniightz.main.Core;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class BroadcastCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("core.broadcast")) {
            sender.sendMessage(ChatColor.RED + "No permission!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /broadcast <message>");
            return true;
        }

        String message = String.join(" ", args);
        String prefix = Core.getInstance().getConfig().getString("messages.broadcast-prefix", "&e[Broadcast] &f");
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', prefix + message));

        return true;
    }
}