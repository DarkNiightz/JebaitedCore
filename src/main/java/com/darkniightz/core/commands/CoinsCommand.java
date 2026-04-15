package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class CoinsCommand implements CommandExecutor {
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;

    public CoinsCommand(ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Messages.prefixed("§cConsole must specify a player: §f/" + label + " <player>"));
                return true;
            }
            PlayerProfile prof = profiles.getOrCreate(p, ranks.getDefaultGroup());
            sender.sendMessage(Messages.prefixed("§6Cosmetic Coins§7: §e" + prof.getCosmeticCoins()));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1 && !equalsAny(sub, "give", "take", "remove", "set")) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (target.getUniqueId() == null) {
                sender.sendMessage(Messages.prefixed("§cPlayer not found: §e" + args[0]));
                return true;
            }
            PlayerProfile tp = profiles.getOrCreate(target, ranks.getDefaultGroup());
            String name = target.getName() != null ? target.getName() : target.getUniqueId().toString().substring(0, 8);
            sender.sendMessage(Messages.prefixed("§6Cosmetic Coins for §e" + name + "§7: §e" + tp.getCosmeticCoins()));
            return true;
        }

        if (args.length >= 3 && equalsAny(sub, "give", "take", "remove", "set")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Messages.prefixed("§cOnly players can manage coins in this build."));
                return true;
            }
            PlayerProfile actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
            boolean bypass = devMode != null && devMode.isActive(p.getUniqueId());
            if (!bypass && !ranks.isAtLeast(actor.getPrimaryRank(), "admin")) {
                sender.sendMessage(com.darkniightz.core.Messages.noPerm());
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (target.getUniqueId() == null) {
                sender.sendMessage(Messages.prefixed("§cPlayer not found: §e" + args[1]));
                return true;
            }

            int amount;
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Messages.prefixed("§cAmount must be a number."));
                return true;
            }
            if (amount < 0) {
                sender.sendMessage(Messages.prefixed("§cAmount must be zero or positive."));
                return true;
            }

            PlayerProfile tp = profiles.getOrCreate(target, ranks.getDefaultGroup());
            int before = tp.getCosmeticCoins();
            int after = before;
            switch (sub) {
                case "give" -> after = before + amount;
                case "take", "remove" -> after = Math.max(0, before - amount);
                case "set" -> after = Math.max(0, amount);
            }
            tp.setCosmeticCoins(after);
            profiles.save(target.getUniqueId());

            String tName = target.getName() != null ? target.getName() : target.getUniqueId().toString().substring(0, 8);
            sender.sendMessage(Messages.prefixed("§aUpdated coins for §e" + tName + "§a: §7" + before + " §8-> §e" + after));
            if (target.isOnline()) {
                Player online = target.getPlayer();
                if (after > before) {
                    online.sendActionBar(net.kyori.adventure.text.Component.text("+" + (after - before) + " coins", net.kyori.adventure.text.format.NamedTextColor.GOLD)
                            .append(net.kyori.adventure.text.Component.text(" • admin update", net.kyori.adventure.text.format.NamedTextColor.GRAY)));
                    online.playSound(online.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.3f);
                }
                online.sendMessage(Messages.prefixed("§aYour §6Cosmetic Coins §7changed: §7" + before + " §8-> §e" + after));
            }
            return true;
        }

        sender.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " [player] §7or §f/" + label + " <give|take|remove|set> <player> <amount>"));
        return true;
    }

    private boolean equalsAny(String s, String... opts) {
        if (s == null) return false;
        for (String o : opts) if (s.equalsIgnoreCase(o)) return true;
        return false;
    }
}
