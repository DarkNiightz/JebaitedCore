package com.darkniightz.core.eventmode;

import com.darkniightz.core.system.EventModeManager;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * CTF wool flag pickup (enemy team only at base poles).
 */
public final class CtfFlagListener implements Listener {

    private final EventModeManager eventModeManager;

    public CtfFlagListener(EventModeManager eventModeManager) {
        this.eventModeManager = eventModeManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        eventModeManager.handleCtfFlagInteract(player, block);
    }
}
