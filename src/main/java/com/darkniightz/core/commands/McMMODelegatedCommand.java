package com.darkniightz.core.commands;

import com.darkniightz.core.permissions.PermissionConstants;
import com.darkniightz.core.system.McMMOIntegration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Forwards to mcMMO's own {@link PluginCommand} so we own the root label (eviction) without reimplementing logic.
 */
public final class McMMODelegatedCommand implements CommandExecutor, TabCompleter {

    private final String mcMMOPluginCommandName;

    public McMMODelegatedCommand(String mcMMOPluginCommandName) {
        this.mcMMOPluginCommandName = mcMMOPluginCommandName;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!McMMOIntegration.isEnabled()) {
            McMMOCommandMessages.sendPrefixedLegacy(sender, "§7mcMMO is not loaded.");
            return true;
        }
        if (sender instanceof Player player) {
            if (!hasPlayerPermission(player)) {
                McMMOCommandMessages.sendNoPermission(sender);
                return true;
            }
        }
        PluginCommand inner = resolveMcmmoCommand();
        if (inner == null) {
            McMMOCommandMessages.sendPrefixedLegacy(sender, "§7That mcMMO command is unavailable.");
            return true;
        }
        return inner.execute(sender, label, args);
    }

    private boolean hasPlayerPermission(Player player) {
        return switch (mcMMOPluginCommandName) {
            case "mcability" ->
                    player.hasPermission(PermissionConstants.CMD_MCABILITY)
                            || player.hasPermission("mcmmo.commands.mcability");
            case "mccooldown" ->
                    player.hasPermission(PermissionConstants.CMD_MCCOOLDOWN)
                            || player.hasPermission("mcmmo.commands.mccooldown");
            case "ptp" ->
                    player.hasPermission(PermissionConstants.CMD_PTP) || player.hasPermission("mcmmo.commands.ptp");
            default -> true;
        };
    }

    private @Nullable PluginCommand resolveMcmmoCommand() {
        Plugin pl = Bukkit.getPluginManager().getPlugin("mcMMO");
        if (!(pl instanceof JavaPlugin jp)) {
            return null;
        }
        return jp.getCommand(mcMMOPluginCommandName);
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        PluginCommand inner = resolveMcmmoCommand();
        if (inner == null) {
            return Collections.emptyList();
        }
        TabCompleter tc = inner.getTabCompleter();
        if (tc == null) {
            return Collections.emptyList();
        }
        List<String> list = tc.onTabComplete(sender, inner, alias, args);
        return list != null ? list : Collections.emptyList();
    }
}
