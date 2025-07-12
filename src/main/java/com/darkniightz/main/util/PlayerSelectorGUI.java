package com.darkniightz.main.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

public class PlayerSelectorGUI implements Listener {

    public static void open(Player caller, String action) {  // action = "ban", "mute", etc.
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
        caller.openInventory(gui);
    }
}