package com.darkniightz.core.system;

import com.darkniightz.main.JebaitedCore;
import com.darkniightz.main.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class WarpsManager {

    private final Plugin plugin;
    private final File warpsFile;
    private FileConfiguration warpsConfig;

    public WarpsManager(Plugin plugin) {
        this.plugin = plugin;
        this.warpsFile = new File(plugin.getDataFolder(), "warps.yml");
        reload();
    }

    public synchronized void reload() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        if (!warpsFile.exists()) {
            try {
                warpsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create warps.yml: " + e.getMessage());
            }
        }
        warpsConfig = YamlConfiguration.loadConfiguration(warpsFile);
        if (isDatabaseAvailable()) {
            initDatabase();
            migrateYamlToDatabase();
        }
    }

    public synchronized void setWarp(String name, Location location, double cost) {
        if (location == null || location.getWorld() == null) return;
        String normalized = normalizeName(name);
        if (normalized == null) return;

        if (isDatabaseAvailable()) {
            upsertWarpInDatabase(normalized, location, cost);
        }

        String base = "warps." + normalized;
        warpsConfig.set(base + ".location", encode(location));
        warpsConfig.set(base + ".cost", Math.max(0D, round(cost)));
        save();
    }

    public synchronized boolean deleteWarp(String name) {
        String normalized = normalizeName(name);
        if (normalized == null) return false;

        boolean removed = false;
        if (isDatabaseAvailable()) {
            removed = deleteWarpFromDatabase(normalized) || removed;
        }

        String base = "warps." + normalized;
        if (warpsConfig.isConfigurationSection(base)) {
            warpsConfig.set(base, null);
            save();
            removed = true;
        }
        return removed;
    }

    public synchronized boolean hasWarp(String name) {
        String normalized = normalizeName(name);
        if (normalized == null) return false;
        if (isDatabaseAvailable() && getWarpFromDatabase(normalized) != null) {
            return true;
        }
        return warpsConfig.isConfigurationSection("warps." + normalized);
    }

    public synchronized Location getWarp(String name) {
        String normalized = normalizeName(name);
        if (normalized == null) return null;

        if (isDatabaseAvailable()) {
            Location fromDb = getWarpFromDatabase(normalized);
            if (fromDb != null) {
                return fromDb;
            }
        }

        String encoded = warpsConfig.getString("warps." + normalized + ".location");
        return decode(encoded);
    }

    public synchronized double getCost(String name) {
        String normalized = normalizeName(name);
        if (normalized == null) return 0D;

        if (isDatabaseAvailable()) {
            Double cost = getWarpCostFromDatabase(normalized);
            if (cost != null) {
                return cost;
            }
        }

        return Math.max(0D, warpsConfig.getDouble("warps." + normalized + ".cost", 0D));
    }

    public synchronized List<String> listWarps() {
        if (isDatabaseAvailable()) {
            List<String> names = listWarpsFromDatabase();
            if (!names.isEmpty()) {
                return names;
            }
        }

        ConfigurationSection section = warpsConfig.getConfigurationSection("warps");
        if (section == null) return List.of();
        List<String> names = new ArrayList<>(section.getKeys(false));
        names.sort(Comparator.naturalOrder());
        return names;
    }

    private String normalizeName(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_-]{1,24}")) {
            return null;
        }
        return normalized;
    }

    private String encode(Location location) {
        return location.getWorld().getName() + ":" +
                location.getX() + ":" +
                location.getY() + ":" +
                location.getZ() + ":" +
                location.getYaw() + ":" +
                location.getPitch();
    }

    private Location decode(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split(":");
        if (parts.length < 6) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = Float.parseFloat(parts[4]);
            float pitch = Float.parseFloat(parts[5]);
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean isDatabaseAvailable() {
        if (!(plugin instanceof JebaitedCore core)) {
            return false;
        }
        DatabaseManager db = core.getDatabaseManager();
        return db != null && db.isEnabled() && db.ensureConnected();
    }

    private void initDatabase() {
        if (!(plugin instanceof JebaitedCore core)) return;
        String sql = """
                CREATE TABLE IF NOT EXISTS server_warps (
                    warp_name VARCHAR(24) PRIMARY KEY,
                    world_name VARCHAR(64) NOT NULL,
                    x DOUBLE PRECISION NOT NULL,
                    y DOUBLE PRECISION NOT NULL,
                    z DOUBLE PRECISION NOT NULL,
                    yaw REAL NOT NULL,
                    pitch REAL NOT NULL,
                    cost DOUBLE PRECISION NOT NULL DEFAULT 0
                )
                """;
        try (Connection conn = core.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to initialize server_warps table: " + e.getMessage());
        }
    }

    private void migrateYamlToDatabase() {
        ConfigurationSection section = warpsConfig.getConfigurationSection("warps");
        if (section == null) return;
        for (String name : section.getKeys(false)) {
            Location location = decode(warpsConfig.getString("warps." + name + ".location"));
            if (location != null) {
                double cost = warpsConfig.getDouble("warps." + name + ".cost", 0D);
                upsertWarpInDatabase(name, location, cost);
            }
        }
    }

    private void upsertWarpInDatabase(String name, Location location, double cost) {
        if (!(plugin instanceof JebaitedCore core) || location.getWorld() == null) return;
        String sql = """
                INSERT INTO server_warps (warp_name, world_name, x, y, z, yaw, pitch, cost)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (warp_name)
                DO UPDATE SET world_name = EXCLUDED.world_name,
                              x = EXCLUDED.x,
                              y = EXCLUDED.y,
                              z = EXCLUDED.z,
                              yaw = EXCLUDED.yaw,
                              pitch = EXCLUDED.pitch,
                              cost = EXCLUDED.cost
                """;
        try (Connection conn = core.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, location.getWorld().getName());
            ps.setDouble(3, location.getX());
            ps.setDouble(4, location.getY());
            ps.setDouble(5, location.getZ());
            ps.setFloat(6, location.getYaw());
            ps.setFloat(7, location.getPitch());
            ps.setDouble(8, Math.max(0D, round(cost)));
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to upsert warp in DB: " + e.getMessage());
        }
    }

    private boolean deleteWarpFromDatabase(String name) {
        if (!(plugin instanceof JebaitedCore core)) return false;
        String sql = "DELETE FROM server_warps WHERE warp_name = ?";
        try (Connection conn = core.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to delete warp from DB: " + e.getMessage());
            return false;
        }
    }

    private Location getWarpFromDatabase(String name) {
        if (!(plugin instanceof JebaitedCore core)) return null;
        String sql = "SELECT world_name, x, y, z, yaw, pitch FROM server_warps WHERE warp_name = ?";
        try (Connection conn = core.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    World world = Bukkit.getWorld(rs.getString("world_name"));
                    if (world == null) return null;
                    return new Location(world,
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load warp from DB: " + e.getMessage());
        }
        return null;
    }

    private Double getWarpCostFromDatabase(String name) {
        if (!(plugin instanceof JebaitedCore core)) return null;
        String sql = "SELECT cost FROM server_warps WHERE warp_name = ?";
        try (Connection conn = core.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Math.max(0D, rs.getDouble("cost"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load warp cost from DB: " + e.getMessage());
        }
        return null;
    }

    private List<String> listWarpsFromDatabase() {
        if (!(plugin instanceof JebaitedCore core)) return List.of();
        String sql = "SELECT warp_name FROM server_warps ORDER BY warp_name ASC";
        List<String> names = new ArrayList<>();
        try (Connection conn = core.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                names.add(rs.getString("warp_name"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to list warps from DB: " + e.getMessage());
        }
        return names;
    }

    private double round(double value) {
        return Math.round(Math.max(0D, value) * 100D) / 100D;
    }

    private void save() {
        try {
            warpsConfig.save(warpsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save warps.yml: " + e.getMessage());
        }
    }
}
