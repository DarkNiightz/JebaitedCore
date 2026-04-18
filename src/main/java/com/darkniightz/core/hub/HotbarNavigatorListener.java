package com.darkniightz.core.hub;

import com.darkniightz.core.gui.StatsMenu;
import com.darkniightz.core.system.MaterialCompat;
import com.darkniightz.core.world.WorldManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Event.Result;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HotbarNavigatorListener implements Listener {
    private static final String KEY_TYPE = "hub_hotbar_type";

    private final Plugin plugin;
    private final NamespacedKey typeKey;
    private final WorldManager worldManager;

    public HotbarNavigatorListener(Plugin plugin) {
        this.plugin = plugin;
        this.typeKey = new NamespacedKey(plugin, KEY_TYPE);
        this.worldManager = plugin instanceof JebaitedCore core ? core.getWorldManager() : new WorldManager(plugin);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled()) return;
        if (!worldManager.isHub(event.getPlayer())) {
            clearHubHotbar(event.getPlayer());
            return;
        }
        ensureHubHotbar(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!enabled()) return;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!worldManager.isHub(event.getPlayer())) {
                    clearHubHotbar(event.getPlayer());
                    return;
                }
                ensureHubHotbar(event.getPlayer());
            }
        }.runTaskLater(plugin, 1L);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onHotbarInteract(PlayerInteractEvent event) {
        if (!enabled()) return;
        if (!worldManager.isHub(event.getPlayer())) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!isClickAction(event.getAction())) return;

        ItemStack held = event.getItem();
        if (held == null) {
            held = event.getPlayer().getInventory().getItemInMainHand();
        }
        HotbarType type = readType(held);
        if (type == null) return;

        openMenuFromType(event.getPlayer(), type);
        event.setCancelled(true);
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!enabled()) return;
        if (!worldManager.isHub(event.getPlayer())) return;
        if (event.getItem() == null) return;
        if (!isClickAction(event.getAction())) return;

        HotbarType type = readType(event.getItem());
        if (type == null) return;

        openMenuFromType(event.getPlayer(), type);
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (!enabled()) return;
        if (!worldManager.isHub(event.getPlayer())) return;
        if (isProtected(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            refreshHotbar(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!enabled()) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!worldManager.isHub((Player) event.getWhoClicked())) return;

        Player player = (Player) event.getWhoClicked();
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        HotbarType clickedType = readType(current);
        if (clickedType == null) {
            clickedType = readType(cursor);
        }
        if (clickedType != null) {
            event.setCancelled(true);
            openMenuFromType(player, clickedType);
            refreshHotbar(player);
            return;
        }

        if (isProtectedSlot(event.getSlot())) {
            event.setCancelled(true);
            refreshHotbar((Player) event.getWhoClicked());
            return;
        }

        if ((event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY || event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD || event.getAction() == InventoryAction.HOTBAR_SWAP)
                && touchesProtectedSlot(event.getView().getTopInventory().getSize(), event.getRawSlot(), event.getHotbarButton())) {
            event.setCancelled(true);
            refreshHotbar((Player) event.getWhoClicked());
            return;
        }

        if (event.getClick() == ClickType.NUMBER_KEY && isProtected(current)) {
            event.setCancelled(true);
            refreshHotbar((Player) event.getWhoClicked());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (!enabled()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!worldManager.isHub(player)) return;
        Set<Integer> slots = event.getRawSlots();
        for (int slot : slots) {
            if (isProtectedSlot(slot)) {
                event.setCancelled(true);
                return;
            }
        }
        if (isProtected(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (!enabled()) return;
        if (!worldManager.isHub(event.getPlayer())) return;
        if (isProtected(event.getMainHandItem()) || isProtected(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        if (!enabled()) return;
        if (!worldManager.isHub(event.getEntity())) return;
        event.getDrops().removeIf(this::isProtected);
    }

    public void ensureHubHotbar(Player player) {
        if (player == null || !enabled() || !worldManager.isHub(player)) return;
        purgeExtraProtectedItems(player);
        placeItem(player, getSlot("hotbar.navigator.slot", 4), "hotbar.navigator", Material.COMPASS, "§b§lNavigator", plugin.getConfig().getStringList("hotbar.navigator.lore"));
        placeItem(player, getSlot("hotbar.toybox.slot", 3), "hotbar.toybox", MaterialCompat.resolve(Material.PAPER, "FIREWORK_ROCKET", "FIREWORK", "PAPER"), "§d§lToybox", plugin.getConfig().getStringList("hotbar.toybox.lore"));
        placeItem(player, getSlot("hotbar.cosmetics.slot", 7), "hotbar.cosmetics", Material.BOOK, "§5§lCosmetics", plugin.getConfig().getStringList("hotbar.cosmetics.lore"));
        placeProfileHead(player, getSlot("hotbar.profile.slot", 8), "hotbar.profile");
    }

    public void clearHubHotbar(Player player) {
        if (player == null) return;
        for (int slot : getReservedSlots()) {
            clearProtectedSlot(player, slot);
        }
        cleanupDuplicates(player);
    }

    private void placeItem(Player player, int slot, String path, Material fallback, String defaultName, List<String> defaultLore) {
        if (!isHotbarSectionEnabled(path)) {
            clearProtectedSlot(player, slot);
            return;
        }
        ItemStack existing = player.getInventory().getItem(slot);
        if (isProtected(existing) && readType(existing) != null) {
            return;
        }
        ItemStack stack = new ItemStack(materialOrDefault(plugin.getConfig().getString(path + ".item"), fallback));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.getConfig().getString(path + ".name", defaultName));
            List<String> lore = plugin.getConfig().getStringList(path + ".lore");
            meta.setLore((lore == null || lore.isEmpty()) ? defaultLore : lore);
            markProtected(meta, path.substring("hotbar.".length()));
            stack.setItemMeta(meta);
        }
        player.getInventory().setItem(slot, stack);
    }

    /**
     * Player skull — opens the same profile GUI as {@code /stats}. Slot defaults to index 8 (hotbar “9”).
     */
    private void placeProfileHead(Player player, int slot, String path) {
        if (!isHotbarSectionEnabled(path)) {
            clearProtectedSlot(player, slot);
            return;
        }
        ItemStack existing = player.getInventory().getItem(slot);
        if (isProtected(existing) && readType(existing) != null) {
            return;
        }
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.setOwningPlayer(player);
        meta.setDisplayName(plugin.getConfig().getString(path + ".name", "§f§lProfile"));
        List<String> lore = plugin.getConfig().getStringList(path + ".lore");
        if (lore == null || lore.isEmpty()) {
            lore = List.of(
                    "§7Your stats, rank, and activity",
                    "§8Across this network"
            );
        }
        meta.setLore(lore);
        markProtected(meta, path.substring("hotbar.".length()));
        stack.setItemMeta(meta);
        player.getInventory().setItem(slot, stack);
    }

    private boolean isHotbarSectionEnabled(String path) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
        if (section == null) {
            // Older config.yml files may omit `hotbar.profile`; treat as enabled so upgrades get the head without a merge.
            return "hotbar.profile".equals(path);
        }
        return section.getBoolean("enabled", true);
    }

    private void clearProtectedSlot(Player player, int slot) {
        ItemStack existing = player.getInventory().getItem(slot);
        if (isProtected(existing)) {
            player.getInventory().setItem(slot, null);
        }
    }

    private void cleanupDuplicates(Player player) {
        if (player == null) return;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (isReservedSlot(i)) continue;
            if (isProtected(player.getInventory().getItem(i))) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    private void purgeExtraProtectedItems(Player player) {
        cleanupDuplicates(player);
        if (player == null) return;
        for (int slot : getReservedSlots()) {
            ItemStack item = player.getInventory().getItem(slot);
            if (isProtected(item)) {
                continue;
            }
            if (item != null && item.getType() != Material.AIR) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    private void refreshHotbar(Player player) {
        if (player == null) return;
        new BukkitRunnable() {
            @Override
            public void run() {
                ensureHubHotbar(player);
            }
        }.runTask(plugin);
    }

    private void openMenuFromType(Player player, HotbarType type) {
        if (player == null || type == null) {
            return;
        }
        switch (type) {
            case NAVIGATOR -> new ServersMenu(plugin).open(player);
            case COSMETICS -> player.performCommand("cosmetics");
            case PROFILE -> {
                if (plugin instanceof JebaitedCore core) {
                    new StatsMenu(
                            core,
                            core.getProfileStore(),
                            core.getRankManager(),
                            core.getAchievementManager(),
                            player
                    ).open(player);
                }
            }
            case TOYBOX -> {
                if (plugin instanceof JebaitedCore core && core.getToyboxManager() != null) {
                    core.getToyboxManager().trigger(player);
                } else {
                    player.sendMessage(com.darkniightz.core.Messages.prefixed("§7Choose a gadget in /cosmetics first."));
                }
            }
        }
    }

    private void markProtected(ItemMeta meta, String type) {
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.toLowerCase(Locale.ROOT));
    }

    private HotbarType readType(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String raw = pdc.get(typeKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return null;
        try {
            return HotbarType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isProtected(ItemStack stack) {
        return readType(stack) != null;
    }

    private boolean isProtectedSlot(int slot) {
        return slot == getSlot("hotbar.navigator.slot", 4)
                || slot == getSlot("hotbar.toybox.slot", 3)
                || slot == getSlot("hotbar.cosmetics.slot", 7)
                || slot == getSlot("hotbar.profile.slot", 8);
    }

    private boolean isReservedSlot(int slot) {
        return isProtectedSlot(slot);
    }

    private int[] getReservedSlots() {
        return new int[] {
                getSlot("hotbar.navigator.slot", 4),
                getSlot("hotbar.toybox.slot", 3),
                getSlot("hotbar.cosmetics.slot", 7),
                getSlot("hotbar.profile.slot", 8)
        };
    }

    private boolean touchesProtectedSlot(int topSize, int rawSlot, int hotbarButton) {
        if (rawSlot < topSize) {
            return isProtectedSlot(rawSlot);
        }
        if (hotbarButton >= 0) {
            int hotbarSlot = topSize + hotbarButton;
            return isProtectedSlot(hotbarButton) || isProtectedSlot(hotbarSlot - topSize);
        }
        return false;
    }

    private boolean isClickAction(Action action) {
        return action == Action.RIGHT_CLICK_AIR
                || action == Action.RIGHT_CLICK_BLOCK
                || action == Action.LEFT_CLICK_AIR
                || action == Action.LEFT_CLICK_BLOCK;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("feature_flags.hub.hotbar_navigator", true);
    }

    private int getSlot(String path, int def) {
        return Math.max(0, Math.min(8, plugin.getConfig().getInt(path, def)));
    }

    private Material materialOrDefault(String name, Material def) {
        return MaterialCompat.resolveConfigured(name, def, "PAPER", "COMPASS", "BOOK");
    }

    private enum HotbarType {
        NAVIGATOR,
        TOYBOX,
        COSMETICS,
        PROFILE
    }
}
