package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /togglerank — lets a player switch their chat prefix between their primary rank
 * and their donor rank (if one is assigned). No-op if no donor rank is set.
 */
public class ToggleRankCommand implements CommandExecutor {

    private final ProfileStore profiles;
    private final RankManager ranks;

    public ToggleRankCommand(ProfileStore profiles, RankManager ranks) {
        this.profiles = profiles;
        this.ranks = ranks;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can use this command."));
            return true;
        }

        PlayerProfile profile = profiles.getOrCreate(p, ranks.getDefaultGroup());

        if (profile.getDonorRank() == null) {
            sender.sendMessage(Messages.prefixed("§cYou don't have a donor rank assigned. Ask an admin."));
            return true;
        }

        boolean currentlyDonor = "donor".equals(profile.getRankDisplayMode());
        String newMode = currentlyDonor ? "primary" : "donor";
        profile.setRankDisplayMode(newMode);
        profiles.save(p.getUniqueId());

        String shownRank = currentlyDonor ? profile.getPrimaryRank() : profile.getDonorRank();
        sender.sendMessage(Messages.prefixed("§aChat prefix now showing: §b" + shownRank
                + " §7(" + (currentlyDonor ? "staff" : "donor") + " rank)"));

        if (JebaitedCore.getInstance() != null) {
            JebaitedCore.getInstance().refreshPlayerPresentation(p);
        }
        return true;
    }
}
