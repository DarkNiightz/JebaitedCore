package com.darkniightz.core.commands.mod;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.moderation.ModerationLogger;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class KickCommand implements CommandExecutor {
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;

    public KickCommand(ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Determine actor name and UUID (console = "_console_")
        String actorName = sender instanceof Player p ? p.getName() : "_console_";
        UUID actorUuid = sender instanceof Player p ? p.getUniqueId() : UUID.fromString("00000000-0000-0000-0000-000000000000");
        boolean isConsole = sender instanceof ConsoleCommandSender;

        // Permission check
        if (!isConsole) {
            Player p = (Player) sender;
            PlayerProfile actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
            boolean bypass = devMode != null && devMode.isActive(p.getUniqueId());
            if (!bypass && !ranks.isAtLeast(actor.getPrimaryRank(), "helper")) {
                sender.sendMessage(Messages.noPerm());
                return true;
            }
        }

        if (args.length < 1) {
            sender.sendMessage("§eUsage: §7/" + label + " <player> [reason]");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found: §e" + args[0]);
            return true;
        }

        // Outrank check (skip for console or DevMode)
        if (!isConsole) {
            Player p = (Player) sender;
            PlayerProfile actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
            PlayerProfile tp = profiles.getOrCreate(target, ranks.getDefaultGroup());
            boolean bypass = devMode != null && devMode.isActive(p.getUniqueId());
            if (!bypass && !ranks.outranksStrict(actor.getPrimaryRank(), tp.getPrimaryRank())) {
                sender.sendMessage(Messages.noPerm());
                return true;
            }
        }

        String reason = args.length >= 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "Kicked by staff";

        // Log to DB as _console_ if from console
        var entry = ModerationLogger.entry("kick", actorName, actorUuid, reason, null, null);
        ModerationLogger.log(target.getUniqueId(), entry);

        // Kick player
        target.kick(Component.text("§cYou were kicked.\n§7Reason: §f" + reason));

        sender.sendMessage("§aKicked §e" + target.getName() + " §7for: §f" + reason);
        return true;
    }
}