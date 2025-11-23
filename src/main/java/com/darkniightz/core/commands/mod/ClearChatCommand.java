package com.darkniightz.core.commands.mod;

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
        if (!bypass && !ranks.isAtLeast(actor.getPrimaryRank(), "helper")) { sender.sendMessage("§cRequires Helper+."); return true; }

        String notice = "§7Chat was cleared by §e" + p.getName();
        String filler = " ".repeat(200);
        for (Player online : Bukkit.getOnlinePlayers()) {
            PlayerProfile prof = profiles.getOrCreate(online, ranks.getDefaultGroup());
            boolean staff = ranks.isAtLeast(prof.getPrimaryRank(), "helper") || (devMode != null && devMode.isActive(online.getUniqueId()));
            if (!staff) {
                for (int i = 0; i < 100; i++) online.sendMessage(filler);
            }
            online.sendMessage(notice);
        }
        return true;
    }
}
