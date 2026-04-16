package com.darkniightz.core.eventmode;

public enum EventState {
    /** No event running or queued. */
    IDLE,
    /** Queue is open — waiting for min players to join. */
    OPEN,
    /** Min players met — counting down to start. Players may still join up to max. */
    LOBBY_COUNTDOWN,
    /** Event is live. */
    RUNNING,
    /** Finalising rewards / restoring snapshots before returning to IDLE. */
    ENDING
}
