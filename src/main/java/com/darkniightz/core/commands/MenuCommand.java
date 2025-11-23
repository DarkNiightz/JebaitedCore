package com.darkniightz.core.commands;

import com.darkniightz.core.hub.ServersMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class MenuCommand implements CommandExecutor {
    private final Plugin plugin;

    public MenuCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can open menus.");
            return true;
        }
        new ServersMenu(plugin).open(p);
        return true;
    }
}
