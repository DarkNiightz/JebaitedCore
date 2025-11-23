package com.darkniightz.core.players;

import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ProfileStore {
    private final Plugin plugin;
    private final File dir;
    private final Map<UUID, PlayerProfile> cache = new HashMap<>();

    public ProfileStore(Plugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "players");
        if (!dir.exists()) dir.mkdirs();
    }

    public PlayerProfile getOrCreate(Player player, String defaultRank) {
        return getOrCreate(player.getUniqueId(), player.getName(), defaultRank);
    }

    public PlayerProfile getOrCreate(OfflinePlayer player, String defaultRank) {
        return getOrCreate(player.getUniqueId(), player.getName(), defaultRank);
    }

    public PlayerProfile get(UUID uuid) {
        return cache.get(uuid);
    }

    public PlayerProfile getOrCreate(UUID uuid, String name, String defaultRank) {
        PlayerProfile cached = cache.get(uuid);
        if (cached != null) return cached;

        File f = file(uuid);
        if (f.exists()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
            PlayerProfile p = new PlayerProfile();
            p.setUuid(uuid);
            p.setName(yml.getString("name", name));
            p.setPrimaryRank(yml.getString("primaryRank", defaultRank));
            p.setCosmeticTickets(yml.getInt("cosmeticTickets", 0));
            p.setMessagesSent(yml.getInt("messagesSent", 0));
            p.setCommandsSent(yml.getInt("commandsSent", 0));
            // Moderation fields
            p.setMuteUntil(loadLong(yml, "moderation.muteUntil"));
            p.setMuteReason(yml.getString("moderation.muteReason"));
            p.setMuteActor(yml.getString("moderation.muteActor"));
            p.setBanUntil(loadLong(yml, "moderation.banUntil"));
            p.setBanReason(yml.getString("moderation.banReason"));
            p.setBanActor(yml.getString("moderation.banActor"));
            var logSec = yml.getList("moderation.log");
            if (logSec instanceof java.util.List<?> list) {
                java.util.List<java.util.Map<String,Object>> entries = new java.util.ArrayList<>();
                for (Object o : list) {
                    if (o instanceof java.util.Map<?,?> m) {
                        java.util.Map<String,Object> entry = new java.util.HashMap<>();
                        for (var e : m.entrySet()) {
                            if (e.getKey() != null) entry.put(e.getKey().toString(), e.getValue());
                        }
                        entries.add(entry);
                    }
                }
                p.setModerationLog(entries);
            }
            cache.put(uuid, p);
            return p;
        } else {
            PlayerProfile p = new PlayerProfile(uuid, name, defaultRank.toLowerCase(Locale.ROOT));
            cache.put(uuid, p);
            save(uuid);
            return p;
        }
    }

    public void save(UUID uuid) {
        PlayerProfile p = cache.get(uuid);
        if (p == null) return;
        File f = file(uuid);
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("uuid", p.getUuid().toString());
        yml.set("name", p.getName());
        yml.set("primaryRank", p.getPrimaryRank());
        yml.set("cosmeticTickets", p.getCosmeticTickets());
        yml.set("messagesSent", p.getMessagesSent());
        yml.set("commandsSent", p.getCommandsSent());
        // Moderation fields
        if (p.getMuteUntil() != null) yml.set("moderation.muteUntil", p.getMuteUntil()); else yml.set("moderation.muteUntil", null);
        yml.set("moderation.muteReason", p.getMuteReason());
        yml.set("moderation.muteActor", p.getMuteActor());
        if (p.getBanUntil() != null) yml.set("moderation.banUntil", p.getBanUntil()); else yml.set("moderation.banUntil", null);
        yml.set("moderation.banReason", p.getBanReason());
        yml.set("moderation.banActor", p.getBanActor());
        yml.set("moderation.log", p.getModerationLog());
        yml.set("createdAt", p.getCreatedAt());
        yml.set("updatedAt", p.getUpdatedAt());
        try {
            yml.save(f);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save profile for " + uuid + ": " + e.getMessage());
        }
    }

    public void flushAll() {
        for (UUID uuid : cache.keySet()) {
            save(uuid);
        }
    }

    private File file(UUID uuid) {
        return new File(dir, uuid.toString() + ".yml");
    }

    private Long loadLong(YamlConfiguration yml, String path) {
        if (!yml.isSet(path)) return null;
        try { return yml.getLong(path); } catch (Exception e) { return null; }
    }
}
