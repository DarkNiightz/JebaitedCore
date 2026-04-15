package com.darkniightz.core.cosmetics;

import com.darkniightz.core.gui.BaseMenu;
import com.darkniightz.core.gui.ItemBuilder;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.system.MaterialCompat;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lounge-style overview for cosmetics.
 * Players get a snapshot of their current look, a featured cosmetic, and clear entry points
 * into the wardrobe, coin shop, collection book, trails, particles, and toybox.
 */
public class CosmeticsMenu extends BaseMenu {

    private final ProfileStore profiles;
    private final CosmeticsManager cosmetics;
    private final ToyboxManager toyboxManager;
    private final CosmeticPreviewService previewService;

    private Tab wardrobeTab;
    private Tab shopTab;
    private Tab particlesTab;
    private Tab trailsTab;
    private Tab gadgetsTab;
    private Tab tagsTab;
    private Tab bookTab;
    private GoldBar goldBar;
    private static final int[] LOADOUT_SLOTS = {37, 38, 39};

    public CosmeticsMenu(Plugin plugin, CosmeticsManager cosmetics, ProfileStore profiles, ToyboxManager toyboxManager) {
        this(plugin, cosmetics, profiles, toyboxManager, null);
    }

    public CosmeticsMenu(Plugin plugin, CosmeticsManager cosmetics, ProfileStore profiles, ToyboxManager toyboxManager, CosmeticPreviewService previewService) {
        super(plugin, titleFromConfig(plugin), sizeFromConfig(plugin));
        this.cosmetics = cosmetics;
        this.profiles = profiles;
        this.toyboxManager = toyboxManager;
        this.previewService = previewService;
    }

    private static String titleFromConfig(Plugin plugin) {
        return plugin.getConfig().getString("menus.cosmetics.title", "§d§lCosmetics Lounge");
    }

    private static int sizeFromConfig(Plugin plugin) {
        int s = plugin.getConfig().getInt("menus.cosmetics.size", 54);
        if (s < 9) s = 9;
        if (s > 54) s = 54;
        int rem = s % 9;
        if (rem != 0) s += (9 - rem);
        return s;
    }

    @Override
    protected void populate(Player viewer) {
        Inventory inv = getInventory();
        PlayerProfile prof = profiles.getOrCreate(viewer, plugin.getConfig().getString("ranks.default", "pleb"));
        if (prof == null) return;

        ConfigurationSection root = plugin.getConfig().getConfigurationSection("menus.cosmetics");
        ConfigurationSection tabs = root == null ? null : root.getConfigurationSection("tabs");
        wardrobeTab = readTab(tabs, "wardrobe", "§aOutfits", Material.LEATHER_CHESTPLATE, 10,
                List.of("§7Open the outfit wardrobe.", "§8Wear full sets like Disco, Frog, and Ghost armor."));
        shopTab = readTab(tabs, "shop", "§6Coin Shop", Material.EMERALD, 18,
                List.of("§7Buy cosmetics with your coin wallet.", "§8Shift-click to purchase, right-click to preview."));
        particlesTab = readTab(tabs, "particles", "§bParticles", Material.BLAZE_POWDER, 12,
                List.of("§7Auras, sparkles, and atmospheric flair.", "§8Pick something subtle or loud."));
        trailsTab = readTab(tabs, "trails", "§eTrails", Material.FEATHER, 14,
                List.of("§7Leave a little story behind your steps.", "§8Soft, bright, or mischievous."));
        gadgetsTab = readTab(tabs, "gadgets", "§dToybox", MaterialCompat.resolve(Material.PAPER, "FIREWORK_ROCKET", "FIREWORK", "PAPER"), 16,
                List.of("§7Little toys for harmless chaos.", "§8Right-click once equipped to play."));
        tagsTab = readTab(tabs, "tags", "§5Tags", Material.NAME_TAG, 20,
            List.of("§7Name cosmetics for prefix/suffix style.", "§8Set custom text with /tag if you own the custom one."));
        bookTab = readTab(tabs, "book", "§5Collection Book", Material.BOOK, 26,
                List.of("§7Browse everything you can unlock.", "§8Preview, equip, and pin favorites."));
        goldBar = readGoldBar(root);

        fill(inv, Material.BLACK_STAINED_GLASS_PANE, " ");
        placeHeader(inv, viewer, prof);
        placeTab(inv, wardrobeTab);
        placeTab(inv, shopTab);
        placeTab(inv, particlesTab);
        placeTab(inv, trailsTab);
        placeTab(inv, gadgetsTab);
        placeTab(inv, tagsTab);
        placeTab(inv, bookTab);
        placeFeatured(inv);
        placeSummary(inv, prof);
        placeFavoritesBar(inv, prof);
        placeLoadouts(inv, prof);
        placeQuickActions(inv, root);
    }

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
        if (slot == 4) {
            openLoungeMessage(who);
            return true;
        }
        // Priority matches render order so overlapping tab slots still open what the player sees.
        if (particlesTab != null && particlesTab.enabled && slot == particlesTab.slot) {
            new CoinShopMenu(plugin, cosmetics, profiles, ((com.darkniightz.main.JebaitedCore) plugin).getCosmeticsEngine(), toyboxManager, CosmeticsManager.Category.PARTICLES).open(who);
            return true;
        }
        if (trailsTab != null && trailsTab.enabled && slot == trailsTab.slot) {
            new CoinShopMenu(plugin, cosmetics, profiles, ((com.darkniightz.main.JebaitedCore) plugin).getCosmeticsEngine(), toyboxManager, CosmeticsManager.Category.TRAILS).open(who);
            return true;
        }
        if (gadgetsTab != null && gadgetsTab.enabled && slot == gadgetsTab.slot) {
            new CoinShopMenu(plugin, cosmetics, profiles, ((com.darkniightz.main.JebaitedCore) plugin).getCosmeticsEngine(), toyboxManager, CosmeticsManager.Category.GADGETS).open(who);
            return true;
        }
        if (tagsTab != null && tagsTab.enabled && slot == tagsTab.slot) {
            new CoinShopMenu(plugin, cosmetics, profiles, ((com.darkniightz.main.JebaitedCore) plugin).getCosmeticsEngine(), toyboxManager, CosmeticsManager.Category.TAGS).open(who);
            return true;
        }
        if (shopTab != null && shopTab.enabled && slot == shopTab.slot) {
            new CoinShopMenu(plugin, cosmetics, profiles, ((com.darkniightz.main.JebaitedCore) plugin).getCosmeticsEngine(), toyboxManager).open(who);
            return true;
        }
        if (bookTab != null && bookTab.enabled && slot == bookTab.slot) {
            openCollectionBook(who);
            return true;
        }
        if (wardrobeTab != null && wardrobeTab.enabled && slot == wardrobeTab.slot) {
            new WardrobeMenu(plugin, cosmetics, profiles, toyboxManager, CosmeticsManager.Category.PARTICLES, previewService).open(who);
            return true;
        }
        if (isLoadoutSlot(slot)) {
            handleLoadoutSlot(who, slot, leftClick, shiftClick, rightClick);
            return true;
        }
        if (isFavoriteSlot(slot)) {
            handleFavoriteSlot(who, slot, leftClick, shiftClick, rightClick);
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
            return true;
        }
        if (goldBar != null && slot == goldBar.slot) {
            if (goldBar.message != null && !goldBar.message.isEmpty()) {
                who.sendMessage(goldBar.message);
            } else {
                who.sendMessage(com.darkniightz.core.Messages.prefixed("§6Earn cosmetic coins by playing minigames and events!"));
            }
            return true;
        }

        if (slot == 22) {
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Use right-click preview on individual cosmetics in the shop or collection."));
            return true;
        }
        return true;
    }

    private void placeHeader(Inventory inv, Player viewer, PlayerProfile prof) {
        String rank = prof.getPrimaryRank() == null ? plugin.getConfig().getString("ranks.default", "pleb") : prof.getPrimaryRank();
        String title = "§d§lCosmetics Lounge";
        List<String> lore = new ArrayList<>();
        lore.add("§7A place for style, silliness, and the occasional sparkly disaster.");
        lore.add("§7Rank: §f" + rank);
        lore.add("§8Welcome back, §f" + viewer.getName() + "§8.");
        inv.setItem(4, new ItemBuilder(Material.NETHER_STAR).name(title).lore(lore).glow(true).build());
    }

    private void placeFeatured(Inventory inv) {
        CosmeticsManager.Cosmetic featured = cosmetics.getFeaturedCosmetic();
        if (featured == null) return;
        List<String> lore = new ArrayList<>(featured.lore == null ? List.of() : featured.lore);
        lore.add(" ");
        lore.add("§7Rarity: " + cosmetics.rarityLabel(featured));
        lore.add("§7Spotlighted today.");
        lore.add("§8Click to preview the featured cosmetic.");
        inv.setItem(22, new ItemBuilder(featured.icon)
                .name("§6Today's Spotlight: " + featured.name)
                .lore(lore)
                .glow(true)
                .build());
    }

    private void placeSummary(Inventory inv, PlayerProfile prof) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Particles: §f" + safeName(cosmetics.get(prof.getEquippedParticles())));
        lore.add("§7Trails: §f" + safeName(cosmetics.get(prof.getEquippedTrail())));
        lore.add("§7Toybox: §f" + safeName(cosmetics.get(prof.getEquippedGadget())));
        lore.add("§7Coins: §6" + prof.getCosmeticCoins());
        lore.add("§7Favorites: §f" + prof.getFavoriteCosmetics().size());
        lore.add("§7Previewed: §f" + prof.getPreviewedCosmetics().size());
        lore.add("§8Your loadout updates live when you equip something.");
        inv.setItem(24, new ItemBuilder(Material.PAPER)
                .name("§fCurrent Loadout")
                .lore(lore)
                .build());
    }

    private void placeFavoritesBar(Inventory inv, PlayerProfile prof) {
        List<String> favorites = new ArrayList<>(prof.getFavoriteCosmetics());
        int[] slots = {28, 29, 30, 31, 32, 33, 34};
        inv.setItem(27, new ItemBuilder(Material.NAME_TAG)
                .name("§dFavorites Bar")
                .lore(List.of("§7Pinned cosmetics appear here.", "§8Shift-right-click any item to pin it."))
                .glow(true)
                .build());
        for (int i = 0; i < slots.length; i++) {
            if (i >= favorites.size()) {
                inv.setItem(slots[i], new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                        .name("§8Empty favorite slot")
                        .lore(List.of("§7Pin a cosmetic here for quick access."))
                        .build());
                continue;
            }
            CosmeticsManager.Cosmetic cosmetic = cosmetics.get(favorites.get(i));
            if (cosmetic == null) {
                inv.setItem(slots[i], new ItemBuilder(Material.BARRIER)
                        .name("§cMissing cosmetic")
                        .lore(List.of("§7This favorite no longer exists.", "§8Remove it from your profile later."))
                        .build());
                continue;
            }
            boolean owned = prof.hasUnlocked(cosmetic.key);
            boolean equipped = isEquipped(prof, cosmetic);
            boolean previewed = prof.isPreviewedCosmetic(cosmetic.key);
            List<String> lore = new ArrayList<>();
            lore.add("§7Rarity: " + cosmetics.rarityLabel(cosmetic));
            lore.add("§7Status: " + (owned ? "§aOwned" : "§cLocked") + (previewed ? " §d(Previewed)" : ""));
            lore.add("§8Left-click to equip if owned.");
            lore.add("§8Right-click to preview.");
            lore.add("§8Shift-right-click to unpin.");
            inv.setItem(slots[i], new ItemBuilder(cosmetic.icon)
                    .name(cosmetic.name + (equipped ? " §a(Equipped)" : owned ? " §7(Owned)" : " §8(Locked)") + " §d★")
                    .lore(lore)
                    .glow(equipped)
                    .build());
        }
    }

    private void placeLoadouts(Inventory inv, PlayerProfile prof) {
        List<String> entries = new ArrayList<>(prof.getCosmeticLoadouts().keySet());
        inv.setItem(36, new ItemBuilder(Material.PAPER)
                .name("§5Loadout Deck")
                .lore(List.of("§7Left-click to apply a stored vibe.", "§8Save more with /loadout save <name>."))
                .glow(true)
                .build());
        for (int i = 0; i < LOADOUT_SLOTS.length; i++) {
            int slot = LOADOUT_SLOTS[i];
            if (i >= entries.size()) {
                inv.setItem(slot, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                        .name("§8Empty loadout slot")
                        .lore(List.of("§7Save a loadout in chat with /loadout save <name>."))
                        .build());
                continue;
            }
            String name = entries.get(i);
            String raw = prof.getCosmeticLoadout(name);
            String[] parts = raw == null ? new String[0] : raw.split("\\|", -1);
            String particle = parts.length > 0 ? parts[0] : "";
            String trail = parts.length > 1 ? parts[1] : "";
            String gadget = parts.length > 2 ? parts[2] : "";
            List<String> lore = new ArrayList<>();
            lore.add("§7Particle: §f" + safeName(cosmetics.get(particle)));
            lore.add("§7Trail: §f" + safeName(cosmetics.get(trail)));
            lore.add("§7Toybox: §f" + safeName(cosmetics.get(gadget)));
            lore.add("§8Left-click to apply this vibe.");
            lore.add("§8Right-click to remind yourself how to save more.");
            inv.setItem(slot, new ItemBuilder(MaterialCompat.resolve(Material.CHEST, "BUNDLE", "CHEST", "ENDER_CHEST"))
                    .name("§d" + name)
                    .lore(lore)
                    .glow(isCurrentLoadout(prof, raw))
                    .build());
        }
    }

    private void placeQuickActions(Inventory inv, ConfigurationSection root) {
        ConfigurationSection disable = root == null ? null : root.getConfigurationSection("disable_all");
        int disableSlot = disable != null ? disable.getInt("slot", 49) : 49;
        String disableName = disable != null ? disable.getString("name", "§cDisable All Effects") : "§cDisable All Effects";
        List<String> disableLore = disable != null ? disable.getStringList("lore") : List.of("§7Reset particles and trails in one click.");
        String iconName = disable != null ? disable.getString("icon", "BARRIER") : "BARRIER";
        Material icon = materialOrDefault(iconName, Material.BARRIER);
        inv.setItem(disableSlot, new ItemBuilder(icon).name(disableName).lore(disableLore).build());

        if (goldBar != null) {
            List<String> lore = new ArrayList<>();
            lore.add("§7Your cosmetic coin wallet.");
            lore.add("§8Click for a reminder about earning them.");
            inv.setItem(goldBar.slot, new ItemBuilder(goldBar.icon)
                    .name("§6Cosmetic Coins")
                    .lore(lore)
                    .glow(true)
                    .build());
        }
    }

    private void openLoungeMessage(Player who) {
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§d§lCosmetics Lounge §7- pick a vibe, then make it yours."));
    }

    private void openCollectionBook(Player who) {
        new CollectionBookMenu(plugin, cosmetics, profiles, previewService, toyboxManager).open(who);
    }

    private void placeTab(Inventory inv, Tab tab) {
        if (tab == null || !tab.enabled) return;
        int slot = clampSlot(tab.slot, inv.getSize());
        inv.setItem(slot, new ItemBuilder(tab.icon)
                .name(tab.name)
                .lore(tab.lore)
                .glow(tab.glow)
                .build());
        tab.slot = slot;
    }

    private Tab readTab(ConfigurationSection tabs, String key, String defName, Material defIcon, int defSlot, List<String> defLore) {
        boolean enabled = true;
        String name = defName;
        Material icon = defIcon;
        int slot = defSlot;
        List<String> lore = new ArrayList<>(defLore);
        boolean glow = false;
        if (tabs != null) {
            ConfigurationSection sec = tabs.getConfigurationSection(key);
            if (sec != null) {
                enabled = sec.getBoolean("enabled", true);
                name = sec.getString("name", defName);
                String iconName = sec.getString("icon");
                if (iconName != null) {
                    try { icon = Material.valueOf(iconName.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ignored) {}
                }
                if (sec.isInt("slot")) slot = sec.getInt("slot", defSlot);
                List<String> customLore = sec.getStringList("lore");
                if (customLore != null && !customLore.isEmpty()) lore = customLore;
                glow = sec.getBoolean("glow", false);
            }
        }
        return new Tab(enabled, name, icon, slot, lore, glow);
    }

    private GoldBar readGoldBar(ConfigurationSection root) {
        if (root == null) return null;
        ConfigurationSection sec = root.getConfigurationSection("gold_bar");
        if (sec == null) return null;
        String iconName = sec.getString("icon", "GOLD_INGOT");
        Material icon = materialOrDefault(iconName, Material.GOLD_INGOT);
        int slot = sec.getInt("slot", 53);
        String msg = sec.getString("message", "§6Earn cosmetic coins by playing minigames and events!");
        return new GoldBar(icon, clampSlot(slot, size), msg);
    }

    private void fill(Inventory inv, Material material, String name) {
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, new ItemBuilder(material).name(name).build());
        }
    }

    private String safeName(CosmeticsManager.Cosmetic cosmetic) {
        return cosmetic == null ? "§7None" : cosmetic.name;
    }

    private boolean isCurrentLoadout(PlayerProfile prof, String raw) {
        if (prof == null || raw == null) return false;
        String current = safe(prof.getEquippedParticles()) + "|" + safe(prof.getEquippedTrail()) + "|" + safe(prof.getEquippedGadget());
        return raw.equals(current);
    }

    private boolean isLoadoutSlot(int slot) {
        for (int candidate : LOADOUT_SLOTS) {
            if (candidate == slot) return true;
        }
        return false;
    }

    private int indexOfLoadoutSlot(int slot) {
        for (int i = 0; i < LOADOUT_SLOTS.length; i++) {
            if (LOADOUT_SLOTS[i] == slot) return i;
        }
        return -1;
    }

    private void handleLoadoutSlot(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
        PlayerProfile prof = profiles.getOrCreate(who, plugin.getConfig().getString("ranks.default", "pleb"));
        if (prof == null) return;
        List<String> entries = new ArrayList<>(prof.getCosmeticLoadouts().keySet());
        int index = indexOfLoadoutSlot(slot);
        if (index < 0 || index >= entries.size()) {
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§7No saved loadout in this slot yet. Use §e/loadout save <name>§7."));
            return;
        }
        String name = entries.get(index);
        String raw = prof.getCosmeticLoadout(name);
        if (raw == null || raw.isBlank()) {
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§7That loadout is empty."));
            return;
        }
        if (rightClick) {
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§dLoadout §f" + name + "§d: §7use §e/loadout apply " + name + "§7 or §e/loadout clear " + name));
            return;
        }
        if (shiftClick) {
            prof.removeCosmeticLoadout(name);
            profiles.save(who.getUniqueId());
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Removed loadout §f" + name + "§7."));
            populate(who);
            who.updateInventory();
            return;
        }
        String[] parts = raw.split("\\|", -1);
        String particle = parts.length > 0 ? emptyToNull(parts[0]) : null;
        String trail = parts.length > 1 ? emptyToNull(parts[1]) : null;
        String gadget = parts.length > 2 ? emptyToNull(parts[2]) : null;
        prof.setEquippedParticles(particle);
        prof.setParticleActivatedAt(particle == null ? null : System.currentTimeMillis());
        prof.setEquippedTrail(trail);
        prof.setTrailActivatedAt(trail == null ? null : System.currentTimeMillis());
        prof.setEquippedGadget(gadget);
        prof.incCosmeticEquips();
        profiles.save(who.getUniqueId());
        if (toyboxManager != null) toyboxManager.refresh(who);
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§aApplied loadout §f" + name + "§a."));
        populate(who);
        who.updateInventory();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean isEquipped(PlayerProfile prof, CosmeticsManager.Cosmetic cosmetic) {
        return switch (cosmetic.category) {
            case PARTICLES -> cosmetic.key.equals(prof.getEquippedParticles());
            case TRAILS -> cosmetic.key.equals(prof.getEquippedTrail());
            case GADGETS -> cosmetic.key.equals(prof.getEquippedGadget());
            case TAGS -> cosmetic.key.equals(prof.getActiveTag());
        };
    }

    private boolean isFavoriteSlot(int slot) {
        return slot >= 28 && slot <= 34;
    }

    private void handleFavoriteSlot(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
        PlayerProfile prof = profiles.getOrCreate(who, plugin.getConfig().getString("ranks.default", "pleb"));
        if (prof == null) return;
        List<String> favorites = new ArrayList<>(prof.getFavoriteCosmetics());
        int index = slot - 28;
        if (index < 0 || index >= favorites.size()) return;
        CosmeticsManager.Cosmetic cosmetic = cosmetics.get(favorites.get(index));
        if (cosmetic == null) {
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§cThat pinned cosmetic no longer exists."));
            return;
        }

        if (shiftClick && rightClick) {
            prof.getFavoriteCosmetics().remove(cosmetic.key);
            profiles.save(who.getUniqueId());
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Removed §f" + cosmetic.name + " §7from favorites."));
            populate(who);
            who.updateInventory();
            return;
        }

        if (rightClick) {
            if (previewService != null) {
                previewService.preview(who, cosmetic);
            }
            return;
        }

        if (!prof.hasUnlocked(cosmetic.key)) {
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Unlock this cosmetic first, then you can pin or equip it."));
            return;
        }

        if (leftClick || shiftClick) {
            switch (cosmetic.category) {
                case PARTICLES -> {
                    prof.setEquippedParticles(cosmetic.key);
                    prof.setParticleActivatedAt(System.currentTimeMillis());
                }
                case TRAILS -> {
                    prof.setEquippedTrail(cosmetic.key);
                    prof.setTrailActivatedAt(System.currentTimeMillis());
                }
                case GADGETS -> {
                    prof.setEquippedGadget(cosmetic.key);
                    if (toyboxManager != null) toyboxManager.refresh(who);
                }
                case TAGS -> prof.setActiveTag(cosmetic.key);
            }
            prof.incCosmeticEquips();
            profiles.save(who.getUniqueId());
            if (plugin instanceof com.darkniightz.main.JebaitedCore core && cosmetic.category == CosmeticsManager.Category.TAGS) {
                core.refreshPlayerPresentation(who);
            }
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§aEquipped §e" + cosmetic.name + "§a."));
            populate(who);
            who.updateInventory();
        }
    }

    private Material materialOrDefault(String name, Material def) {
        return MaterialCompat.resolveConfigured(name, def, "BARRIER", "PAPER");
    }

    private int clampSlot(int slot, int invSize) {
        return Math.max(0, Math.min(invSize - 1, slot));
    }

    private static final class Tab {
        final boolean enabled;
        final String name;
        final Material icon;
        int slot;
        final List<String> lore;
        final boolean glow;

        Tab(boolean enabled, String name, Material icon, int slot, List<String> lore, boolean glow) {
            this.enabled = enabled;
            this.name = name;
            this.icon = icon;
            this.slot = slot;
            this.lore = lore;
            this.glow = glow;
        }
    }

    private static final class GoldBar {
        final Material icon;
        final int slot;
        final String message;

        GoldBar(Material icon, int slot, String message) {
            this.icon = icon;
            this.slot = slot;
            this.message = message;
        }
    }
}
