package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.system.EconomyManager;
import com.darkniightz.core.system.NicknameManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class WhoisCommand implements CommandExecutor {

    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;
    private final EconomyManager economy;
    private final NicknameManager nicknames;

    public WhoisCommand(ProfileStore profiles, RankManager ranks, DevModeManager devMode, EconomyManager economy, NicknameManager nicknames) {
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
        this.economy = economy;
        this.nicknames = nicknames;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " <player>"));
            return true;
        }

        if (sender instanceof Player actor) {
            PlayerProfile actorProfile = profiles.getOrCreate(actor, ranks.getDefaultGroup());
            boolean bypass = devMode != null && devMode.isActive(actor.getUniqueId());
            if (!bypass && !ranks.isAtLeast(actorProfile.getPrimaryRank(), "helper")) {
                sender.sendMessage(Messages.noPerm());
                return true;
            }
        }

        OfflinePlayer target = resolveByNameOrNick(args[0]);
        if (!target.isOnline() && !target.hasPlayedBefore()) {
            sender.sendMessage(Messages.prefixed("§cPlayer not found: §e" + args[0]));
            return true;
        }

        PlayerProfile targetProfile = profiles.getOrCreate(target, ranks.getDefaultGroup());
        String targetName = target.getName() == null ? target.getUniqueId().toString().substring(0, 8) : target.getName();
        String nick = nicknames.getNickname(target.getUniqueId());

        sender.sendMessage(Messages.prefixed("§6Whois §e" + targetName));
        sender.sendMessage(Messages.prefixed("§7UUID: §f" + target.getUniqueId()));
        sender.sendMessage(Messages.prefixed("§7Nickname: " + (nick == null ? "§8none" : "§f" + nick)));
        sender.sendMessage(Messages.prefixed("§7Rank: §f" + targetProfile.getPrimaryRank()));
        String donorRank = targetProfile.getDonorRank();
        if (donorRank != null) {
            sender.sendMessage(Messages.prefixed("§7Donor Rank: §b" + donorRank
                    + " §8(displaying: §7" + targetProfile.getRankDisplayMode() + "§8)"));
        }
        sender.sendMessage(Messages.prefixed("§7Online: " + (target.isOnline() ? "§ayes" : "§cno")));
        sender.sendMessage(Messages.prefixed("§7Balance: §f" + economy.format(economy.getBalance(target))));
        sender.sendMessage(Messages.prefixed("§7Coins: §f" + targetProfile.getCosmeticCoins()));
        Long muteUntil = targetProfile.getMuteUntil();
        sender.sendMessage(Messages.prefixed("§7Muted: " + (muteUntil != null && muteUntil > System.currentTimeMillis() ? "§cyes" : "§ano")));

        if (target.isOnline() && target.getPlayer() != null) {
            Player online = target.getPlayer();
            sender.sendMessage(Messages.prefixed("§7World: §f" + online.getWorld().getName()));
            sender.sendMessage(Messages.prefixed("§7Location: §f" + Math.round(online.getLocation().getX()) + ", " + Math.round(online.getLocation().getY()) + ", " + Math.round(online.getLocation().getZ())));
        }
        return true;
    }

    private OfflinePlayer resolveByNameOrNick(String input) {
        if (input == null || input.isBlank()) {
            return Bukkit.getOfflinePlayer("invalid");
        }
        String needle = input.toLowerCase(Locale.ROOT);
        for (Player online : Bukkit.getOnlinePlayers()) {
            String nick = nicknames.getNickname(online.getUniqueId());
            if (nick != null && nick.equalsIgnoreCase(needle)) {
                return online;
            }
            String rendered = nicknames.displayName(online.getName(), online.getUniqueId());
            if (rendered != null && rendered.equalsIgnoreCase(needle)) {
                return online;
            }
            if (online.getName().equalsIgnoreCase(needle)) {
                return online;
            }
        }
        return Bukkit.getOfflinePlayer(input);
    }
}
