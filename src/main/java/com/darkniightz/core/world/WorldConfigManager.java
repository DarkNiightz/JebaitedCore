package com.darkniightz.core.world;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

public class WorldConfigManager {
    private final Plugin plugin;

    public WorldConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public String getHubWorldName() {
        return plugin.getConfig().getString("worlds.hub", "world");
    }

    public String getSmpWorldName() {
        return plugin.getConfig().getString("worlds.smp", "smp");
    }

    public boolean shouldRouteJoinToHub() {
        return plugin.getConfig().getBoolean("worlds.route_join_to_hub", true);
    }

    public boolean shouldAutoCreateSmp() {
        return plugin.getConfig().getBoolean("worlds.smp_settings.create_if_missing", true);
    }

    public World.Environment getSmpEnvironment() {
        String envRaw = plugin.getConfig().getString("worlds.smp_settings.environment", "NORMAL");
        try {
            return World.Environment.valueOf(envRaw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid worlds.smp_settings.environment='" + envRaw + "'. Using NORMAL.");
            return World.Environment.NORMAL;
        }
    }

    public boolean shouldGenerateStructures() {
        return plugin.getConfig().getBoolean("worlds.smp_settings.generate_structures", true);
    }

    public Long getSmpSeed() {
        if (!plugin.getConfig().isSet("worlds.smp_settings.seed")) {
            return null;
        }
        return plugin.getConfig().getLong("worlds.smp_settings.seed");
    }

    public Location getSpawnForWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        FileConfiguration cfg = plugin.getConfig();
        String path = pathForWorld(worldName);
        String raw = cfg.getString(path, null);
        if ((raw == null || raw.isBlank()) && getHubWorldName().equalsIgnoreCase(worldName)) {
            raw = cfg.getString("teleport.spawn.location", null);
            if (raw != null && !raw.isBlank()) {
                cfg.set(path, raw);
                cfg.set("teleport.spawn.location", null);
                plugin.saveConfig();
            }
        }
        return decode(raw);
    }

    public void setSpawnForWorld(String worldName, Location location) {
        if (worldName == null || worldName.isBlank() || location == null || location.getWorld() == null) {
            return;
        }
        String encoded = String.format(Locale.ROOT, "%s:%.3f:%.3f:%.3f:%.2f:%.2f",
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch());
        plugin.getConfig().set(pathForWorld(worldName), encoded);
        plugin.getConfig().set("teleport.spawn.location", null);
        plugin.saveConfig();
    }

    private String pathForWorld(String worldName) {
        return "teleport.spawn.worlds." + worldName.toLowerCase(Locale.ROOT);
    }

    private Location decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(":");
        if (parts.length < 6) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = Float.parseFloat(parts[4]);
            float pitch = Float.parseFloat(parts[5]);
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException ex) {
            plugin.getLogger().warning("Invalid spawn location in config: " + raw);
            return null;
        }
    }
}
