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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class FreezeCommand implements CommandExecutor {
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;
    private final ModerationManager moderation;

    public FreezeCommand(ProfileStore profiles, RankManager ranks, DevModeManager devMode, ModerationManager moderation) {
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
        this.moderation = moderation;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("§cIn-game only."); return true; }
        PlayerProfile actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
        boolean bypass = devMode != null && devMode.isActive(p.getUniqueId());
        if (!bypass && !ranks.isAtLeast(actor.getPrimaryRank(), "moderator")) { sender.sendMessage(Messages.noPerm()); return true; }
        if (args.length < 1) { sender.sendMessage("§eUsage: §7/"+label+" <player>"); return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { sender.sendMessage("§cPlayer not found: §e"+args[0]); return true; }
        boolean now = !moderation.isFrozen(target.getUniqueId());
        moderation.setFrozen(target.getUniqueId(), now);
        String state = now ? "frozen" : "unfrozen";
        var entry = ModerationLogger.entry(now?"freeze":"unfreeze", p.getName(), p.getUniqueId(), null, null, null);
        profiles.getOrCreate(target, ranks.getDefaultGroup());
        ModerationLogger.log(target.getUniqueId(), entry);
        sender.sendMessage("§a"+state.substring(0,1).toUpperCase()+state.substring(1)+" §e"+target.getName());
        if (now) target.sendMessage("§cYou have been frozen by staff. Do not move."); else target.sendMessage("§aYou are no longer frozen.");
        return true;
    }
}
