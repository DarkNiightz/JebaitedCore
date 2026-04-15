package com.darkniightz.core.gui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ItemBuilder {
    private final ItemStack stack;

    public ItemBuilder(Material material) {
        this.stack = new ItemStack(material);
    }

    public ItemBuilder amount(int amt) {
        stack.setAmount(Math.max(1, Math.min(64, amt)));
        return this;
    }

    public ItemBuilder name(String displayName) {
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(displayName);
        stack.setItemMeta(meta);
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        if (lines == null) return this;
        ItemMeta meta = stack.getItemMeta();
        meta.setLore(new ArrayList<>(lines));
        stack.setItemMeta(meta);
        return this;
    }

    public ItemBuilder glow(boolean enable) {
        ItemMeta meta = stack.getItemMeta();
        if (enable) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        stack.setItemMeta(meta);
        return this;
    }

    public ItemStack build() {
        return stack;
    }
}
