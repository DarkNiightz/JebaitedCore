package com.darkniightz.core.hub;

import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

/**
 * HubProtectionListener enforces hub world protections:
 * - Only admins+ can place/break blocks (configurable minimum rank).
 * - Cancel all damage and hunger loss.
 * - On respawn, send players to world spawn.
 */
public class HubProtectionListener implements Listener {
    private final Plugin plugin;
    private final ProfileStore profiles;
    private final RankManager ranks;

    public HubProtectionListener(Plugin plugin, ProfileStore profiles, RankManager ranks) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.ranks = ranks;
    }

    private String minBuildRank() {
        return plugin.getConfig().getString("hub.protection.min_rank_build", "admin");
    }

    private boolean disableDamage() {
        return plugin.getConfig().getBoolean("hub.protection.disable_damage", true);
    }

    private boolean disableHunger() {
        return plugin.getConfig().getBoolean("hub.protection.disable_hunger", true);
    }

    private boolean respawnAtSpawn() {
        return plugin.getConfig().getBoolean("hub.protection.on_death_teleport_spawn", true);
    }

    private boolean disableMobBlockDamage() {
        return plugin.getConfig().getBoolean("hub.protection.disable_mob_block_damage", true);
    }

    private boolean disableMobTargeting() {
        return plugin.getConfig().getBoolean("hub.protection.disable_mob_targeting", true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent event) {
        String min = minBuildRank();
        PlayerProfile p = profiles.getOrCreate(event.getPlayer(), ranks.getDefaultGroup());
        if (!ranks.isAtLeast(p.getPrimaryRank(), min)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        String min = minBuildRank();
        PlayerProfile p = profiles.getOrCreate(event.getPlayer(), ranks.getDefaultGroup());
        if (!ranks.isAtLeast(p.getPrimaryRank(), min)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (!disableDamage()) return;
        if (event.getEntity() instanceof org.bukkit.entity.Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onFood(FoodLevelChangeEvent event) {
        if (!disableHunger()) return;
        if (event.getEntity() instanceof org.bukkit.entity.Player) {
            event.setCancelled(true);
            event.setFoodLevel(20);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!respawnAtSpawn()) return;
        Location spawn = event.getPlayer().getWorld().getSpawnLocation();
        if (spawn == null) spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
        if (spawn != null) event.setRespawnLocation(spawn);
    }

    // Prevent explosions from destroying blocks in hub
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!disableMobBlockDamage()) return;
        event.blockList().clear();
    }

    // Prevent withers, endermen, ravagers etc. from modifying blocks
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!disableMobBlockDamage()) return;
        if (!(event.getEntity() instanceof org.bukkit.entity.Player)) {
            event.setCancelled(true);
        }
    }

    // Prevent any mob from targeting players in hub
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEntityTargetPlayer(EntityTargetLivingEntityEvent event) {
        if (!disableMobTargeting()) return;
        if (event.getTarget() instanceof org.bukkit.entity.Player) {
            event.setCancelled(true);
        }
    }
}
