package com.darkniightz.main.commands;

import com.darkniightz.main.Core;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class CoreCommand implements CommandExecutor {

    private final Core plugin;

    public CoreCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /core <reload|restart>");
            return true;
        }

        String subCmd = args[0].toLowerCase();

        if (subCmd.equals("reload")) {
            if (!sender.hasPermission("core.reload")) {
                sender.sendMessage(ChatColor.RED + "No permission!");
                return true;
            }
            plugin.onDisable();
            plugin.onEnable();
            sender.sendMessage(ChatColor.GREEN + "Core plugin reloaded successfully!");
            return true;
        } else if (subCmd.equals("restart")) {
            if (!sender.hasPermission("core.restart")) {
                sender.sendMessage(ChatColor.RED + "No permission!");
                return true;
            }
            sender.sendMessage(ChatColor.GREEN + "Restarting the server...");
            Bukkit.shutdown(); // Shuts down safely—use a wrapper script for auto-restart
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Invalid subcommand! Use reload or restart.");
        return true;
    }
}