package com.darkniightz.core.eventmode.handler;

import com.darkniightz.core.eventmode.EventSession;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Handles 1v1 Duels and Hardcore Duels.
 * Elimination logic is identical to FFA (last player standing), just min/max is 2.
 * Delegates entirely to FfaHandler.
 */
public final class DuelsHandler implements EventHandler {

    private final FfaHandler delegate;

    public DuelsHandler(FfaHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onStart(EventSession session) {
        delegate.onStart(session);
    }

    @Override
    public void onDeath(EventSession session, Player player) {
        delegate.onDeath(session, player);
    }

    @Override
    public void onRespawn(EventSession session, Player player) {
        delegate.onRespawn(session, player);
    }

    @Override
    public void onTick(EventSession session) {
        delegate.onTick(session);
    }

    @Override
    public void onEnd(EventSession session) {
        delegate.onEnd(session);
    }

    @Override
    public List<String> getScoreboardLines(EventSession session) {
        return delegate.getScoreboardLines(session);
    }
}
