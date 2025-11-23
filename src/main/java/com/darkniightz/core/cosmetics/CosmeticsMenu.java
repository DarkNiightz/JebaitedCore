package com.darkniightz.core.cosmetics;

import com.darkniightz.core.gui.BaseMenu;
import com.darkniightz.core.gui.ItemBuilder;
import com.darkniightz.core.players.ProfileStore;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-level Cosmetics Menu driven by config (menus.cosmetics).
 * Tabs are configurable. For Phase 2, only Wardrobe routes to existing WardrobeMenu;
 * other tabs display placeholders. Gold bar shows a placeholder coin display and
 * on click posts an informational message from config.
 */
public class CosmeticsMenu extends BaseMenu {

    private final ProfileStore profiles;
    private final CosmeticsManager cosmetics;

    // Cached config-derived layout
    private Tab wardrobeTab;
    private Tab particlesTab;
    private Tab trailsTab;
    private Tab gadgetsTab;
    private GoldBar goldBar;

    public CosmeticsMenu(Plugin plugin, CosmeticsManager cosmetics, ProfileStore profiles) {
        super(plugin, titleFromConfig(plugin), sizeFromConfig(plugin));
        this.cosmetics = cosmetics;
        this.profiles = profiles;
    }

    private static String titleFromConfig(Plugin plugin) {
        return plugin.getConfig().getString("menus.cosmetics.title", "§d§lCosmetics");
    }

    private static int sizeFromConfig(Plugin plugin) {
        int s = plugin.getConfig().getInt("menus.cosmetics.size", 54);
        if (s < 9) s = 9; if (s > 54) s = 54; int rem = s % 9; if (rem != 0) s += (9 - rem);
        return s;
    }

    @Override
    protected void populate(Player viewer) {
        Inventory inv = getInventory();

        // Read tabs (& gold bar) from config every open to allow live tweaking
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("menus.cosmetics");
        ConfigurationSection tabs = root == null ? null : root.getConfigurationSection("tabs");
        wardrobeTab = readTab(tabs, "wardrobe", "§aWardrobe", Material.LEATHER_CHESTPLATE, 10);
        particlesTab = readTab(tabs, "particles", "§bParticles", Material.BLAZE_POWDER, 12);
        trailsTab = readTab(tabs, "trails", "§eTrails", Material.FEATHER, 14);
        gadgetsTab = readTab(tabs, "gadgets", "§7Gadgets", Material.STICK, 16);

        goldBar = readGoldBar(root);

        // Clear inventory first
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, null);

        // Place enabled tabs
        placeTab(inv, wardrobeTab);
        placeTab(inv, particlesTab);
        placeTab(inv, trailsTab);
        placeTab(inv, gadgetsTab);

        // Place gold bar if configured
        if (goldBar != null) {
            List<String> lore = new ArrayList<>();
            // Placeholder balance (Phase 3 will make this live)
            lore.add("§7Balance: §e0 coins");
            lore.add("§8Click for info");
            inv.setItem(goldBar.slot, new ItemBuilder(goldBar.icon)
                    .name("§6Cosmetic Coins")
                    .lore(lore)
                    .build());
        }
    }

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick) {
        if (wardrobeTab != null && wardrobeTab.enabled && slot == wardrobeTab.slot) {
            new WardrobeMenu(plugin, cosmetics, profiles).open(who);
            return true;
        }
        if (particlesTab != null && particlesTab.enabled && slot == particlesTab.slot) {
            who.sendMessage("§bParticles §7menu is coming soon.");
            return true;
        }
        if (trailsTab != null && trailsTab.enabled && slot == trailsTab.slot) {
            who.sendMessage("§eTrails §7menu is coming soon.");
            return true;
        }
        if (gadgetsTab != null && gadgetsTab.enabled && slot == gadgetsTab.slot) {
            who.sendMessage("§7Gadgets §7menu is coming soon.");
            return true;
        }

        if (goldBar != null && slot == goldBar.slot) {
            if (goldBar.message != null && !goldBar.message.isEmpty()) {
                who.sendMessage(goldBar.message);
            } else {
                who.sendMessage("§6Earn cosmetic coins by playing minigames and events!");
            }
            return true;
        }

        return true; // read-only menu
    }

    private void placeTab(Inventory inv, Tab tab) {
        if (tab == null || !tab.enabled) return;
        int slot = clampSlot(tab.slot, inv.getSize());
        inv.setItem(slot, new ItemBuilder(tab.icon).name(tab.name).build());
        tab.slot = slot; // update after clamp
    }

    private Tab readTab(ConfigurationSection tabs, String key, String defName, Material defIcon, int defSlot) {
        boolean enabled = true;
        String name = defName;
        Material icon = defIcon;
        int slot = defSlot;
        if (tabs != null) {
            ConfigurationSection sec = tabs.getConfigurationSection(key);
            if (sec != null) {
                enabled = sec.getBoolean("enabled", true);
                name = sec.getString("name", defName);
                String iconName = sec.getString("icon");
                if (iconName != null) {
                    try { icon = Material.valueOf(iconName.toUpperCase()); } catch (IllegalArgumentException ignored) {}
                }
                if (sec.isInt("slot")) slot = sec.getInt("slot", defSlot);
            }
        }
        return new Tab(enabled, name, icon, slot);
    }

    private GoldBar readGoldBar(ConfigurationSection root) {
        if (root == null) return null;
        ConfigurationSection sec = root.getConfigurationSection("gold_bar");
        if (sec == null) return null;
        String iconName = sec.getString("icon", "GOLD_INGOT");
        Material icon;
        try { icon = Material.valueOf(iconName.toUpperCase()); } catch (IllegalArgumentException e) { icon = Material.GOLD_INGOT; }
        int slot = sec.getInt("slot", 53);
        String msg = sec.getString("message", "§6Earn cosmetic coins by playing minigames and events!");
        return new GoldBar(icon, clampSlot(slot, size), msg);
    }

    private int clampSlot(int slot, int invSize) {
        return Math.max(0, Math.min(invSize - 1, slot));
    }

    private static final class Tab {
        final boolean enabled;
        final String name;
        final Material icon;
        int slot;
        Tab(boolean enabled, String name, Material icon, int slot) { this.enabled = enabled; this.name = name; this.icon = icon; this.slot = slot; }
    }

    private static final class GoldBar {
        final Material icon;
        final int slot;
        final String message;
        GoldBar(Material icon, int slot, String message) { this.icon = icon; this.slot = slot; this.message = message; }
    }
}
