package com.darkniightz.core.commands;

import com.darkniightz.core.permissions.PermissionConstants;
import com.darkniightz.core.system.McMMOIntegration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Jebaited-owned wrapper for mcMMO leaderboards (database-backed, same ordering as mcMMO /mctop).
 */
public final class McTopCommand implements CommandExecutor, TabCompleter {

    private static final int DEFAULT_PER_PAGE = 10;
    private static final int MAX_PER_PAGE = 20;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player player) {
            if (!player.hasPermission(PermissionConstants.CMD_MCTOP) && !player.hasPermission("mcmmo.commands.mctop")) {
                McMMOCommandMessages.sendNoPermission(sender);
                return true;
            }
        }
        if (!McMMOIntegration.isEnabled()) {
            McMMOCommandMessages.sendPrefixedLegacy(sender, "§7mcMMO is not loaded.");
            return true;
        }

        Parsed p = parseArgs(args);
        if (p.skillToken() != null && McMMOIntegration.matchPrimarySkill(p.skillToken()) == null) {
            McMMOCommandMessages.sendPrefixedLegacy(sender,
                    "§cUnknown skill §f" + p.skillToken() + "§c. Try §f/" + label + " [skill] [page]§c.");
            return true;
        }

        int per = Math.min(MAX_PER_PAGE, DEFAULT_PER_PAGE);
        List<McMMOIntegration.LeaderboardRow> rows = McMMOIntegration.readLeaderboardPage(p.skillToken(), p.page(), per);
        String skillLabel = p.skillToken() == null || "all".equalsIgnoreCase(p.skillToken())
                ? "Power Level"
                : McMMOCommandMessages.formatSkillEnumName(p.skillToken());

        McMMOCommandMessages.sendPrefixedLegacy(sender, "§6mcMMO Top §7— §f" + skillLabel + " §7(page §f" + p.page() + "§7)");
        if (rows.isEmpty()) {
            McMMOCommandMessages.sendPrefixedLegacy(sender, "§7No entries (yet).");
            return true;
        }
        int rank = 1 + (p.page() - 1) * per;
        for (McMMOIntegration.LeaderboardRow row : rows) {
            McMMOCommandMessages.sendPrefixedLegacy(sender,
                    "§8#" + rank + " §e" + row.playerName() + " §7— §a" + row.value());
            rank++;
        }
        return true;
    }

    private record Parsed(@Nullable String skillToken, int page) {
    }

    private static Parsed parseArgs(String[] args) {
        if (args.length == 0) {
            return new Parsed(null, 1);
        }
        if (args.length == 1) {
            if (isPositiveInt(args[0])) {
                return new Parsed(null, Integer.parseInt(args[0]));
            }
            return new Parsed(args[0], 1);
        }
        int page = isPositiveInt(args[1]) ? Integer.parseInt(args[1]) : 1;
        return new Parsed(args[0], page);
    }

    private static boolean isPositiveInt(String s) {
        try {
            int n = Integer.parseInt(s);
            return n > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            return Collections.emptyList();
        }
        if (!p.hasPermission(PermissionConstants.CMD_MCTOP) && !p.hasPermission("mcmmo.commands.mctop")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            List<String> skills = new ArrayList<>(McMMOIntegration.primarySkillEnumNames());
            skills.add(0, "all");
            List<String> out = new ArrayList<>();
            for (String s : skills) {
                if (s.toLowerCase(Locale.ROOT).startsWith(partial)) {
                    out.add(s);
                }
            }
            return out;
        }
        return Collections.emptyList();
    }
}
