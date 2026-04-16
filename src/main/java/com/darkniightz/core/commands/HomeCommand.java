package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.system.HomesManager;
import com.darkniightz.core.system.TeleportWarmupManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

public class HomeCommand implements CommandExecutor {

    private final Plugin plugin;
    private final HomesManager homes;

    public HomeCommand(Plugin plugin, HomesManager homes) {
        this.plugin = plugin;
        this.homes = homes;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can use this command."));
            return true;
        }

        String homeName = args.length >= 1 ? args[0] : "home";
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Location home = homes.getHome(player.getUniqueId(), homeName);
            if (home == null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) player.sendMessage(Messages.prefixed("§cHome not found: §e" + homeName.toLowerCase()));
                });
                return;
            }
            long delay = Math.max(0L, Math.round(plugin.getConfig().getDouble("homes.teleport_delay_seconds", 0D) * 20D));
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (delay <= 0L) {
                    player.teleport(home);
                    player.sendMessage(Messages.prefixed("§aTeleported to home §e" + homeName.toLowerCase() + "§a."));
                    return;
                }
                player.sendMessage(Messages.prefixed("§7Teleporting to §e" + homeName.toLowerCase() + " §7in §f" + (delay / 20L) + "s§7..."));
                BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    TeleportWarmupManager.complete(player.getUniqueId());
                    if (!player.isOnline()) return;
                    player.teleport(home);
                    player.sendMessage(Messages.prefixed("§aTeleported to home §e" + homeName.toLowerCase() + "§a."));
                }, delay);
                TeleportWarmupManager.register(player.getUniqueId(), task);
            });
        });
        return true;
    }
}
