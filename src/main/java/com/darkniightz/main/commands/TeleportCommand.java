package com.darkniightz.main.commands;

import com.darkniightz.main.Core;
import com.darkniightz.main.managers.LogManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TeleportCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("core.teleport")) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', Core.getInstance().getConfig().getString("teleport.permission-message", "&cNo permission!")));
            return true;
        }

        // Debug: Log to console and player for easy troubleshooting
        String argsStr = args.length > 0 ? String.join(", ", args) : "none";
        String debugMsg = "Debug TP: Label /" + label + ", Args [" + argsStr + "], Length " + args.length;
        Bukkit.getLogger().info(debugMsg);
        player.sendMessage(ChatColor.YELLOW + debugMsg);

        if (args.length == 1) {
            // TP to player - trim for extra spaces
            String targetName = args[0].trim();
            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Player not found: '" + targetName + "' (check if online/exact name).");
                return true;
            }
            player.teleport(target.getLocation());
            player.sendMessage(ChatColor.GREEN + "Teleported to " + target.getName() + ".");
            if (Core.getInstance().getConfig().getBoolean("logging.log-moderation", true)) {
                LogManager.getInstance(Core.getInstance()).log("TELEPORT: " + player.getName() + " to player " + target.getName());
            }
        } else if (args.length == 3) {
            // TP to coords
            try {
                double x = Double.parseDouble(args[0]);
                double y = Double.parseDouble(args[1]);
                double z = Double.parseDouble(args[2]);
                Location loc = new Location(player.getWorld(), x, y, z);
                if (y < 0 || y > player.getWorld().getMaxHeight()) {
                    loc = player.getWorld().getHighestBlockAt(loc).getLocation().add(0, 1, 0);
                    player.sendMessage(ChatColor.YELLOW + "Debug: Adjusted Y to safe height.");
                }
                player.teleport(loc);
                player.sendMessage(ChatColor.GREEN + "Teleported to " + x + ", " + y + ", " + z + ".");
                if (Core.getInstance().getConfig().getBoolean("logging.log-moderation", true)) {
                    LogManager.getInstance(Core.getInstance()).log("TELEPORT: " + player.getName() + " to coords " + x + "," + y + "," + z);
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid coordinates (must be numbers)! Args: " + argsStr);
            }
        } else {
            player.sendMessage(ChatColor.RED + "Usage: /tp <player> or /tp <x> <y> <z>");
        }
        return true;
    }
}