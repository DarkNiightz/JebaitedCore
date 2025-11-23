package com.darkniightz.core.commands.mod;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.moderation.ModerationLogger;
import com.darkniightz.core.moderation.ModerationManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class VanishCommand implements CommandExecutor {
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;
    private final ModerationManager moderation;

    public VanishCommand(ProfileStore profiles, RankManager ranks, DevModeManager devMode, ModerationManager moderation) {
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
        if (!bypass && !ranks.isAtLeast(actor.getPrimaryRank(), "helper")) { sender.sendMessage(Messages.noPerm()); return true; }
        boolean now = moderation.toggleVanish(p);
        var entry = ModerationLogger.entry(now?"vanish_on":"vanish_off", p.getName(), p.getUniqueId(), null, null, null);
        ModerationLogger.log(p.getUniqueId(), entry);
        p.sendMessage(now ? "§7Vanish: §aON" : "§7Vanish: §cOFF");
        return true;
    }
}
