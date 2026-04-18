package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.gui.ShopMenu;
import com.darkniightz.core.permissions.PermissionConstants;
import com.darkniightz.core.shop.ShopManager;
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

public class ShopCommand implements CommandExecutor, TabCompleter {

    private final JebaitedCore plugin;

    public ShopCommand(JebaitedCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can use this command."));
            return true;
        }
        if (!player.hasPermission(PermissionConstants.CMD_SHOP)) {
            player.sendMessage(Messages.noPerm());
            return true;
        }
        ShopManager shop = plugin.getShopManager();
        if (shop == null || !shop.isAvailable()) {
            player.sendMessage(Messages.prefixed("§cThe shop is unavailable right now."));
            return true;
        }
        if (!shop.canUseShop(player)) {
            return true;
        }
        new ShopMenu(plugin, shop, plugin.getEconomyManager(), plugin.getProfileStore(), plugin.getRankManager(),
                ShopManager.categoryIds().get(0), 0, null).open(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
