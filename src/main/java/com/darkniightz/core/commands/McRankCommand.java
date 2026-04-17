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
 * Jebaited-owned wrapper for mcMMO {@code /mcrank} (leaderboard ranks per skill + overall).
 */
public final class McRankCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player player) {
            if (!player.hasPermission(PermissionConstants.CMD_MCRANK) && !player.hasPermission("mcmmo.commands.mcrank")) {
                McMMOCommandMessages.sendNoPermission(sender);
                return true;
            }
        }
        if (!McMMOIntegration.isEnabled()) {
            McMMOCommandMessages.sendPrefixedLegacy(sender, "§7mcMMO is not loaded.");
            return true;
        }

        OfflinePlayer target;
        if (args.length == 0) {
            if (!(sender instanceof Player self)) {
                McMMOCommandMessages.sendPrefixedLegacy(sender, "§cUsage: /" + label + " <player>");
                return true;
            }
            target = self;
        } else {
            target = Bukkit.getOfflinePlayer(args[0]);
        }

        if (target.getName() == null || target.getName().isBlank()) {
            McMMOCommandMessages.sendPrefixedLegacy(sender, "§cUnknown player.");
            return true;
        }

        java.util.UUID uuid = target.getUniqueId();
        Integer overall = McMMOIntegration.getOverallRank(uuid);
        McMMOCommandMessages.sendPrefixedLegacy(sender,
                "§6mcMMO ranks §7— §f" + target.getName()
                        + (overall != null && overall > 0 ? " §7| §ePower #" + overall : " §7| §ePower §7—"));

        List<String> skills = McMMOIntegration.primarySkillEnumNames();
        if (skills.isEmpty()) {
            McMMOCommandMessages.sendPrefixedLegacy(sender, "§7(Skill ranks unavailable.)");
            return true;
        }

        StringBuilder line = new StringBuilder();
        for (String skill : skills) {
            Integer r = McMMOIntegration.getSkillRank(uuid, skill);
            String rankStr = (r == null || r <= 0) ? "§7—" : "§e#" + r;
            if (!line.isEmpty()) {
                line.append("§8 | §r");
            }
            line.append("§a").append(McMMOCommandMessages.formatSkillEnumName(skill)).append(" ").append(rankStr);
        }
        McMMOCommandMessages.sendPrefixedLegacy(sender, line.toString());
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player p) || args.length != 1) {
            return Collections.emptyList();
        }
        if (!p.hasPermission(PermissionConstants.CMD_MCRANK) && !p.hasPermission("mcmmo.commands.mcrank")) {
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
