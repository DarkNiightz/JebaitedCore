package com.darkniightz.core.commands.mod;

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

public class UnmuteCommand implements CommandExecutor {
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;

    public UnmuteCommand(ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("§cIn-game only."); return true; }
        PlayerProfile actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
        boolean bypass = devMode != null && devMode.isActive(p.getUniqueId());
        if (!bypass && !ranks.isAtLeast(actor.getPrimaryRank(), "moderator")) { sender.sendMessage(com.darkniightz.core.Messages.noPerm()); return true; }
        if (args.length < 1) { sender.sendMessage("§eUsage: §7/"+label+" <player>"); return true; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target.getUniqueId() == null) { sender.sendMessage("§cPlayer not found: §e"+args[0]); return true; }
        PlayerProfile tp = profiles.getOrCreate(target, ranks.getDefaultGroup());
        tp.setMuteUntil(null); tp.setMuteReason(null); tp.setMuteActor(null);
        profiles.save(target.getUniqueId());
        var entry = com.darkniightz.core.moderation.ModerationLogger.entry("unmute", p.getName(), p.getUniqueId(), null, null, null);
        ModerationLogger.log(profiles, target.getUniqueId(), entry);
        if (target.isOnline()) target.getPlayer().sendMessage("§aYou have been unmuted.");
        sender.sendMessage("§aUnmuted §e" + (target.getName()!=null?target.getName():target.getUniqueId()));
        return true;
    }
}
