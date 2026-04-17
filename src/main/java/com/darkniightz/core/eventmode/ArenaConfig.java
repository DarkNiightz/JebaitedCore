package com.darkniightz.core.eventmode;

import org.bukkit.Location;

import java.util.Collections;
import java.util.List;

/**
 * One configured arena instance (YAML under {@code event_mode.arena_registry}).
 */
public final class ArenaConfig {

    private final String key;
    private final String displayName;
    private final List<Location> spawns;
    private final KothHill hill;
    private final Integer kothDurationSeconds;
    private final CtfLayout ctf;

    public ArenaConfig(String key, String displayName, List<Location> spawns,
                       KothHill hill, Integer kothDurationSeconds, CtfLayout ctf) {
        this.key = key == null ? "default" : key;
        this.displayName = displayName == null || displayName.isBlank() ? this.key : displayName;
        this.spawns = spawns == null ? List.of() : List.copyOf(spawns);
        this.hill = hill;
        this.kothDurationSeconds = kothDurationSeconds;
        this.ctf = ctf;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public List<Location> spawns() {
        return spawns;
    }

    public KothHill hill() {
        return hill;
    }

    public Integer kothDurationSeconds() {
        return kothDurationSeconds;
    }

    public CtfLayout ctf() {
        return ctf;
    }

    /** Axis-aligned KOTH hill (same semantics as {@link com.darkniightz.core.eventmode.handler.KothHandler.Cuboid}). */
    public record KothHill(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public KothHill {
            int loX = Math.min(minX, maxX), hiX = Math.max(minX, maxX);
            int loY = Math.min(minY, maxY), hiY = Math.max(minY, maxY);
            int loZ = Math.min(minZ, maxZ), hiZ = Math.max(minZ, maxZ);
            minX = loX;
            maxX = hiX;
            minY = loY;
            maxY = hiY;
            minZ = loZ;
            maxZ = hiZ;
        }

        public com.darkniightz.core.eventmode.handler.KothHandler.Cuboid toHandlerCuboid() {
            return new com.darkniightz.core.eventmode.handler.KothHandler.Cuboid(
                    world, minX, minY, minZ, maxX, maxY, maxZ);
        }

        public boolean contains(Location loc) {
            return toHandlerCuboid().contains(loc);
        }
    }

    public record CtfLayout(
            Location redSpawn,
            Location blueSpawn,
            Location redFlagBlock,
            Location blueFlagBlock,
            int winScore,
            int flagReturnSeconds
    ) {
        public static CtfLayout empty() {
            return new CtfLayout(null, null, null, null, 3, 30);
        }

        public boolean isComplete() {
            return redSpawn != null && redSpawn.getWorld() != null
                    && blueSpawn != null && blueSpawn.getWorld() != null
                    && redFlagBlock != null && redFlagBlock.getWorld() != null
                    && blueFlagBlock != null && blueFlagBlock.getWorld() != null;
        }
    }

    public static ArenaConfig empty(String key) {
        return new ArenaConfig(key, key, Collections.emptyList(), null, null, CtfLayout.empty());
    }
}
