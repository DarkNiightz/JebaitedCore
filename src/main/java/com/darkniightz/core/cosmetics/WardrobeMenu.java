package com.darkniightz.core.cosmetics;

import com.darkniightz.core.gui.BaseMenu;
import com.darkniightz.core.gui.ItemBuilder;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
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
        super(plugin, "§d§lWardrobe", 27);
        this.cosmetics = cosmetics;
        this.profiles = profiles;
    }

    @Override
    protected void populate(Player viewer) {
        Inventory inv = getInventory();

        // Category selector
        inv.setItem(2, new ItemBuilder(Material.BLAZE_POWDER).name("§6Particles").build());
        inv.setItem(4, new ItemBuilder(Material.FEATHER).name("§5Trails").build());
        inv.setItem(6, new ItemBuilder(Material.FIREWORK_ROCKET).name("§dGadgets").build());

        // Clear grid area
        for (int i = 9; i < 27; i++) inv.setItem(i, null);

        PlayerProfile prof = profiles.getOrCreate(viewer, plugin.getConfig().getString("ranks.default", "friend"));
        List<CosmeticsManager.Cosmetic> list = switch (current) {
            case PARTICLES -> cosmetics.getByCategory(CosmeticsManager.Category.PARTICLES);
            case TRAILS -> cosmetics.getByCategory(CosmeticsManager.Category.TRAILS);
            case GADGETS -> cosmetics.getByCategory(CosmeticsManager.Category.GADGETS);
        };
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25};
        int idx = 0;
        for (CosmeticsManager.Cosmetic c : list) {
            if (idx >= slots.length) break;
            boolean equipped = isEquipped(prof, c);
            String state = equipped ? " §a(Equipped)" : (prof.hasUnlocked(c.key) || true) ? "" : " §8(Locked)"; // treat all as unlocked for MVP
            inv.setItem(slots[idx++], new ItemBuilder(c.icon).name(c.name + state).lore(c.lore).build());
        }
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

        if (slot >= 9) {
            PlayerProfile prof = profiles.getOrCreate(who, plugin.getConfig().getString("ranks.default", "friend"));
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
                switch (pick.category) {
                    case PARTICLES -> prof.setEquippedParticles(pick.key);
                    case TRAILS -> prof.setEquippedTrail(pick.key);
                    case GADGETS -> prof.setEquippedGadget(pick.key);
                }
                prof.incCosmeticEquips();
                who.sendMessage("§aEquipped §e" + pick.name + "§a.");
                populate(who); who.updateInventory();
                return true;
            }
        }
        return true;
    }
}
