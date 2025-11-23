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
import org.bukkit.command.CommandSender;
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
        if (!(sender instanceof Player p)) { sender.sendMessage("§cIn-game only."); return true; }
        PlayerProfile actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
        boolean bypass = devMode != null && devMode.isActive(p.getUniqueId());
        if (!bypass && !ranks.isAtLeast(actor.getPrimaryRank(), "helper")) { sender.sendMessage(Messages.noPerm()); return true; }
        if (args.length < 1) { sender.sendMessage("§eUsage: §7/"+label+" <player> [reason]"); return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { sender.sendMessage("§cPlayer not found: §e"+args[0]); return true; }
        PlayerProfile tp = profiles.getOrCreate(target, ranks.getDefaultGroup());
        // Guardrail: must outrank target unless DevMode bypass
        if (!bypass && !ranks.outranksStrict(actor.getPrimaryRank(), tp.getPrimaryRank())) {
            sender.sendMessage(Messages.noPerm());
            return true;
        }
        String reason = args.length >= 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "Kicked";
        // log
        var entry = ModerationLogger.entry("kick", p.getName(), p.getUniqueId(), reason, null, null);
        ModerationLogger.log(profiles, target.getUniqueId(), entry);
        // kick
        target.kick(net.kyori.adventure.text.Component.text("§cYou were kicked. §7Reason: §e" + reason));
        sender.sendMessage("§aKicked §e"+target.getName()+" §7for: §f"+reason);
        return true;
    }
}
