package com.darkniightz.core.commands.mod;

import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.moderation.ModerationLogger;
import com.darkniightz.core.moderation.TimeUtil;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class MuteCommand implements CommandExecutor {
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;
    private final boolean permanentOnly;

    public MuteCommand(ProfileStore profiles, RankManager ranks, DevModeManager devMode, boolean permanentOnly) {
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
        this.permanentOnly = permanentOnly;
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
                sender.sendMessage(com.darkniightz.core.Messages.noPerm());
                return true;
            }
        }

        if (permanentOnly) {
            if (args.length < 1) {
                sender.sendMessage("§eUsage: §7/" + label + " <player> [reason]");
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (target.getUniqueId() == null) {
                sender.sendMessage("§cPlayer not found: §e" + args[0]);
                return true;
            }
            String reason = args.length >= 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "Muted";
            PlayerProfile tp = profiles.getOrCreate(target, ranks.getDefaultGroup());
            tp.setMuteUntil(Long.MAX_VALUE);
            tp.setMuteReason(reason);
            tp.setMuteActor(actorName);
            profiles.save(target.getUniqueId());
            var entry = ModerationLogger.entry("mute", actorName, actorUuid, reason, null, Long.MAX_VALUE);
            ModerationLogger.log(target.getUniqueId(), entry);
            if (target.isOnline()) target.getPlayer().sendMessage("§cYou have been muted §7(permanent). Reason: §f" + reason);
            sender.sendMessage("§aMuted §e" + (target.getName() != null ? target.getName() : target.getUniqueId()) + " §7(permanent)");
        } else {
            if (args.length < 2) {
                sender.sendMessage("§eUsage: §7/" + label + " <player> <duration> [reason]");
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (target.getUniqueId() == null) {
                sender.sendMessage("§cPlayer not found: §e" + args[0]);
                return true;
            }
            long dur = TimeUtil.parseDurationMillis(args[1]);
            if (dur <= 0) {
                sender.sendMessage("§cInvalid duration: §e" + args[1]);
                return true;
            }
            String reason = args.length >= 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "Temporarily muted";
            PlayerProfile tp = profiles.getOrCreate(target, ranks.getDefaultGroup());
            long until = System.currentTimeMillis() + dur;
            tp.setMuteUntil(until);
            tp.setMuteReason(reason);
            tp.setMuteActor(actorName);
            profiles.save(target.getUniqueId());
            var entry = ModerationLogger.entry("tempmute", actorName, actorUuid, reason, dur, until);
            ModerationLogger.log(target.getUniqueId(), entry);
            if (target.isOnline()) target.getPlayer().sendMessage("§cYou have been muted for §e" + TimeUtil.formatDurationShort(dur) + "§7. Reason: §f" + reason);
            sender.sendMessage("§aTemporarily muted §e" + (target.getName() != null ? target.getName() : target.getUniqueId()) + " §7for §e" + TimeUtil.formatDurationShort(dur));
        }
        return true;
    }
}