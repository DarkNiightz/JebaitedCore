package com.darkniightz.core.world;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SmpReturnManager {
    private static final Map<UUID, Location> LAST_SMP_LOCATION = new ConcurrentHashMap<>();

    private SmpReturnManager() {
    }

    public static void remember(Player player) {
        if (player == null || player.getLocation() == null) {
            return;
        }
        LAST_SMP_LOCATION.put(player.getUniqueId(), player.getLocation().clone());
    }

    public static void remember(UUID playerId, Location location) {
        if (playerId == null || location == null) {
            return;
        }
        LAST_SMP_LOCATION.put(playerId, location.clone());
    }

    public static Location get(Player player) {
        if (player == null) {
            return null;
        }
        Location location = LAST_SMP_LOCATION.get(player.getUniqueId());
        return location == null ? null : location.clone();
    }
}
