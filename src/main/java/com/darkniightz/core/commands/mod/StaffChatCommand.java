package com.darkniightz.core.commands.mod;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.moderation.ModerationManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class StaffChatCommand implements CommandExecutor {
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;
    private final ModerationManager moderation;

    public StaffChatCommand(ProfileStore profiles, RankManager ranks, DevModeManager devMode, ModerationManager moderation) {
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
        this.moderation = moderation;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String actorName = sender instanceof Player p ? p.getName() : "_console_";
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

        if (args.length == 0) {
            if (isConsole) {
                sender.sendMessage("§cCannot toggle staff chat from console.");
                return true;
            }
            Player p = (Player) sender;
            boolean on = moderation.toggleStaffChat(p.getUniqueId());
            p.sendMessage(on ? "§dStaffChat: §aON" : "§dStaffChat: §cOFF");
            return true;
        }

        String msg = String.join(" ", args);
        String out = "§d[Staff] §7" + actorName + ": §f" + msg;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            PlayerProfile vp = profiles.getOrCreate(viewer, ranks.getDefaultGroup());
            boolean staff = ranks.isAtLeast(vp.getPrimaryRank(), "helper") || (devMode != null && devMode.isActive(viewer.getUniqueId()));
            if (staff) viewer.sendMessage(out);
        }
        return true;
    }
}