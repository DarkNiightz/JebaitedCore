package com.darkniightz.main.managers;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;  // Add for logging

public class MuteManager {

    private static MuteManager instance;
    public Map<UUID, Instant> mutedPlayers = new HashMap<>();

    private MuteManager() {}

    public static MuteManager getInstance() {
        if (instance == null) {
            instance = new MuteManager();
        }
        return instance;
    }

    public void mutePlayer(UUID uuid, Instant expiry) {
        mutedPlayers.put(uuid, expiry);
        Bukkit.getLogger().info("[Debug] Muted " + uuid + " with expiry: " + (expiry != null ? expiry.toString() : "permanent") + ". Map size: " + mutedPlayers.size());
    }

    public void unmutePlayer(UUID uuid) {
        if (mutedPlayers.remove(uuid) != null) {
            Bukkit.getLogger().info("[Debug] Unmuted " + uuid + ". Map size now: " + mutedPlayers.size());
        } else {
            Bukkit.getLogger().info("[Debug] Tried to unmute " + uuid + " but not found in map.");
        }
    }

    public boolean isMuted(UUID uuid) {
        Instant expiry = mutedPlayers.get(uuid);
        if (expiry == null) {
            if (mutedPlayers.containsKey(uuid)) {
                Bukkit.getLogger().info("[Debug] " + uuid + " is permanently muted.");
                return true;  // Permanent
            }
            return false;
        }
        if (Instant.now().isAfter(expiry)) {
            unmutePlayer(uuid);
            Bukkit.getLogger().info("[Debug] Auto-unmuted " + uuid + " (expired).");
            return false;
        }
        Bukkit.getLogger().info("[Debug] " + uuid + " is temp muted until " + expiry);
        return true;
    }

    // Bonus: Add this to see all muted for testing
    public void logAllMuted() {
        Bukkit.getLogger().info("[Debug] Current muted players: " + mutedPlayers.keySet().size());
        for (Map.Entry<UUID, Instant> entry : mutedPlayers.entrySet()) {
            Bukkit.getLogger().info("[Debug] - " + entry.getKey() + ": " + (entry.getValue() != null ? entry.getValue().toString() : "permanent"));
        }
    }
}