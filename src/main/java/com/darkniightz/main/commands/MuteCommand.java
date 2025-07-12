package com.darkniightz.main.commands;

import com.darkniightz.main.Core;
import com.darkniightz.main.managers.LogManager;
import com.darkniightz.main.managers.MuteManager;
import com.darkniightz.main.util.RankUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;


import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MuteCommand implements CommandExecutor {

    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([smhd])");  // e.g., 30m, 1h, 2d, 10s

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String cmdName = cmd.getName().toLowerCase();

        if (!sender.hasPermission("core.staff")) {
            sender.sendMessage(ChatColor.RED + "No permission, sorry!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " <player> [time] [reason...]");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
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

        Instant expiry = null;
        String reason = "Muted by staff.";
        int argIndex = 1;

        // Parse optional time
        if (args.length > 1) {
            Matcher matcher = TIME_PATTERN.matcher(args[1]);
            if (matcher.matches()) {
                long amount = Long.parseLong(matcher.group(1));
                String unit = matcher.group(2);
                Duration duration = switch (unit) {
                    case "s" -> Duration.ofSeconds(amount);
                    case "m" -> Duration.ofMinutes(amount);
                    case "h" -> Duration.ofHours(amount);
                    case "d" -> Duration.ofDays(amount);
                    default -> null;
                };
                if (duration != null) {
                    expiry = Instant.now().plus(duration);
                    argIndex = 2;
                }
            }
        }

        // Parse optional reason (rest of args)
        if (args.length > argIndex) {
            reason = String.join(" ", java.util.Arrays.copyOfRange(args, argIndex, args.length));
        }

        if (cmdName.equals("mute")) {
            MuteManager.getInstance().mutePlayer(target.getUniqueId(), expiry);
            String msg = ChatColor.GREEN + "Muted " + target.getName();
            if (expiry != null) {
                msg += " until " + expiry.toString();
            }
            msg += " for: " + reason;
            sender.sendMessage(msg);

            target.sendMessage(Core.getInstance().getConfig().getString("messages.muted", "&cYou've been muted!") + " Reason: " + reason);

            // Schedule auto-unmute if temp
            if (expiry != null) {
                long delayTicks = Duration.between(Instant.now(), expiry).getSeconds() * 20;  // Seconds to ticks
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (MuteManager.getInstance().isMuted(target.getUniqueId())) {
                            MuteManager.getInstance().unmutePlayer(target.getUniqueId());
                            target.sendMessage(Core.getInstance().getConfig().getString("messages.unmuted", "&aYou've been unmuted!"));
                        }
                    }
                }.runTaskLater(Core.getInstance(), delayTicks);
            }

            // Log if enabled
            if (Core.getInstance().getConfig().getBoolean("logging.log-moderation", true)) {
                LogManager.getInstance(Core.getInstance()).log("MUTE: " + sender.getName() + " muted " + target.getName() + " (temp: " + (expiry != null) + ") reason: " + reason);
            }
        } else if (cmdName.equals("unmute")) {
            if (!MuteManager.getInstance().isMuted(target.getUniqueId())) {
                sender.sendMessage(ChatColor.RED + target.getName() + " isn't muted!");
                return true;
            }
            MuteManager.getInstance().unmutePlayer(target.getUniqueId());
            MuteManager.getInstance().logAllMuted();
            sender.sendMessage(ChatColor.GREEN + "Unmuted " + target.getName() + ".");
            target.sendMessage(Core.getInstance().getConfig().getString("messages.unmuted", "&aYou've been unmuted!"));

            // New: Debug log to confirm removal
            if (Core.getInstance().getConfig().getBoolean("logging.log-moderation", true)) {
                LogManager.getInstance(Core.getInstance()).log("UNMUTE: " + sender.getName() + " unmuted " + target.getName());
            }
            // Force check after unmute
            if (MuteManager.getInstance().isMuted(target.getUniqueId())) {
                sender.sendMessage(ChatColor.RED + "Debug: Unmute failed—still muted! Check console.");
            } else {
                sender.sendMessage(ChatColor.GREEN + "Debug: Unmute successful.");
            }
        }

        return true;
    }
}