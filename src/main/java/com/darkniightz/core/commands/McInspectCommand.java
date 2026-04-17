package com.darkniightz.core.commands;

import com.darkniightz.core.permissions.PermissionConstants;
import com.darkniightz.core.system.McMMOIntegration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
 * Jebaited-owned wrapper for mcMMO {@code /inspect} (view another player's skills).
 */
public final class McInspectCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player player) {
            if (!player.hasPermission(PermissionConstants.CMD_MCINSPECT) && !player.hasPermission("mcmmo.commands.inspect")) {
                McMMOCommandMessages.sendNoPermission(sender);
                return true;
            }
        }
        if (!McMMOIntegration.isEnabled()) {
            McMMOCommandMessages.sendPrefixedLegacy(sender, "§7mcMMO is not loaded.");
            return true;
        }
        if (args.length < 1) {
            McMMOCommandMessages.sendPrefixedLegacy(sender, "§cUsage: /" + label + " <player>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target.getName() == null || target.getName().isBlank()) {
            McMMOCommandMessages.sendPrefixedLegacy(sender, "§cUnknown player.");
            return true;
        }

        McMMOCommandMessages.sendMcStatsBreakdown(sender, target);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player p) || args.length != 1) {
            return Collections.emptyList();
        }
        if (!p.hasPermission(PermissionConstants.CMD_MCINSPECT) && !p.hasPermission("mcmmo.commands.inspect")) {
            return Collections.emptyList();
        }
        String partial = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            String n = o.getName();
            if (n != null && n.toLowerCase(Locale.ROOT).startsWith(partial)) {
                out.add(n);
            }
        }
        return out;
    }
}
