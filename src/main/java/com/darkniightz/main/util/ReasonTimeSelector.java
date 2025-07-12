package com.darkniightz.main.util;

import com.darkniightz.main.Core;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class ReasonTimeSelector {

    public static void open(Player caller, String action, String target) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.GRAY + "Reason & Time for " + action + " " + target);
        List<String> reasons = Core.getInstance().getConfig().getStringList("moderation.reasons");
        List<String> times = Core.getInstance().getConfig().getStringList("moderation.times");

        // Reasons buttons (paper items, left side)
        for (int i = 0; i < reasons.size() && i < 9; i++) {
            ItemStack reasonItem = new ItemStack(Material.PAPER);
            ItemMeta meta = reasonItem.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + reasons.get(i));
            reasonItem.setItemMeta(meta);
            gui.setItem(i, reasonItem);
        }

        // Times buttons (clock items, right side)
        for (int i = 0; i < times.size() && i < 9; i++) {
            ItemStack timeItem = new ItemStack(Material.CLOCK);
            ItemMeta meta = timeItem.getItemMeta();
            meta.setDisplayName(ChatColor.GREEN + times.get(i));
            timeItem.setItemMeta(meta);
            gui.setItem(18 + i, timeItem);
        }

        caller.openInventory(gui);
    }
}