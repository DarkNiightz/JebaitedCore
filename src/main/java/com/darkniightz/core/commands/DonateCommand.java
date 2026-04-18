package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.gui.DonateMenu;
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

public final class DonateCommand implements CommandExecutor, TabCompleter {

    private final JebaitedCore plugin;

    public DonateCommand(JebaitedCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can use this command."));
            return true;
        }
        if (!player.hasPermission(PermissionConstants.CMD_DONATE)) {
            player.sendMessage(Messages.noPerm());
            return true;
        }
        if (plugin.getStoreService() == null || !plugin.getStoreService().isStoreEnabled()) {
            player.sendMessage(Messages.prefixed("§cThe store is not available right now."));
            return true;
        }
        new DonateMenu(plugin, plugin.getStoreService()).open(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
