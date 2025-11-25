package com.darkniightz.core.cosmetics;

import com.darkniightz.core.gui.BaseMenu;
import com.darkniightz.core.gui.ItemBuilder;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.List;

public class WardrobeMenu extends BaseMenu {
    private final CosmeticsManager cosmetics;
    private final ProfileStore profiles;

    private CosmeticsManager.Category current = CosmeticsManager.Category.PARTICLES;

    public WardrobeMenu(Plugin plugin, CosmeticsManager cosmetics, ProfileStore profiles) {
        super(plugin,
                plugin.getConfig().getString("menus.cosmetics.title", "§d§lCosmetics"),
                Math.max(9, Math.min(54, plugin.getConfig().getInt("menus.cosmetics.size", 54)))
        );
        this.cosmetics = cosmetics;
        this.profiles = profiles;
    }

    @Override
    protected void populate(Player viewer) {
        Inventory inv = getInventory();

        // Category selector (fixed top row)
        inv.setItem(2, new ItemBuilder(Material.BLAZE_POWDER).name("§6Particles").build());
        inv.setItem(4, new ItemBuilder(Material.FEATHER).name("§5Trails").build());
        inv.setItem(6, new ItemBuilder(Material.FIREWORK_ROCKET).name("§dGadgets").build());

        // Clear grid area
        for (int i = 9; i < 36; i++) inv.setItem(i, null);

        PlayerProfile prof = profiles.getOrCreate(viewer, plugin.getConfig().getString("ranks.default", "friend"));
        if (prof == null) return; // Database might be disabled
        List<CosmeticsManager.Cosmetic> list = switch (current) {
            case PARTICLES -> cosmetics.getByCategory(CosmeticsManager.Category.PARTICLES);
            case TRAILS -> cosmetics.getByCategory(CosmeticsManager.Category.TRAILS);
            case GADGETS -> cosmetics.getByCategory(CosmeticsManager.Category.GADGETS);
        };
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25};
        int idx = 0;
        for (CosmeticsManager.Cosmetic c : list) {
            if (idx >= slots.length) break;
            boolean unlocked = prof.hasUnlocked(c.key);
            boolean equipped = isEquipped(prof, c);
            String state = equipped ? " §a(Equipped)" : (!unlocked ? " §8(Locked)" : "");
            ItemBuilder ib = new ItemBuilder(c.icon).name(c.name + state);
            // Lore: include price if locked
            java.util.ArrayList<String> lore = new java.util.ArrayList<>();
            if (c.lore != null) lore.addAll(c.lore);
            if (!unlocked) lore.add("§7Price: §6" + c.price + " coins");
            ib.lore(lore);
            inv.setItem(slots[idx++], ib.build());
        }

        // Gold bar with coins balance (bottom-right)
        var goldConf = plugin.getConfig().getConfigurationSection("menus.cosmetics.gold_bar");
        int goldSlot = goldConf != null ? goldConf.getInt("slot", 53) : 53;
        String message = goldConf != null ? goldConf.getString("message", "§6Earn cosmetic coins by playing minigames!") : "§6Earn cosmetic coins by playing minigames!";
        inv.setItem(goldSlot, new ItemBuilder(Material.GOLD_INGOT)
                .name("§6Cosmetic Coins: §e" + prof.getCosmeticCoins())
                .lore(java.util.List.of("§7Click for info", message))
                .build());

        // Disable-all button (center bottom by default)
        var disConf = plugin.getConfig().getConfigurationSection("menus.cosmetics.disable_all");
        int disSlot = disConf != null ? disConf.getInt("slot", 49) : 49;
        String disName = disConf != null ? disConf.getString("name", "§cDisable All Effects") : "§cDisable All Effects";
        java.util.List<String> disLore = disConf != null ? disConf.getStringList("lore") : java.util.List.of("§7Click to turn off all your particles and trails.");
        String iconName = disConf != null ? disConf.getString("icon", "BARRIER") : "BARRIER";
        Material icon;
        try { icon = Material.valueOf(iconName.toUpperCase(java.util.Locale.ROOT)); } catch (IllegalArgumentException e) { icon = Material.BARRIER; }
        inv.setItem(disSlot, new ItemBuilder(icon).name(disName).lore(disLore).build());
    }

    private boolean isEquipped(PlayerProfile prof, CosmeticsManager.Cosmetic c) {
        return switch (c.category) {
            case PARTICLES -> c.key.equals(prof.getEquippedParticles());
            case TRAILS -> c.key.equals(prof.getEquippedTrail());
            case GADGETS -> c.key.equals(prof.getEquippedGadget());
        };
    }

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick) {
        if (slot == 2) { current = CosmeticsManager.Category.PARTICLES; populate(who); who.updateInventory(); return true; }
        if (slot == 4) { current = CosmeticsManager.Category.TRAILS; populate(who); who.updateInventory(); return true; }
        if (slot == 6) { current = CosmeticsManager.Category.GADGETS; populate(who); who.updateInventory(); return true; }

        // Gold bar click: show message
        var goldConf = plugin.getConfig().getConfigurationSection("menus.cosmetics.gold_bar");
        int goldSlot = goldConf != null ? goldConf.getInt("slot", 53) : 53;
        if (slot == goldSlot) {
            String message = goldConf != null ? goldConf.getString("message", "§6Earn cosmetic coins by playing minigames and events!") : "§6Earn cosmetic coins by playing minigames and events!";
            who.sendMessage(message);
            return true;
        }

        // Disable-all click: clear active particle/trail
        var disConf = plugin.getConfig().getConfigurationSection("menus.cosmetics.disable_all");
        int disSlot = disConf != null ? disConf.getInt("slot", 49) : 49;
        if (slot == disSlot) {
            PlayerProfile prof = profiles.getOrCreate(who, plugin.getConfig().getString("ranks.default", "friend"));
            if (prof != null) {
                boolean had = false;
                if (prof.getEquippedParticles() != null) { prof.setEquippedParticles(null); prof.setParticleActivatedAt(null); had = true; }
                if (prof.getEquippedTrail() != null) { prof.setEquippedTrail(null); prof.setTrailActivatedAt(null); had = true; }
                if (had) {
                    profiles.save(who.getUniqueId());
                    who.sendMessage("§aAll your cosmetic effects have been disabled.");
                } else {
                    who.sendMessage("§7You have no active effects.");
                }
            }
            populate(who); who.updateInventory();
            return true;
        }

        if (slot >= 9) {
            PlayerProfile prof = profiles.getOrCreate(who, plugin.getConfig().getString("ranks.default", "friend"));
            if (prof == null) return true; // DB disabled
            List<CosmeticsManager.Cosmetic> list = switch (current) {
                case PARTICLES -> cosmetics.getByCategory(CosmeticsManager.Category.PARTICLES);
                case TRAILS -> cosmetics.getByCategory(CosmeticsManager.Category.TRAILS);
                case GADGETS -> cosmetics.getByCategory(CosmeticsManager.Category.GADGETS);
            };
            int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25};
            int index = -1;
            for (int i = 0; i < slots.length; i++) if (slots[i] == slot) { index = i; break; }
            if (index >= 0 && index < list.size()) {
                CosmeticsManager.Cosmetic pick = list.get(index);
                boolean unlocked = prof.getUnlockedCosmetics().contains(pick.key);
                if (!unlocked) {
                    // Attempt purchase
                    int price = pick.price;
                    if (!pick.enabled) { who.sendMessage("§cThis item is currently disabled."); return true; }
                    if (!prof.spendCosmeticCoins(price)) {
                        who.sendMessage("§cNot enough coins. §7Price: §6" + price + " coins");
                        return true;
                    }
                    // Persist coin deduction
                    profiles.save(who.getUniqueId());
                    // Unlock in DB and cache
                    String type = switch (pick.category) {
                        case PARTICLES -> "particle";
                        case TRAILS -> "trail";
                        case GADGETS -> "gadget";
                    };
                    JebaitedCore.getInstance().getPlayerProfileDAO().unlockCosmetic(who.getUniqueId(), pick.key, type);
                    prof.getUnlockedCosmetics().add(pick.key);
                    who.sendMessage("§aPurchased §e" + pick.name + " §afor §6" + price + " coins§a.");
                }

                // Equip now
                switch (pick.category) {
                    case PARTICLES -> { prof.setEquippedParticles(pick.key); prof.setParticleActivatedAt(System.currentTimeMillis()); }
                    case TRAILS -> { prof.setEquippedTrail(pick.key); prof.setTrailActivatedAt(System.currentTimeMillis()); }
                    case GADGETS -> prof.setEquippedGadget(pick.key);
                }
                prof.incCosmeticEquips();
                profiles.save(who.getUniqueId());
                who.sendMessage("§aEquipped §e" + pick.name + "§a.");
                populate(who); who.updateInventory();
                return true;
            }
        }
        return true;
    }
}
