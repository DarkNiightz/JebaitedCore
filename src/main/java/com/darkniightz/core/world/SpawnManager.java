package com.darkniightz.core.world;

import com.darkniightz.main.JebaitedCore;
import com.darkniightz.main.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
public class SpawnManager {
    private final Plugin plugin;
    private final WorldManager worldManager;
    private final WorldConfigManager configManager;

    /** In-memory cache: world name (lower) → spawn location */
    private final Map<String, Location> spawnCache = new ConcurrentHashMap<>();

    public SpawnManager(Plugin plugin, WorldManager worldManager) {
        this(plugin, worldManager, new WorldConfigManager(plugin));
    }

    public SpawnManager(Plugin plugin, WorldManager worldManager, WorldConfigManager configManager) {
        this.plugin = plugin;
        this.worldManager = worldManager;
        this.configManager = configManager;
        loadSpawnsFromDb();
    }

    // ── DB helpers ────────────────────────────────────────────────────────────

    private static final String KEY_PREFIX = "spawn.";

    private DatabaseManager getDb() {
        return plugin instanceof JebaitedCore core ? core.getDatabaseManager() : null;
    }

    private void loadSpawnsFromDb() {
        DatabaseManager db = getDb();
        if (db == null) return;
        try (var conn = db.getConnection();
             var ps = conn.prepareStatement(
                     "SELECT setting_key, setting_value FROM server_settings WHERE setting_key LIKE 'spawn.%'");
             var rs = ps.executeQuery()) {
            while (rs.next()) {
                String key = rs.getString("setting_key");
                String value = rs.getString("setting_value");
                String worldName = key.substring(KEY_PREFIX.length());
                Location loc = decodeLocation(value);
                if (loc != null) {
                    spawnCache.put(worldName.toLowerCase(Locale.ROOT), loc);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[SpawnManager] DB load failed: " + e.getMessage());
        }
    }

    private void saveSpawnToDb(String worldName, Location location) {
        DatabaseManager db = getDb();
        if (db == null) return;
        String key = KEY_PREFIX + worldName.toLowerCase(Locale.ROOT);
        String value = encodeLocation(worldName, location);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (var conn = db.getConnection();
                 var ps = conn.prepareStatement(
                         "INSERT INTO server_settings (setting_key, setting_value, updated_at) " +
                         "VALUES (?, ?, ?) ON CONFLICT (setting_key) DO UPDATE " +
                         "SET setting_value = EXCLUDED.setting_value, updated_at = EXCLUDED.updated_at")) {
                ps.setString(1, key);
                ps.setString(2, value);
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[SpawnManager] DB save failed: " + e.getMessage());
            }
        });
    }

    private static String encodeLocation(String worldName, Location loc) {
        return worldName + ":" + loc.getX() + ":" + loc.getY() + ":" + loc.getZ()
                + ":" + loc.getYaw() + ":" + loc.getPitch();
    }

    private static Location decodeLocation(String encoded) {
        if (encoded == null) return null;
        String[] parts = encoded.split(":");
        if (parts.length < 7) return null;
        try {
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) return null;
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = Float.parseFloat(parts[4]);
            float pitch = Float.parseFloat(parts[5]);
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public Location getConfiguredSpawn() {
        return getSpawnForWorld(worldManager.getHubWorldName());
    }

    public void setConfiguredSpawn(Location location) {
        setSpawnForWorld(worldManager.getHubWorldName(), location);
    }

    public Location getSpawnForWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) return null;

        // 1. Memory cache (loaded from DB at startup)
        Location cached = spawnCache.get(worldName.toLowerCase(Locale.ROOT));
        if (cached != null) return cached;

        // 2. Legacy config.yml fallback
        Location fromConfig = configManager.getSpawnForWorld(worldName);
        if (fromConfig != null) {
            // Migrate config value into DB so Docker restarts don't lose it
            spawnCache.put(worldName.toLowerCase(Locale.ROOT), fromConfig);
            saveSpawnToDb(worldName, fromConfig);
            return fromConfig;
        }

        // 3. Bootstrap from vanilla world spawn
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
        if (worldName == null || worldName.isBlank()) return;
        if (location == null || location.getWorld() == null) return;

        spawnCache.put(worldName.toLowerCase(Locale.ROOT), location.clone());
        configManager.setSpawnForWorld(worldName, location); // keep config.yml in sync
        saveSpawnToDb(worldName, location);
    }

    public boolean teleportToSpawn(Player player) {
        Location spawn = getSpawnForWorld(player.getWorld().getName());
        if (spawn == null) {
            World world = player.getWorld();
            if (world != null) spawn = world.getSpawnLocation();
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


