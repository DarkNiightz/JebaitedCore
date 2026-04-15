package com.darkniightz.core.system;

import com.darkniightz.main.JebaitedCore;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

public class EventWorldProtectionListener implements Listener {

    private final JebaitedCore plugin;
    private final EventModeManager manager;

    public EventWorldProtectionListener(JebaitedCore plugin, EventModeManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        if (isEventWorld(event.getBlock().getWorld()) && !canEdit(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent event) {
        if (isEventWorld(event.getBlock().getWorld()) && !canEdit(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onExplode(EntityExplodeEvent event) {
        if (isEventWorld(event.getLocation().getWorld())) {
            event.blockList().clear();
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (isEventWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isEventWorld(player.getWorld()) || canEdit(player)) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            event.setCancelled(true);
            return;
        }
        if (!manager.isActive()) {
            event.setCancelled(true);
        }
    }

    private boolean isEventWorld(World world) {
        if (world == null) {
            return false;
        }
        String eventWorld = plugin.getConfig().getString("event_mode.world", "events");
        return world.getName().equalsIgnoreCase(eventWorld);
    }

    private boolean canEdit(Player player) {
        if (player == null || !manager.canAdminEdit(player)) {
            return false;
        }
        if (player.isOp() || (plugin.getDevModeManager() != null && plugin.getDevModeManager().isActive(player.getUniqueId()))) {
            return true;
        }
        var profile = plugin.getProfileStore().getOrCreate(player, plugin.getRankManager().getDefaultGroup());
        return profile != null && plugin.getRankManager().isAtLeast(profile.getPrimaryRank(), "admin");
    }
}
