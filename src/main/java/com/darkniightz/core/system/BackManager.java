package com.darkniightz.core.system;

import org.bukkit.Location;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * In-memory store for last-death locations.
 * Used by /back (Grandmaster perk). One entry per UUID; consumed on use.
 */
public class BackManager {

    private final Map<UUID, Location> deathLocations = new ConcurrentHashMap<>();

    /** Records (or overwrites) the death location for a player. */
    public void recordDeath(UUID uuid, Location location) {
        if (uuid != null && location != null) {
            deathLocations.put(uuid, location.clone());
        }
    }

    /**
     * Retrieves and removes the stored death location.
     * Returns null if no location is stored.
     */
    public Location consumeDeathLocation(UUID uuid) {
        if (uuid == null) return null;
        return deathLocations.remove(uuid);
    }

    /** Returns true if a death location is stored for this UUID. */
    public boolean hasDeathLocation(UUID uuid) {
        return uuid != null && deathLocations.containsKey(uuid);
    }

    /** Clears any stored location without returning it (used on quit/cleanup). */
    public void clear(UUID uuid) {
        if (uuid != null) deathLocations.remove(uuid);
    }
}
