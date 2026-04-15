package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class WorldStatusCommand implements CommandExecutor {
    private final WorldManager worldManager;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;

    public WorldStatusCommand(WorldManager worldManager, ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
        this.worldManager = worldManager;
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player player) {
            var profile = profiles.getOrCreate(player, ranks.getDefaultGroup());
            boolean bypass = devMode != null && devMode.isActive(player.getUniqueId());
            if (!bypass && !ranks.isAtLeast(profile.getPrimaryRank(), "admin")) {
                sender.sendMessage(Messages.noPerm());
                return true;
            }
        }

        World hub = Bukkit.getWorld(worldManager.getHubWorldName());
        World smp = Bukkit.getWorld(worldManager.getSmpWorldName());
        String eventName = "events";
        World event = Bukkit.getWorld(eventName);

        sender.sendMessage(Messages.prefixed("§6§lWorld Support Status"));
        sender.sendMessage(Messages.prefixed("§7Hub: §f" + worldManager.getHubWorldName() + " §8(" + (hub != null ? "loaded" : "missing") + ")"));
        sender.sendMessage(Messages.prefixed("§7SMP: §f" + worldManager.getSmpWorldName() + " §8(" + (smp != null ? "loaded" : "missing") + ")"));
        sender.sendMessage(Messages.prefixed("§7Events: §f" + eventName + " §8(" + (event != null ? "loaded" : "missing") + ")"));
        if (sender instanceof Player p) {
            sender.sendMessage(Messages.prefixed("§7You are in: §f" + p.getWorld().getName()));
        }
        sender.sendMessage(Messages.prefixed("§8Use /hub, /smp, and /event tp for world routing."));
        return true;
    }
}
