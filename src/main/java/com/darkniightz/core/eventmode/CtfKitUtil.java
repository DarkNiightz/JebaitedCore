package com.darkniightz.core.eventmode;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;

/**
 * Applies configured CTF loadouts after SMP inventory is cleared (snapshot already stored).
 */
public final class CtfKitUtil {

    private CtfKitUtil() {}

    public static void apply(Player player, List<ArenaConfig.CtfKitEntry> entries) {
        if (player == null || entries == null || entries.isEmpty()) return;
        PlayerInventory inv = player.getInventory();
        int mainCursor = 0;
        for (ArenaConfig.CtfKitEntry entry : entries) {
            ItemStack stack = entry.stack();
            if (stack == null || stack.getType().isAir()) continue;
            ItemStack give = stack.clone();
            EquipmentSlot slot = entry.equipSlot();
            if (slot == null) {
                if (mainCursor < 36) {
                    inv.setItem(mainCursor++, give);
                }
                continue;
            }
            EntityEquipment eq = player.getEquipment();
            if (eq == null) continue;
            switch (slot) {
                case HEAD -> eq.setHelmet(give);
                case CHEST -> eq.setChestplate(give);
                case LEGS -> eq.setLeggings(give);
                case FEET -> eq.setBoots(give);
                case HAND -> eq.setItemInMainHand(give);
                case OFF_HAND -> eq.setItemInOffHand(give);
                case BODY -> { /* wolf armor / future — ignore for CTF */ }
            }
        }
        player.updateInventory();
    }
}
