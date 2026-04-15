package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.system.HomesManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class DelHomeCommand implements CommandExecutor {

    private final Plugin plugin;
    private final HomesManager homes;

    public DelHomeCommand(Plugin plugin, HomesManager homes) {
        this.plugin = plugin;
        this.homes = homes;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can use this command."));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " <name>"));
            return true;
        }

        String name = args[0].toLowerCase();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean deleted = homes.deleteHome(player.getUniqueId(), name);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (!deleted) {
                    player.sendMessage(Messages.prefixed("§cHome not found: §e" + name));
                } else {
                    player.sendMessage(Messages.prefixed("§aDeleted home: §e" + name));
                }
            });
        });
        return true;
    }
}
