package com.darkniightz.core.commands.mod;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.moderation.ModerationLogger;
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

public class WarnCommand implements CommandExecutor, TabCompleter {
    private final Plugin plugin;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;

    public WarnCommand(Plugin plugin, ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
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
            if (!bypass && !ranks.isAtLeast(actor.getPrimaryRank(), "helper")) {
                sender.sendMessage(Messages.noPerm());
                return true;
            }
        }

        if (args.length < 2) {
            sender.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " <player> <reason>"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target.getUniqueId() == null) {
            sender.sendMessage(Messages.prefixed("§cPlayer not found: §e" + args[0]));
            return true;
        }

        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        final OfflinePlayer ftarget = target;
        final String freason = reason;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            var entry = ModerationLogger.entry("warn", actorName, actorUuid, freason, null, null);
            ModerationLogger.log(ftarget.getUniqueId(), entry);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (ftarget.isOnline()) {
                    ftarget.getPlayer().sendMessage(Messages.prefixed("§eYou have been warned: §f" + freason));
                }
                sender.sendMessage(Messages.prefixed("§aWarned §e" + (ftarget.getName() != null ? ftarget.getName() : ftarget.getUniqueId()) + " §7for: §f" + freason));
            });
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) return List.of();
        PlayerProfile actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
        boolean bypass = devMode != null && devMode.isActive(p.getUniqueId());
        if (!bypass && !ranks.isAtLeast(actor.getPrimaryRank(), "helper")) return List.of();
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
