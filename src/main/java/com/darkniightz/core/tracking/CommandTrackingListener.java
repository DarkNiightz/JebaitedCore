package com.darkniightz.core.tracking;

import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.dev.DebugFeedManager;
import com.darkniightz.core.system.OverallStatsManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Tracks commands executed by players as a separate stat (commandsSent).
 */
public class CommandTrackingListener implements Listener {

    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DebugFeedManager feed;
    private final OverallStatsManager overallStats;

    public CommandTrackingListener(ProfileStore profiles, RankManager ranks, DebugFeedManager feed, OverallStatsManager overallStats) {
        this.profiles = profiles;
        this.ranks = ranks;
        this.feed = feed;
        this.overallStats = overallStats;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        PlayerProfile prof = profiles.getOrCreate(p, ranks.getDefaultGroup());
        if (prof == null) return; // Should not happen for an online player
        prof.incCommands(); // This updates the cached object. It will be saved later.
        profiles.saveDeferred(p.getUniqueId());
        if (overallStats != null) {
            overallStats.increment(OverallStatsManager.TOTAL_COMMANDS, 1);
        }
        if (feed != null) {
            String message = event.getMessage() == null ? "" : event.getMessage().trim();
            feed.recordCommand(p, message.startsWith("/") ? message.substring(1) : message);
        }
    }
}
