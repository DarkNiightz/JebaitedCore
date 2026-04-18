package com.darkniightz.core.commands.mod;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.moderation.ModerationLogger;
import com.darkniightz.core.moderation.ModerationManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public class FreezeCommand implements CommandExecutor, TabCompleter {
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;
    private final ModerationManager moderation;
    private final Boolean forcedFrozenState;

    public FreezeCommand(ProfileStore profiles, RankManager ranks, DevModeManager devMode, ModerationManager moderation) {
        this(profiles, ranks, devMode, moderation, null);
    }

    public FreezeCommand(ProfileStore profiles, RankManager ranks, DevModeManager devMode, ModerationManager moderation, Boolean forcedFrozenState) {
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
        this.moderation = moderation;
        this.forcedFrozenState = forcedFrozenState;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String actorName = sender instanceof Player p ? p.getName() : "_console_";
        UUID actorUuid = sender instanceof Player p ? p.getUniqueId() : UUID.fromString("00000000-0000-0000-0000-000000000000");
        boolean isConsole = !(sender instanceof Player);

        if (!isConsole) {
            Player p = (Player) sender;
            PlayerProfile actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
            boolean bypass = devMode != null && devMode.isActive(p.getUniqueId());
            if (!bypass && !ranks.isAtLeast(actor.getPrimaryRank(), "moderator")) {
                sender.sendMessage(Messages.noPerm());
                return true;
            }
        }

        if (args.length < 1) {
            sender.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " <player>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Messages.prefixed("§cPlayer not found: §e" + args[0]));
            return true;
        }

        boolean now = forcedFrozenState != null ? forcedFrozenState : !moderation.isFrozen(target.getUniqueId());
        moderation.setFrozen(target.getUniqueId(), now);
        String state = now ? "frozen" : "unfrozen";

        var entry = ModerationLogger.entry(now ? "freeze" : "unfreeze", actorName, actorUuid, null, null, null);
        ModerationLogger.log(target.getUniqueId(), entry);

        sender.sendMessage(Messages.prefixed("§a" + state.substring(0, 1).toUpperCase() + state.substring(1) + " §e" + target.getName()));
        if (now) {
            target.sendMessage(Messages.prefixed("§cYou have been frozen by staff. Do not move."));
        } else {
            target.sendMessage(Messages.prefixed("§aYou are no longer frozen."));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) return List.of();
        PlayerProfile actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
        boolean bypass = devMode != null && devMode.isActive(p.getUniqueId());
        if (!bypass && !ranks.isAtLeast(actor.getPrimaryRank(), "moderator")) return List.of();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
