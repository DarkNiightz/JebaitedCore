package com.darkniightz.core.system;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.Plugin;

/**
 * Intercepts fatal damage for event participants so they NEVER actually die.
 * Cancelling the damage event means no death screen, no respawn sequence, no DO_IMMEDIATE_RESPAWN
 * fight. The engine decides what happens next (spectator for FFA/Duels, KOTH ring spawns or world spawn).
 */
public class EventModeCombatListener implements Listener {
    private final EventModeManager manager;
    private final Plugin plugin;

    public EventModeCombatListener(EventModeManager manager, Plugin plugin) {
        this.manager = manager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFatalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!manager.isParticipant(player)) return;
        // Only intercept damage that would actually kill (health would reach 0 or below)
        if (player.getHealth() - event.getFinalDamage() > 0) return;
        // Cancel the killing blow — player stays alive, zero death screen, zero respawn dance
        event.setCancelled(true);
        Player killer = null;
        if (event instanceof EntityDamageByEntityEvent by && by.getDamager() instanceof Player damager) {
            killer = damager;
        }
        manager.handleParticipantFatalDamage(player, killer);
    }
}