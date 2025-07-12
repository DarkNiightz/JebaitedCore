package com.darkniightz.main.commands;

import com.darkniightz.main.Core;
import com.darkniightz.main.managers.SpawnManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this!");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("core.spawn")) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', Core.getInstance().getConfig().getString("spawn.no-permission", "&cNo permission!")));
            return true;
        }

        player.teleport(SpawnManager.getInstance().getSpawn());
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', Core.getInstance().getConfig().getString("spawn.message", "&aTeleported to spawn!")));

        return true;
    }
}