package com.darkniightz.main.commands;

import com.darkniightz.main.Core;
import com.darkniightz.main.managers.LogManager;
import com.darkniightz.main.util.RankUtil;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CoreBanCommand implements CommandExecutor {

    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([smhd])");

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("core.staff")) {
            sender.sendMessage(ChatColor.RED + "No permission, sorry!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /coreban <player> [time] [reason...]");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not online! (Bans offline too, but confirm name)");
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

        Duration duration = null;
        String reason = "Banned for chat violation.";
        int argIndex = 1;

        // Parse optional time
        if (args.length > 1) {
            Matcher matcher = TIME_PATTERN.matcher(args[1]);
            if (matcher.matches()) {
                long amount = Long.parseLong(matcher.group(1));
                String unit = matcher.group(2);
                duration = switch (unit) {
                    case "s" -> Duration.ofSeconds(amount);
                    case "m" -> Duration.ofMinutes(amount);
                    case "h" -> Duration.ofHours(amount);
                    case "d" -> Duration.ofDays(amount);
                    default -> null;
                };
                if (duration != null) {
                    argIndex = 2;
                }
            }
        }

        // Parse reason
        if (args.length > argIndex) {
            reason = String.join(" ", java.util.Arrays.copyOfRange(args, argIndex, args.length));
        }

        // Ban (use Duration for temp)
        BanList banList = Bukkit.getBanList(BanList.Type.NAME);
        banList.addBan(target.getName(), reason, duration, sender.getName());

        target.kickPlayer(Core.getInstance().getConfig().getString("messages.banned", "Banned by staff.") + " Reason: " + reason);
        sender.sendMessage(ChatColor.GREEN + "Banned " + target.getName() + (duration != null ? " for " + duration.toString() : " permanently") + ". Reason: " + reason);

        // Log
        if (Core.getInstance().getConfig().getBoolean("logging.log-moderation", true)) {
            LogManager.getInstance(Core.getInstance()).log("BAN: " + sender.getName() + " banned " + target.getName() + " (temp: " + (duration != null) + ") reason: " + reason);
        }

        return true;
    }
}