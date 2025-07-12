package com.darkniightz.main.commands;

import com.darkniightz.main.Core;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StaffChatCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("core.staff")) {
            sender.sendMessage(ChatColor.RED + "No permission!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /sc <message>");
            return true;
        }

        String message = String.join(" ", args);
        String prefix = Core.getInstance().getConfig().getString("messages.staff-chat-prefix", "&b[Staff] &f");
        String fullMsg = ChatColor.translateAlternateColorCodes('&', prefix) + sender.getName() + ": " + message;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("core.staff")) {
                player.sendMessage(fullMsg);
            }
        }

        return true;
    }
}