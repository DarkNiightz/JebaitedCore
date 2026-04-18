package com.darkniightz.core.gui;

import com.darkniightz.core.Messages;
import com.darkniightz.core.system.EventModeManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays pending hardcore loot pool rewards and lets winners claim in one click.
 */
public final class LootPoolMenu extends BaseMenu {
    private final JebaitedCore plugin;
    private final EventModeManager events;

    public LootPoolMenu(JebaitedCore plugin, EventModeManager events) {
        super(plugin, ChatColor.DARK_RED + "Hardcore Loot Pool", 54);
        this.plugin = plugin;
        this.events = events;
    }

    @Override
    protected void populate(Player viewer) {
        inventory.clear();
        List<ItemStack> loot = events.getPendingHardcoreLootPreview(viewer);
        int slot = 0;
        for (ItemStack item : loot) {
            if (slot >= 45) break;
            inventory.setItem(slot++, item.clone());
        }
        while (slot < 45) {
            inventory.setItem(slot++, pane());
        }
        inventory.setItem(49, claimButton(loot.size()));
        inventory.setItem(53, closeButton());
    }

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
        if (slot == 49) {
            int delivered = events.claimPendingHardcoreLoot(who);
            if (delivered <= 0) {
                who.sendMessage(Messages.prefixed("§7No pending hardcore loot to claim."));
            } else {
                who.sendMessage(Messages.prefixed("§aClaimed §f" + delivered + "§a loot stack" + (delivered == 1 ? "" : "s") + "."));
            }
            who.closeInventory();
            return true;
        }
        if (slot == 53) {
            who.closeInventory();
            return true;
        }
        return true;
    }

    private ItemStack pane() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_GRAY + " ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack claimButton(int stacks) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Claim Loot");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Pending stacks: " + ChatColor.YELLOW + stacks);
            lore.add(ChatColor.DARK_GRAY + " ");
            lore.add(ChatColor.GREEN + "Click to claim everything.");
            lore.add(ChatColor.GRAY + "Overflow drops at your feet.");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack closeButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Close");
            item.setItemMeta(meta);
        }
        return item;
    }
}
