package com.darkniightz.core.playerjoin;

import com.darkniightz.core.system.JoinPriorityManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.Plugin;

/**
 * Handles priority-based slot management on a full server.
 *
 * <p>On {@link PlayerLoginEvent}:
 * <ol>
 *   <li>If the server is not full (non-staff player count) → pass through.</li>
 *   <li>If the joining player is staff-immune → pass through (staff always join).</li>
 *   <li>Otherwise → attempt to kick the lowest-priority non-immune player whose
 *       priority is strictly below the joining player's. If no such player exists,
 *       deny entry with a "server full" message.</li>
 * </ol>
 *
 * <p>Priority (highest → lowest):
 * <ul>
 *   <li>Staff (owner/developer/admin/srmod/moderator/helper) — immune</li>
 *   <li>Grand Master</li>
 *   <li>Legend</li>
 *   <li>Diamond</li>
 *   <li>Gold</li>
 *   <li>vip / builder / pleb / unknown</li>
 * </ul>
 */
public class PriorityJoinListener implements Listener {

    private final Plugin plugin;
    private final JoinPriorityManager priorityManager;

    public PriorityJoinListener(Plugin plugin, JoinPriorityManager priorityManager) {
        this.plugin = plugin;
        this.priorityManager = priorityManager;
    }

    // LOWEST priority so we run before other join-blocking listeners
    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(PlayerLoginEvent event) {
        if (!priorityManager.isEnabled()) return;

        int maxPlayers = Bukkit.getMaxPlayers();
        long nonStaffOnline = priorityManager.countNonStaff();

        // Server has room? Nothing to do.
        if (nonStaffOnline < maxPlayers) return;

        String joiningRank = getJoiningRank(event);

        // Staff are always admitted regardless of player count
        if (priorityManager.isStaffImmune(joiningRank)) {
            event.allow();
            return;
        }

        // Not full when measuring total (rare edge — someone was already allowed by vanilla)
        if (event.getResult() == PlayerLoginEvent.Result.ALLOWED) {
            // Vanilla already allowed them before our check — could be op-slot, just leave it
            return;
        }

        // Server is full for non-staff. Try to bump someone out.
        boolean madeRoom = priorityManager.tryMakeRoom(joiningRank);
        if (madeRoom) {
            event.allow();
            // Notify the kicked slot was freed — the actual kick message is sent by tryMakeRoom
        } else {
            // Nobody can be bumped; deny entry
            String fullMsg = plugin.getConfig().getString(
                    "priority_queue.full_message",
                    "§cThe server is full. Purchase a donor rank at §ejebaited.net §cfor priority access.");
            event.disallow(PlayerLoginEvent.Result.KICK_FULL, fullMsg);
        }
    }

    /**
     * Tries to resolve the joining player's rank from ProfileStore before their
     * profile fully loads. Falls back to pleb if not available (new player).
     * The check uses the persisted donor rank where present.
     */
    private String getJoiningRank(PlayerLoginEvent event) {
        if (plugin instanceof com.darkniightz.main.JebaitedCore core) {
            var profiles = core.getProfileStore();
            var ranks = core.getRankManager();
            if (profiles != null && ranks != null) {
                var profile = profiles.get(event.getPlayer().getUniqueId());
                if (profile != null) {
                    String donor = profile.getDonorRank();
                    if (donor != null && !donor.isBlank()) return donor;
                    String primary = profile.getPrimaryRank();
                    if (primary != null && !primary.isBlank()) return primary;
                }
            }
        }
        return "pleb";
    }
}
