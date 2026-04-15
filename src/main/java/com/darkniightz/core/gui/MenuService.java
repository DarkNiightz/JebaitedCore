package com.darkniightz.core.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central menu registry + routing.
 */
public final class MenuService {
    private static final MenuService INSTANCE = new MenuService();

    public static MenuService get() { return INSTANCE; }

    private final Map<UUID, BaseMenu> openMenus = new ConcurrentHashMap<>();

    private MenuService() {}

    public void open(Player player, BaseMenu menu) {
        openMenus.put(player.getUniqueId(), menu);
        player.openInventory(menu.getInventory());
    }

    public void close(Player player) {
        openMenus.remove(player.getUniqueId());
        player.closeInventory();
    }

    public BaseMenu getOpen(Player player) {
        return openMenus.get(player.getUniqueId());
    }

    public void clear(Player player) {
        openMenus.remove(player.getUniqueId());
    }
}
