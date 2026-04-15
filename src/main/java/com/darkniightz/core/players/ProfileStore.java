package com.darkniightz.core.players;

import com.darkniightz.main.JebaitedCore;
import com.darkniightz.main.PlayerProfileDAO;
import com.darkniightz.core.system.OverallStatsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
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
    private final java.util.Set<UUID> dirtyProfiles = ConcurrentHashMap.newKeySet();

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
        if (player == null) return null;
        return getOrCreateInternal(player.getUniqueId(), player.getName(), defaultRank);
    }

    /**
     * Overload for OfflinePlayer (commands may target offline players).
     */
    public PlayerProfile getOrCreate(OfflinePlayer player, String defaultRank) {
        if (player == null) return null;
        UUID uuid = player.getUniqueId();
        if (uuid == null) return null;
        String name = player.getName() != null ? player.getName() : uuid.toString();
        return getOrCreateInternal(uuid, name, defaultRank);
    }

    public void preloadProfile(UUID uuid, String name, String defaultRank) {
        if (uuid == null) return;
        getOrCreateInternal(uuid, name == null || name.isBlank() ? uuid.toString() : name, defaultRank);
    }

    private PlayerProfile getOrCreateInternal(UUID uuid, String name, String defaultRank) {
        PlayerProfile cached = profileCache.get(uuid);
        if (cached != null) {
            return cached;
        }

        if (!plugin.getDatabaseManager().isEnabled()) {
            PlayerProfile inMemory = new PlayerProfile(uuid, name);
            inMemory.setRank(defaultRank);
            inMemory.setBalance(getStartingBalance());
            long now = System.currentTimeMillis();
            inMemory.setFirstJoined(now);
            inMemory.setLastJoined(now);
            profileCache.put(uuid, inMemory);
            return inMemory;
        }

        PlayerProfile profile = dao.loadPlayerProfile(uuid);
        if (profile == null) {
            profile = new PlayerProfile(uuid, name);
            profile.setRank(defaultRank);
            profile.setBalance(getStartingBalance());
            long now = System.currentTimeMillis();
            profile.setFirstJoined(now);
            profile.setLastJoined(now);
            dirtyProfiles.add(uuid);
            OverallStatsManager overall = plugin.getOverallStatsManager();
            if (overall != null) {
                overall.increment(OverallStatsManager.UNIQUE_LOGINS, 1);
            }
        } else {
            profile.setLastJoined(System.currentTimeMillis());
            dirtyProfiles.add(uuid);
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
        dirtyProfiles.remove(uuid);
        if (profile != null && plugin.getDatabaseManager().isEnabled()) {
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
        dirtyProfiles.clear();
        plugin.getLogger().info("... all profiles saved.");
    }

    /**
     * Persist a single cached player profile immediately.
     */
    public void save(UUID uuid) {
        if (!plugin.getDatabaseManager().isEnabled()) return;
        PlayerProfile p = profileCache.get(uuid);
        if (p != null) {
            dao.savePlayerProfile(p);
            dirtyProfiles.remove(uuid);
        }
    }

    /**
     * Marks a profile for deferred persistence by background flusher.
     */
    public void saveDeferred(UUID uuid) {
        if (!plugin.getDatabaseManager().isEnabled()) return;
        if (profileCache.containsKey(uuid)) dirtyProfiles.add(uuid);
    }

    /**
     * Grants cosmetic coins to an online player and shows a tiny toast.
     * Future minigame/event reward code should use this instead of mutating the profile directly.
     */
    public int grantCosmeticCoins(Player player, int amount, String reason) {
        if (player == null || amount == 0) return 0;
        PlayerProfile profile = getOrCreate(player, plugin.getConfig().getString("ranks.default", "pleb"));
        if (profile == null) return 0;
        int before = profile.getCosmeticCoins();
        profile.addCosmeticCoins(amount);
        dirtyProfiles.add(player.getUniqueId());
        save(player.getUniqueId());

        int delta = profile.getCosmeticCoins() - before;
        if (player.isOnline() && delta > 0) {
            String label = reason == null || reason.isBlank() ? "Coins earned" : reason.trim();
            player.sendActionBar(Component.text("+" + delta + " coins", NamedTextColor.GOLD)
                    .append(Component.text(" • " + label, NamedTextColor.GRAY)));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f);
        }
        return delta;
    }

    /**
     * Flushes only dirty profiles marked via saveDeferred().
     * Intended for periodic background execution.
     */
    public void flushDirty() {
        if (!plugin.getDatabaseManager().isEnabled()) return;
        if (dirtyProfiles.isEmpty()) return;

        for (UUID uuid : java.util.Set.copyOf(dirtyProfiles)) {
            PlayerProfile p = profileCache.get(uuid);
            if (p != null) dao.savePlayerProfile(p);
            dirtyProfiles.remove(uuid);
        }
    }

    /**
     * Clears the cache and reloads profiles for all currently online players from the database.
     * If a player is not found in DB, a new profile is created using the provided default rank.
     * This is used by the reload command to refresh in-memory state after config changes.
     *
     * @param defaultRank The default rank to use when creating new profiles
     */
    public void reloadOnlineFromDatabase(String defaultRank) {
        if (!plugin.getDatabaseManager().isEnabled()) return;

        // Clear cache but do not save here (caller should have flushed already)
        profileCache.clear();

        // Re-hydrate all online players
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            UUID uuid = p.getUniqueId();
            PlayerProfile profile = dao.loadPlayerProfile(uuid);
            if (profile == null) {
                profile = new PlayerProfile(uuid, p.getName());
                profile.setRank(defaultRank);
                long now = System.currentTimeMillis();
                profile.setFirstJoined(now);
                profile.setLastJoined(now);
                dao.savePlayerProfile(profile);
            } else {
                profile.setLastJoined(System.currentTimeMillis());
                dao.savePlayerProfile(profile);
            }
            profileCache.put(uuid, profile);
        }
    }

    public void updateEventStats(UUID playerUuid, String eventKey, int participatedDelta, int wonDelta, int lostDelta) {
        if (!plugin.getDatabaseManager().isEnabled()) return;
        dao.updateEventStats(playerUuid, eventKey, participatedDelta, wonDelta, lostDelta);
    }

    public Map<String, PlayerProfileDAO.EventStatRecord> loadEventStats(UUID playerUuid) {
        if (!plugin.getDatabaseManager().isEnabled()) return new LinkedHashMap<>();
        return dao.loadEventStats(playerUuid);
    }

    public int cachedCount() {
        return profileCache.size();
    }

    public int dirtyCount() {
        return dirtyProfiles.size();
    }

    public java.util.List<PlayerProfileDAO.BalanceTopRecord> loadTopBalances(int limit) {
        if (!plugin.getDatabaseManager().isEnabled()) return java.util.List.of();
        return dao.loadTopBalances(limit);
    }

    private double getStartingBalance() {
        return Math.max(0D, plugin.getConfig().getDouble("economy.starting_balance", 100D));
    }
}
