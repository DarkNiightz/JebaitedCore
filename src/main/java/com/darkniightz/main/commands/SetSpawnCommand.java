package com.darkniightz.main.commands;

import com.darkniightz.main.Core;
import com.darkniightz.main.managers.SpawnManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetSpawnCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this!");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("core.setspawn")) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', Core.getInstance().getConfig().getString("spawn.no-permission", "&cNo permission!")));
            return true;
        }

        SpawnManager.getInstance().setSpawn(player.getLocation());
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', Core.getInstance().getConfig().getString("spawn.set-message", "&aSpawn set at your location!")));

        return true;
    }
}