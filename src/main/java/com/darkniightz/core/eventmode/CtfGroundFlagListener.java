package com.darkniightz.core.eventmode;

import com.darkniightz.core.system.EventModeManager;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;

/**
 * Picks up CTF flags spawned on the ground when a carrier is downed ({@code Item} + PDC).
 */
public final class CtfGroundFlagListener implements Listener {

    private final EventModeManager eventModeManager;

    public CtfGroundFlagListener(EventModeManager eventModeManager) {
        this.eventModeManager = eventModeManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Item item = event.getItem();
        if (!eventModeManager.handleCtfGroundFlagPickup(player, item)) return;
        event.setCancelled(true);
    }
}
