package com.darkniightz.core.cosmetics;

import com.darkniightz.core.gui.BaseMenu;
import com.darkniightz.core.gui.ItemBuilder;
import com.darkniightz.core.gui.MenuService;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.system.MaterialCompat;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public class CosmeticActionMenu extends BaseMenu {

    private final CosmeticsManager cosmetics;
    private final ProfileStore profiles;
    private final CosmeticsEngine cosmeticsEngine;
    private final ToyboxManager toyboxManager;
    private final CosmeticPreviewService previewService;
    private final CosmeticsManager.Cosmetic cosmetic;
    private final boolean returnToShop;
    private final CosmeticsManager.Category returnCategory;

    public CosmeticActionMenu(Plugin plugin,
                              CosmeticsManager cosmetics,
                              ProfileStore profiles,
                              CosmeticsEngine cosmeticsEngine,
                              ToyboxManager toyboxManager,
                              CosmeticPreviewService previewService,
                              CosmeticsManager.Cosmetic cosmetic,
                              boolean returnToShop,
                              CosmeticsManager.Category returnCategory) {
        super(plugin, "§6Cosmetic Actions", 27);
        this.cosmetics = cosmetics;
        this.profiles = profiles;
        this.cosmeticsEngine = cosmeticsEngine;
        this.toyboxManager = toyboxManager;
        this.previewService = previewService;
        this.cosmetic = cosmetic;
        this.returnToShop = returnToShop;
        this.returnCategory = returnCategory == null ? CosmeticsManager.Category.PARTICLES : returnCategory;
    }

    @Override
    protected void populate(Player viewer) {
        if (inventory == null || cosmetic == null) {
            return;
        }
        inventory.clear();
        fill(Material.GRAY_STAINED_GLASS_PANE, "§8 ");

        PlayerProfile profile = profiles.getOrCreate(viewer, plugin.getConfig().getString("ranks.default", "pleb"));
        boolean owned = profile != null && profile.hasUnlocked(cosmetic.key);

        List<String> lore = new ArrayList<>();
        if (cosmetic.lore != null && !cosmetic.lore.isEmpty()) {
            lore.addAll(cosmetic.lore);
        }
        lore.add(" ");
        lore.add("§7Price: §6" + cosmetic.price + " coins");
        lore.add("§7Status: " + (owned ? "§aOwned" : "§cLocked"));

        inventory.setItem(13, new ItemBuilder(cosmetic.icon)
                .name(cosmetic.name)
                .lore(lore)
                .glow(true)
                .build());

        inventory.setItem(11, new ItemBuilder(Material.EMERALD)
                .name(owned ? "§aAlready Owned" : "§6Purchase")
                .lore(List.of(
                        owned ? "§7You already own this cosmetic." : "§7Buy this cosmetic and return.",
                        "§8You can click it again to equip after buying."
                ))
                .build());

        inventory.setItem(15, new ItemBuilder(MaterialCompat.resolve(Material.COMPASS, "SPYGLASS", "COMPASS"))
                .name("§bPreview")
                .lore(List.of("§7Try it without committing first."))
                .build());

        inventory.setItem(22, new ItemBuilder(Material.BARRIER)
                .name("§cBack")
                .lore(List.of("§7Return to the previous cosmetics screen."))
                .build());
    }

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
        if (cosmetic == null) {
            MenuService.get().close(who);
            return true;
        }

        PlayerProfile profile = profiles.getOrCreate(who, plugin.getConfig().getString("ranks.default", "pleb"));
        if (profile == null) {
            return true;
        }

        switch (slot) {
            case 11 -> {
                if (profile.hasUnlocked(cosmetic.key)) {
                    who.sendMessage(com.darkniightz.core.Messages.prefixed("§7You already own §f" + cosmetic.name + "§7."));
                    reopenOrigin(who);
                    return true;
                }
                if (!cosmetic.enabled) {
                    who.sendMessage(com.darkniightz.core.Messages.prefixed("§cThis cosmetic is currently disabled."));
                    return true;
                }
                if (!profile.spendCosmeticCoins(cosmetic.price)) {
                    who.sendMessage(com.darkniightz.core.Messages.prefixed("§cNot enough coins. §7Price: §6" + cosmetic.price + " coins"));
                    return true;
                }
                unlockCosmetic(who, profile, cosmetic);
                who.sendMessage(com.darkniightz.core.Messages.prefixed("§aPurchased §e" + cosmetic.name + "§a. Click it again in the menu to equip it."));
                reopenOrigin(who);
            }
            case 15 -> {
                if (previewService != null) {
                    previewService.preview(who, cosmetic);
                } else {
                    fallbackPreview(who, cosmetic);
                }
            }
            case 22 -> reopenOrigin(who);
            default -> {
            }
        }
        return true;
    }

    private void reopenOrigin(Player who) {
        if (returnToShop) {
            new CoinShopMenu(plugin, cosmetics, profiles, cosmeticsEngine, toyboxManager, returnCategory).open(who);
            return;
        }
        new CollectionBookMenu(plugin, cosmetics, profiles, previewService, toyboxManager, returnCategory).open(who);
    }

    private void unlockCosmetic(Player who, PlayerProfile profile, CosmeticsManager.Cosmetic pick) {
        String type = switch (pick.category) {
            case PARTICLES -> "particle";
            case TRAILS -> "trail";
            case GADGETS -> "gadget";
            case TAGS -> "tag";
        };
        JebaitedCore.getInstance().getPlayerProfileDAO().unlockCosmetic(who.getUniqueId(), pick.key, type);
        profile.getUnlockedCosmetics().add(pick.key);
        profiles.save(who.getUniqueId());
    }

    private void fallbackPreview(Player who, CosmeticsManager.Cosmetic pick) {
        switch (pick.category) {
            case PARTICLES -> {
                if (cosmeticsEngine != null) cosmeticsEngine.previewParticle(who, pick.key);
            }
            case TRAILS -> {
                if (cosmeticsEngine != null) cosmeticsEngine.previewTrail(who, pick.key);
            }
            case GADGETS -> {
                if (toyboxManager != null) toyboxManager.preview(who, pick.key);
            }
            case TAGS -> who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Tags do not have a visual preview pulse."));
        }
    }

    private void fill(Material material, String name) {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, new ItemBuilder(material).name(name).build());
        }
    }
}
