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
        String typeLabel = currentlyDonor ? "Regular Rank" : "Donator Rank";
        sender.sendMessage(Messages.prefixed("§aChat prefix now showing: " + formatRankDisplay(shownRank)
                + " §7(" + typeLabel + ")"));

        if (JebaitedCore.getInstance() != null) {
            JebaitedCore.getInstance().refreshPlayerPresentation(p);
        }
        return true;
    }

    /** Returns a coloured, properly capitalised rank label for the feedback message. */
    private static String formatRankDisplay(String rank) {
        if (rank == null) return "§7None";
        return switch (rank.toLowerCase()) {
            case "owner"      -> "§4Owner";
            case "developer"  -> "§5Developer";
            case "admin"      -> "§cAdmin";
            case "srmod"      -> "§6Sr. Mod";
            case "moderator"  -> "§6Moderator";
            case "helper"     -> "§eHelper";
            case "vip"        -> "§bVIP";
            case "builder"    -> "§3Builder";
            case "grandmaster"-> "§5Grand Master";
            case "legend"     -> "§6Legend";
            case "diamond"    -> "§bDiamond";
            case "gold"       -> "§6Gold";
            case "pleb"       -> "§7Pleb";
            default           -> "§7" + rank;
        };
    }
}
