package com.darkniightz.core.cosmetics;

import com.darkniightz.core.gui.BaseMenu;
import com.darkniightz.core.gui.ItemBuilder;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.system.MaterialCompat;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CoinShopMenu extends BaseMenu {
    private static final int[] GRID_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final CosmeticsManager cosmetics;
    private final ProfileStore profiles;
    private final CosmeticsEngine cosmeticsEngine;
    private final ToyboxManager toyboxManager;

    private CosmeticsManager.Category current;

    public CoinShopMenu(Plugin plugin, CosmeticsManager cosmetics, ProfileStore profiles, CosmeticsEngine cosmeticsEngine, ToyboxManager toyboxManager) {
        this(plugin, cosmetics, profiles, cosmeticsEngine, toyboxManager, CosmeticsManager.Category.PARTICLES);
    }

    public CoinShopMenu(Plugin plugin, CosmeticsManager cosmetics, ProfileStore profiles, CosmeticsEngine cosmeticsEngine, ToyboxManager toyboxManager, CosmeticsManager.Category startCategory) {
        super(plugin,
                plugin.getConfig().getString("menus.cosmetics.shop_title", "§6§lCoin Shop"),
                Math.max(9, Math.min(54, plugin.getConfig().getInt("menus.cosmetics.size", 54)))
        );
        this.cosmetics = cosmetics;
        this.profiles = profiles;
        this.cosmeticsEngine = cosmeticsEngine;
        this.toyboxManager = toyboxManager;
        this.current = startCategory == null ? CosmeticsManager.Category.PARTICLES : startCategory;
    }

    @Override
    protected void populate(Player viewer) {
        Inventory inv = getInventory();
        PlayerProfile prof = profiles.getOrCreate(viewer, plugin.getConfig().getString("ranks.default", "pleb"));
        if (prof == null) return;

        fill(inv, Material.GRAY_STAINED_GLASS_PANE, " ");
        placeHeader(inv, viewer, prof);
        placeCategories(inv, prof);
        placeInstructions(inv, prof);
        placeCoins(inv, prof);
        placeGrid(inv, prof);
        placeUtilityButtons(inv, prof);
    }

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
        if (slot == 0) {
            new CollectionBookMenu(plugin, cosmetics, profiles, ((JebaitedCore) plugin).getCosmeticPreviewService(), toyboxManager).open(who);
            return true;
        }
        if (slot == 7) {
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Outfits are parked for now and will come back later."));
            return true;
        }
        if (slot == 1) { current = CosmeticsManager.Category.PARTICLES; populate(who); who.updateInventory(); return true; }
        if (slot == 3) { current = CosmeticsManager.Category.TRAILS; populate(who); who.updateInventory(); return true; }
        if (slot == 5) { current = CosmeticsManager.Category.GADGETS; populate(who); who.updateInventory(); return true; }
        if (slot == 6) { current = CosmeticsManager.Category.TAGS; populate(who); who.updateInventory(); return true; }
        if (slot == 49) {
            PlayerProfile prof = profiles.getOrCreate(who, plugin.getConfig().getString("ranks.default", "pleb"));
            if (prof != null) {
                boolean had = false;
                if (prof.getEquippedParticles() != null) { prof.setEquippedParticles(null); prof.setParticleActivatedAt(null); had = true; }
                if (prof.getEquippedTrail() != null) { prof.setEquippedTrail(null); prof.setTrailActivatedAt(null); had = true; }
                if (prof.getEquippedGadget() != null) { prof.setEquippedGadget(null); had = true; }
                if (had) {
                    profiles.save(who.getUniqueId());
                    if (toyboxManager != null) toyboxManager.refresh(who);
                    who.sendMessage(com.darkniightz.core.Messages.prefixed("§aAll your cosmetic effects have been disabled."));
                } else {
                    who.sendMessage(com.darkniightz.core.Messages.prefixed("§7You have no active effects."));
                }
            }
            populate(who);
            who.updateInventory();
            return true;
        }
        if (slot == 53) {
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§6Shift-click a cosmetic to buy and equip it. Left-click equips owned items. Right-click previews."));
            return true;
        }

        if (slot >= 9) {
            PlayerProfile prof = profiles.getOrCreate(who, plugin.getConfig().getString("ranks.default", "pleb"));
            if (prof == null) return true;
            List<CosmeticsManager.Cosmetic> list = getListForCurrent();
            int index = indexFor(slot);
            if (index < 0 || index >= list.size()) return true;

            CosmeticsManager.Cosmetic pick = list.get(index);
            if (rightClick) {
                preview(who, pick);
                return true;
            }

            boolean owned = prof.hasUnlocked(pick.key);
            if (!owned) {
                if (shiftClick) {
                    purchaseAndEquip(who, prof, pick);
                    return true;
                }
                new CosmeticActionMenu(plugin, cosmetics, profiles, cosmeticsEngine, toyboxManager, ((JebaitedCore) plugin).getCosmeticPreviewService(), pick, true, current).open(who);
                return true;
            }

            if (leftClick || shiftClick) {
                equipCosmetic(who, prof, pick);
                return true;
            }
            return true;
        }
        return true;
    }

    private void placeHeader(Inventory inv, Player viewer, PlayerProfile prof) {
        String rank = prof.getPrimaryRank() == null ? plugin.getConfig().getString("ranks.default", "pleb") : prof.getPrimaryRank();
        var style = ((JebaitedCore) plugin).getRankManager().getStyle(rank);
        String prefix = style.prefix == null || style.prefix.isEmpty() ? "§6Coins" : style.prefix + " §7Coins";
        List<String> lore = new ArrayList<>();
        lore.add("§7Buy cosmetics with your wallet, then equip them instantly.");
        lore.add("§8Left-click opens the buy menu. Shift-click buys instantly.");
        inv.setItem(4, new ItemBuilder(Material.EMERALD)
                .name(prefix)
                .lore(lore)
                .glow(true)
                .build());
    }

    private void placeCategories(Inventory inv, PlayerProfile prof) {
        inv.setItem(1, categoryButton(Material.BLAZE_POWDER, "§6Particles", CosmeticsManager.Category.PARTICLES, prof, "§7Buy atmosphere and aura effects.").build());
        inv.setItem(3, categoryButton(Material.FEATHER, "§eTrails", CosmeticsManager.Category.TRAILS, prof, "§7Buy footsteps with a little flair.").build());
        inv.setItem(5, categoryButton(MaterialCompat.resolve(Material.PAPER, "FIREWORK_ROCKET", "FIREWORK", "PAPER"), "§dToybox", CosmeticsManager.Category.GADGETS, prof, "§7Buy harmless chaos in your hotbar.").build());
        inv.setItem(6, categoryButton(Material.NAME_TAG, "§5Tags", CosmeticsManager.Category.TAGS, prof, "§7Buy custom server chat/tab tags.").build());
    }

    private void placeInstructions(Inventory inv, PlayerProfile prof) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Left-click: equip owned cosmetics / open buy menu.");
        lore.add("§7Shift-click: buy locked cosmetics instantly.");
        lore.add("§7Right-click: preview without committing.");
        lore.add("§7Coins: §6" + prof.getCosmeticCoins());
        lore.add("§8This is the place to spend your event money.");
        inv.setItem(40, new ItemBuilder(Material.PAPER)
                .name("§fShop Instructions")
                .lore(lore)
                .build());
    }

    private void placeCoins(Inventory inv, PlayerProfile prof) {
        inv.setItem(53, new ItemBuilder(Material.GOLD_INGOT)
                .name("§6Cosmetic Coins: §e" + prof.getCosmeticCoins())
                .lore(List.of("§7Your balance updates live.", "§8Use it to buy unlocks and toys."))
                .glow(true)
                .build());
    }

    private void placeGrid(Inventory inv, PlayerProfile prof) {
        List<CosmeticsManager.Cosmetic> list = getListForCurrent();
        for (int i = 0; i < GRID_SLOTS.length; i++) {
            if (i >= list.size()) break;
            CosmeticsManager.Cosmetic cosmetic = list.get(i);
            boolean owned = prof.hasUnlocked(cosmetic.key);
            boolean equipped = isEquipped(prof, cosmetic);
            List<String> lore = new ArrayList<>();
            if (cosmetic.lore != null && !cosmetic.lore.isEmpty()) lore.addAll(cosmetic.lore);
            lore.add(" ");
            lore.add("§7Price: §6" + cosmetic.price + " coins");
            lore.add("§7Status: " + (owned ? "§aOwned" : "§cLocked"));
            if (equipped) lore.add("§aCurrently equipped.");
            lore.add("§8Left-click to equip owned items or open the buy menu.");
            if (!owned) lore.add("§8Shift-click buys it instantly.");
            lore.add("§8Right-click to preview.");

            ItemBuilder item = new ItemBuilder(cosmetic.icon)
                    .name(cosmetic.name + (equipped ? " §a(Equipped)" : owned ? " §7(Owned)" : " §8(Locked)"))
                    .lore(lore)
                    .glow(equipped);
            inv.setItem(GRID_SLOTS[i], item.build());
        }

        if (list.isEmpty()) {
            inv.setItem(22, new ItemBuilder(Material.BARRIER)
                    .name("§cNo cosmetics here yet")
                    .lore(List.of("§7Try another tab or add more cosmetics to the catalog."))
                    .build());
        }
    }

    private void placeUtilityButtons(Inventory inv, PlayerProfile prof) {
        inv.setItem(0, new ItemBuilder(Material.BOOK)
                .name("§5Collection Book")
                .lore(List.of("§7Return to the cleaner cosmetics view."))
                .glow(true)
                .build());
        inv.setItem(7, new ItemBuilder(Material.LEATHER_CHESTPLATE)
                .name("§8Outfits Placeholder")
                .lore(List.of("§7Wardrobe is disabled for now.", "§8We’ll revisit it later."))
                .build());
        inv.setItem(8, new ItemBuilder(Material.OAK_SIGN)
            .name("§7Shop Focus")
            .lore(List.of("§7Shift-click buys instantly.", "§8Left-click keeps the cleaner menu flow."))
                .build());
        inv.setItem(49, new ItemBuilder(Material.BARRIER)
                .name("§cDisable All Effects")
                .lore(List.of("§7Turn off particles, trails, and gadgets.", "§8A clean reset for your look."))
                .build());
    }

    private void unlockCosmetic(Player who, PlayerProfile prof, CosmeticsManager.Cosmetic pick) {
        String type = switch (pick.category) {
            case PARTICLES -> "particle";
            case TRAILS -> "trail";
            case GADGETS -> "gadget";
            case TAGS -> "tag";
        };
        JebaitedCore.getInstance().getPlayerProfileDAO().unlockCosmetic(who.getUniqueId(), pick.key, type);
        prof.getUnlockedCosmetics().add(pick.key);
        profiles.save(who.getUniqueId());
    }

    private void purchaseAndEquip(Player who, PlayerProfile prof, CosmeticsManager.Cosmetic pick) {
        if (!pick.enabled) {
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§cThis cosmetic is currently disabled."));
            return;
        }
        if (!prof.spendCosmeticCoins(pick.price)) {
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§cNot enough coins. §7Price: §6" + pick.price + " coins"));
            return;
        }
        unlockCosmetic(who, prof, pick);
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§aPurchased §e" + pick.name + "§a."));
        equipCosmetic(who, prof, pick);
    }

    private void equipCosmetic(Player who, PlayerProfile prof, CosmeticsManager.Cosmetic pick) {
        switch (pick.category) {
            case PARTICLES -> {
                prof.setEquippedParticles(pick.key);
                prof.setParticleActivatedAt(System.currentTimeMillis());
            }
            case TRAILS -> {
                prof.setEquippedTrail(pick.key);
                prof.setTrailActivatedAt(System.currentTimeMillis());
            }
            case GADGETS -> {
                prof.setEquippedGadget(pick.key);
                if (toyboxManager != null) toyboxManager.refresh(who);
            }
            case TAGS -> prof.setActiveTag(pick.key);
        }
        prof.incCosmeticEquips();
        profiles.save(who.getUniqueId());
        if (plugin instanceof JebaitedCore core && pick.category == CosmeticsManager.Category.TAGS) {
            core.refreshPlayerPresentation(who);
        }
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§aEquipped §e" + pick.name + "§a."));
        populate(who);
        who.updateInventory();
    }

    private void preview(Player who, CosmeticsManager.Cosmetic cosmetic) {
        if (!cosmetic.enabled) {
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§cThat cosmetic is currently disabled."));
            return;
        }
        switch (cosmetic.category) {
            case PARTICLES -> {
                if (cosmeticsEngine != null) cosmeticsEngine.previewParticle(who, cosmetic.key);
            }
            case TRAILS -> {
                if (cosmeticsEngine != null) cosmeticsEngine.previewTrail(who, cosmetic.key);
            }
            case GADGETS -> {
                if (toyboxManager != null) toyboxManager.preview(who, cosmetic.key);
            }
            case TAGS -> {
                who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Tags do not have an effect preview. Equip one to apply it in chat and tab."));
                return;
            }
        }
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Previewing §f" + cosmetic.name + "§7."));
    }

    private boolean isEquipped(PlayerProfile prof, CosmeticsManager.Cosmetic cosmetic) {
        return switch (cosmetic.category) {
            case PARTICLES -> cosmetic.key.equals(prof.getEquippedParticles());
            case TRAILS -> cosmetic.key.equals(prof.getEquippedTrail());
            case GADGETS -> cosmetic.key.equals(prof.getEquippedGadget());
            case TAGS -> cosmetic.key.equals(prof.getActiveTag());
        };
    }

    private int indexFor(int slot) {
        for (int i = 0; i < GRID_SLOTS.length; i++) {
            if (GRID_SLOTS[i] == slot) return i;
        }
        return -1;
    }

    private List<CosmeticsManager.Cosmetic> getListForCurrent() {
        return switch (current) {
            case PARTICLES -> cosmetics.getByCategory(CosmeticsManager.Category.PARTICLES);
            case TRAILS -> cosmetics.getByCategory(CosmeticsManager.Category.TRAILS);
            case GADGETS -> cosmetics.getByCategory(CosmeticsManager.Category.GADGETS);
            case TAGS -> cosmetics.getByCategory(CosmeticsManager.Category.TAGS);
        };
    }

    private ItemBuilder categoryButton(Material icon, String title, CosmeticsManager.Category category, PlayerProfile prof, String line) {
        boolean selected = current == category;
        List<String> lore = new ArrayList<>();
        lore.add(line);
        lore.add(" ");
        lore.add("§7Owned: §f" + cosmetics.getByCategory(category).stream().filter(c -> prof.hasUnlocked(c.key)).count() + "§7/§f" + cosmetics.getByCategory(category).size());
        lore.add("§8Click to browse " + prettyCategory(category).toLowerCase(Locale.ROOT) + ".");
        return new ItemBuilder(icon).name(title).lore(lore).glow(selected);
    }

    private String prettyCategory(CosmeticsManager.Category category) {
        return switch (category) {
            case PARTICLES -> "Particles";
            case TRAILS -> "Trails";
            case GADGETS -> "Toybox";
            case TAGS -> "Tags";
        };
    }

    private void fill(Inventory inv, Material material, String name) {
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, new ItemBuilder(material).name(name).build());
        }
    }
}
