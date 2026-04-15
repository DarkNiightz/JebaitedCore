package com.darkniightz.core.system;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatTagManager {
    private static final long DEFAULT_TAG_MS = 15_000L;

    private final Map<UUID, Long> taggedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastAttackerByVictim = new ConcurrentHashMap<>();

    public void tag(Player player) {
        if (player == null) return;
        taggedUntil.put(player.getUniqueId(), System.currentTimeMillis() + DEFAULT_TAG_MS);
    }

    public void tag(Player a, Player b) {
        tag(a);
        tag(b);
        if (a != null && b != null) {
            lastAttackerByVictim.put(b.getUniqueId(), a.getUniqueId());
        }
    }

    public UUID getLastAttacker(UUID victimId) {
        if (victimId == null) return null;
        return lastAttackerByVictim.get(victimId);
    }

    public boolean isTagged(Player player) {
        if (player == null) return false;
        Long until = taggedUntil.get(player.getUniqueId());
        if (until == null) return false;
        if (until <= System.currentTimeMillis()) {
            taggedUntil.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public long remainingMillis(Player player) {
        if (player == null) return 0L;
        Long until = taggedUntil.get(player.getUniqueId());
        if (until == null) return 0L;
        long remain = until - System.currentTimeMillis();
        if (remain <= 0L) {
            taggedUntil.remove(player.getUniqueId());
            return 0L;
        }
        return remain;
    }

    public void clear(Player player) {
        if (player == null) return;
        taggedUntil.remove(player.getUniqueId());
        lastAttackerByVictim.remove(player.getUniqueId());
    }

    public int activeCount() {
        long now = System.currentTimeMillis();
        taggedUntil.entrySet().removeIf(e -> e.getValue() <= now);
        return taggedUntil.size();
    }

    public long tagDurationSeconds() {
        return DEFAULT_TAG_MS / 1000L;
    }
}
