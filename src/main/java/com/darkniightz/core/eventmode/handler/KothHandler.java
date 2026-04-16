package com.darkniightz.core.eventmode.handler;

import com.darkniightz.core.eventmode.EventSession;
import com.darkniightz.core.eventmode.InventorySnapshot;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * Handles KOTH and Hardcore KOTH timer-based events.
 *
 * KOTH: Hill-holder accumulates seconds. Most seconds at time expiry wins.
 * HC_KOTH: Same scoring, but dying strips inventory (loot pool) and players
 *          respawn at world spawn — they can still accumulate hill time after respawn.
 */
public final class KothHandler implements EventHandler {

    private final Plugin plugin;
    private final CuboidSupplier cuboidSupplier;
    private final LocationSupplier worldSpawnSupplier;
    private final FinishCallback finishCallback;

    @FunctionalInterface public interface CuboidSupplier  { Cuboid get(); }
    @FunctionalInterface public interface LocationSupplier { Location get(); }
    @FunctionalInterface public interface FinishCallback   { void onKothExpired(EventSession session); }

    /** Minimal axis-aligned bounding box for the KOTH hill. Auto-normalises min/max on construction. */
    public record Cuboid(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        /** Compact canonical constructor — normalises so min <= max on each axis. */
        public Cuboid {
            int loX = Math.min(minX, maxX), hiX = Math.max(minX, maxX);
            int loY = Math.min(minY, maxY), hiY = Math.max(minY, maxY);
            int loZ = Math.min(minZ, maxZ), hiZ = Math.max(minZ, maxZ);
            minX = loX; maxX = hiX; minY = loY; maxY = hiY; minZ = loZ; maxZ = hiZ;
        }
        public boolean contains(Location loc) {
            if (loc == null || loc.getWorld() == null) return false;
            if (!world.equalsIgnoreCase(loc.getWorld().getName())) return false;
            int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    public KothHandler(Plugin plugin, CuboidSupplier cuboidSupplier,
                       LocationSupplier worldSpawnSupplier, FinishCallback finishCallback) {
        this.plugin              = plugin;
        this.cuboidSupplier      = cuboidSupplier;
        this.worldSpawnSupplier  = worldSpawnSupplier;
        this.finishCallback      = finishCallback;
    }

    @Override
    public void onStart(EventSession session) {
        // Hill tick starts in EventEngine once RUNNING state is entered.
    }

    @Override
    public void onDeath(EventSession session, Player player) {
        // KOTH is not elimination-based — dying just sends you back to spawn.
        // HC_KOTH: inventory is stripped (handled by EventEngine), loot pool grows.
        session.pendingReturn.add(player.getUniqueId());
    }

    @Override
    public void onRespawn(EventSession session, Player player) {
        UUID id = player.getUniqueId();
        if (!session.pendingReturn.remove(id)) return;

        InventorySnapshot snapshot = session.snapshots.get(id); // don't remove — they stay active
        boolean isHC = session.spec.kind.isHardcore();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            // For both HC and normal KOTH: respawn at world spawn, reset vitals.
            // Normal KOTH: restore inventory immediately (they're still in the event).
            if (isHC) {
                double hp = Math.max(1.0, Math.min(player.getMaxHealth(), 20.0));
                player.setHealth(hp);
                player.setFoodLevel(20);
                Location spawn = worldSpawnSupplier.get();
                if (spawn != null) player.teleport(spawn);
            } else {
                if (snapshot != null) snapshot.restore(player);
                else {
                    Location spawn = worldSpawnSupplier.get();
                    if (spawn != null) player.teleport(spawn);
                }
            }
        });
    }

    @Override
    public void onTick(EventSession session) {
        if (session.endsAtMs > 0 && System.currentTimeMillis() >= session.endsAtMs) {
            finishCallback.onKothExpired(session);
            return;
        }
        Cuboid hill = cuboidSupplier.get();
        if (hill == null) return;
        for (UUID id : session.active) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            if (!hill.contains(p.getLocation())) continue;
            session.kothSeconds.merge(id, 1, Integer::sum);
        }
    }

    @Override
    public void onEnd(EventSession session) {
        // Snapshot restoration handled by EventEngine.
    }

    @Override
    public List<String> getScoreboardLines(EventSession session) {
        long remain = session.endsAtMs > 0
                ? Math.max(0, (session.endsAtMs - System.currentTimeMillis()) / 1000L) : 0;

        // Find current hill holder
        Cuboid hill = cuboidSupplier.get();
        String holder = "§7Nobody";
        if (hill != null) {
            for (UUID id : session.active) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && hill.contains(p.getLocation())) {
                    holder = "§e" + p.getName();
                    break;
                }
            }
        }

        // Top 3 by hill seconds
        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(session.kothSeconds.entrySet());
        sorted.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());

        List<String> lines = new ArrayList<>();
        lines.add("§7Holder: " + holder);
        lines.add("§7Time: §f" + remain + "s");
        for (int i = 0; i < Math.min(3, sorted.size()); i++) {
            UUID id = sorted.get(i).getKey();
            int secs = sorted.get(i).getValue();
            Player p = Bukkit.getPlayer(id);
            String name = p != null ? p.getName() : id.toString().substring(0, 6);
            lines.add("§e" + (i + 1) + ". §f" + name + " §8— §a" + secs + "s");
        }
        return lines;
    }
}
