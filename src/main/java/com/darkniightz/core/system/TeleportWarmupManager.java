package com.darkniightz.core.system;

import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks pending delayed teleports (home / warp warmup timers).
 * Commands register their BukkitTask here; a PlayerMoveEvent cancels it on
 * any block-level position change.
 */
public final class TeleportWarmupManager {

    private static final Map<UUID, BukkitTask> pending = new ConcurrentHashMap<>();

    private TeleportWarmupManager() {}

    /** Register a pending teleport task. Cancels any previous pending task for the player. */
    public static void register(UUID uuid, BukkitTask task) {
        BukkitTask old = pending.put(uuid, task);
        if (old != null) {
            old.cancel();
        }
    }

    /** Cancel and remove a pending task. Returns true if a task was cancelled. */
    public static boolean cancel(UUID uuid) {
        BukkitTask task = pending.remove(uuid);
        if (task != null) {
            task.cancel();
            return true;
        }
        return false;
    }

    /** Remove from tracking without cancelling (call inside the task when it fires normally). */
    public static void complete(UUID uuid) {
        pending.remove(uuid);
    }

    public static boolean has(UUID uuid) {
        return pending.containsKey(uuid);
    }
}
