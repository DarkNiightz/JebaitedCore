package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * /repair [all] — repairs the item in hand (or all items in inventory).
 * Grandmaster+ donor perk.
 *
 * Configured cooldown: donor.perks.repair_cooldown_ms (default 300000 = 5 min).
 */
public class RepairCommand implements CommandExecutor, TabCompleter {

    private static final long DEFAULT_COOLDOWN_MS = 300_000L; // 5 minutes

    private final JebaitedCore plugin;

    public RepairCommand(JebaitedCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("§cThis command can only be used in-game."));
            return true;
        }

        PlayerProfile profile = plugin.getProfileStore().get(player.getUniqueId());
        RankManager ranks = plugin.getRankManager();

        String primary = profile != null ? profile.getPrimaryRank() : null;
        String donor   = profile != null ? profile.getDonorRank()   : null;

        boolean isGrandmaster = (primary != null && ranks.isAtLeast(primary, "grandmaster"))
                || (donor != null && ranks.isAtLeast(donor, "grandmaster"));

        if (!isGrandmaster) {
            player.sendMessage(Messages.noPerm());
            return true;
        }

        boolean repairAll = args.length > 0 && args[0].equalsIgnoreCase("all");

        if (repairAll) {
            int count = repairAll(player);
            if (count == 0) {
                player.sendMessage(Messages.prefixed("§7No damageable items found in your inventory."));
            } else {
                player.sendMessage(Messages.prefixed("§aRepaired " + count + " item" + (count == 1 ? "" : "s") + "."));
            }
        } else {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (!isRepairable(held)) {
                player.sendMessage(Messages.prefixed("§cThe item in your hand cannot be repaired."));
                return true;
            }
            repairItem(held);
            player.getInventory().setItemInMainHand(held);
            player.sendMessage(Messages.prefixed("§aYour item has been repaired."));
        }

        return true;
    }

    // -----------------------------------------------------------------
    // Repair helpers
    // -----------------------------------------------------------------

    private boolean isRepairable(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta instanceof Damageable;
    }

    private void repairItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            damageable.setDamage(0);
            item.setItemMeta(meta);
        }
    }

    private int repairAll(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isRepairable(item)) {
                repairItem(item);
                count++;
            }
        }
        // Armour slots
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (isRepairable(item)) {
                repairItem(item);
                count++;
            }
        }
        return count;
    }

    // -----------------------------------------------------------------
    // Tab completion
    // -----------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        PlayerProfile profile = (sender instanceof Player p)
                ? plugin.getProfileStore().get(p.getUniqueId()) : null;
        RankManager ranks = plugin.getRankManager();
        String primary = profile != null ? profile.getPrimaryRank() : null;
        String donor   = profile != null ? profile.getDonorRank()   : null;
        boolean isGrandmaster = (primary != null && ranks.isAtLeast(primary, "grandmaster"))
                || (donor != null && ranks.isAtLeast(donor, "grandmaster"));
        if (!isGrandmaster) return List.of();
        List<String> completions = new ArrayList<>();
        StringUtil.copyPartialMatches(args[0], List.of("all"), completions);
        return completions;
    }
}
