package com.darkniightz.main.commands;

import com.darkniightz.main.Core;
import com.darkniightz.main.managers.LogManager;
import com.darkniightz.main.util.RankUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GamemodeCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /gamemode <mode> [player]");
            return true;
        }

        GameMode mode = parseGameMode(args[0]);
        if (mode == null) {
            sender.sendMessage(ChatColor.RED + "Invalid mode! Use survival/creative/adventure/spectator or 0/1/2/3.");
            return true;
        }

        Player target;
        if (args.length == 1) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Specify a player!");
                return true;
            }
            target = (Player) sender;
            if (!sender.hasPermission("core.gamemode.self")) {
                sender.sendMessage(ChatColor.RED + "No permission!");
                return true;
            }
        } else {
            if (!sender.hasPermission("core.gamemode.others")) {
                sender.sendMessage(ChatColor.RED + "No permission to change for others!");
                return true;
            }
            target = Bukkit.getPlayer(args[1]);
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

        target.setGameMode(mode);
        target.sendMessage(ChatColor.GREEN + "Gamemode set to " + mode.name().toLowerCase() + ".");
        if (sender != target) {
            sender.sendMessage(ChatColor.GREEN + "Set " + target.getName() + "'s gamemode to " + mode.name().toLowerCase() + ".");
        }

        // Log if enabled
        if (Core.getInstance().getConfig().getBoolean("logging.log-moderation", true)) {
            LogManager.getInstance(Core.getInstance()).log("GAMEMODE: " + sender.getName() + " set " + target.getName() + " to " + mode.name());
        }

        return true;
    }

    private GameMode parseGameMode(String input) {
        return switch (input.toLowerCase()) {
            case "survival", "0" -> GameMode.SURVIVAL;
            case "creative", "1" -> GameMode.CREATIVE;
            case "adventure", "2" -> GameMode.ADVENTURE;
            case "spectator", "3" -> GameMode.SPECTATOR;
            default -> null;
        };
    }
}