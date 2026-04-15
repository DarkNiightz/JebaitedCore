package com.darkniightz.core.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

public abstract class BaseMenu {
    protected final Plugin plugin;
    protected final String title;
    protected final int size; // multiple of 9
    protected Inventory inventory;

    protected BaseMenu(Plugin plugin, String title, int size) {
        this.plugin = plugin;
        this.title = title;
        this.size = Math.max(9, Math.min(54, size - (size % 9 == 0 ? 0 : size % 9))); // clamp to [9,54] and multiple of 9
    }

    public void open(Player player) {
        if (inventory == null) {
            inventory = Bukkit.createInventory(player, size, title);
            populate(player);
        }
        MenuService.get().open(player, this);
    }

    public Inventory getInventory() {
        return inventory;
    }

    protected abstract void populate(Player viewer);

    /**
     * Return true if click is handled and event should be cancelled.
     */
    public abstract boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick);
}
