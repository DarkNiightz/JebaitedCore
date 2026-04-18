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
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Alias command for setting ranks: /setrank <player> <group>
 */
public class SetRankCommand implements CommandExecutor, TabCompleter {

    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;

    public SetRankCommand(ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        final boolean consoleBypass = sender instanceof ConsoleCommandSender || !(sender instanceof Player);
        PlayerProfile actor = null;
        String actorRankEarly = ranks.getDefaultGroup();
        boolean bypassEarly = consoleBypass;
        boolean actorIsDevOrOwnerEarly = consoleBypass;
        if (sender instanceof Player p) {
            actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
            actorRankEarly = actor.getPrimaryRank();
            bypassEarly = devMode != null && devMode.isActive(p.getUniqueId());
            actorIsDevOrOwnerEarly = equalsAny(actorRankEarly, "owner", "developer");
            if (!bypassEarly && !actorIsDevOrOwnerEarly && !ranks.isAtLeast(actorRankEarly, "srmod")) {
                sender.sendMessage(Messages.noPerm());
                return true;
            }
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " <player> <group>"));
            return true;
        }
        String targetName = args[0];
        String newGroup = args[1].toLowerCase(Locale.ROOT);

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

        PlayerProfile tp = profiles.getOrCreate(target, ranks.getDefaultGroup());

        String actorRank = actor == null ? "owner" : actor.getPrimaryRank();
        String targetRank = tp.getPrimaryRank();

        boolean bypass = bypassEarly;
        boolean actorIsDevOrOwner = actorIsDevOrOwnerEarly;
        boolean actorIsAdmin = bypass || actorIsDevOrOwner || ranks.isAtLeast(actorRank, "admin");
        boolean allowed = bypass || actorIsDevOrOwner
                || (ranks.outranksStrict(actorRank, targetRank) && ranks.outranksStrict(actorRank, newGroup));
        if (!allowed) { sender.sendMessage(Messages.noPerm()); return true; }
        // srmod can only assign up to helper — admin+ can assign up to their own level
        if (!actorIsAdmin && ranks.isAtLeast(newGroup, "moderator")) {
            sender.sendMessage(Messages.prefixed("§cSr. Mods can only assign ranks up to §eHelper§c."));
            return true;
        }

        // Auto-fallback: if deranking to pleb but they have a donor rank, return them there instead.
        // To force true pleb, clear donor rank first via /setdonor <player> none.
        String effectiveGroup = newGroup;
        String donorFallback = tp.getDonorRank();
        if (newGroup.equals("pleb") && donorFallback != null) {
            effectiveGroup = donorFallback;
        }

        tp.setPrimaryRank(effectiveGroup);
        // Preserve donor display preference — only reset to primary if they have no donor rank.
        if (tp.getDonorRank() == null) {
            tp.setRankDisplayMode("primary");
        }
        profiles.save(tuid);
        if (JebaitedCore.getInstance() != null && JebaitedCore.getInstance().getPlayerProfileDAO() != null) {
            boolean persisted = JebaitedCore.getInstance().getPlayerProfileDAO()
                    .persistRankImmediate(tuid, target.getName(), effectiveGroup);
            if (!persisted) {
                JebaitedCore.getInstance().getLogger().warning("[SetRank] Immediate rank persist affected 0 rows for " + targetName + " (" + tuid + ")");
            }
        }

        String targetDisplayName = target.getName() != null ? target.getName() : target.getUniqueId().toString();
        if (!effectiveGroup.equals(newGroup)) {
            // Redirected to donor rank
            sender.sendMessage(Messages.prefixed("§e" + targetDisplayName + "§a has a donor rank (§b" + effectiveGroup
                    + "§a) — returned there instead of pleb. Use §f/setdonor " + targetDisplayName + " none§a first to fully derank."));
        } else {
            sender.sendMessage(Messages.prefixed("§aSet §e" + targetDisplayName + "§a to group §b" + effectiveGroup + "§a."));
        }
        if (target.isOnline()) {
            var online = target.getPlayer();
            if (!effectiveGroup.equals(newGroup)) {
                online.sendMessage(Messages.prefixed("§aYour rank has been returned to your donor rank: §b" + effectiveGroup + "§a."));
            } else {
                online.sendMessage(Messages.prefixed("§aYour rank has been set to §b" + effectiveGroup + "§a."));
            }
            if (JebaitedCore.getInstance() != null) {
                JebaitedCore.getInstance().refreshPlayerPresentation(online);
                JebaitedCore.getInstance().refreshAllPlayerPresentations();
            }
        }
        return true;
    }

    private boolean equalsAny(String s, String... opts) {
        if (s == null) return false;
        for (String o : opts) if (s.equalsIgnoreCase(o)) return true;
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        boolean consoleBypass = sender instanceof ConsoleCommandSender || !(sender instanceof Player);
        PlayerProfile actor = null;
        boolean bypass = consoleBypass;
        boolean actorIsDevOrOwner = consoleBypass;
        if (sender instanceof Player p) {
            actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
            bypass = devMode != null && devMode.isActive(p.getUniqueId());
            actorIsDevOrOwner = equalsAny(actor.getPrimaryRank(), "owner", "developer");
            // Must be at least able to set ranks
            if (!bypass && !actorIsDevOrOwner && !ranks.isAtLeast(actor.getPrimaryRank(), "srmod")) {
                return List.of();
            }
        }
        String prefix = args.length > 0 ? args[args.length - 1].toLowerCase(Locale.ROOT) : "";
        if (args.length == 1) {
            // arg[0] = target player name
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            // arg[1] = rank name — only suggest ranks the actor can assign
            final boolean bypassFinal = bypass;
            final boolean actorIsDevOrOwnerFinal = actorIsDevOrOwner;
            final String actorRankForTab = actor == null ? null : actor.getPrimaryRank();
            boolean actorIsAdminTab = bypassFinal || actorIsDevOrOwnerFinal || (actorRankForTab != null && ranks.isAtLeast(actorRankForTab, "admin"));
            return ranks.getLadder().stream()
                    .filter(r -> bypassFinal || actorIsDevOrOwnerFinal || (actorRankForTab != null && ranks.outranksStrict(actorRankForTab, r)))
                    .filter(r -> actorIsAdminTab || !ranks.isAtLeast(r, "moderator")) // srmod cap at helper
                    .filter(r -> r.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
