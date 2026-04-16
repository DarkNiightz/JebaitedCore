package com.darkniightz.core.eventmode.handler;

import com.darkniightz.core.eventmode.EventSession;
import com.darkniightz.core.eventmode.InventorySnapshot;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.*;

/**
 * Handles FFA and Hardcore FFA elimination logic.
 * Also handles DUELS (1v1 bracket) — elimination logic is identical, just fewer players.
 */
public final class FfaHandler implements EventHandler {

    private final Plugin plugin;

    /** Callback to the engine — called when this handler determines the event is over. */
    private final EndCallback endCallback;

    /** Callback to teleport a player to the world spawn (used on HC respawn). */
    private final LocationSupplier worldSpawnSupplier;

    @FunctionalInterface
    public interface EndCallback {
        void onElimination(EventSession session);
    }

    @FunctionalInterface
    public interface LocationSupplier {
        Location get();
    }

    public FfaHandler(Plugin plugin, EndCallback endCallback, LocationSupplier worldSpawnSupplier) {
        this.plugin = plugin;
        this.endCallback = endCallback;
        this.worldSpawnSupplier = worldSpawnSupplier;
    }

    @Override
    public void onStart(EventSession session) {
        // Inventory snapshots + HC inventory clear are handled by EventEngine before calling onStart.
        // Nothing extra needed here for FFA/DUELS.
    }

    @Override
    public void onDeath(EventSession session, Player player) {
        UUID id = player.getUniqueId();
        session.eliminated.add(id);
        session.pendingReturn.add(id);
        checkElimination(session);
    }

    @Override
    public void onRespawn(EventSession session, Player player) {
        UUID id = player.getUniqueId();
        if (!session.pendingReturn.remove(id)) return;

        InventorySnapshot snapshot = session.snapshots.remove(id);
        boolean isHC = session.spec.kind.isHardcore();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (isHC) {
                // HC: teleport to world spawn, reset vitals, no inventory to restore
                double hp = Math.max(1.0, Math.min(player.getMaxHealth(), 20.0));
                player.setHealth(hp);
                player.setFoodLevel(20);
                if (snapshot != null && snapshot.returnLocation() != null
                        && snapshot.returnLocation().getWorld() != null) {
                    player.teleport(snapshot.returnLocation());
                } else {
                    Location spawn = worldSpawnSupplier.get();
                    if (spawn != null) player.teleport(spawn);
                }
            } else {
                // Normal FFA/DUELS: switch to spectator (inventory was kept via setKeepInventory)
                if (snapshot != null) {
                    session.spectatorSnapshots.put(id, snapshot);
                } else {
                    session.spectatorSnapshots.put(id, null);
                }
                session.spectating.add(id);
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage(Component.text("\u2717 Eliminated! Spectating the rest of the event.",
                        NamedTextColor.YELLOW));
            }
        });
    }

    @Override
    public void onTick(EventSession session) {
        // FFA is event-driven (deaths), not tick-driven.
    }

    @Override
    public void onEnd(EventSession session) {
        // Snapshot restoration is handled by EventEngine.restoreSnapshots() which reads
        // session.snapshots and session.spectatorSnapshots.
    }

    @Override
    public List<String> getScoreboardLines(EventSession session) {
        int alive = session.active.size() - session.eliminated.size();
        return List.of(
                "§7Alive: §f" + Math.max(0, alive) + "§8/§7" + session.active.size(),
                "§7Eliminated: §c" + session.eliminated.size()
        );
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private void checkElimination(EventSession session) {
        List<UUID> alive = new ArrayList<>();
        for (UUID id : session.active) {
            if (!session.eliminated.contains(id)) alive.add(id);
        }
        if (alive.size() <= 1 && !session.active.isEmpty()) {
            endCallback.onElimination(session);
        }
    }
}
