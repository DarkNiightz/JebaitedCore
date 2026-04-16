package com.darkniightz.core.achievements;

import com.darkniightz.core.achievements.AchievementDefinition.AchievementType;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hooks Bukkit events and drives achievement progress increments.
 */
public final class AchievementListener implements Listener {

    private final JebaitedCore           plugin;
    private final AchievementManager      achievements;
    private final ProfileStore            profiles;
    private final RankManager             ranks;
    /** Accumulates sub-block horizontal distance per player; flushed when >= 1.0. */
    private final Map<UUID, Double>       distAcc = new HashMap<>();

    public AchievementListener(
        JebaitedCore plugin,
        AchievementManager achievements,
        ProfileStore profiles,
        RankManager ranks
    ) {
        this.plugin       = plugin;
        this.achievements = achievements;
        this.profiles     = profiles;
        this.ranks        = ranks;
    }

    // ── Cache lifecycle ────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (achievements == null) return;
        UUID uuid = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> achievements.loadPlayer(uuid));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (achievements == null) return;
        UUID uuid = event.getPlayer().getUniqueId();
        distAcc.remove(uuid); // discard sub-block remainder — losing < 1 block on quit is fine
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> achievements.unloadPlayer(uuid));
    }

    // ── Stat events ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (achievements == null) return;
        UUID victim = event.getEntity().getUniqueId();
        achievements.increment(victim, AchievementType.DEATHS, 1);

        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            achievements.increment(killer.getUniqueId(), AchievementType.KILLS, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (achievements == null) return;
        if (event.getEntity() instanceof Player) return; // handled by onPlayerDeath
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            achievements.increment(killer.getUniqueId(), AchievementType.MOBS_KILLED, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (achievements == null) return;
        achievements.increment(event.getPlayer().getUniqueId(), AchievementType.BLOCKS_BROKEN, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (achievements == null) return;
        achievements.increment(event.getPlayer().getUniqueId(), AchievementType.BLOCKS_PLACED, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (achievements == null) return;
        if (!event.hasChangedPosition()) return;
        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist <= 0) return;
        // Accumulate sub-block fractions; only increment achievements on full blocks
        UUID uuid = event.getPlayer().getUniqueId();
        double total = distAcc.merge(uuid, dist, Double::sum);
        if (total >= 1.0) {
            long blocks = (long) total;
            distAcc.put(uuid, total - blocks);
            achievements.increment(uuid, AchievementType.DISTANCE_TRAVELLED, blocks);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (achievements == null) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        achievements.increment(event.getPlayer().getUniqueId(), AchievementType.FISH_CAUGHT, 1);
    }
}
