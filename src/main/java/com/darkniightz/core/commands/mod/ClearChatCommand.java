package com.darkniightz.core.commands.mod;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ClearChatCommand implements CommandExecutor {
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;
    private long lastClearAt = 0L;

    public ClearChatCommand(ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("§cIn-game only."); return true; }
        PlayerProfile actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
        boolean bypass = devMode != null && devMode.isActive(p.getUniqueId());

        // Configurable minimum rank for using /clearchat
        String minStaffRank = p.getServer().getPluginManager().getPlugin("JebaitedCore")
                .getConfig().getString("moderation.clearchat.min_staff_rank", "helper");

        if (!bypass && !ranks.isAtLeast(actor.getPrimaryRank(), minStaffRank)) { sender.sendMessage(Messages.noPerm()); return true; }

        // Cooldown
        int cooldownSec = p.getServer().getPluginManager().getPlugin("JebaitedCore")
                .getConfig().getInt("moderation.clearchat.cooldown_seconds", 5);
        long now = System.currentTimeMillis();
        long remaining = (lastClearAt + cooldownSec * 1000L) - now;
        if (!bypass && remaining > 0) {
            long secs = (remaining + 999) / 1000;
            p.sendMessage("§eClearChat cooldown: §7wait §e" + secs + "§7s");
            return true;
        }

        int blankLines = p.getServer().getPluginManager().getPlugin("JebaitedCore")
                .getConfig().getInt("moderation.clearchat.blank_lines", 150);
        String notice = "§7Chat was cleared by §e" + p.getName();
        String filler = "§r"; // visible reset so clients actually push lines
        for (Player online : Bukkit.getOnlinePlayers()) {
            PlayerProfile prof = profiles.getOrCreate(online, ranks.getDefaultGroup());
            boolean staff = ranks.isAtLeast(prof.getPrimaryRank(), minStaffRank) || (devMode != null && devMode.isActive(online.getUniqueId()));
            if (!staff) {
                for (int i = 0; i < blankLines; i++) online.sendMessage(filler);
            }
            online.sendMessage(notice);
        }
        lastClearAt = now;
        return true;
    }
}
