package com.darkniightz.core.system;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class EventModeCombatListener implements Listener {
    private final EventModeManager manager;

    public EventModeCombatListener(EventModeManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        manager.handleParticipantDeath(event.getEntity());
        if (manager.shouldKeepInventoryOnDeath(event.getEntity())) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setDeathMessage(null);
        } else if (manager.isParticipantInHardcore(event.getEntity())) {
            // Collect all drops into the loot pool — winner claims everything
            manager.collectHardcoreLoot(event.getEntity(), event.getDrops());
            event.getDrops().clear();
            event.setDeathMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        manager.handleParticipantRespawn(event.getPlayer());
    }
}