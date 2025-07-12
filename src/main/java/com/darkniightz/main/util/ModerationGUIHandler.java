package com.darkniightz.main.util;

import com.darkniightz.main.Core;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ModerationGUIHandler implements Listener {

    private static final Map<UUID, String> pendingReason = new HashMap<>();

    public static void openMainModGUI(Player player, boolean shiftClick) {
        if (shiftClick && RankUtil.getRankLevel(player) >= 5) {
            openAdminSubGUI(player);
            return;
        }
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.RED + "Moderation Tools");
        addModItem(gui, 10, Material.CLOCK, ChatColor.BLUE + "Mute Player");
        addModItem(gui, 12, Material.LEATHER_BOOTS, ChatColor.YELLOW + "Kick Player");
        addModItem(gui, 14, Material.BARRIER, ChatColor.DARK_RED + "Ban Player");
        addModItem(gui, 16, Material.WRITTEN_BOOK, ChatColor.GREEN + "Player History");
        player.openInventory(gui);
    }

    private static void addModItem(Inventory gui, int slot, Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        gui.setItem(slot, item);
    }

    private static void openAdminSubGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "Admin Tools");
        addModItem(gui, 10, Material.BOOK, ChatColor.GREEN + "Set Rank");
        addModItem(gui, 12, Material.WRITABLE_BOOK, ChatColor.BLUE + "Reload Config");
        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null) return;

            event.setCancelled(true);  // Cancel all clicks in mod GUIs for control

            UUID uuid = player.getUniqueId();

            // Main Mod GUI clicks - open player selector for actions
            if (event.getView().getTitle().equals(ChatColor.RED + "Moderation Tools")) {
                String action = null;
                if (clicked.getType() == Material.CLOCK) action = "mute";
                else if (clicked.getType() == Material.LEATHER_BOOTS) action = "kick";
                else if (clicked.getType() == Material.BARRIER) action = "ban";
                else if (clicked.getType() == Material.WRITTEN_BOOK) action = "history";

                if (action != null) {
                    player.closeInventory();
                    openPlayerSelector(player, action);
                }
                return;
            }

            // Player Selector clicks - handle based on action
            if (event.getView().getTitle().contains(ChatColor.GRAY + "Select Player for ")) {
                if (clicked.getType() == Material.PLAYER_HEAD) {
                    String target = clicked.getItemMeta().getDisplayName();
                    String action = event.getView().getTitle().replace(ChatColor.GRAY + "Select Player for ", "").trim();
                    player.closeInventory();
                    if (action.equals("kick") || action.equals("history")) {
                        // Direct action for kick and history
                        String command = action.equals("kick") ? "kick " + target + " default_reason" : "history " + target;
                        player.performCommand(command);
                        player.sendMessage(ChatColor.GREEN + "Performed " + action + " on " + target);
                    } else {
                        // For mute/ban, open reason/time
                        openReasonTimeGUI(player, action, target);
                    }
                }
                return;
            }

            // Reason/Time GUI clicks - select reason (don't close), time (apply and close)
            if (event.getView().getTitle().contains(ChatColor.GRAY + "Reason & Time for ")) {
                String[] parts = event.getView().getTitle().replace(ChatColor.GRAY + "Reason & Time for ", "").split(" ");
                String action = parts[0];
                String target = parts[1];

                String selected = ChatColor.stripColor(clicked.getItemMeta().getDisplayName()).trim();
                if (clicked.getType() == Material.PAPER) {  // Reason - select but don't close
                    pendingReason.put(uuid, selected);
                    player.sendMessage(ChatColor.YELLOW + "Selected reason: " + selected + ". Now select a time to apply.");
                } else if (clicked.getType() == Material.CLOCK) {  // Time - apply with reason or default
                    String reason = pendingReason.getOrDefault(uuid, "default_reason");
                    player.performCommand(action + " " + target + " " + selected + " " + reason);
                    player.sendMessage(ChatColor.GREEN + "Applied " + action + " to " + target + " for " + selected + " with reason: " + reason);
                    pendingReason.remove(uuid);
                    player.closeInventory();
                }
                return;
            }

            // Admin Sub-GUI clicks
            if (event.getView().getTitle().equals(ChatColor.DARK_RED + "Admin Tools")) {
                if (clicked.getType() == Material.BOOK) {
                    player.performCommand("setrank");
                } else if (clicked.getType() == Material.WRITABLE_BOOK) {
                    player.performCommand("core reload");
                }
                player.closeInventory();
                return;
            }
        }
    }

    private void openPlayerSelector(Player player, String action) {
        Inventory gui = Bukkit.createInventory(null, 54, ChatColor.GRAY + "Select Player for " + action);
        List<Player> online = (List<Player>) Bukkit.getOnlinePlayers();
        for (int i = 0; i < online.size() && i < 54; i++) {
            Player p = online.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(p);
            meta.setDisplayName(p.getName());
            head.setItemMeta(meta);
            gui.setItem(i, head);
        }
        player.openInventory(gui);
    }

    private void openReasonTimeGUI(Player player, String action, String target) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.GRAY + "Reason & Time for " + action + " " + target);
        List<String> reasons = Core.getInstance().getConfig().getStringList("moderation.reasons");
        List<String> times = Core.getInstance().getConfig().getStringList("moderation.times");

        // Reasons (paper, left)
        for (int i = 0; i < reasons.size() && i < 9; i++) {
            ItemStack reason = new ItemStack(Material.PAPER);
            ItemMeta meta = reason.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + reasons.get(i));
            reason.setItemMeta(meta);
            gui.setItem(i, reason);
        }

        // Times (clock, right)
        for (int i = 0; i < times.size() && i < 9; i++) {
            ItemStack time = new ItemStack(Material.CLOCK);
            ItemMeta meta = time.getItemMeta();
            meta.setDisplayName(ChatColor.GREEN + times.get(i));
            time.setItemMeta(meta);
            gui.setItem(18 + i, time);
        }

        player.openInventory(gui);
    }
}