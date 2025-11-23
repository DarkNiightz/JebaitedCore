package com.darkniightz.core.hub;

import com.darkniightz.core.gui.MenuService;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.List;

public class HotbarNavigatorListener implements Listener {
    private final Plugin plugin;

    public HotbarNavigatorListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("feature_flags.hub.hotbar_navigator", true)) return;
        Player p = event.getPlayer();

        // Navigator item
        var nav = plugin.getConfig().getConfigurationSection("hotbar.navigator");
        if (nav != null && nav.getBoolean("enabled", true)) {
            int slot = Math.max(0, Math.min(8, nav.getInt("slot", 4)));
            Material mat = materialOrDefault(nav.getString("item"), Material.COMPASS);
            String name = nav.getString("name", "§b§lNavigator");
            List<String> lore = nav.getStringList("lore");
            ItemStack existing = p.getInventory().getItem(slot);
            if (existing == null || existing.getType() == Material.AIR) {
                ItemStack give = new ItemStack(mat);
                ItemMeta meta = give.getItemMeta();
                meta.setDisplayName(name);
                if (lore != null && !lore.isEmpty()) meta.setLore(lore);
                give.setItemMeta(meta);
                p.getInventory().setItem(slot, give);
            }
        }

        // Cosmetics item
        var cos = plugin.getConfig().getConfigurationSection("hotbar.cosmetics");
        if (cos != null && cos.getBoolean("enabled", true)) {
            int slot = Math.max(0, Math.min(8, cos.getInt("slot", 0)));
            Material mat = materialOrDefault(cos.getString("item"), Material.GOLDEN_HELMET);
            String name = cos.getString("name", "§d§lCosmetics");
            List<String> lore = cos.getStringList("lore");
            ItemStack existing = p.getInventory().getItem(slot);
            if (existing == null || existing.getType() == Material.AIR) {
                ItemStack give = new ItemStack(mat);
                ItemMeta meta = give.getItemMeta();
                meta.setDisplayName(name);
                if (lore != null && !lore.isEmpty()) meta.setLore(lore);
                give.setItemMeta(meta);
                p.getInventory().setItem(slot, give);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("feature_flags.hub.hotbar_navigator", true)) return;
        if (event.getItem() == null) return;

        // Navigator click
        var nav = plugin.getConfig().getConfigurationSection("hotbar.navigator");
        if (nav != null && nav.getBoolean("enabled", true)) {
            String name = nav.getString("name", "§b§lNavigator");
            if (matches(event.getItem(), name)) {
                new ServersMenu(plugin).open(event.getPlayer());
                event.setCancelled(true);
                return;
            }
        }

        // Cosmetics click -> open via command to avoid tight coupling
        var cos = plugin.getConfig().getConfigurationSection("hotbar.cosmetics");
        if (cos != null && cos.getBoolean("enabled", true)) {
            String name = cos.getString("name", "§d§lCosmetics");
            if (matches(event.getItem(), name)) {
                event.getPlayer().performCommand("cosmetics");
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (!plugin.getConfig().getBoolean("feature_flags.hub.hotbar_navigator", true)) return;
        // Navigator drop prevention
        var nav = plugin.getConfig().getConfigurationSection("hotbar.navigator");
        if (nav != null && nav.getBoolean("enabled", true)) {
            String name = nav.getString("name", "§b§lNavigator");
            if (matches(event.getItemDrop().getItemStack(), name)) {
                event.setCancelled(true);
                return;
            }
        }
        // Cosmetics drop prevention
        var cos = plugin.getConfig().getConfigurationSection("hotbar.cosmetics");
        if (cos != null && cos.getBoolean("enabled", true)) {
            String name = cos.getString("name", "§d§lCosmetics");
            if (matches(event.getItemDrop().getItemStack(), name)) {
                event.setCancelled(true);
            }
        }
    }

    private boolean matches(ItemStack stack, String expectedName) {
        if (stack == null) return false;
        ItemMeta meta = stack.getItemMeta();
        return meta != null && expectedName.equals(meta.getDisplayName());
    }

    private Material materialOrDefault(String name, Material def) {
        if (name == null) return def;
        try { return Material.valueOf(name.toUpperCase()); } catch (IllegalArgumentException e) { return def; }
    }
}
