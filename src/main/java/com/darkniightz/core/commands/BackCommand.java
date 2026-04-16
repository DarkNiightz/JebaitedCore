package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.permissions.PermissionConstants;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.system.BackManager;
import com.darkniightz.core.system.CombatTagManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /back — teleport to last death location.
 * Grandmaster (or higher on donor ladder) only. One-use; must be out of combat.
 */
public class BackCommand implements CommandExecutor, TabCompleter {

    private final JebaitedCore plugin;

    public BackCommand(JebaitedCore plugin) {
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

        // Combat check
        CombatTagManager combatTag = plugin.getCombatTagManager();
        if (combatTag != null && combatTag.isTagged(player)) {
            player.sendMessage(Messages.prefixed("§cYou cannot use §f/back §cwhile in combat."));
            return true;
        }

        // SMP check — /back only makes sense in the SMP world
        if (plugin.getWorldManager() != null && !plugin.getWorldManager().isSmp(player.getWorld())) {
            player.sendMessage(Messages.prefixed("§c/back can only be used in the SMP world."));
            return true;
        }

        BackManager backManager = plugin.getBackManager();
        if (backManager == null || !backManager.hasDeathLocation(player.getUniqueId())) {
            player.sendMessage(Messages.prefixed("§7You have no recent death location to return to."));
            return true;
        }

        Location dest = backManager.consumeDeathLocation(player.getUniqueId());
        if (dest == null || dest.getWorld() == null) {
            player.sendMessage(Messages.prefixed("§cDeath location is no longer valid."));
            return true;
        }

        player.teleport(dest);
        player.sendMessage(Messages.prefixed("§aTeleported to your last death location."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        // No subcommands — hide completions from non-grandmaster players
        if (!(sender instanceof Player player)) return List.of();
        PlayerProfile profile = plugin.getProfileStore().get(player.getUniqueId());
        RankManager ranks = plugin.getRankManager();
        String primary = profile != null ? profile.getPrimaryRank() : null;
        String donor   = profile != null ? profile.getDonorRank()   : null;
        boolean ok = (primary != null && ranks.isAtLeast(primary, "grandmaster"))
                || (donor != null && ranks.isAtLeast(donor, "grandmaster"));
        return ok ? List.of() : List.of();
    }
}
