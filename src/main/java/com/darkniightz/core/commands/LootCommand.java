package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.gui.LootPoolMenu;
import com.darkniightz.core.permissions.PermissionConstants;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public final class LootCommand implements CommandExecutor, TabCompleter {
    private final JebaitedCore plugin;

    public LootCommand(JebaitedCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can use this command."));
            return true;
        }
        if (!player.hasPermission(PermissionConstants.CMD_LOOT)) {
            player.sendMessage(Messages.noPerm());
            return true;
        }
        if (plugin.getEventModeManager() == null) {
            player.sendMessage(Messages.prefixed("§cEvent system unavailable right now."));
            return true;
        }
        int pending = plugin.getEventModeManager().getPendingHardcoreLootCount(player);
        if (pending <= 0) {
            player.sendMessage(Messages.prefixed("§7You have no pending hardcore loot to claim."));
            return true;
        }
        if (args.length > 0 && "claim".equalsIgnoreCase(args[0])) {
            int delivered = plugin.getEventModeManager().claimPendingHardcoreLoot(player);
            player.sendMessage(Messages.prefixed("§aClaimed §f" + delivered + "§a loot stack" + (delivered == 1 ? "" : "s") + "."));
            return true;
        }
        new LootPoolMenu(plugin, plugin.getEventModeManager()).open(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return List.of("claim");
        return Collections.emptyList();
    }
}
