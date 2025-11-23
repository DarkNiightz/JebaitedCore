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
        var sec = plugin.getConfig().getConfigurationSection("hotbar.navigator");
        if (sec == null || !sec.getBoolean("enabled", true)) return;
        int slot = Math.max(0, Math.min(8, sec.getInt("slot", 4)));
        Material mat = materialOrDefault(sec.getString("item"), Material.COMPASS);
        String name = sec.getString("name", "§b§lNavigator");
        List<String> lore = sec.getStringList("lore");

        Player p = event.getPlayer();
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

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("feature_flags.hub.hotbar_navigator", true)) return;
        var sec = plugin.getConfig().getConfigurationSection("hotbar.navigator");
        if (sec == null || !sec.getBoolean("enabled", true)) return;
        if (event.getItem() == null) return;
        String name = sec.getString("name", "§b§lNavigator");
        if (!matches(event.getItem(), name)) return;
        // Open servers menu
        new ServersMenu(plugin).open(event.getPlayer());
        event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (!plugin.getConfig().getBoolean("feature_flags.hub.hotbar_navigator", true)) return;
        var sec = plugin.getConfig().getConfigurationSection("hotbar.navigator");
        if (sec == null || !sec.getBoolean("enabled", true)) return;
        String name = sec.getString("name", "§b§lNavigator");
        if (matches(event.getItemDrop().getItemStack(), name)) {
            // keep navigator bound in hub
            event.setCancelled(true);
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
