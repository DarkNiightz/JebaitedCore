package com.darkniightz.core.gui;

import com.darkniightz.core.Messages;
import com.darkniightz.core.system.EventModeManager;
import com.darkniightz.core.system.MaterialCompat;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * 54-slot event admin control GUI.
 * Opened via /events (no-args) or /event gui.
 * <p>
 * Layout:
 * Row 0 (0-8)  : black glass border
 * Row 1 (9-17) : [status] [event start buttons x7]
 * Row 2 (18-26): [stop] [gap] [tp world] [gap] [rebuild]
 * Row 3 (27-35): [ffa header] [ffa set] [ffa view] [ffa tp] [gap] [duels header] [duels set] [duels view] [duels tp]
 * Row 4 (36-44): black glass border
 * Row 5 (45-53): black glass border, [close] at slot 49
 */
public class EventsAdminMenu extends BaseMenu {

    private final EventModeManager em;

    public EventsAdminMenu(Plugin plugin, EventModeManager em) {
        super(plugin, "§8Event Control", 54);
        this.em = em;
    }

    @Override
    protected void populate(Player viewer) {
        inventory.clear();
        renderBorders();

        String statusLine = em.getStatusLine();

        // Slot 4: status book
        inventory.setItem(4, new ItemBuilder(Material.BOOK)
                .name("§d§lEvent Status")
                .lore(List.of("§r" + statusLine, "", "§7Click for chat status"))
                .build());

        // Slots 10-16: event start buttons
        List<String> keys = em.getConfiguredEventKeys();
        int[] startSlots = {10, 11, 12, 13, 14, 15, 16};
        boolean active = em.isActive();
        for (int i = 0; i < Math.min(keys.size(), startSlots.length); i++) {
            String key = keys.get(i);
            inventory.setItem(startSlots[i], new ItemBuilder(active ? Material.RED_DYE : Material.GREEN_DYE)
                    .name((active ? "§c" : "§a") + key.toUpperCase())
                    .lore(List.of(active ? "§7An event is already active." : "§7Click to start this event"))
                    .build());
        }

        // Row 2 controls
        inventory.setItem(19, new ItemBuilder(Material.RED_CONCRETE)
                .name("§c§l■ Stop Event")
                .lore(List.of("§7Stop the currently active event."))
                .build());

        inventory.setItem(22, new ItemBuilder(MaterialCompat.resolve(Material.COMPASS, "ENDER_PEARL"))
                .name("§b✈ Teleport to Event World")
                .lore(List.of("§7TP to the event world spawn.", "§7Grants temporary edit access."))
                .build());

        inventory.setItem(25, new ItemBuilder(Material.BARRIER)
                .name("§4⚠ Rebuild Event World")
                .lore(List.of("§7Wipe and recreate event world.", "§cRight-click to confirm."))
                .build());

        // Row 3: FFA arena controls
        int ffaCount = em.getArenaSpawnCount("ffa");
        Location ffaSpawn = em.getFirstArenaSpawn("ffa");
        inventory.setItem(28, new ItemBuilder(Material.DIAMOND_SWORD)
                .name("§6FFA Arena §8— §7" + ffaCount + " spawn(s)")
                .lore(List.of(ffaSpawn != null
                        ? String.format("§7Spawn #1: §f%.1f, %.1f, %.1f", ffaSpawn.getX(), ffaSpawn.getY(), ffaSpawn.getZ())
                        : "§7No spawns set yet"))
                .build());

        inventory.setItem(29, new ItemBuilder(Material.STICK)
                .name("§a⊕ Set FFA Spawn")
                .lore(List.of("§7Saves your current position as an FFA spawn."))
                .build());

        inventory.setItem(30, new ItemBuilder(Material.ENDER_EYE)
                .name("§b◎ View FFA Spawns")
                .lore(List.of("§7Ghost blocks at spawn points for 30s."))
                .build());

        if (ffaCount > 0) {
            inventory.setItem(31, new ItemBuilder(Material.ARROW)
                    .name("§e✈ TP to FFA Spawn #1")
                    .lore(List.of("§7Teleport to the centre of the FFA map."))
                    .build());
        }

        // Row 3: Duels arena controls
        int duelsCount = em.getArenaSpawnCount("duels");
        Location duelsSpawn = em.getFirstArenaSpawn("duels");
        inventory.setItem(33, new ItemBuilder(Material.IRON_SWORD)
                .name("§5Duels Arena §8— §7" + duelsCount + " spawn(s)")
                .lore(List.of(duelsSpawn != null
                        ? String.format("§7Spawn #1: §f%.1f, %.1f, %.1f", duelsSpawn.getX(), duelsSpawn.getY(), duelsSpawn.getZ())
                        : "§7No spawns set yet"))
                .build());

        inventory.setItem(34, new ItemBuilder(Material.STICK)
                .name("§a⊕ Set Duels Spawn")
                .lore(List.of("§7Saves your current position as a Duels spawn."))
                .build());

        inventory.setItem(35, new ItemBuilder(Material.ENDER_EYE)
                .name("§b◎ View Duels Spawns")
                .lore(List.of("§7Ghost blocks at spawn points for 30s."))
                .build());

        if (duelsCount > 0) {
            inventory.setItem(36, new ItemBuilder(Material.ARROW)
                    .name("§e✈ TP to Duels Spawn #1")
                    .lore(List.of("§7Teleport to Duels spawn #1."))
                    .build());
        }

        // Close
        inventory.setItem(49, new ItemBuilder(Material.BARRIER)
                .name("§cClose")
                .build());
    }

    private void renderBorders() {
        var border = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 9; i++) inventory.setItem(i, border);         // row 0
        for (int i = 36; i < 54; i++) inventory.setItem(i, border);       // rows 4-5
        inventory.setItem(9, border);  inventory.setItem(17, border);      // row 1 edges
        inventory.setItem(18, border); inventory.setItem(26, border);      // row 2 edges
        inventory.setItem(27, border); inventory.setItem(32, border);      // row 3 gap + edge
    }

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
        // Close
        if (slot == 49) { who.closeInventory(); return true; }

        // Status book
        if (slot == 4) {
            who.sendMessage(Messages.prefixed(em.getStatusLine()));
            return true;
        }

        // Event start buttons (slots 10-16)
        List<String> keys = em.getConfiguredEventKeys();
        int[] startSlots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < Math.min(keys.size(), startSlots.length); i++) {
            if (slot == startSlots[i] && !em.isActive()) {
                EventModeManager.ActionResult r = em.startEvent(keys.get(i));
                who.sendMessage(Messages.prefixed(r.message()));
                who.closeInventory();
                return true;
            }
        }

        // Stop
        if (slot == 19) {
            EventModeManager.ActionResult r = em.stopEvent("GUI stop by " + who.getName());
            who.sendMessage(Messages.prefixed(r.message()));
            refreshMenu(who);
            return true;
        }

        // TP to event world
        if (slot == 22) {
            Location spawn = em.getAdminEditSpawn();
            if (spawn != null && spawn.getWorld() != null) {
                who.closeInventory();
                if (who.teleport(spawn)) {
                    em.grantAdminEditAccess(who);
                    who.sendMessage(Messages.prefixed("§aTeleported to event world."));
                } else {
                    who.sendMessage(Messages.prefixed("§cCould not teleport."));
                }
            } else {
                who.sendMessage(Messages.prefixed("§cEvent world unavailable."));
            }
            return true;
        }

        // Rebuild world (right-click only)
        if (slot == 25 && rightClick) {
            EventModeManager.ActionResult r = em.rebuildEventWorld(true);
            who.sendMessage(Messages.prefixed(r.message()));
            who.closeInventory();
            return true;
        }

        // FFA controls
        if (slot == 29) {
            EventModeManager.ActionResult r = em.setupArenaSpawn(who, "ffa");
            who.sendMessage(Messages.prefixed(r.message()));
            refreshMenu(who);
            return true;
        }
        if (slot == 30) {
            EventModeManager.ActionResult r = em.viewArenaSpawns(who, "ffa", 30);
            who.sendMessage(Messages.prefixed(r.message()));
            return true;
        }
        if (slot == 31) {
            Location spawn = em.getFirstArenaSpawn("ffa");
            if (spawn != null && spawn.getWorld() != null) {
                who.closeInventory();
                who.teleport(spawn);
                who.sendMessage(Messages.prefixed("§aTP to FFA spawn #1."));
            } else {
                who.sendMessage(Messages.prefixed("§7No FFA spawn configured yet."));
            }
            return true;
        }

        // Duels controls
        if (slot == 34) {
            EventModeManager.ActionResult r = em.setupArenaSpawn(who, "duels");
            who.sendMessage(Messages.prefixed(r.message()));
            refreshMenu(who);
            return true;
        }
        if (slot == 35) {
            EventModeManager.ActionResult r = em.viewArenaSpawns(who, "duels", 30);
            who.sendMessage(Messages.prefixed(r.message()));
            return true;
        }
        if (slot == 36) {
            Location spawn = em.getFirstArenaSpawn("duels");
            if (spawn != null && spawn.getWorld() != null) {
                who.closeInventory();
                who.teleport(spawn);
                who.sendMessage(Messages.prefixed("§aTP to Duels spawn #1."));
            } else {
                who.sendMessage(Messages.prefixed("§7No Duels spawn configured yet."));
            }
            return true;
        }

        return true; // cancel all other clicks
    }

    private void refreshMenu(Player player) {
        inventory.clear();
        populate(player);
    }
}
