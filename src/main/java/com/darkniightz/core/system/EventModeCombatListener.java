package com.darkniightz.core.system;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

public class EventModeCombatListener implements Listener {
    private final EventModeManager manager;
    private final Plugin plugin;

    public EventModeCombatListener(EventModeManager manager, Plugin plugin) {
        this.manager = manager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!manager.isParticipant(player)) return;

        manager.handleParticipantDeath(player);
        if (manager.shouldKeepInventoryOnDeath(player)) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setDeathMessage(null);
        } else if (manager.isParticipantInHardcore(player)) {
            // Collect all drops into the loot pool — winner claims everything
            manager.collectHardcoreLoot(player, event.getDrops());
            event.getDrops().clear();
            event.setDeathMessage(null);
        }

        // Force auto-respawn after 1 tick — enough for the death packet to process without a visible death screen.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.isDead()) {
                player.spigot().respawn();
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        manager.handleParticipantRespawn(event.getPlayer());
    }
}