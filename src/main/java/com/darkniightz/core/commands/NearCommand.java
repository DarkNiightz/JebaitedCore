package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class NearCommand implements CommandExecutor {

    private final Plugin plugin;

    public NearCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can use this command."));
            return true;
        }

        if (plugin instanceof JebaitedCore core) {
            PlayerProfile profile = core.getProfileStore().getOrCreate(player, core.getRankManager().getDefaultGroup());
            String minRank = plugin.getConfig().getString("near.min_rank", "diamond");
            String actorRank = profile == null || profile.getPrimaryRank() == null ? core.getRankManager().getDefaultGroup() : profile.getPrimaryRank();
            String donorRank = profile == null ? null : profile.getDonorRank();
            boolean hasAccess = core.getRankManager().isAtLeast(actorRank, minRank)
                    || (donorRank != null && core.getRankManager().isAtLeast(donorRank, minRank));
            if (!hasAccess) {
                player.sendMessage(Messages.prefixed("Unknown command. Type \"/help\" for help."));
                return true;
            }
        }

        double radius = plugin.getConfig().getDouble("near.radius", 120D);
        if (args.length >= 1) {
            try {
                radius = Math.max(1D, Double.parseDouble(args[0]));
            } catch (NumberFormatException ignored) {
                player.sendMessage(Messages.prefixed("§cRadius must be a number."));
                return true;
            }
        }

        List<Player> nearby = new ArrayList<>();
        for (Player online : player.getWorld().getPlayers()) {
            if (online.getUniqueId().equals(player.getUniqueId())) continue;
            if (online.getLocation().distanceSquared(player.getLocation()) <= radius * radius) {
                nearby.add(online);
            }
        }

        nearby.sort(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(player.getLocation())));

        if (nearby.isEmpty()) {
            player.sendMessage(Messages.prefixed("§7No players nearby within §f" + Math.round(radius) + "m§7."));
            return true;
        }

        player.sendMessage(Messages.prefixed("§6Nearby players §7(within " + Math.round(radius) + "m):"));
        for (Player target : nearby) {
            double dist = Math.sqrt(target.getLocation().distanceSquared(player.getLocation()));
            player.sendMessage(Messages.prefixed("§7- §e" + target.getName() + " §8(" + Math.round(dist) + "m)"));
        }
        return true;
    }
}
