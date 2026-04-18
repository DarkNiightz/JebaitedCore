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
 * <p><b>Uncontested rule:</b> each 1s tick, at most one active participant may be
 * inside the hill cuboid. If exactly one player is in the zone, they earn +1s toward
 * their total. If zero or two or more are in the zone, nobody earns that second
 * (contested or empty hill). At time expiry, the highest uncontested total wins;
 * hardcore ties at the top split the loot pool equally (see {@code EventEngine}).
 */
public final class KothHandler implements EventHandler {

    private final Plugin plugin;
    private final SessionCuboidSupplier cuboidSupplier;
    /** Arena spawns when configured; otherwise engine falls back to SMP world spawn. */
    private final SessionLocationSupplier respawnLocationSupplier;
    private final FinishCallback finishCallback;

    @FunctionalInterface public interface SessionCuboidSupplier { Cuboid get(EventSession session); }
    @FunctionalInterface public interface SessionLocationSupplier { Location get(EventSession session); }
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

    public KothHandler(Plugin plugin, SessionCuboidSupplier cuboidSupplier,
                       SessionLocationSupplier respawnLocationSupplier, FinishCallback finishCallback) {
        this.plugin                     = plugin;
        this.cuboidSupplier             = cuboidSupplier;
        this.respawnLocationSupplier    = respawnLocationSupplier;
        this.finishCallback             = finishCallback;
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
                Location spawn = respawnLocationSupplier.get(session);
                if (spawn != null) player.teleport(spawn);
            } else {
                if (snapshot != null) snapshot.restore(player);
                else {
                    Location spawn = respawnLocationSupplier.get(session);
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
        Cuboid hill = cuboidSupplier.get(session);
        if (hill == null) return;
        List<UUID> onHill = new ArrayList<>();
        for (UUID id : session.active) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            if (hill.contains(p.getLocation())) {
                onHill.add(id);
            }
        }
        if (onHill.size() == 1) {
            session.kothSeconds.merge(onHill.get(0), 1, Integer::sum);
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

        // Who is on hill (contested if 2+)
        Cuboid hill = cuboidSupplier.get(session);
        String holder = "§7Nobody";
        if (hill != null) {
            List<Player> on = new ArrayList<>();
            for (UUID id : session.active) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && hill.contains(p.getLocation())) {
                    on.add(p);
                }
            }
            if (on.size() == 1) {
                holder = "§e" + on.get(0).getName();
            } else if (on.size() > 1) {
                holder = "§cContested §7(" + on.size() + ")";
            }
        }

        // Top 3 by hill seconds
        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(session.kothSeconds.entrySet());
        sorted.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());

        List<String> lines = new ArrayList<>();
        lines.add("§7Hill: " + holder);
        if (!sorted.isEmpty()) {
            UUID leadId = sorted.get(0).getKey();
            int leadSecs = sorted.get(0).getValue();
            Player lp = Bukkit.getPlayer(leadId);
            String leadName = lp != null ? lp.getName() : leadId.toString().substring(0, Math.min(8, leadId.toString().length()));
            lines.add("§7Unc. leader: §f" + leadName + " §8— §a" + leadSecs + "s");
        }
        lines.add("§7Match ends: §f" + remain + "s");
        for (int i = 0; i < Math.min(3, sorted.size()); i++) {
            UUID id = sorted.get(i).getKey();
            int secs = sorted.get(i).getValue();
            Player p = Bukkit.getPlayer(id);
            String name = p != null ? p.getName() : id.toString().substring(0, 6);
            lines.add("§e" + (i + 1) + ". §f" + name + " §8— §a" + secs + "s unc.");
        }
        return lines;
    }
}
