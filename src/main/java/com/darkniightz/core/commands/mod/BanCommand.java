package com.darkniightz.core.commands.mod;

import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.moderation.ModerationLogger;
import com.darkniightz.core.moderation.TimeUtil;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BanCommand implements CommandExecutor {
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;
    private final boolean permanentOnly; // true for /ban, false for /tempban

    public BanCommand(ProfileStore profiles, RankManager ranks, DevModeManager devMode, boolean permanentOnly) {
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
        this.permanentOnly = permanentOnly;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("§cIn-game only."); return true; }
        PlayerProfile actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
        boolean bypass = devMode != null && devMode.isActive(p.getUniqueId());
        boolean allowed = bypass || (permanentOnly ? ranks.isAtLeast(actor.getPrimaryRank(), "moderator") : ranks.isAtLeast(actor.getPrimaryRank(), "helper"));
        if (!allowed) { sender.sendMessage("§cInsufficient rank."); return true; }

        if (permanentOnly) {
            if (args.length < 1) { sender.sendMessage("§eUsage: §7/ban <player> [reason]"); return true; }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (target.getUniqueId() == null) { sender.sendMessage("§cPlayer not found: §e"+args[0]); return true; }
            PlayerProfile tp = profiles.getOrCreate(target, ranks.getDefaultGroup());
            String reason = args.length >= 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "Banned";
            tp.setBanUntil(Long.MAX_VALUE);
            tp.setBanReason(reason);
            tp.setBanActor(p.getName());
            profiles.save(target.getUniqueId());
            var entry = ModerationLogger.entry("ban", p.getName(), p.getUniqueId(), reason, null, Long.MAX_VALUE);
            ModerationLogger.log(profiles, target.getUniqueId(), entry);
            if (target.isOnline()) target.getPlayer().kick(Component.text("§cYou are banned.§7 Reason: §e"+reason));
            sender.sendMessage("§aBanned §e" + (target.getName()!=null?target.getName():target.getUniqueId()) + " §7(permanent)");
        } else {
            if (args.length < 2) { sender.sendMessage("§eUsage: §7/tempban <player> <duration> [reason]"); return true; }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (target.getUniqueId() == null) { sender.sendMessage("§cPlayer not found: §e"+args[0]); return true; }
            long dur = TimeUtil.parseDurationMillis(args[1]);
            if (dur <= 0) { sender.sendMessage("§cInvalid duration: §e"+args[1]); return true; }
            String reason = args.length >= 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "Temporarily banned";
            PlayerProfile tp = profiles.getOrCreate(target, ranks.getDefaultGroup());
            long until = System.currentTimeMillis() + dur;
            tp.setBanUntil(until);
            tp.setBanReason(reason);
            tp.setBanActor(p.getName());
            profiles.save(target.getUniqueId());
            var entry = ModerationLogger.entry("tempban", p.getName(), p.getUniqueId(), reason, dur, until);
            ModerationLogger.log(profiles, target.getUniqueId(), entry);
            if (target.isOnline()) target.getPlayer().kick(Component.text("§cYou are temporarily banned for §e"+ TimeUtil.formatDurationShort(dur) + "§7. Reason: §f"+reason));
            sender.sendMessage("§aTemporarily banned §e" + (target.getName()!=null?target.getName():target.getUniqueId()) + " §7for §e"+ TimeUtil.formatDurationShort(dur));
        }
        return true;
    }
}
