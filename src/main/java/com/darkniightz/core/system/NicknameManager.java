package com.darkniightz.core.system;

import com.darkniightz.main.JebaitedCore;
import com.darkniightz.main.database.DatabaseManager;
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

public class NicknameManager {

    private final Plugin plugin;
    private final File nickFile;
    private FileConfiguration nickConfig;

    public NicknameManager(Plugin plugin) {
        this.plugin = plugin;
        this.nickFile = new File(plugin.getDataFolder(), "nicknames.yml");
        reload();
    }

    public synchronized void reload() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        if (!nickFile.exists()) {
            try {
                nickFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create nicknames.yml: " + e.getMessage());
            }
        }
        nickConfig = YamlConfiguration.loadConfiguration(nickFile);
        if (isDatabaseAvailable()) {
            initDatabase();
            migrateYamlToDatabase();
        }
    }

    public synchronized String getNickname(UUID uuid) {
        if (uuid == null) return null;

        if (isDatabaseAvailable()) {
            String fromDb = getNicknameFromDatabase(uuid);
            if (fromDb != null && !fromDb.isBlank()) {
                return fromDb;
            }
        }

        String value = nickConfig.getString("players." + uuid);
        if (value == null || value.isBlank()) return null;
        return value;
    }

    public synchronized boolean clearNickname(UUID uuid) {
        if (uuid == null) return false;

        boolean cleared = false;
        if (isDatabaseAvailable()) {
            cleared = deleteNicknameFromDatabase(uuid) || cleared;
        }

        String path = "players." + uuid;
        if (nickConfig.isSet(path)) {
            nickConfig.set(path, null);
            save();
            cleared = true;
        }
        return cleared;
    }

    public synchronized boolean setNickname(UUID uuid, String rawNickname) {
        if (uuid == null) return false;
        String normalized = normalize(rawNickname);
        if (normalized == null) return false;

        if (isDatabaseAvailable()) {
            upsertNicknameInDatabase(uuid, normalized);
        }

        nickConfig.set("players." + uuid, normalized);
        save();
        return true;
    }

    public String normalize(String nickname) {
        if (nickname == null || nickname.isBlank()) return null;
        String clean = nickname.trim();
        if (clean.length() > 16) return null;
        if (!clean.matches("[A-Za-z0-9_]+")) {
            return null;
        }
        return clean;
    }

    public String displayName(String realName, UUID uuid) {
        if (!plugin.getConfig().getBoolean("nick.enabled", true)) {
            return realName;
        }
        String nick = getNickname(uuid);
        return nick == null ? realName : nick;
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
                CREATE TABLE IF NOT EXISTS player_nicknames (
                    player_uuid UUID PRIMARY KEY,
                    nickname VARCHAR(16) NOT NULL
                )
                """;
        try (Connection conn = core.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to initialize player_nicknames table: " + e.getMessage());
        }
    }

    private void migrateYamlToDatabase() {
        var players = nickConfig.getConfigurationSection("players");
        if (players == null) return;
        for (String uuidKey : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidKey);
                String nick = players.getString(uuidKey);
                if (nick != null && !nick.isBlank()) {
                    upsertNicknameInDatabase(uuid, nick);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private String getNicknameFromDatabase(UUID uuid) {
        if (!(plugin instanceof JebaitedCore core)) return null;
        String sql = "SELECT nickname FROM player_nicknames WHERE player_uuid = ?";
        try (Connection conn = core.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("nickname");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load nickname from DB: " + e.getMessage());
        }
        return null;
    }

    private void upsertNicknameInDatabase(UUID uuid, String nickname) {
        if (!(plugin instanceof JebaitedCore core)) return;
        String sql = """
                INSERT INTO player_nicknames (player_uuid, nickname)
                VALUES (?, ?)
                ON CONFLICT (player_uuid)
                DO UPDATE SET nickname = EXCLUDED.nickname
                """;
        try (Connection conn = core.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, uuid);
            ps.setString(2, nickname);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to upsert nickname in DB: " + e.getMessage());
        }
    }

    private boolean deleteNicknameFromDatabase(UUID uuid) {
        if (!(plugin instanceof JebaitedCore core)) return false;
        String sql = "DELETE FROM player_nicknames WHERE player_uuid = ?";
        try (Connection conn = core.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, uuid);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to delete nickname from DB: " + e.getMessage());
            return false;
        }
    }

    private void save() {
        try {
            nickConfig.save(nickFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save nicknames.yml: " + e.getMessage());
        }
    }
}
