package com.darkniightz.core.commands.mod;

import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.moderation.ModerationManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SlowmodeCommand implements CommandExecutor {
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;
    private final ModerationManager moderation;

    public SlowmodeCommand(ProfileStore profiles, RankManager ranks, DevModeManager devMode, ModerationManager moderation) {
        this.profiles = profiles; this.ranks = ranks; this.devMode = devMode; this.moderation = moderation;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("§cIn-game only."); return true; }
        PlayerProfile actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
        boolean bypass = devMode != null && devMode.isActive(p.getUniqueId());
        if (!bypass && !ranks.isAtLeast(actor.getPrimaryRank(), "moderator")) { sender.sendMessage("§cRequires Moderator+."); return true; }
        if (args.length < 1) { sender.sendMessage("§eUsage: §7/"+label+" <seconds|off>"); return true; }
        if (args[0].equalsIgnoreCase("off") || args[0].equals("0")) {
            moderation.setSlowmodeSeconds(0);
            sender.sendMessage("§eSlowmode §7is now §cOFF");
            return true;
        }
        try {
            int seconds = Integer.parseInt(args[0]);
            if (seconds < 0) { sender.sendMessage("§cSeconds must be >= 0"); return true; }
            moderation.setSlowmodeSeconds(seconds);
            sender.sendMessage("§eSlowmode §7set to §e"+seconds+"s");
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid number: §e"+args[0]);
        }
        return true;
    }
}
