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

import java.util.Locale;

public class EcoCommand implements CommandExecutor {

    private final EconomyManager economy;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;

    public EcoCommand(EconomyManager economy, ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
        this.economy = economy;
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player actor)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can manage economy in this build."));
            return true;
        }

        PlayerProfile actorProfile = profiles.getOrCreate(actor, ranks.getDefaultGroup());
        boolean bypass = devMode != null && devMode.isActive(actor.getUniqueId());
        if (!bypass && !ranks.isAtLeast(actorProfile.getPrimaryRank(), "admin")) {
            sender.sendMessage(Messages.noPerm());
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " <give|take|set> <player> <amount>"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.isOnline() && !target.hasPlayedBefore()) {
            sender.sendMessage(Messages.prefixed("§cPlayer not found: §e" + args[1]));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Messages.prefixed("§cAmount must be a number."));
            return true;
        }
        if (amount < 0D) {
            sender.sendMessage(Messages.prefixed("§cAmount must be zero or positive."));
            return true;
        }

        double before = economy.getBalance(target);
        switch (sub) {
            case "give", "add" -> economy.addBalance(target, amount);
            case "take", "remove" -> {
                if (!economy.removeBalance(target, amount)) {
                    sender.sendMessage(Messages.prefixed("§cThat player does not have enough money."));
                    return true;
                }
            }
            case "set" -> economy.setBalance(target, amount);
            default -> {
                sender.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " <give|take|set> <player> <amount>"));
                return true;
            }
        }

        double after = economy.getBalance(target);
        String tName = target.getName() != null ? target.getName() : target.getUniqueId().toString().substring(0, 8);
        sender.sendMessage(Messages.prefixed("§aUpdated balance for §e" + tName + "§a: §7" + economy.format(before) + " §8-> §a" + economy.format(after)));

        if (target.isOnline() && target.getPlayer() != null) {
            target.getPlayer().sendMessage(Messages.prefixed("§aYour balance changed: §7" + economy.format(before) + " §8-> §a" + economy.format(after)));
        }
        return true;
    }
}
