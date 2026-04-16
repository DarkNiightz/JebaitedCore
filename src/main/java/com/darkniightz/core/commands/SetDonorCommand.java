package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /setdonor <player> <rank|none>
 * Sets (or clears) a player's donor rank. Admin+ only.
 * The donor rank is a secondary rank that unlocks donor perks while the player
 * holds a staff primary rank, or can be used standalone for cosmetic display.
 */
public class SetDonorCommand implements CommandExecutor, TabCompleter {

    private static final Set<String> DONOR_RANKS = Set.of(
            "gold", "diamond", "legend", "grandmaster");

    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;

    public SetDonorCommand(ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can run this command."));
            return true;
        }

        PlayerProfile actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
        boolean bypass = devMode != null && devMode.isActive(p.getUniqueId());
        boolean actorIsDevOrOwner = equalsAny(actor.getPrimaryRank(), "owner", "developer");

        if (!bypass && !actorIsDevOrOwner && !ranks.isAtLeast(actor.getPrimaryRank(), "admin")) {
            sender.sendMessage(Messages.noPerm());
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " <player> <rank|none>"));
            return true;
        }

        String targetName = args[0];
        String newDonorRank = args[1].toLowerCase(Locale.ROOT);
        boolean clearing = newDonorRank.equals("none") || newDonorRank.equals("clear") || newDonorRank.equals("reset");

        if (!clearing && !DONOR_RANKS.contains(newDonorRank)) {
            sender.sendMessage(Messages.prefixed("§cInvalid donor rank: §e" + newDonorRank
                    + "§c. Valid: §f" + String.join(", ", DONOR_RANKS) + "§c. Use §fnone§c to clear."));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        UUID tuid = target.getUniqueId();
        if (tuid == null) {
            sender.sendMessage(Messages.prefixed("§cPlayer not found: §e" + targetName));
            return true;
        }

        PlayerProfile tp = profiles.getOrCreate(target, ranks.getDefaultGroup());

        // Prevent overwriting an existing donor rank without explicitly clearing first.
        // Upgrades are a purchase — use /setdonor <player> none first, then assign the new tier.
        if (!clearing && tp.getDonorRank() != null) {
            sender.sendMessage(Messages.prefixed(
                    "§c" + (target.getName() != null ? target.getName() : tuid)
                    + " already has donor rank §e" + tp.getDonorRank()
                    + "§c. Clear it first: §f/" + label + " " + targetName + " none"));
            return true;
        }

        tp.setDonorRank(clearing ? null : newDonorRank);
        if (clearing) {
            // Clear donor display preference
            tp.setRankDisplayMode("primary");
        } else {
            // Auto-elevate primary rank if currently pleb (donators never show as pleb)
            if ("pleb".equalsIgnoreCase(tp.getPrimaryRank())) {
                tp.setPrimaryRank(newDonorRank);
            }
            // Auto-switch display to show donor rank immediately
            tp.setRankDisplayMode("donor");
        }
        profiles.save(tuid);

        if (clearing) {
            sender.sendMessage(Messages.prefixed("§aCleared donor rank for §e"
                    + (target.getName() != null ? target.getName() : tuid) + "§a."));
        } else {
            sender.sendMessage(Messages.prefixed("§aSet donor rank for §e"
                    + (target.getName() != null ? target.getName() : tuid) + "§a to §b" + newDonorRank + "§a."));
        }

        if (target.isOnline()) {
            Player online = target.getPlayer();
            if (online != null) {
                if (clearing) {
                    online.sendMessage(Messages.prefixed("§aYour donor rank has been removed."));
                } else {
                    online.sendMessage(Messages.prefixed("§aYour donor rank has been set to §b" + newDonorRank + "§a."));
                }
                if (JebaitedCore.getInstance() != null) {
                    JebaitedCore.getInstance().refreshPlayerPresentation(online);
                    JebaitedCore.getInstance().refreshAllPlayerPresentations();
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) return List.of();
        PlayerProfile actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
        boolean bypass = devMode != null && devMode.isActive(p.getUniqueId());
        boolean actorIsDevOrOwner = equalsAny(actor.getPrimaryRank(), "owner", "developer");
        if (!bypass && !actorIsDevOrOwner && !ranks.isAtLeast(actor.getPrimaryRank(), "admin")) {
            return List.of();
        }
        String prefix = args.length > 0 ? args[args.length - 1].toLowerCase(Locale.ROOT) : "";
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            List<String> options = new java.util.ArrayList<>(DONOR_RANKS);
            options.add("none");
            return options.stream()
                    .filter(r -> r.startsWith(prefix))
                    .sorted()
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private boolean equalsAny(String s, String... opts) {
        if (s == null) return false;
        for (String o : opts) if (s.equalsIgnoreCase(o)) return true;
        return false;
    }
}
