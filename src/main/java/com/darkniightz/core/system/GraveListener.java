package com.darkniightz.core.system;

import com.darkniightz.main.JebaitedCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GraveListener implements Listener {
    private final JebaitedCore plugin;
    private final GraveManager graves;

    private final Map<UUID, UUID> openGraveByViewer = new ConcurrentHashMap<>();

    public GraveListener(JebaitedCore plugin, GraveManager graves) {
        this.plugin = plugin;
        this.graves = graves;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        var cause = player.getLastDamageCause() == null ? null : player.getLastDamageCause().getCause();
        if (!graves.shouldCreateGraveForPlayerDeath(player, cause)) {
            return;
        }

        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        if (drops.isEmpty()) return;

        GraveManager.Grave grave = graves.createNormalGrave(player, player.getLocation(), drops);
        if (grave == null) return;

        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepLevel(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        // Ignore off-hand duplicate events — each right-click fires once per hand
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;
        Block block = event.getClickedBlock();
        if (!graves.isGraveBlock(block)) return;

        event.setCancelled(true);
        GraveManager.Grave grave = graves.getByBlock(block.getLocation());
        if (grave == null) return;

        Player player = event.getPlayer();
        if (grave.combatLog() && player.getUniqueId().equals(grave.owner())) {
            player.sendMessage(com.darkniightz.core.Messages.prefixed("§cYou combat logged. You cannot recover this grave."));
            return;
        }

        openGraveInventory(player, grave);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!graves.isGraveBlock(block)) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(com.darkniightz.core.Messages.prefixed("§cThis grave cannot be broken until it is emptied or expires."));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> graves.isGraveBlock(block));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID graveId = openGraveByViewer.get(player.getUniqueId());
        if (graveId == null) return;

        GraveManager.Grave grave = findGrave(graveId);
        if (grave == null) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        String expectedTitle = titleFor(grave);
        if (!event.getView().getTitle().equals(expectedTitle)) return;

        // Allow moving items in/out of grave inventory.
        if (event.isShiftClick()) {
            event.setCancelled(false);
            return;
        }
        event.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID graveId = openGraveByViewer.get(player.getUniqueId());
        if (graveId == null) return;

        GraveManager.Grave grave = findGrave(graveId);
        if (grave == null) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        String expectedTitle = titleFor(grave);
        if (!event.getView().getTitle().equals(expectedTitle)) return;

        event.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID graveId = openGraveByViewer.remove(player.getUniqueId());
        if (graveId == null) return;

        GraveManager.Grave grave = findGrave(graveId);
        if (grave == null) return;

        String expectedTitle = titleFor(grave);
        if (!event.getView().getTitle().equals(expectedTitle)) return;

        List<ItemStack> contents = new ArrayList<>();
        for (ItemStack item : event.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) continue;
            contents.add(item.clone());
        }
        graves.saveInventory(grave.id(), contents);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Check for a pending combat log grave and handle the relog
        java.util.Optional<GraveManager.Grave> clGrave = graves.getAllForOwner(player.getUniqueId())
                .stream().filter(GraveManager.Grave::combatLog).findFirst();
        if (clGrave.isPresent()) {
            GraveManager.Grave cg = clGrave.get();
            // Use a 5-tick delay: lets WorldChangeListener's 1-tick hub routing run first,
            // and acts as a safety net in case that routing didn't complete (e.g. dead respawn
            // in SMP before the join-route fires).
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                // Inform the player of the consequences
                org.bukkit.Location l = cg.location();
                player.sendMessage(com.darkniightz.core.Messages.prefixed(
                        "§cYou combat logged last session and died. Your items are locked at §f"
                        + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()
                        + " §c\u2014 anyone can loot them."));
                // Safety-net: ensure they end up in hub, not stuck in SMP
                com.darkniightz.core.world.WorldManager wm = plugin.getWorldManager();
                com.darkniightz.core.world.SpawnManager sm = plugin.getSpawnManager();
                if (wm != null && sm != null && !wm.isHub(player)) {
                    org.bukkit.World hub = Bukkit.getWorld(wm.getHubWorldName());
                    if (hub != null) {
                        org.bukkit.Location spawn = sm.getSpawnForWorld(hub.getName());
                        if (spawn == null) spawn = hub.getSpawnLocation();
                        player.teleport(spawn);
                    }
                }
            }, 5L);
        }

        graves.trackOwnerToLatestGrave(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        openGraveByViewer.remove(event.getPlayer().getUniqueId());
        graves.stopTracking(event.getPlayer().getUniqueId());
    }

    private void openGraveInventory(Player viewer, GraveManager.Grave grave) {
        Inventory inv = Bukkit.createInventory(null, 54, titleFor(grave));
        int i = 0;
        for (ItemStack item : grave.contents()) {
            if (i >= inv.getSize()) break;
            inv.setItem(i++, item.clone());
        }
        // openInventory MUST come before put: opening a new inventory fires InventoryCloseEvent
        // for any currently open inventory, which calls onInventoryClose and removes whatever
        // entry is in openGraveByViewer. Putting after ensures we only set the entry once the
        // new view is actually active, so a subsequent close correctly saves back to this grave.
        viewer.openInventory(inv);
        openGraveByViewer.put(viewer.getUniqueId(), grave.id());
    }

    private String titleFor(GraveManager.Grave grave) {
        return "§8Grave " + grave.id().toString().substring(0, 6);
    }

    private GraveManager.Grave findGrave(UUID graveId) {
        return graves.getById(graveId);
    }
}
