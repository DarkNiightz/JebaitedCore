package com.darkniightz.core.players;

import com.darkniightz.main.JebaitedCore;
import com.darkniightz.main.PlayerProfileDAO;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages a cache of PlayerProfile objects.
 * Handles loading from and saving to the database.
 */
public class ProfileStore {

    private final JebaitedCore plugin;
    private final PlayerProfileDAO dao;
    private final Map<UUID, PlayerProfile> profileCache = new ConcurrentHashMap<>();

    public ProfileStore(JebaitedCore plugin) {
        this.plugin = plugin;
        this.dao = plugin.getPlayerProfileDAO();
    }

    /**
     * Gets a player's profile from the cache. If not in the cache, it's loaded from the database.
     * If it's not in the database (e.g., first join), a new profile is created and saved.
     *
     * @param player The player to get the profile for.
     * @param defaultRank The rank to assign if the player is new.
     * @return The PlayerProfile, or null if the database is disabled.
     */
    public PlayerProfile getOrCreate(Player player, String defaultRank) {
        if (!plugin.getDatabaseManager().isEnabled()) {
            return null; // Cannot function without a database
        }

        UUID uuid = player.getUniqueId();
        // 1. Check cache
        if (profileCache.containsKey(uuid)) {
            return profileCache.get(uuid);
        }

        // 2. Not in cache, try loading from DB
        PlayerProfile profile = dao.loadPlayerProfile(uuid);

        // 3. Not in DB, create a new profile for a new player
        if (profile == null) {
            profile = new PlayerProfile(uuid, player.getName());
            profile.setRank(defaultRank);
            long now = System.currentTimeMillis();
            profile.setFirstJoined(now);
            profile.setLastJoined(now);
            // Save the new profile immediately to create the DB record
            dao.savePlayerProfile(profile);
        } else {
            // Update last joined time for existing player
            profile.setLastJoined(System.currentTimeMillis());
        }

        // 4. Add to cache
        profileCache.put(uuid, profile);
        return profile;
    }

    /**
     * Overload for OfflinePlayer (commands may target offline players).
     */
    public PlayerProfile getOrCreate(OfflinePlayer player, String defaultRank) {
        if (!plugin.getDatabaseManager().isEnabled()) {
            return null;
        }
        UUID uuid = player.getUniqueId();
        if (uuid == null) return null;
        if (profileCache.containsKey(uuid)) return profileCache.get(uuid);

        PlayerProfile profile = dao.loadPlayerProfile(uuid);
        if (profile == null) {
            String name = player.getName() != null ? player.getName() : uuid.toString();
            profile = new PlayerProfile(uuid, name);
            profile.setRank(defaultRank);
            long now = System.currentTimeMillis();
            profile.setFirstJoined(now);
            profile.setLastJoined(now);
            dao.savePlayerProfile(profile);
        } else {
            profile.setLastJoined(System.currentTimeMillis());
        }
        profileCache.put(uuid, profile);
        return profile;
    }

    /**
     * Gets a profile from the cache only. Does not load from DB.
     *
     * @param uuid The player's UUID.
     * @return The cached PlayerProfile, or null if not online/cached.
     */
    public PlayerProfile get(UUID uuid) {
        return profileCache.get(uuid);
    }

    /**
     * Removes a player's profile from the cache (e.g., on quit).
     *
     * @param uuid The player's UUID.
     */
    public void unload(UUID uuid) {
        PlayerProfile profile = profileCache.remove(uuid);
        if (profile != null) {
            // Save on unload
            dao.savePlayerProfile(profile);
        }
    }

    /**
     * Saves all currently cached profiles to the database.
     * Called on server shutdown to ensure all data is persisted.
     */
    public void flushAll() {
        if (!plugin.getDatabaseManager().isEnabled()) return;

        plugin.getLogger().info("Saving all cached player profiles to the database...");
        for (PlayerProfile profile : profileCache.values()) {
            dao.savePlayerProfile(profile);
        }
        plugin.getLogger().info("... all profiles saved.");
    }

    /**
     * Persist a single cached player profile immediately.
     */
    public void save(UUID uuid) {
        PlayerProfile p = profileCache.get(uuid);
        if (p != null) dao.savePlayerProfile(p);
    }
}