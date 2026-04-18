package com.darkniightz.core.eventmode;

import com.darkniightz.core.system.EventModeManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Blocks player-vs-player damage between teammates during CTF (red vs red, blue vs blue).
 * Cross-team hits are unaffected. Works with {@link EventModeManager#areCtfTeammates(Player, Player)}.
 */
public final class CtfTeamDamageListener implements Listener {

    private final EventModeManager events;

    public CtfTeamDamageListener(EventModeManager events) {
        this.events = events;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTeammateHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!events.isActiveEventParticipant(attacker)) return;
        if (!events.isActiveEventParticipant(victim)) return;
        if (!events.areCtfTeammates(attacker, victim)) return;
        event.setCancelled(true);
    }
}
