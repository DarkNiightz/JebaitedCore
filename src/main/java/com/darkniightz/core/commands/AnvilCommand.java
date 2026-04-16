package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.main.JebaitedCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * /anvil — opens a portable anvil.
 * Gold+ donor perk. SMP-only.
 */
public class AnvilCommand implements CommandExecutor, TabCompleter {

    private final JebaitedCore plugin;

    public AnvilCommand(JebaitedCore plugin) {
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
            player.sendMessage(Messages.prefixed("§c/anvil can only be used in the SMP world."));
            return true;
        }

        player.openAnvil(null, true);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
