package com.darkniightz.core.tracking;

import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
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

    public CommandTrackingListener(ProfileStore profiles, RankManager ranks) {
        this.profiles = profiles;
        this.ranks = ranks;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        PlayerProfile prof = profiles.getOrCreate(p, ranks.getDefaultGroup());
        prof.incCommands();
        profiles.save(p.getUniqueId());
    }
}
