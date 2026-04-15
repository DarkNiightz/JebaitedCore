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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class HomesManager {

    public enum SetHomeResult {
        CREATED,
        UPDATED,
        LIMIT_REACHED,
        INVALID
    }

    private final Plugin plugin;
    private final File homesFile;
    private FileConfiguration homesConfig;

    public HomesManager(Plugin plugin) {
        this.plugin = plugin;
        this.homesFile = new File(plugin.getDataFolder(), "homes.yml");
        reload();
    }

    public synchronized void reload() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        if (!homesFile.exists()) {
            try {
                homesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create homes.yml: " + e.getMessage());
            }
        }
        homesConfig = YamlConfiguration.loadConfiguration(homesFile);
        if (isDatabaseAvailable()) {
            initDatabase();
            migrateYamlToDatabase();
        }
    }

    public synchronized int getHomeLimit(String rank) {
        String normalized = rank == null ? "default" : rank.toLowerCase(Locale.ROOT);
        String path = "homes.limits." + normalized;
        if (plugin.getConfig().isInt(path)) {
            return Math.max(1, plugin.getConfig().getInt(path));
        }
        return Math.max(1, plugin.getConfig().getInt("homes.limits.default", 1));
    }

    public synchronized SetHomeResult setHome(UUID uuid, String homeName, Location location, int homeLimit) {
        if (uuid == null || location == null || location.getWorld() == null) {
            return SetHomeResult.INVALID;
        }
        String normalized = normalizeName(homeName);
        if (normalized == null) {
            return SetHomeResult.INVALID;
        }

        boolean existed = hasStoredHome(uuid, normalized);
        int existingCount = isDatabaseAvailable() ? countHomesInDatabase(uuid) : getYamlHomeCount(uuid);
        if (!existed && existingCount >= Math.max(1, homeLimit)) {
            return SetHomeResult.LIMIT_REACHED;
        }

        if (isDatabaseAvailable()) {
            upsertHomeInDatabase(uuid, normalized, location);
        }

        ConfigurationSection section = getPlayerSection(uuid, true);
        if (section != null) {
            section.set(normalized, encode(location));
            save();
        }
        return existed ? SetHomeResult.UPDATED : SetHomeResult.CREATED;
    }

    public synchronized boolean deleteHome(UUID uuid, String homeName) {
        if (uuid == null) return false;
        String normalized = normalizeName(homeName);
        if (normalized == null) return false;

        boolean removed = false;
        if (isDatabaseAvailable()) {
            removed = deleteHomeFromDatabase(uuid, normalized) || removed;
        }

        String path = "players." + uuid + "." + normalized;
        if (homesConfig.isSet(path)) {
            homesConfig.set(path, null);
            save();
            removed = true;
        }
        return removed;
    }

    public synchronized Location getHome(UUID uuid, String homeName) {
        if (uuid == null) return null;
        String normalized = normalizeName(homeName);
        if (normalized == null) return null;

        if (isDatabaseAvailable()) {
            Location fromDb = getHomeFromDatabase(uuid, normalized);
            if (fromDb != null) {
                return fromDb;
            }
        }

        String encoded = homesConfig.getString("players." + uuid + "." + normalized);
        return decode(encoded);
    }

    public synchronized List<String> getHomeNames(UUID uuid) {
        if (uuid == null) return List.of();

        if (isDatabaseAvailable()) {
            List<String> dbNames = getHomeNamesFromDatabase(uuid);
            if (!dbNames.isEmpty()) {
                return dbNames;
            }
        }

        ConfigurationSection section = homesConfig.getConfigurationSection("players." + uuid);
        if (section == null) return List.of();
        List<String> names = new ArrayList<>(section.getKeys(false));
        names.sort(Comparator.naturalOrder());
        return names;
    }

    public synchronized Map<String, Location> getHomes(UUID uuid) {
        Map<String, Location> out = new HashMap<>();
        for (String name : getHomeNames(uuid)) {
            Location location = getHome(uuid, name);
            if (location != null) {
                out.put(name, location);
            }
        }
        return out;
    }

    private String normalizeName(String raw) {
        if (raw == null || raw.isBlank()) return "home";
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_-]{1,16}")) {
            return null;
        }
        return normalized;
    }

    private boolean hasStoredHome(UUID uuid, String normalized) {
        if (isDatabaseAvailable() && getHomeFromDatabase(uuid, normalized) != null) {
            return true;
        }
        ConfigurationSection section = getPlayerSection(uuid, false);
        return section != null && section.isString(normalized);
    }

    private int getYamlHomeCount(UUID uuid) {
        ConfigurationSection section = getPlayerSection(uuid, false);
        return section == null ? 0 : section.getKeys(false).size();
    }

    private ConfigurationSection getPlayerSection(UUID uuid, boolean create) {
        String path = "players." + uuid;
        ConfigurationSection section = homesConfig.getConfigurationSection(path);
        if (section == null && create) {
            section = homesConfig.createSection(path);
        }
        return section;
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
                CREATE TABLE IF NOT EXISTS player_homes (
                    player_uuid UUID NOT NULL,
                    home_name VARCHAR(16) NOT NULL,
                    uuid UUID,
                    name TEXT,
                    world TEXT,
                    world_name VARCHAR(64) NOT NULL,
                    x DOUBLE PRECISION NOT NULL,
                    y DOUBLE PRECISION NOT NULL,
                    z DOUBLE PRECISION NOT NULL,
                    yaw REAL NOT NULL,
                    pitch REAL NOT NULL,
                    created_at BIGINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (player_uuid, home_name)
                )
                """;
        try (Connection conn = core.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             PreparedStatement addUuid = conn.prepareStatement("ALTER TABLE IF EXISTS player_homes ADD COLUMN IF NOT EXISTS uuid UUID");
             PreparedStatement addName = conn.prepareStatement("ALTER TABLE IF EXISTS player_homes ADD COLUMN IF NOT EXISTS name TEXT");
             PreparedStatement addWorld = conn.prepareStatement("ALTER TABLE IF EXISTS player_homes ADD COLUMN IF NOT EXISTS world TEXT");
             PreparedStatement addCreatedAt = conn.prepareStatement("ALTER TABLE IF EXISTS player_homes ADD COLUMN IF NOT EXISTS created_at BIGINT NOT NULL DEFAULT 0");
             PreparedStatement backfill = conn.prepareStatement("UPDATE player_homes SET uuid = COALESCE(uuid, player_uuid), name = COALESCE(name, home_name), world = COALESCE(world, world_name), created_at = CASE WHEN COALESCE(created_at, 0) <= 0 THEN ? ELSE created_at END")) {
            ps.execute();
            addUuid.execute();
            addName.execute();
            addWorld.execute();
            addCreatedAt.execute();
            backfill.setLong(1, System.currentTimeMillis());
            backfill.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to initialize player_homes table: " + e.getMessage());
        }
    }

    private void migrateYamlToDatabase() {
        ConfigurationSection players = homesConfig.getConfigurationSection("players");
        if (players == null) return;

        for (String uuidKey : players.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidKey);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            ConfigurationSection section = players.getConfigurationSection(uuidKey);
            if (section == null) continue;
            for (String homeName : section.getKeys(false)) {
                Location location = decode(section.getString(homeName));
                if (location != null) {
                    upsertHomeInDatabase(uuid, homeName, location);
                }
            }
        }
    }

    private int countHomesInDatabase(UUID uuid) {
        if (!(plugin instanceof JebaitedCore core)) return getYamlHomeCount(uuid);
        String sql = "SELECT COUNT(*) FROM player_homes WHERE player_uuid = ?";
        try (Connection conn = core.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to count homes in DB: " + e.getMessage());
        }
        return getYamlHomeCount(uuid);
    }

    private void upsertHomeInDatabase(UUID uuid, String homeName, Location location) {
        if (!(plugin instanceof JebaitedCore core) || location.getWorld() == null) return;
        String sql = """
                INSERT INTO player_homes (player_uuid, home_name, uuid, name, world, world_name, x, y, z, yaw, pitch, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (player_uuid, home_name)
                DO UPDATE SET uuid = EXCLUDED.uuid,
                              name = EXCLUDED.name,
                              world = EXCLUDED.world,
                              world_name = EXCLUDED.world_name,
                              x = EXCLUDED.x,
                              y = EXCLUDED.y,
                              z = EXCLUDED.z,
                              yaw = EXCLUDED.yaw,
                              pitch = EXCLUDED.pitch
                """;
        try (Connection conn = core.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, uuid);
            ps.setString(2, homeName);
            ps.setObject(3, uuid);
            ps.setString(4, homeName);
            ps.setString(5, location.getWorld().getName());
            ps.setString(6, location.getWorld().getName());
            ps.setDouble(7, location.getX());
            ps.setDouble(8, location.getY());
            ps.setDouble(9, location.getZ());
            ps.setFloat(10, location.getYaw());
            ps.setFloat(11, location.getPitch());
            ps.setLong(12, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to upsert home in DB: " + e.getMessage());
        }
    }

    private boolean deleteHomeFromDatabase(UUID uuid, String homeName) {
        if (!(plugin instanceof JebaitedCore core)) return false;
        String sql = "DELETE FROM player_homes WHERE player_uuid = ? AND home_name = ?";
        try (Connection conn = core.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, uuid);
            ps.setString(2, homeName);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to delete home from DB: " + e.getMessage());
            return false;
        }
    }

    private Location getHomeFromDatabase(UUID uuid, String homeName) {
        if (!(plugin instanceof JebaitedCore core)) return null;
        String sql = "SELECT world_name, x, y, z, yaw, pitch FROM player_homes WHERE player_uuid = ? AND home_name = ?";
        try (Connection conn = core.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, uuid);
            ps.setString(2, homeName);
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
            plugin.getLogger().warning("Failed to load home from DB: " + e.getMessage());
        }
        return null;
    }

    private List<String> getHomeNamesFromDatabase(UUID uuid) {
        if (!(plugin instanceof JebaitedCore core)) return List.of();
        String sql = "SELECT home_name FROM player_homes WHERE player_uuid = ? ORDER BY home_name ASC";
        List<String> names = new ArrayList<>();
        try (Connection conn = core.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString("home_name"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to list homes from DB: " + e.getMessage());
        }
        return names;
    }

    private void save() {
        try {
            homesConfig.save(homesFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save homes.yml: " + e.getMessage());
        }
    }
}
