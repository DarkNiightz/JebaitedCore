package com.darkniightz.core.eventmode.handler;

import com.darkniightz.core.eventmode.EventSession;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Capture the Flag handler — STUB for §21 CTF implementation.
 * Wire is live so the EventEngine can instantiate it, but onStart/onDeath
 * currently fall back to FFA-style elimination until CTF is fully built.
 *
 * Full implementation plan is in ROADMAP §21 (CtfHandler section).
 */
public final class CtfHandler implements EventHandler {

    private final FfaHandler fallback;

    public CtfHandler(FfaHandler fallback) {
        this.fallback = fallback;
    }

    @Override
    public void onStart(EventSession session) {
        // TODO: place Red/Blue flag wool blocks at configured positions, assign teams
        fallback.onStart(session);
    }

    @Override
    public void onDeath(EventSession session, Player player) {
        // TODO: drop carried flag at death location, schedule auto-return
        fallback.onDeath(session, player);
    }

    @Override
    public void onRespawn(EventSession session, Player player) {
        fallback.onRespawn(session, player);
    }

    @Override
    public void onTick(EventSession session) {
        // TODO: check time limit, update flag auto-return timers
    }

    @Override
    public void onEnd(EventSession session) {
        fallback.onEnd(session);
    }

    @Override
    public List<String> getScoreboardLines(EventSession session) {
        // TODO: Red X — Y Blue | time remaining
        return List.of("§c[CTF - coming soon]");
    }
}
