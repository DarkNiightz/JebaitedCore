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

import java.util.UUID;

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
        String actorName = sender instanceof Player p ? p.getName() : "_console_";
        boolean isConsole = !(sender instanceof Player);

        if (!isConsole) {
            Player p = (Player) sender;
            PlayerProfile actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
            boolean bypass = devMode != null && devMode.isActive(p.getUniqueId());
            String minRank = p.getServer().getPluginManager().getPlugin("JebaitedCore")
                    .getConfig().getString("moderation.clearchat.min_staff_rank", "helper");
            if (!bypass && !ranks.isAtLeast(actor.getPrimaryRank(), minRank)) {
                sender.sendMessage(Messages.noPerm());
                return true;
            }
        }

        int cooldownSec = Bukkit.getPluginManager().getPlugin("JebaitedCore")
                .getConfig().getInt("moderation.clearchat.cooldown_seconds", 5);
        long now = System.currentTimeMillis();
        long remaining = (lastClearAt + cooldownSec * 1000L) - now;
        if (!isConsole && remaining > 0) {
            long secs = (remaining + 999) / 1000;
            sender.sendMessage("§eClearChat cooldown: §7wait §e" + secs + "§7s");
            return true;
        }

        int blankLines = Bukkit.getPluginManager().getPlugin("JebaitedCore")
                .getConfig().getInt("moderation.clearchat.blank_lines", 150);
        String notice = "§7Chat was cleared by §e" + actorName;
        String filler = "§r";

        for (Player online : Bukkit.getOnlinePlayers()) {
            PlayerProfile prof = profiles.getOrCreate(online, ranks.getDefaultGroup());
            boolean staff = ranks.isAtLeast(prof.getPrimaryRank(), "helper") || (devMode != null && devMode.isActive(online.getUniqueId()));
            if (!staff) {
                for (int i = 0; i < blankLines; i++) online.sendMessage(filler);
            }
            online.sendMessage(notice);
        }
        lastClearAt = now;
        return true;
    }
}