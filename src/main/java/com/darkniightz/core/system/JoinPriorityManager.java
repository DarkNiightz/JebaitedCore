package com.darkniightz.core.system;

import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

/**
 * Manages priority-based slot reservation on a full server.
 *
 * <p>Priority order (highest → lowest):
 * <ol>
 *   <li>Staff-immune ranks (owner, developer, admin, srmod, moderator, helper) — never kicked, don't count toward the max</li>
 *   <li>grandmaster</li>
 *   <li>legend</li>
 *   <li>diamond</li>
 *   <li>gold</li>
 *   <li>vip, builder, pleb, and any unknown rank (kicked first)</li>
 * </ol>
 *
 * <p>When a player connects to a full server:<ul>
 *   <li>Staff → admitted, exceeds limit (Paper allows +5 for ops by default; we never block them)</li>
 *   <li>Donor / vip / etc → if any online non-immune player has LOWER priority, that player is
 *       kicked and the newcomer is permitted.</li>
 *   <li>No kick target found → newcomer is denied with a "server full" message.</li>
 * </ul>
 *
 * <p>Staff are excluded from both the player count and the kick-pool. A full server of
 * 100 staff means a GM still cannot get in because there is nobody to kick.
 */
public class JoinPriorityManager {

    private static final int PRIORITY_IMMUNE      = Integer.MAX_VALUE; // staff — unkickable
    private static final int PRIORITY_GRANDMASTER = 5;
    private static final int PRIORITY_LEGEND      = 4;
    private static final int PRIORITY_DIAMOND     = 3;
    private static final int PRIORITY_GOLD        = 2;
    private static final int PRIORITY_DEFAULT     = 1; // pleb, vip, builder, unknown

    private final Plugin plugin;
    private final ProfileStore profiles;
    private final RankManager ranks;

    public JoinPriorityManager(Plugin plugin, ProfileStore profiles, RankManager ranks) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.ranks = ranks;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("priority_queue.enabled", true);
    }

    /**
     * Returns true if the rank is staff-immune — will never be kicked and does not
     * count toward the player-slot limit for priority purposes.
     */
    public boolean isStaffImmune(String rank) {
        return switch (normalise(rank)) {
            case "owner", "developer", "admin", "srmod", "moderator", "helper" -> true;
            default -> false;
        };
    }

    /**
     * Returns the numeric priority level for a resolved rank string.
     * Higher value = harder to kick.
     */
    public int getPriority(String rank) {
        return switch (normalise(rank)) {
            case "owner", "developer", "admin", "srmod", "moderator", "helper" -> PRIORITY_IMMUNE;
            case "grandmaster" -> PRIORITY_GRANDMASTER;
            case "legend"      -> PRIORITY_LEGEND;
            case "diamond"     -> PRIORITY_DIAMOND;
            case "gold"        -> PRIORITY_GOLD;
            default            -> PRIORITY_DEFAULT;
        };
    }

    /**
     * Returns the effective rank for priority checks. Donor rank is used when it
     * exists because it outranks the primary display rank for slot-access purposes.
     */
    public String effectiveRank(Player player) {
        if (player == null) return ranks.getDefaultGroup();
        PlayerProfile p = profiles.get(player.getUniqueId());
        if (p == null) return ranks.getDefaultGroup();
        String donor = p.getDonorRank();
        if (donor != null && !donor.isBlank()) return donor.toLowerCase(Locale.ROOT);
        String primary = p.getPrimaryRank();
        return primary == null || primary.isBlank() ? ranks.getDefaultGroup() : primary.toLowerCase(Locale.ROOT);
    }

    /**
     * Attempts to make room for the joining player by kicking the lowest-priority
     * non-immune online player whose priority is strictly less than the joiner's.
     *
     * @return true if a player was kicked (room was made), false if nobody could be bumped
     */
    public boolean tryMakeRoom(String joiningRank) {
        int joiningPriority = getPriority(joiningRank);
        if (joiningPriority == PRIORITY_IMMUNE) return true; // staff always get in

        // Find the kickable player with the lowest priority
        Optional<Player> kickTarget = Bukkit.getOnlinePlayers().stream()
                .map(p -> (Player) p)
                .filter(p -> !isStaffImmune(effectiveRank(p)))
                .filter(p -> getPriority(effectiveRank(p)) < joiningPriority)
                .min(Comparator.comparingInt(p -> getPriority(effectiveRank(p))));

        if (kickTarget.isEmpty()) return false;

        Player target = kickTarget.get();
        String kickMsg = plugin.getConfig().getString(
                "priority_queue.kicked_message",
                "§cYou were removed to make room for a higher-priority player.");
        target.kickPlayer(kickMsg);
        return true;
    }

    /**
     * Returns the number of online players that DO count against the server cap
     * (i.e. non-staff online players).
     */
    public long countNonStaff() {
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> !isStaffImmune(effectiveRank(p)))
                .count();
    }

    private String normalise(String rank) {
        return rank == null ? "" : rank.trim().toLowerCase(Locale.ROOT);
    }
}
