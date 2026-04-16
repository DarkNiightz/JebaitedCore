package com.darkniightz.core.eventmode;

/**
 * Immutable description of an event loaded from config.yml.
 * Passed into EventSession on open and remains read-only throughout.
 */
public final class EventSpec {
    public final String key;
    public final String displayName;
    public final boolean enabled;
    public final int coinReward;
    public final int minPlayers;
    public final int maxPlayers;
    public final EventKind kind;

    public EventSpec(String key, String displayName, boolean enabled,
                     int coinReward, int minPlayers, int maxPlayers, EventKind kind) {
        this.key = key;
        this.displayName = displayName;
        this.enabled = enabled;
        this.coinReward = coinReward;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.kind = kind;
    }

    @Override
    public String toString() {
        return "EventSpec{key=" + key + ", kind=" + kind + ", players=" + minPlayers + "-" + maxPlayers + "}";
    }
}
