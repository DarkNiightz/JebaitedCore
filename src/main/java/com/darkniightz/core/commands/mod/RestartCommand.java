package com.darkniightz.core.commands.mod;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.system.RestartManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * /restart [<time>] [<reason...>] | /restart cancel | /restart status
 *
 * Time format: bare number = seconds, suffixed = 30s / 5m / 1h
 *
 * Permission: admin rank or dev bypass.
 * Can also be dispatched via RCON (Console sender bypasses rank check).
 */
public class RestartCommand implements CommandExecutor, TabCompleter {

    private final RestartManager restartManager;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;

    public RestartCommand(RestartManager restartManager, ProfileStore profiles,
                          RankManager ranks, DevModeManager devMode) {
        this.restartManager = restartManager;
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!hasPermission(sender)) {
            sender.sendMessage(Messages.noPerm());
            return true;
        }

        if (args.length == 0) {
            // Default: 5-minute countdown
            if (restartManager.isRestartPending()) {
                sender.sendMessage(Messages.prefixed("§eA restart is already scheduled (§f"
                        + formatSeconds(restartManager.getSecondsUntilRestart()) + " §eremaining). Use §f/restart cancel§e to abort."));
                return true;
            }
            long defaultSeconds = 300L; // 5 min
            restartManager.scheduleRestart(defaultSeconds, "Admin restart", sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("cancel")) {
            boolean cancelled = restartManager.cancelRestart(sender);
            if (!cancelled) {
                sender.sendMessage(Messages.prefixed("§eThere is no pending restart to cancel."));
            }
            return true;
        }

        if (sub.equals("status")) {
            if (restartManager.isRestartPending()) {
                sender.sendMessage(Messages.prefixed("§aRestart pending — §e"
                        + formatSeconds(restartManager.getSecondsUntilRestart()) + " §aremaining."));
            } else {
                sender.sendMessage(Messages.prefixed("§7No restart is currently scheduled."));
            }
            return true;
        }

        // Parse optional time as first argument, rest = reason
        long countdownSeconds = parseTime(sub);
        String reason;
        if (countdownSeconds > 0) {
            // First arg was a valid time; rest is reason
            reason = args.length > 1
                    ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                    : "Admin restart";
        } else {
            // Treat everything as reason, use default time
            countdownSeconds = 300;
            reason = String.join(" ", args);
        }

        if (restartManager.isRestartPending()) {
            sender.sendMessage(Messages.prefixed("§eA restart is already pending. Use §f/restart cancel§e first."));
            return true;
        }

        restartManager.scheduleRestart(countdownSeconds, reason, sender);
        return true;
    }

    // -------------------------------------------------------------------------
    // TabCompleter
    // -------------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        if (!hasPermission(sender)) return List.of();

        String prefix = args.length > 0 ? args[args.length - 1].toLowerCase(Locale.ROOT) : "";

        if (args.length == 1) {
            List<String> suggestions = Arrays.asList("cancel", "status", "30s", "1m", "5m", "10m", "15m", "30m", "1h");
            return suggestions.stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean hasPermission(CommandSender sender) {
        if (!(sender instanceof Player p)) return true; // Console / RCON always allowed
        boolean bypass = devMode != null && devMode.isActive(p.getUniqueId());
        if (bypass) return true;
        PlayerProfile actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
        return ranks.isAtLeast(actor.getPrimaryRank(), "admin");
    }

    /**
     * Parse a time string like "30s", "5m", "1h", or bare seconds.
     * Returns 0 if not parseable.
     */
    private static long parseTime(String s) {
        if (s == null || s.isBlank()) return 0;
        try {
            s = s.toLowerCase(Locale.ROOT);
            if (s.endsWith("h")) return Long.parseLong(s.substring(0, s.length() - 1)) * 3600;
            if (s.endsWith("m")) return Long.parseLong(s.substring(0, s.length() - 1)) * 60;
            if (s.endsWith("s")) return Long.parseLong(s.substring(0, s.length() - 1));
            long v = Long.parseLong(s);
            return v > 0 ? v : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String formatSeconds(long seconds) {
        if (seconds >= 3600) {
            long h = seconds / 3600;
            long m = (seconds % 3600) / 60;
            return h + "h" + (m > 0 ? m + "m" : "");
        } else if (seconds >= 60) {
            long m = seconds / 60;
            long s = seconds % 60;
            return m + "m" + (s > 0 ? s + "s" : "");
        } else {
            return seconds + "s";
        }
    }
}
