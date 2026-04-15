package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.system.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BalanceCommand implements CommandExecutor {

    private final EconomyManager economy;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;

    public BalanceCommand(EconomyManager economy, ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
        this.economy = economy;
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Messages.prefixed("§cConsole must specify a player: §f/" + label + " <player>"));
                return true;
            }
            sender.sendMessage(Messages.prefixed("§6Balance§7: §a" + economy.format(economy.getBalance(player))));
            return true;
        }

        if (!(sender instanceof Player actor)) {
            sender.sendMessage(Messages.prefixed("§cConsole does not have a profile rank; use /eco for admin actions."));
            return true;
        }

        PlayerProfile actorProfile = profiles.getOrCreate(actor, ranks.getDefaultGroup());
        boolean bypass = devMode != null && devMode.isActive(actor.getUniqueId());
        if (!bypass && !ranks.isAtLeast(actorProfile.getPrimaryRank(), "helper")) {
            sender.sendMessage(Messages.noPerm());
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.isOnline() && !target.hasPlayedBefore()) {
            sender.sendMessage(Messages.prefixed("§cPlayer not found: §e" + args[0]));
            return true;
        }

        String targetName = target.getName() != null ? target.getName() : target.getUniqueId().toString().substring(0, 8);
        sender.sendMessage(Messages.prefixed("§6Balance for §e" + targetName + "§7: §a" + economy.format(economy.getBalance(target))));
        return true;
    }
}
