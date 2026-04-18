package com.darkniightz.core.gui;

import com.darkniightz.core.chat.ChatInputService;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.shop.ShopManager;
import com.darkniightz.core.shop.ShopPriceRow;
import com.darkniightz.core.system.EconomyManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ROADMAP §17 — 54-slot server shop GUI.
 */
public class ShopMenu extends BaseMenu {

    private static final int[] GRID_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    /** Row 0: all nine categories */
    private static final int[] TAB_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    private static final Material[] TAB_ICONS = {
            Material.GRASS_BLOCK, Material.WHEAT, Material.ROTTEN_FLESH, Material.DIAMOND, Material.RED_DYE,
            Material.MUSIC_DISC_CAT, Material.BREAD, Material.FLOWER_POT, Material.REDSTONE
    };

    /** Bottom row: head left, pagination, search center */
    private static final int SLOT_HEAD = 45;
    private static final int SLOT_PREV = 46;
    private static final int SLOT_SEARCH = 49;
    private static final int SLOT_NEXT = 52;

    private final JebaitedCore plugin;
    private final ShopManager shop;
    private final EconomyManager economy;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final String categoryId;
    private final int page;
    private final String searchQuery;

    public ShopMenu(JebaitedCore plugin, ShopManager shop, EconomyManager economy,
                    ProfileStore profiles, RankManager ranks,
                    String categoryId, int page, String searchQuery) {
        super(plugin, plugin.getConfig().getString("server_shop.menu_title", "§8Server Shop"), 54);
        this.plugin = plugin;
        this.shop = shop;
        this.economy = economy;
        this.profiles = profiles;
        this.ranks = ranks;
        this.categoryId = categoryId == null ? ShopManager.categoryIds().get(0) : categoryId.toLowerCase(Locale.ROOT);
        this.page = Math.max(0, page);
        this.searchQuery = searchQuery == null || searchQuery.isBlank() ? null : searchQuery.trim();
    }

    private boolean stackConfirmEnabled(Player who) {
        PlayerProfile p = profiles.getOrCreate(who, ranks.getDefaultGroup());
        return p == null || p.getPreference(PlayerProfile.PREF_SHOP_STACK_CONFIRM, true);
    }

    @Override
    protected void populate(Player viewer) {
        List<ShopPriceRow> all = filterRows(shop.rowsInCategory(this.categoryId));
        int perPage = GRID_SLOTS.length;
        int totalPages = Math.max(1, (all.size() + perPage - 1) / perPage);
        int pg = Math.min(this.page, totalPages - 1);

        // Row 1 (9–17): spacer under tabs
        for (int s = 9; s <= 17; s++) {
            inventory.setItem(s, filler(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        List<String> catIds = ShopManager.categoryIds();
        for (int i = 0; i < TAB_SLOTS.length && i < catIds.size(); i++) {
            String cid = catIds.get(i);
            boolean active = cid.equalsIgnoreCase(this.categoryId);
            Material icon = i < TAB_ICONS.length ? TAB_ICONS[i] : Material.PAPER;
            List<String> lore = new ArrayList<>();
            lore.add("§7" + ShopManager.categoryTitle(cid));
            if (active) lore.add("§a§lSelected");
            ItemStack tab = new ItemBuilder(icon)
                    .name((active ? "§e§l" : "§7") + ShopManager.categoryTitle(cid))
                    .lore(lore)
                    .glow(active)
                    .build();
            if (active) {
                tab.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
            }
            inventory.setItem(TAB_SLOTS[i], tab);
        }

        // Item grid
        int start = pg * perPage;
        for (int i = 0; i < GRID_SLOTS.length; i++) {
            int idx = start + i;
            if (idx >= all.size()) {
                inventory.setItem(GRID_SLOTS[i], filler(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " "));
                continue;
            }
            ShopPriceRow row = all.get(idx);
            Material mat = row.material();
            if (mat == null) {
                inventory.setItem(GRID_SLOTS[i], filler(Material.BARRIER, "§cInvalid"));
                continue;
            }
            ItemStack show = new ItemStack(mat);
            ItemMeta meta = show.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§f" + row.displayName());
                List<String> lore = new ArrayList<>();
                lore.add("§7Buy: §a" + economy.format(row.buyPrice()) + " §8each");
                lore.add("§7Sell: §c" + economy.format(row.sellPrice()) + " §8each");
                int maxBuy = Math.max(1, Math.min(row.maxStack(), 64));
                lore.add("§8Shift-buy stack: §e" + economy.format(row.buyPrice() * maxBuy));
                lore.add("");
                lore.add("§eLeft-click §7Buy 1  §8|  §eShift-left §7Buy stack");
                if (stackConfirmEnabled(viewer)) {
                    lore.add("§8(confirm screen)");
                }
                lore.add("§eRight-click §7Sell 1  §8|  §eShift-right §7Sell all");
                meta.setLore(lore);
                show.setItemMeta(meta);
            }
            inventory.setItem(GRID_SLOTS[i], show);
        }

        // Bottom row: head (left), prev, search, next
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta hMeta = (SkullMeta) head.getItemMeta();
        if (hMeta != null) {
            hMeta.setOwningPlayer(viewer);
            PlayerProfile profile = profiles.getOrCreate(viewer, ranks.getDefaultGroup());
            String name = profile != null && profile.getName() != null ? profile.getName() : viewer.getName();
            hMeta.setDisplayName("§e" + name);
            hMeta.setLore(List.of("§7Balance: §a" + economy.format(economy.getBalance(viewer))));
            head.setItemMeta(hMeta);
        }
        inventory.setItem(SLOT_HEAD, head);

        for (int s : new int[]{47, 48, 50, 51, 53}) {
            inventory.setItem(s, filler(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        if (pg > 0) {
            inventory.setItem(SLOT_PREV, new ItemBuilder(Material.ARROW).name("§ePrevious page").lore(List.of("§7Page " + (pg + 1) + " / " + totalPages)).build());
        } else {
            inventory.setItem(SLOT_PREV, filler(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        if (pg < totalPages - 1) {
            inventory.setItem(SLOT_NEXT, new ItemBuilder(Material.ARROW).name("§eNext page").lore(List.of("§7Page " + (pg + 1) + " / " + totalPages)).build());
        } else {
            inventory.setItem(SLOT_NEXT, filler(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        List<String> sq = new ArrayList<>();
        sq.add("§7§oLeft-click: §7type a filter");
        sq.add("§7§oRight-click: §7clear filter");
        if (this.searchQuery != null) {
            sq.add("§aActive: §f" + this.searchQuery);
        }
        inventory.setItem(SLOT_SEARCH, new ItemBuilder(Material.HOPPER).name("§eSearch").lore(sq).build());
    }

    private List<ShopPriceRow> filterRows(List<ShopPriceRow> rows) {
        if (searchQuery == null) return rows;
        String q = searchQuery.toLowerCase(Locale.ROOT);
        List<ShopPriceRow> out = new ArrayList<>();
        for (ShopPriceRow r : rows) {
            if (r.displayName().toLowerCase(Locale.ROOT).contains(q)
                    || r.itemKey().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(r);
            }
        }
        return out;
    }

    private static ItemStack filler(Material m, String name) {
        return new ItemBuilder(m).name(name).build();
    }

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
        List<String> catIds = ShopManager.categoryIds();
        for (int i = 0; i < TAB_SLOTS.length; i++) {
            if (slot == TAB_SLOTS[i] && i < catIds.size()) {
                new ShopMenu(plugin, shop, economy, profiles, ranks, catIds.get(i), 0, searchQuery).open(who);
                return true;
            }
        }
        if (slot == SLOT_HEAD) {
            return true;
        }
        if (slot == SLOT_PREV) {
            List<ShopPriceRow> all = filterRows(shop.rowsInCategory(categoryId));
            int totalPages = Math.max(1, (all.size() + GRID_SLOTS.length - 1) / GRID_SLOTS.length);
            int pg = Math.min(page, totalPages - 1);
            if (pg > 0) {
                new ShopMenu(plugin, shop, economy, profiles, ranks, categoryId, pg - 1, searchQuery).open(who);
            }
            return true;
        }
        if (slot == SLOT_NEXT) {
            List<ShopPriceRow> all = filterRows(shop.rowsInCategory(categoryId));
            int totalPages = Math.max(1, (all.size() + GRID_SLOTS.length - 1) / GRID_SLOTS.length);
            int pg = Math.min(page, totalPages - 1);
            if (pg < totalPages - 1) {
                new ShopMenu(plugin, shop, economy, profiles, ranks, categoryId, pg + 1, searchQuery).open(who);
            }
            return true;
        }
        if (slot == SLOT_SEARCH) {
            if (rightClick) {
                if (searchQuery != null) {
                    new ShopMenu(plugin, shop, economy, profiles, ranks, categoryId, 0, null).open(who);
                }
                return true;
            }
            if (leftClick) {
                ChatInputService.prompt(who, "§7Type part of an item name to filter.", plugin, text -> {
                    if (text == null) return;
                    String t = text.trim();
                    if (t.isEmpty()) {
                        new ShopMenu(plugin, shop, economy, profiles, ranks, categoryId, 0, null).open(who);
                    } else {
                        new ShopMenu(plugin, shop, economy, profiles, ranks, categoryId, 0, t).open(who);
                    }
                });
            }
            return true;
        }

        int gridIndex = -1;
        for (int i = 0; i < GRID_SLOTS.length; i++) {
            if (GRID_SLOTS[i] == slot) {
                gridIndex = i;
                break;
            }
        }
        if (gridIndex < 0) {
            return true;
        }

        List<ShopPriceRow> all = filterRows(shop.rowsInCategory(categoryId));
        int perPage = GRID_SLOTS.length;
        int totalPages = Math.max(1, (all.size() + perPage - 1) / perPage);
        int pg = Math.min(Math.max(0, page), totalPages - 1);
        int idx = pg * perPage + gridIndex;
        if (idx < 0 || idx >= all.size()) {
            return true;
        }
        ShopPriceRow row = all.get(idx);

        if (rightClick) {
            boolean ok = shop.sell(who, row, shiftClick);
            if (ok) {
                int reopenPage = pg;
                Bukkit.getScheduler().runTask(plugin, () ->
                        new ShopMenu(plugin, shop, economy, profiles, ranks, categoryId, reopenPage, searchQuery).open(who));
            }
            return true;
        }
        if (leftClick) {
            if (shiftClick && stackConfirmEnabled(who)) {
                new ShopStackConfirmMenu(plugin, shop, economy, profiles, ranks, row, categoryId, pg, searchQuery).open(who);
                return true;
            }
            if (shiftClick) {
                boolean ok = shop.buy(who, row, 1, true);
                if (ok) {
                    int reopenPage = pg;
                    Bukkit.getScheduler().runTask(plugin, () ->
                            new ShopMenu(plugin, shop, economy, profiles, ranks, categoryId, reopenPage, searchQuery).open(who));
                }
                return true;
            }
            boolean ok = shop.buy(who, row, 1, false);
            if (ok) {
                int reopenPage = pg;
                Bukkit.getScheduler().runTask(plugin, () ->
                        new ShopMenu(plugin, shop, economy, profiles, ranks, categoryId, reopenPage, searchQuery).open(who));
            }
            return true;
        }
        return true;
    }

    @Override
    public void open(Player player) {
        this.inventory = null;
        super.open(player);
    }
}
