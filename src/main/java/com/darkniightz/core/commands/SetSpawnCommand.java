package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.world.SpawnManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class SetSpawnCommand implements CommandExecutor {
    private final Plugin plugin;
    private final SpawnManager spawnManager;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;

    public SetSpawnCommand(Plugin plugin, SpawnManager spawnManager, ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
        this.plugin = plugin;
        this.spawnManager = spawnManager;
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can set spawn."));
            return true;
        }

        var profile = profiles.getOrCreate(p, ranks.getDefaultGroup());
        boolean bypass = devMode != null && devMode.isActive(p.getUniqueId());
        if (!bypass && !ranks.isAtLeast(profile.getPrimaryRank(), "admin")) {
            sender.sendMessage(Messages.noPerm());
            return true;
        }

        String worldName = p.getWorld().getName();
        spawnManager.setSpawnForWorld(worldName, p.getLocation());
        p.sendMessage(Messages.prefixed(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("teleport.spawn.set-message", "&aSpawn set at your location!"))
            + " §7(" + worldName + ")"));
        return true;
    }
}
