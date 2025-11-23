package com.darkniightz.core.hub;

import com.darkniightz.core.gui.BaseMenu;
import com.darkniightz.core.gui.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public class ServersMenu extends BaseMenu {

    public ServersMenu(Plugin plugin) {
        super(plugin, titleFromConfig(plugin), 27);
    }

    private static String titleFromConfig(Plugin plugin) {
        return plugin.getConfig().getString("menus.servers.title", "§b§lServer Navigator");
    }

    @Override
    protected void populate(Player viewer) {
        Inventory inv = getInventory();
        // Read items from config; if not present, provide sensible defaults
        ConfigurationSection base = plugin.getConfig().getConfigurationSection("menus.servers.items");
        // Positions in menu
        place(inv, 11, base, "hub", Material.NETHER_STAR, "§aHub", List.of("§7You are here"));
        place(inv, 13, base, "pvp", Material.IRON_SWORD, "§cPvP", List.of("§7Coming soon"));
        place(inv, 15, base, "survival", Material.OAK_SAPLING, "§2Survival", List.of("§7Coming soon"));
        place(inv, 22, base, "creative", Material.BRICKS, "§dCreative", List.of("§7Coming soon"));
    }

    private void place(Inventory inv, int slot, ConfigurationSection base, String key, Material defMat, String defName, List<String> defLore) {
        boolean enabled = true;
        Material mat = defMat;
        String name = defName;
        List<String> lore = new ArrayList<>(defLore);
        int effectiveSlot = slot;
        if (base != null) {
            ConfigurationSection sec = base.getConfigurationSection(key);
            if (sec != null) {
                enabled = sec.getBoolean("enabled", true);
                String icon = sec.getString("icon");
                if (icon != null) {
                    try { mat = Material.valueOf(icon.toUpperCase()); } catch (IllegalArgumentException ignored) {}
                }
                name = sec.getString("name", name);
                List<String> l = sec.getStringList("lore");
                if (l != null && !l.isEmpty()) lore = l;
                // Optional slot override from config
                if (sec.isInt("slot")) {
                    int s = sec.getInt("slot", slot);
                    // clamp within inventory size
                    effectiveSlot = Math.max(0, Math.min(inv.getSize() - 1, s));
                }
            }
        }
        if (!enabled) return;
        inv.setItem(effectiveSlot, new ItemBuilder(mat).name(name).lore(lore).build());
    }

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick) {
        // For now, just show placeholders and close menu
        switch (slot) {
            case 11 -> who.sendMessage("§aYou are already in the Hub.");
            case 13 -> who.sendMessage("§cPvP §7is coming soon.");
            case 15 -> who.sendMessage("§2Survival §7is coming soon.");
            case 22 -> who.sendMessage("§dCreative §7is coming soon.");
            default -> { return true; }
        }
        who.closeInventory();
        return true;
    }
}
