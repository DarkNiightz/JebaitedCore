package com.darkniightz.core.cosmetics;

import com.darkniightz.core.gui.BaseMenu;
import com.darkniightz.core.gui.ItemBuilder;
import com.darkniightz.core.players.ProfileStore;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.List;

public class WardrobeMenu extends BaseMenu {

    private final CosmeticsManager cosmetics;
    private final ProfileStore profiles;
    private final ToyboxManager toyboxManager;
    private final CosmeticPreviewService previewService;

    public WardrobeMenu(Plugin plugin, CosmeticsManager cosmetics, ProfileStore profiles) {
        this(plugin, cosmetics, profiles, null, CosmeticsManager.Category.PARTICLES, null);
    }

    public WardrobeMenu(Plugin plugin, CosmeticsManager cosmetics, ProfileStore profiles, ToyboxManager toyboxManager, CosmeticsManager.Category startCategory) {
        this(plugin, cosmetics, profiles, toyboxManager, startCategory, null);
    }

    public WardrobeMenu(Plugin plugin, CosmeticsManager cosmetics, ProfileStore profiles, ToyboxManager toyboxManager, CosmeticsManager.Category startCategory, CosmeticPreviewService previewService) {
        super(plugin,
                plugin.getConfig().getString("menus.cosmetics.wardrobe_title", "§8§lOutfits"),
                27
        );
        this.cosmetics = cosmetics;
        this.profiles = profiles;
        this.toyboxManager = toyboxManager;
        this.previewService = previewService;
    }

    @Override
    protected void populate(Player viewer) {
        Inventory inv = getInventory();
        inv.clear();
        fill(inv, Material.GRAY_STAINED_GLASS_PANE, " ");

        inv.setItem(13, new ItemBuilder(Material.LEATHER_CHESTPLATE)
                .name("§8Outfits Placeholder")
                .lore(List.of(
                        "§7The wardrobe has been pulled out for now.",
                        "§8We’ll rebuild it properly later."
                ))
                .glow(true)
                .build());

        inv.setItem(11, new ItemBuilder(Material.BOOK)
                .name("§5Back to Collection Book")
                .lore(List.of("§7Return to the main cosmetics browser."))
                .build());

        inv.setItem(15, new ItemBuilder(Material.BARRIER)
                .name("§cClose")
                .lore(List.of("§7Close this placeholder screen."))
                .build());
    }

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
        if (slot == 11 || slot == 13) {
            new CollectionBookMenu(plugin, cosmetics, profiles, previewService, toyboxManager).open(who);
            return true;
        }
        if (slot == 15) {
            who.closeInventory();
            return true;
        }
        return true;
    }

    private void fill(Inventory inv, Material material, String name) {
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, new ItemBuilder(material).name(name).build());
        }
    }
}
