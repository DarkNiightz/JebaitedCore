package com.darkniightz.core.eventmode.handler;

import com.darkniightz.core.eventmode.EventSession;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Strategy interface — one implementation per event kind.
 * EventEngine calls these at the appropriate lifecycle points.
 */
public interface EventHandler {

    /** Called once on the main thread when the event transitions to RUNNING. */
    void onStart(EventSession session);

    /** Called when a participant dies. Run on main thread via EventModeCombatListener. */
    void onDeath(EventSession session, Player player);

    /** Called when a participant's respawn event fires (PlayerRespawnEvent / post-death tick). */
    void onRespawn(EventSession session, Player player);

    /**
     * Called once per second while the event is RUNNING.
     * For KOTH: tick hill capture time. For CTF: tick time limit. For FFA: no-op.
     */
    void onTick(EventSession session);

    /** Called when the event is finalising (ENDING state). Restore inventories etc. */
    void onEnd(EventSession session);

    /**
     * Returns 0–5 scoreboard sidebar lines for this event.
     * Lines are legacy-formatted (§ codes). Empty list = no event-specific lines.
     */
    List<String> getScoreboardLines(EventSession session);
}
