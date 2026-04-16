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

import java.util.Collections;
import java.util.List;

/**
 * /enderchest (/ec) — opens the player's own ender chest anywhere.
 * Gold+ donor perk. SMP-only (hub-blocked).
 */
public class EnderchestCommand implements CommandExecutor, TabCompleter {

    private final JebaitedCore plugin;

    public EnderchestCommand(JebaitedCore plugin) {
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

        boolean isGold = (primary != null && ranks.isAtLeast(primary, "gold"))
                || (donor != null && ranks.isAtLeast(donor, "gold"));

        if (!isGold) {
            player.sendMessage(Messages.noPerm());
            return true;
        }

        if (plugin.getWorldManager() != null && !plugin.getWorldManager().isSmp(player.getWorld())) {
            player.sendMessage(Messages.prefixed("§c/enderchest can only be used in the SMP world."));
            return true;
        }

        player.openInventory(player.getEnderChest());
        player.sendMessage(Messages.prefixed("§aEnder chest opened."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
