package com.darkniightz.core.system;

import com.darkniightz.main.JebaitedCore;
import com.darkniightz.main.database.DatabaseManager;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class TagCustomizationManager {
    public static final String CUSTOM_TAG_KEY = "tag_custom";

    private final Plugin plugin;
    private final File file;
    private FileConfiguration config;

    public TagCustomizationManager(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "custom-tags.yml");
        reload();
    }

    public synchronized void reload() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create custom-tags.yml: " + e.getMessage());
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        if (isDatabaseAvailable()) {
            initDatabase();
            migrateYamlToDatabase();
        }
    }

    public synchronized String getCustomTag(UUID uuid) {
        if (uuid == null) return null;

        if (isDatabaseAvailable()) {
            String fromDb = getCustomTagFromDatabase(uuid);
            if (fromDb != null && !fromDb.isBlank()) {
                return fromDb;
            }
        }

        String value = config.getString("players." + uuid);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    public synchronized boolean setCustomTag(UUID uuid, String input) {
        if (uuid == null) return false;
        String normalized = normalize(input);
        if (normalized == null) return false;

        if (isDatabaseAvailable()) {
            upsertCustomTagInDatabase(uuid, normalized);
        }

        config.set("players." + uuid, normalized);
        save();
        return true;
    }

    /**
     * Sets a tag earned via an achievement tier reward, but only if the player
     * has no existing custom tag (never overwrites a player's chosen tag).
     */
    public synchronized void unlockAchievementTag(UUID uuid, String tagText) {
        if (uuid == null || tagText == null || tagText.isBlank()) return;
        if (getCustomTag(uuid) != null) return; // preserve the player's own tag choice
        setCustomTag(uuid, tagText);
    }

    public synchronized boolean clearCustomTag(UUID uuid) {
        if (uuid == null) return false;

        boolean cleared = false;
        if (isDatabaseAvailable()) {
            cleared = deleteCustomTagFromDatabase(uuid) || cleared;
        }

        String path = "players." + uuid;
        if (config.isSet(path)) {
            config.set(path, null);
            save();
            cleared = true;
        }
        return cleared;
    }

    public String normalize(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        if (trimmed.isBlank()) return null;
        if (trimmed.length() > 24) return null;
        String translated = ChatColor.translateAlternateColorCodes('&', trimmed);
        String plain = ChatColor.stripColor(translated);
        if (plain == null || plain.isBlank()) return null;
        return translated;
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
                CREATE TABLE IF NOT EXISTS player_custom_tags (
                    player_uuid UUID PRIMARY KEY,
                    custom_tag VARCHAR(64) NOT NULL
                )
                """;
        try (Connection conn = core.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to initialize player_custom_tags table: " + e.getMessage());
        }
    }

    private void migrateYamlToDatabase() {
        var players = config.getConfigurationSection("players");
        if (players == null) return;
        for (String uuidKey : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidKey);
                String tag = players.getString(uuidKey);
                if (tag != null && !tag.isBlank()) {
                    upsertCustomTagInDatabase(uuid, tag);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private String getCustomTagFromDatabase(UUID uuid) {
        if (!(plugin instanceof JebaitedCore core)) return null;
        String sql = "SELECT custom_tag FROM player_custom_tags WHERE player_uuid = ?";
        try (Connection conn = core.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("custom_tag");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load custom tag from DB: " + e.getMessage());
        }
        return null;
    }

    private void upsertCustomTagInDatabase(UUID uuid, String tag) {
        if (!(plugin instanceof JebaitedCore core)) return;
        String sql = """
                INSERT INTO player_custom_tags (player_uuid, custom_tag)
                VALUES (?, ?)
                ON CONFLICT (player_uuid)
                DO UPDATE SET custom_tag = EXCLUDED.custom_tag
                """;
        try (Connection conn = core.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, uuid);
            ps.setString(2, tag);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to upsert custom tag in DB: " + e.getMessage());
        }
    }

    private boolean deleteCustomTagFromDatabase(UUID uuid) {
        if (!(plugin instanceof JebaitedCore core)) return false;
        String sql = "DELETE FROM player_custom_tags WHERE player_uuid = ?";
        try (Connection conn = core.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, uuid);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to delete custom tag from DB: " + e.getMessage());
            return false;
        }
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save custom-tags.yml: " + e.getMessage());
        }
    }
}
