package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.permissions.PermissionConstants;
import com.darkniightz.core.system.CombatTagManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Shows whether the player is combat-tagged and remaining time.
 */
public final class CombatLogsCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;

    public CombatLogsCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can use this command."));
            return true;
        }
        if (!player.hasPermission(PermissionConstants.CMD_COMBATLOG)) {
            player.sendMessage(Messages.noPerm());
            return true;
        }
        if (!(plugin instanceof JebaitedCore core)) {
            return true;
        }
        CombatTagManager tags = core.getCombatTagManager();
        if (tags == null) {
            player.sendMessage(Messages.prefixed("§cCombat tag is unavailable."));
            return true;
        }
        if (!tags.isTagged(player)) {
            player.sendMessage(Messages.prefixed("§aYou are §fnot§a combat tagged."));
            return true;
        }
        long sec = (tags.remainingMillis(player) + 999L) / 1000L;
        player.sendMessage(Messages.prefixed("§cCombat tagged §8— §f" + sec + "s §7remaining."));
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        return List.of();
    }
}
