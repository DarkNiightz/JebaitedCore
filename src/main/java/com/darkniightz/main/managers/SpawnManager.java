package com.darkniightz.main.managers;

import com.darkniightz.main.Core;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

public class SpawnManager {

    private static SpawnManager instance;
    private Location spawnLocation;

    private SpawnManager() {
        loadSpawn();
    }

    public static SpawnManager getInstance() {
        if (instance == null) {
            instance = new SpawnManager();
        }
        return instance;
    }

    private void loadSpawn() {
        FileConfiguration config = Core.getInstance().getConfig();
        String locStr = config.getString("spawn.location");
        if (locStr != null) {
            String[] parts = locStr.split(":");
            if (parts.length == 6) {
                World world = Core.getInstance().getServer().getWorld(parts[0]);
                if (world != null) {
                    double x = Double.parseDouble(parts[1]);
                    double y = Double.parseDouble(parts[2]);
                    double z = Double.parseDouble(parts[3]);
                    float yaw = Float.parseFloat(parts[4]);
                    float pitch = Float.parseFloat(parts[5]);
                    spawnLocation = new Location(world, x, y, z, yaw, pitch);
                }
            }
        }
        // Fallback to world spawn if not set
        if (spawnLocation == null) {
            spawnLocation = Core.getInstance().getServer().getWorlds().get(0).getSpawnLocation();
        }
    }

    public void setSpawn(Location loc) {
        spawnLocation = loc;
        String locStr = loc.getWorld().getName() + ":" + loc.getX() + ":" + loc.getY() + ":" + loc.getZ() + ":" + loc.getYaw() + ":" + loc.getPitch();
        Core.getInstance().getConfig().set("spawn.location", locStr);
        Core.getInstance().saveConfig();
    }

    public Location getSpawn() {
        return spawnLocation;
    }
}