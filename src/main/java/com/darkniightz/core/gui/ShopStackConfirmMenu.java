package com.darkniightz.core.gui;

import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.shop.ShopManager;
import com.darkniightz.core.shop.ShopPriceRow;
import com.darkniightz.core.system.EconomyManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Confirms shift-click stack purchase from {@link ShopMenu} when enabled in Gameplay settings.
 */
public class ShopStackConfirmMenu extends BaseMenu {

    private static final int SLOT_CONFIRM = 11;
    private static final int SLOT_INFO = 13;
    private static final int SLOT_CANCEL = 15;

    private final JebaitedCore plugin;
    private final ShopManager shop;
    private final EconomyManager economy;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final ShopPriceRow row;
    private final String categoryId;
    private final int page;
    private final String searchQuery;

    public ShopStackConfirmMenu(JebaitedCore plugin, ShopManager shop, EconomyManager economy,
                                ProfileStore profiles, RankManager ranks,
                                ShopPriceRow row, String categoryId, int page, String searchQuery) {
        super(plugin, plugin.getConfig().getString("server_shop.confirm_title", "§8Confirm stack purchase"), 27);
        this.plugin = plugin;
        this.shop = shop;
        this.economy = economy;
        this.profiles = profiles;
        this.ranks = ranks;
        this.row = row;
        this.categoryId = categoryId;
        this.page = page;
        this.searchQuery = searchQuery;
    }

    @Override
    protected void populate(Player viewer) {
        Material mat = row.material();
        int max = mat == null ? 1 : Math.max(1, Math.min(row.maxStack(), 64));
        double total = Math.round(row.buyPrice() * max * 100D) / 100D;

        for (int i = 0; i < 27; i++) {
            if (i != SLOT_CONFIRM && i != SLOT_INFO && i != SLOT_CANCEL) {
                inventory.setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
            }
        }

        inventory.setItem(SLOT_CONFIRM, new ItemBuilder(Material.LIME_CONCRETE)
                .name("§a§lBuy stack")
                .lore(List.of("§7Click to buy §f" + max + " §7for §a" + economy.format(total)))
                .build());

        inventory.setItem(SLOT_CANCEL, new ItemBuilder(Material.RED_CONCRETE)
                .name("§c§lCancel")
                .lore(List.of("§7Return to the shop"))
                .build());

        if (mat != null) {
            ItemStack show = new ItemStack(mat);
            show.setAmount(Math.min(max, show.getMaxStackSize()));
            ItemMeta meta = show.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§f" + row.displayName());
                List<String> lore = new ArrayList<>();
                lore.add("§7Quantity: §f" + max);
                lore.add("§7Total: §a" + economy.format(total));
                lore.add("§7Your balance: §f" + economy.format(economy.getBalance(viewer)));
                meta.setLore(lore);
                show.setItemMeta(meta);
            }
            inventory.setItem(SLOT_INFO, show);
        } else {
            inventory.setItem(SLOT_INFO, new ItemBuilder(Material.BARRIER).name("§cInvalid item").build());
        }
    }

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
        if (slot == SLOT_CANCEL) {
            Bukkit.getScheduler().runTask(plugin, () ->
                    new ShopMenu(plugin, shop, economy, profiles, ranks, categoryId, page, searchQuery).open(who));
            return true;
        }
        if (slot == SLOT_CONFIRM) {
            boolean ok = shop.buy(who, row, 1, true);
            if (ok) {
                int reopenPage = page;
                Bukkit.getScheduler().runTask(plugin, () ->
                        new ShopMenu(plugin, shop, economy, profiles, ranks, categoryId, reopenPage, searchQuery).open(who));
            } else {
                Bukkit.getScheduler().runTask(plugin, () ->
                        new ShopMenu(plugin, shop, economy, profiles, ranks, categoryId, page, searchQuery).open(who));
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
