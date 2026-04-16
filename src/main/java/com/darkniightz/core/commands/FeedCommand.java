package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.system.CombatTagManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * /feed — replenishes hunger and saturation to maximum.
 * Diamond+ donor perk. Blocked while in combat.
 */
public class FeedCommand implements CommandExecutor, TabCompleter {

    private final JebaitedCore plugin;

    public FeedCommand(JebaitedCore plugin) {
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

        boolean isDiamond = (primary != null && ranks.isAtLeast(primary, "diamond"))
                || (donor != null && ranks.isAtLeast(donor, "diamond"));

        if (!isDiamond) {
            player.sendMessage(Messages.noPerm());
            return true;
        }

        CombatTagManager combatTag = plugin.getCombatTagManager();
        if (combatTag != null && combatTag.isTagged(player)) {
            player.sendMessage(Messages.prefixed("§cYou cannot use §f/feed §cwhile in combat."));
            return true;
        }

        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.sendMessage(Messages.prefixed("§aYou have been fed!"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
