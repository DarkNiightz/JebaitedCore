package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
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

import java.util.Collections;
import java.util.List;

/**
 * /deathtp (/dtp) — teleport to last death location.
 * Legend+ donor perk. One-use; must be out of combat; SMP world only.
 *
 * Same mechanics as /back (Grandmaster), but unlocked at Legend.
 */
public class DeathTpCommand implements CommandExecutor, TabCompleter {

    private final JebaitedCore plugin;

    public DeathTpCommand(JebaitedCore plugin) {
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

        boolean isLegend = (primary != null && ranks.isAtLeast(primary, "legend"))
                || (donor != null && ranks.isAtLeast(donor, "legend"));

        if (!isLegend) {
            player.sendMessage(Messages.noPerm());
            return true;
        }

        CombatTagManager combatTag = plugin.getCombatTagManager();
        if (combatTag != null && combatTag.isTagged(player)) {
            player.sendMessage(Messages.prefixed("§cYou cannot use §f/deathtp §cwhile in combat."));
            return true;
        }

        if (plugin.getWorldManager() != null && !plugin.getWorldManager().isSmp(player.getWorld())) {
            player.sendMessage(Messages.prefixed("§c/deathtp can only be used in the SMP world."));
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
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
