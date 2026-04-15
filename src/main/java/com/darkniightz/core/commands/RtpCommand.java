package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.world.WorldManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

public class RtpCommand implements CommandExecutor {

    private final Plugin plugin;
    private final WorldManager worldManager;

    public RtpCommand(Plugin plugin, WorldManager worldManager) {
        this.plugin = plugin;
        this.worldManager = worldManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can use this command."));
            return true;
        }

        if (plugin.getConfig().getBoolean("rtp.smp_only", true) && !worldManager.isSmp(player)) {
            player.sendMessage(Messages.prefixed("§c/rtp can only be used in SMP."));
            return true;
        }

        World world = player.getWorld();
        int min = plugin.getConfig().getInt("rtp.min_radius", 300);
        int max = Math.max(min + 1, plugin.getConfig().getInt("rtp.max_radius", 2500));
        int tries = Math.max(1, plugin.getConfig().getInt("rtp.max_attempts", 16));

        Location found = null;
        for (int i = 0; i < tries; i++) {
            int x = randomCoord(min, max);
            int z = randomCoord(min, max);
            int y = world.getHighestBlockYAt(x, z) + 1;
            if (y <= world.getMinHeight() + 1) continue;

            Location candidate = new Location(world, x + 0.5, y, z + 0.5);
            if (candidate.getBlock().isLiquid()) continue;
            if (candidate.clone().subtract(0, 1, 0).getBlock().isLiquid()) continue;
            found = candidate;
            break;
        }

        if (found == null) {
            player.sendMessage(Messages.prefixed("§cCould not find a safe random location. Try again."));
            return true;
        }

        player.teleport(found);
        player.sendMessage(Messages.prefixed("§aRandomly teleported to §f" + Math.round(found.getX()) + "§7, §f" + Math.round(found.getY()) + "§7, §f" + Math.round(found.getZ())));
        return true;
    }

    private int randomCoord(int minAbs, int maxAbs) {
        int value = ThreadLocalRandom.current().nextInt(minAbs, maxAbs + 1);
        return ThreadLocalRandom.current().nextBoolean() ? value : -value;
    }
}
