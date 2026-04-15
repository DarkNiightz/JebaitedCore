package com.darkniightz.core.world;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class WorldManager {
    private final Plugin plugin;
    private final WorldConfigManager config;

    public WorldManager(Plugin plugin) {
        this(plugin, new WorldConfigManager(plugin));
    }

    public WorldManager(Plugin plugin, WorldConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public String getHubWorldName() {
        return config.getHubWorldName();
    }

    public String getSmpWorldName() {
        return config.getSmpWorldName();
    }

    public boolean isHub(Player player) {
        return player != null && player.getWorld() != null && isHub(player.getWorld());
    }

    public boolean isHub(World world) {
        return world != null && getHubWorldName().equalsIgnoreCase(world.getName());
    }

    public boolean isSmp(Player player) {
        return player != null && player.getWorld() != null && isSmp(player.getWorld());
    }

    public boolean isSmp(World world) {
        return world != null && getSmpWorldName().equalsIgnoreCase(world.getName());
    }

    public boolean isManaged(World world) {
        return isHub(world) || isSmp(world);
    }

    public World getSmpWorld() {
        return Bukkit.getWorld(getSmpWorldName());
    }

    public World ensureSmpWorldLoaded() {
        World existing = getSmpWorld();
        if (existing != null) {
            existing.setDifficulty(Difficulty.HARD);
            existing.setPVP(true);
            return existing;
        }
        boolean createIfMissing = config.shouldAutoCreateSmp();
        if (!createIfMissing) {
            plugin.getLogger().warning("SMP world '" + getSmpWorldName() + "' is not loaded and auto-create is disabled.");
            return null;
        }
        WorldCreator creator = new WorldCreator(getSmpWorldName());
        creator.environment(config.getSmpEnvironment());
        creator.generateStructures(config.shouldGenerateStructures());
        Long seed = config.getSmpSeed();
        if (seed != null) {
            creator.seed(seed);
        }
        World created = creator.createWorld();
        if (created == null) {
            plugin.getLogger().severe("Failed to create/load SMP world '" + getSmpWorldName() + "'.");
            return null;
        }
        created.setDifficulty(Difficulty.HARD);
        created.setPVP(true);
        plugin.getLogger().info("SMP world ready: " + created.getName());
        return created;
    }

    public boolean requireHub(Player player, DevModeManager devMode) {
        if (player == null) {
            return false;
        }
        boolean bypass = devMode != null && devMode.isActive(player.getUniqueId());
        if (!bypass && !isHub(player)) {
            player.sendMessage(Messages.hubOnly());
            return false;
        }
        return true;
    }
}
