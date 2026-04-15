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

public class CollectionBookMenu extends BaseMenu {
    private static final int[] GRID_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private final CosmeticsManager cosmetics;
    private final ProfileStore profiles;
    private final CosmeticPreviewService previewService;
    private final ToyboxManager toyboxManager;

    private CosmeticsManager.Category current;

    public CollectionBookMenu(Plugin plugin, CosmeticsManager cosmetics, ProfileStore profiles, CosmeticPreviewService previewService, ToyboxManager toyboxManager) {
        this(plugin, cosmetics, profiles, previewService, toyboxManager, null);
    }

    public CollectionBookMenu(Plugin plugin, CosmeticsManager cosmetics, ProfileStore profiles, CosmeticPreviewService previewService, ToyboxManager toyboxManager, CosmeticsManager.Category startCategory) {
        super(plugin,
                plugin.getConfig().getString("menus.cosmetics.collection_title", "§5§lCollection Book"),
                Math.max(9, Math.min(54, plugin.getConfig().getInt("menus.cosmetics.size", 54)))
        );
        this.cosmetics = cosmetics;
        this.profiles = profiles;
        this.previewService = previewService;
        this.toyboxManager = toyboxManager;
        this.current = startCategory;
    }

    @Override
    protected void populate(Player viewer) {
        Inventory inv = getInventory();
        PlayerProfile prof = profiles.getOrCreate(viewer, plugin.getConfig().getString("ranks.default", "pleb"));
        if (prof == null) return;

        fill(inv, Material.BLACK_STAINED_GLASS_PANE, " ");
        placeHeader(inv, viewer, prof);
        if (current == null) {
            placeCategoryHub(inv, prof);
            placeRootUtility(inv, prof);
        } else {
            placeBrowseInfo(inv);
            placeGrid(inv, prof);
            placeSummary(inv, prof);
            placeCategoryUtility(inv, prof);
        }
    }

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
        if (current == null) {
            if (slot == 19) { current = CosmeticsManager.Category.PARTICLES; populate(who); who.updateInventory(); return true; }
            if (slot == 21) { current = CosmeticsManager.Category.TRAILS; populate(who); who.updateInventory(); return true; }
            if (slot == 23) { current = CosmeticsManager.Category.GADGETS; populate(who); who.updateInventory(); return true; }
            if (slot == 25) { current = CosmeticsManager.Category.TAGS; populate(who); who.updateInventory(); return true; }
            if (slot == 45) {
                who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Outfits are coming back later. This slot is just a placeholder for now."));
                return true;
            }
            return true;
        }

        if (slot == 45) {
            current = null;
            populate(who);
            who.updateInventory();
            return true;
        }

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

        int index = indexFor(slot);
        if (index < 0) return true;
        List<CosmeticsManager.Cosmetic> list = getListForCurrent();
        if (index >= list.size()) return true;

        PlayerProfile prof = profiles.getOrCreate(who, plugin.getConfig().getString("ranks.default", "pleb"));
        if (prof == null) return true;
        CosmeticsManager.Cosmetic pick = list.get(index);
        boolean owned = prof.hasUnlocked(pick.key);

        if (rightClick) {
            if (previewService != null) {
                previewService.preview(who, pick);
            }
            return true;
        }

        if (!owned) {
            if (shiftClick) {
                purchaseAndEquip(who, prof, pick);
                return true;
            }
            new CosmeticActionMenu(plugin, cosmetics, profiles, ((JebaitedCore) plugin).getCosmeticsEngine(), toyboxManager, previewService, pick, false, current).open(who);
            return true;
        }

        equipCosmetic(who, prof, pick);
        return true;
    }

    private void placeHeader(Inventory inv, Player viewer, PlayerProfile prof) {
        String rank = prof.getPrimaryRank() == null ? plugin.getConfig().getString("ranks.default", "pleb") : prof.getPrimaryRank();
        var style = ((JebaitedCore) plugin).getRankManager().getStyle(rank);
        List<String> lore = new ArrayList<>();
        lore.add(current == null
                ? "§7Choose a category to open that collection."
                : "§7Browsing " + prettyCategory(current) + " cosmetics.");
        lore.add("§8Clean flow: collection book first, category second.");
        inv.setItem(4, new ItemBuilder(Material.BOOK)
                .name((style.prefix == null || style.prefix.isEmpty() ? "§5Collection Book" : style.prefix + " §7Collection Book"))
                .lore(lore)
                .glow(true)
                .build());
    }

    private void placeCategoryHub(Inventory inv, PlayerProfile prof) {
        inv.setItem(19, categoryButton(Material.BLAZE_POWDER, "§6Particles", CosmeticsManager.Category.PARTICLES, prof, "§7Aura shelf and ambience.").build());
        inv.setItem(21, categoryButton(Material.FEATHER, "§eTrails", CosmeticsManager.Category.TRAILS, prof, "§7Footsteps and movement flair.").build());
        inv.setItem(23, categoryButton(MaterialCompat.resolve(Material.PAPER, "FIREWORK_ROCKET", "FIREWORK", "PAPER"), "§dToybox", CosmeticsManager.Category.GADGETS, prof, "§7Harmless gadgets and toys.").build());
        inv.setItem(25, categoryButton(Material.NAME_TAG, "§5Tags", CosmeticsManager.Category.TAGS, prof, "§7Chat and tab cosmetics.").build());
    }

    private void placeRootUtility(Inventory inv, PlayerProfile prof) {
        int total = cosmetics.getAll().size();
        int owned = prof.getUnlockedCosmetics().size();
        inv.setItem(49, new ItemBuilder(Material.PAPER)
                .name("§fCollection Status")
                .lore(List.of(
                        "§7Owned: §f" + owned + "§7/§f" + total,
                        "§7Coins: §6" + prof.getCosmeticCoins(),
                        "§8Pick a category to continue."
                ))
                .build());
        inv.setItem(45, new ItemBuilder(Material.LEATHER_CHESTPLATE)
                .name("§8Outfits Placeholder")
                .lore(List.of("§7Wardrobe is disabled for now.", "§8We’ll come back to it later."))
                .build());
        inv.setItem(53, new ItemBuilder(Material.GOLD_INGOT)
                .name("§6Cosmetic Coins: §e" + prof.getCosmeticCoins())
                .lore(List.of("§7Your coin total stays visible here."))
                .glow(true)
                .build());
    }

    private void placeBrowseInfo(Inventory inv) {
        inv.setItem(13, new ItemBuilder(MaterialCompat.resolve(Material.COMPASS, "SPYGLASS", "COMPASS"))
                .name("§bBrowse Controls")
                .lore(List.of(
                        "§7Left-click: equip owned items / open buy menu",
                        "§7Shift-click: instant buy locked items",
                        "§7Right-click: preview first"
                ))
                .glow(true)
                .build());
    }

    private void placeSummary(Inventory inv, PlayerProfile prof) {
        int total = cosmetics.getByCategory(current).size();
        long owned = cosmetics.getByCategory(current).stream().filter(c -> prof.hasUnlocked(c.key)).count();
        int previewed = prof.getPreviewedCosmetics().size();
        List<String> lore = new ArrayList<>();
        lore.add("§7Owned here: §f" + owned + "§7/§f" + total);
        lore.add("§7Previewed: §f" + previewed);
        lore.add("§7Coins: §6" + prof.getCosmeticCoins());
        lore.add("§8Back returns to the Collection Book.");
        inv.setItem(48, new ItemBuilder(Material.PAPER)
                .name("§fCollection Status")
                .lore(lore)
                .build());
    }

    private void placeGrid(Inventory inv, PlayerProfile prof) {
        List<CosmeticsManager.Cosmetic> list = getListForCurrent();
        for (int i = 0; i < GRID_SLOTS.length; i++) {
            if (i >= list.size()) break;
            CosmeticsManager.Cosmetic cosmetic = list.get(i);
            boolean owned = prof.hasUnlocked(cosmetic.key);
            boolean equipped = isEquipped(prof, cosmetic);
            boolean previewed = prof.isPreviewedCosmetic(cosmetic.key);
            List<String> lore = new ArrayList<>();
            if (cosmetic.lore != null && !cosmetic.lore.isEmpty()) lore.addAll(cosmetic.lore);
            lore.add(" ");
            lore.add("§7Rarity: " + cosmetics.rarityLabel(cosmetic));
            lore.add("§7Price: §6" + cosmetic.price + " coins");
            lore.add("§7Status: " + (owned ? "§aOwned" : "§cLocked") + (previewed ? " §d(Previewed)" : ""));
            if (equipped) lore.add("§aCurrently equipped.");
            if (owned) {
                lore.add("§8Left-click to equip it.");
            } else {
                lore.add("§8Left-click to open the buy menu.");
                lore.add("§8Shift-click to buy it instantly.");
            }
            lore.add("§8Right-click to preview.");
            ItemBuilder item = new ItemBuilder(cosmetic.icon)
                    .name(cosmetic.name + (equipped ? " §a(Equipped)" : owned ? " §7(Owned)" : " §8(Locked)"))
                    .lore(lore)
                    .glow(equipped);
            inv.setItem(GRID_SLOTS[i], item.build());
        }

        if (list.isEmpty()) {
            inv.setItem(31, new ItemBuilder(Material.BARRIER)
                    .name("§cNo cosmetics here yet")
                    .lore(List.of("§7Try another tab or add more cosmetics to the catalog."))
                    .build());
        }
    }

    private void placeCategoryUtility(Inventory inv, PlayerProfile prof) {
        inv.setItem(45, new ItemBuilder(Material.ARROW)
                .name("§aBack to Collection Book")
                .lore(List.of("§7Return to the category hub."))
                .build());
        inv.setItem(49, new ItemBuilder(Material.BARRIER)
                .name("§cDisable All Effects")
                .lore(List.of("§7Turn off particles, trails, and gadgets.", "§8A clean reset for your look."))
                .build());
        inv.setItem(53, new ItemBuilder(Material.GOLD_INGOT)
                .name("§6Cosmetic Coins: §e" + prof.getCosmeticCoins())
                .lore(List.of("§7Your coin total stays visible here."))
                .glow(true)
                .build());
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
        String type = switch (pick.category) {
            case PARTICLES -> "particle";
            case TRAILS -> "trail";
            case GADGETS -> "gadget";
            case TAGS -> "tag";
        };
        JebaitedCore.getInstance().getPlayerProfileDAO().unlockCosmetic(who.getUniqueId(), pick.key, type);
        prof.getUnlockedCosmetics().add(pick.key);
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
            core.refreshAllPlayerPresentations();
        }
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§aEquipped §e" + pick.name + "§a."));
        populate(who);
        who.updateInventory();
    }

    private boolean isEquipped(PlayerProfile prof, CosmeticsManager.Cosmetic cosmetic) {
        return switch (cosmetic.category) {
            case PARTICLES -> cosmetic.key.equals(prof.getEquippedParticles());
            case TRAILS -> cosmetic.key.equals(prof.getEquippedTrail());
            case GADGETS -> cosmetic.key.equals(prof.getEquippedGadget());
            case TAGS -> cosmetic.key.equals(prof.getActiveTag());
        };
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
        List<String> lore = new ArrayList<>();
        lore.add(line);
        lore.add(" ");
        lore.add("§7Owned: §f" + cosmetics.getByCategory(category).stream().filter(c -> prof.hasUnlocked(c.key)).count() + "§7/§f" + cosmetics.getByCategory(category).size());
        lore.add("§8Click to open " + prettyCategory(category).toLowerCase(Locale.ROOT) + ".");
        return new ItemBuilder(icon).name(title).lore(lore).glow(true);
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

    private int indexFor(int slot) {
        for (int i = 0; i < GRID_SLOTS.length; i++) {
            if (GRID_SLOTS[i] == slot) return i;
        }
        return -1;
    }
}
