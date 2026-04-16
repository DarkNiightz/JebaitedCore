package com.darkniightz.core.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class MenuListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        BaseMenu menu = MenuService.get().getOpen(player);
        if (menu == null) return;
        if (event.getClickedInventory() == null) { event.setCancelled(true); return; }
        // Always cancel to prevent items being moved out of the menu view
        event.setCancelled(true);
        // Only dispatch handleClick for clicks on the menu itself, not the player's own inventory
        if (!menu.getInventory().equals(event.getClickedInventory())) return;

        int slot = event.getRawSlot();
        boolean left = event.isLeftClick();
        boolean shift = event.isShiftClick();
        boolean right = event.isRightClick();
        boolean handled = menu.handleClick(player, slot, left, shift, right);
        // already cancelled above; no need to re-cancel
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        BaseMenu open = MenuService.get().getOpen(player);
        if (open != null && !event.getInventory().equals(open.getInventory())) return;
        MenuService.get().clear(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        MenuService.get().clear(event.getPlayer());
    }
}
