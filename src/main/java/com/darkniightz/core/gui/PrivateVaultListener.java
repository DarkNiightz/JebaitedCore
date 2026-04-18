package com.darkniightz.core.gui;

import com.darkniightz.core.system.PrivateVaultManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.UUID;

public class PrivateVaultListener implements Listener {

    private final JebaitedCore plugin;
    private final PrivateVaultManager vaultManager;

    public PrivateVaultListener(JebaitedCore plugin, PrivateVaultManager vaultManager) {
        this.plugin = plugin;
        this.vaultManager = vaultManager;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof PrivateVaultHolder holder)) return;
        if (holder.isReadOnly()) return; // read-only inspection — discard changes
        // Save to vault OWNER, not necessarily the player who had it open
        UUID saveTarget = holder.getTargetUUID() != null ? holder.getTargetUUID() : player.getUniqueId();
        vaultManager.saveVaultPage(saveTarget, holder.getPage(), event.getInventory());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof PrivateVaultHolder holder)) return;

        int slot = event.getRawSlot();

        // Read-only: block all clicks
        if (holder.isReadOnly()) {
            event.setCancelled(true);
            return;
        }

        // Bottom border row: slots 45-53. Always cancel — these are navigation/decoration.
        if (slot >= 45 && slot <= 53) {
            event.setCancelled(true);

            switch (slot) {
                case 45 -> { // ◄ Previous page
                    if (holder.getPage() > 0) {
                        player.closeInventory();
                        if (holder.getTargetUUID() != null) {
                            vaultManager.openVaultForStaff(player, holder.getTargetUUID(), holder.getPage() - 1, false);
                        } else {
                            vaultManager.openVault(player, holder.getPage() - 1);
                        }
                    }
                }
                case 49 -> player.closeInventory(); // Close button
                case 53 -> { // ► Next page
                    if (holder.getPage() < holder.getMaxPages() - 1) {
                        player.closeInventory();
                        if (holder.getTargetUUID() != null) {
                            vaultManager.openVaultForStaff(player, holder.getTargetUUID(), holder.getPage() + 1, false);
                        } else {
                            vaultManager.openVault(player, holder.getPage() + 1);
                        }
                    }
                }
                default -> { /* glass pane — just cancelled */ }
            }
            return;
        }

        // Shift-clicks from player inventory that would land in bottom row — cancel them too.
        if (event.isShiftClick() && slot >= 54) {
            // Check if the shift-click would spill into the border row; easiest: just allow
            // normal item movement into slots 0-44 and let Bukkit handle it.
            // We only need to block clicks that hit border slots (already handled above).
        }
    }

    /** Prevent drag operations that touch the border row. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!(event.getInventory().getHolder() instanceof PrivateVaultHolder)) return;

        for (int slot : event.getRawSlots()) {
            if (slot >= 45 && slot <= 53) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** Persist immediately when contents change (not only on close). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClickSave(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof PrivateVaultHolder holder)) return;
        if (holder.isReadOnly()) return;
        int raw = event.getRawSlot();
        if (raw >= 45 && raw <= 53) return;
        scheduleVaultSave(player, holder);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDragSave(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof PrivateVaultHolder holder)) return;
        if (holder.isReadOnly()) return;
        scheduleVaultSave(player, holder);
    }

    private void scheduleVaultSave(Player player, PrivateVaultHolder holder) {
        UUID saveTarget = holder.getTargetUUID() != null ? holder.getTargetUUID() : player.getUniqueId();
        int page = holder.getPage();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof PrivateVaultHolder h)
                    || h.getPage() != page) {
                return;
            }
            vaultManager.saveVaultPage(saveTarget, page, player.getOpenInventory().getTopInventory());
        });
    }
}

