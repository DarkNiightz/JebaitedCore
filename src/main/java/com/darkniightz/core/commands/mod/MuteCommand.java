package com.darkniightz.core.commands.mod;

import com.darkniightz.core.Messages;
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
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public class MuteCommand implements CommandExecutor, TabCompleter {
    private final Plugin plugin;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;
    private final boolean permanentOnly;

    public MuteCommand(Plugin plugin, ProfileStore profiles, RankManager ranks, DevModeManager devMode, boolean permanentOnly) {
        this.plugin = plugin;
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
                sender.sendMessage(Messages.noPerm());
                return true;
            }
        }

        if (permanentOnly) {
            if (args.length < 1) {
                sender.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " <player> [reason]"));
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (target.getUniqueId() == null) {
                sender.sendMessage(Messages.prefixed("§cPlayer not found: §e" + args[0]));
                return true;
            }
            String reason = args.length >= 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "Muted";
            final OfflinePlayer ftarget = target;
            final String freason = reason;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                PlayerProfile tp = profiles.getOrCreate(ftarget, ranks.getDefaultGroup());
                tp.setMuteUntil(Long.MAX_VALUE);
                tp.setMuteReason(freason);
                tp.setMuteActor(actorName);
                profiles.save(ftarget.getUniqueId());
                var entry = ModerationLogger.entry("mute", actorName, actorUuid, freason, null, Long.MAX_VALUE);
                ModerationLogger.log(ftarget.getUniqueId(), entry);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (ftarget.isOnline()) ftarget.getPlayer().sendMessage(Messages.prefixed("§cYou have been muted §7(permanent). Reason: §f" + freason));
                    sender.sendMessage(Messages.prefixed("§aMuted §e" + (ftarget.getName() != null ? ftarget.getName() : ftarget.getUniqueId()) + " §7(permanent)"));
                });
            });
        } else {
            if (args.length < 2) {
                sender.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " <player> <duration> [reason]"));
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (target.getUniqueId() == null) {
                sender.sendMessage(Messages.prefixed("§cPlayer not found: §e" + args[0]));
                return true;
            }
            long dur = TimeUtil.parseDurationMillis(args[1]);
            if (dur <= 0) {
                sender.sendMessage(Messages.prefixed("§cInvalid duration: §e" + args[1]));
                return true;
            }
            String reason = args.length >= 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "Temporarily muted";
            long until = System.currentTimeMillis() + dur;
            final OfflinePlayer ftarget2 = target;
            final long funtil = until;
            final long fdur = dur;
            final String freason2 = reason;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                PlayerProfile tp = profiles.getOrCreate(ftarget2, ranks.getDefaultGroup());
                tp.setMuteUntil(funtil);
                tp.setMuteReason(freason2);
                tp.setMuteActor(actorName);
                profiles.save(ftarget2.getUniqueId());
                var entry = ModerationLogger.entry("tempmute", actorName, actorUuid, freason2, fdur, funtil);
                ModerationLogger.log(ftarget2.getUniqueId(), entry);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (ftarget2.isOnline()) ftarget2.getPlayer().sendMessage(Messages.prefixed("§cYou have been muted for §e" + TimeUtil.formatDurationShort(fdur) + "§7. Reason: §f" + freason2));
                    sender.sendMessage(Messages.prefixed("§aTemporarily muted §e" + (ftarget2.getName() != null ? ftarget2.getName() : ftarget2.getUniqueId()) + " §7for §e" + TimeUtil.formatDurationShort(fdur)));
                });
            });
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
