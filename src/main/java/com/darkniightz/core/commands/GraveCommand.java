package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.system.GraveManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GraveCommand implements CommandExecutor {
    private final GraveManager graves;

    public GraveCommand(GraveManager graves) {
        this.graves = graves;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can use /" + label + "."));
            return true;
        }

        if ("graves".equalsIgnoreCase(label)) {
            List<GraveManager.Grave> list = graves.getAllForOwner(player.getUniqueId());
            if (list.isEmpty()) {
                player.sendMessage(Messages.prefixed("§7You have no active graves."));
                return true;
            }
            player.sendMessage(Messages.prefixed("§dActive graves: §f" + list.size()));
            int max = Math.min(5, list.size());
            for (int i = 0; i < max; i++) {
                var g = list.get(i);
                var l = g.location();
                long sec = Math.max(0L, (g.expiresAt() - System.currentTimeMillis()) / 1000L);
                player.sendMessage(Messages.prefixed("§8- §f" + l.getWorld().getName() + " §7" + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + " §8(" + sec + "s)"));
            }
            return true;
        }

        var latest = graves.getLatestForOwner(player.getUniqueId());
        if (latest.isEmpty()) {
            player.sendMessage(Messages.prefixed("§7You have no active graves."));
            return true;
        }

        var grave = latest.get();
        var l = grave.location();
        player.setCompassTarget(l);
        player.sendMessage(Messages.prefixed("§aTracking grave at §f" + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + "§a."));
        graves.trackOwnerToLatestGrave(player);
        return true;
    }
}
