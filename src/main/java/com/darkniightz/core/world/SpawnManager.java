package com.darkniightz.core.world;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

public class SpawnManager {
    private final Plugin plugin;
    private final WorldManager worldManager;
    private final WorldConfigManager configManager;

    public SpawnManager(Plugin plugin, WorldManager worldManager) {
        this(plugin, worldManager, new WorldConfigManager(plugin));
    }

    public SpawnManager(Plugin plugin, WorldManager worldManager, WorldConfigManager configManager) {
        this.plugin = plugin;
        this.worldManager = worldManager;
        this.configManager = configManager;
    }

    public Location getConfiguredSpawn() {
        return getSpawnForWorld(worldManager.getHubWorldName());
    }

    public void setConfiguredSpawn(Location location) {
        setSpawnForWorld(worldManager.getHubWorldName(), location);
    }

    public Location getSpawnForWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        Location decoded = configManager.getSpawnForWorld(worldName);
        if (decoded != null) {
            return decoded;
        }

        // Bootstrap missing per-world spawn from vanilla world spawn and persist it.
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            Location fallback = world.getSpawnLocation();
            if (fallback != null) {
                setSpawnForWorld(worldName, fallback);
                return fallback;
            }
        }
        return null;
    }

    public void setSpawnForWorld(String worldName, Location location) {
        if (worldName == null || worldName.isBlank()) {
            return;
        }
        if (location == null || location.getWorld() == null) {
            return;
        }
        configManager.setSpawnForWorld(worldName, location);
    }

    public boolean teleportToSpawn(Player player) {
        Location spawn = getSpawnForWorld(player.getWorld().getName());
        if (spawn == null) {
            World world = player.getWorld();
            if (world != null && world.getSpawnLocation() != null) {
                spawn = world.getSpawnLocation();
            }
        }
        if (spawn == null) {
            player.sendMessage(color("&cNo spawn is configured for this world yet."));
            return false;
        }
        boolean moved = player.teleport(spawn);
        if (moved) {
            player.sendMessage(color(plugin.getConfig().getString("teleport.spawn.message", "&aTeleported to spawn!")));
        }
        return moved;
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

}

