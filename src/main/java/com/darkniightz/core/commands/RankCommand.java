package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public class RankCommand implements CommandExecutor, TabCompleter {

    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;

    public RankCommand(ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " <get|set> ..."));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "get" -> {
                if (args.length < 2) {
                    sender.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " get <player>"));
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (target.getUniqueId() == null) {
                    sender.sendMessage(Messages.prefixed("§cPlayer not found: §e" + args[1]));
                    return true;
                }
                PlayerProfile tp = profiles.getOrCreate(target, ranks.getDefaultGroup());
                sender.sendMessage(Messages.prefixed("§6Rank of §e" + target.getName() + "§6: §b" + tp.getPrimaryRank()));
                return true;
            }
            case "set" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Messages.prefixed("§cOnly in-game staff can set ranks in this MVP."));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " set <player> <group> [duration]"));
                    return true;
                }
                String targetName = args[1];
                String newGroup = args[2].toLowerCase(Locale.ROOT);
                // Validate group exists
                if (!ranks.getLadder().contains(newGroup)) {
                    sender.sendMessage(Messages.prefixed("§cUnknown group: §e" + newGroup));
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                UUID tuid = target.getUniqueId();
                if (tuid == null) {
                    sender.sendMessage(Messages.prefixed("§cPlayer not found: §e" + targetName));
                    return true;
                }
                PlayerProfile actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
                PlayerProfile tp = profiles.getOrCreate(target, ranks.getDefaultGroup());

                String actorRank = actor.getPrimaryRank();
                String targetRank = tp.getPrimaryRank();

                boolean bypass = devMode != null && devMode.isActive(p.getUniqueId());
                boolean actorIsDevOrOwner = equalsAny(actorRank, "owner", "developer");
                boolean allowed = bypass || actorIsDevOrOwner
                        || (ranks.outranksStrict(actorRank, targetRank) && ranks.outranksStrict(actorRank, newGroup));
                if (!allowed) { sender.sendMessage(Messages.noPerm()); return true; }

                tp.setPrimaryRank(newGroup);
                profiles.save(tuid);
                sender.sendMessage(Messages.prefixed("§aSet §e" + (target.getName() != null ? target.getName() : target.getUniqueId()) + "§a to group §b" + newGroup + "§a."));
                if (target.isOnline()) {
                    target.getPlayer().sendMessage(Messages.prefixed("§aYour rank has been set to §b" + newGroup + "§a."));
                }
                return true;
            }
            default -> {
                sender.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " <get|set> ..."));
                return true;
            }
        }
    }

    private boolean equalsAny(String s, String... opts) {
        if (s == null) return false;
        for (String o : opts) if (s.equalsIgnoreCase(o)) return true;
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        String prefix = args.length > 0 ? args[args.length - 1].toLowerCase(Locale.ROOT) : "";
        if (args.length == 1) {
            return Arrays.asList("get", "set").stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        // /rank get <player> or /rank set <player>
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("get".equals(sub) || "set".equals(sub)) {
                // For 'set', gate by permission
                if ("set".equals(sub) && sender instanceof Player p) {
                    PlayerProfile actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
                    boolean bypass = devMode != null && devMode.isActive(p.getUniqueId());
                    boolean privileged = equalsAny(actor.getPrimaryRank(), "owner", "developer")
                            || ranks.isAtLeast(actor.getPrimaryRank(), "admin");
                    if (!bypass && !privileged) return List.of();
                }
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .collect(Collectors.toList());
            }
        }
        // /rank set <player> <rank>
        if (args.length == 3 && "set".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player p)) {
                return ranks.getLadder().stream()
                        .filter(r -> r.startsWith(prefix))
                        .collect(Collectors.toList());
            }
            PlayerProfile actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
            boolean bypass = devMode != null && devMode.isActive(p.getUniqueId());
            boolean actorIsDevOrOwner = equalsAny(actor.getPrimaryRank(), "owner", "developer");
            if (!bypass && !actorIsDevOrOwner && !ranks.isAtLeast(actor.getPrimaryRank(), "admin")) {
                return List.of();
            }
            return ranks.getLadder().stream()
                    .filter(r -> bypass || actorIsDevOrOwner || ranks.outranksStrict(actor.getPrimaryRank(), r))
                    .filter(r -> r.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
