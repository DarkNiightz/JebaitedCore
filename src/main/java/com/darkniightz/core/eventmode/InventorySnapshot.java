package com.darkniightz.core.eventmode;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * A point-in-time snapshot of a player's inventory and vitals.
 * Captured before an event starts; restored when the event ends.
 */
public record InventorySnapshot(
        ItemStack[] contents,
        ItemStack[] armor,
        ItemStack offHand,
        int level,
        float exp,
        int food,
        double health,
        Location returnLocation
) {
    public static InventorySnapshot capture(Player player) {
        ItemStack[] contents = player.getInventory().getContents().clone();
        ItemStack[] armor    = player.getInventory().getArmorContents().clone();
        ItemStack offHand    = player.getInventory().getItemInOffHand();
        offHand = offHand == null ? null : offHand.clone();
        int level            = player.getLevel();
        float exp            = player.getExp();
        int food             = player.getFoodLevel();
        double health        = player.getHealth();
        Location loc         = player.getLocation().clone();
        return new InventorySnapshot(contents, armor, offHand, level, exp, food, health, loc);
    }

    public void restore(Player player) {
        player.getInventory().setContents(contents == null ? new ItemStack[0] : contents.clone());
        player.getInventory().setArmorContents(armor == null ? new ItemStack[0] : armor.clone());
        player.getInventory().setItemInOffHand(offHand == null ? null : offHand.clone());
        player.setLevel(level);
        player.setExp(exp);
        player.setFoodLevel(food);
        double targetHealth = Math.max(1.0, Math.min(player.getMaxHealth(), health));
        player.setHealth(targetHealth);
        if (returnLocation != null && returnLocation.getWorld() != null) {
            player.teleport(returnLocation);
        }
        player.updateInventory();
    }
}
