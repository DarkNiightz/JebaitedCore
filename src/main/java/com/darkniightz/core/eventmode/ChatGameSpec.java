package com.darkniightz.core.eventmode;

/**
 * Immutable chat game definition (from {@code chat_games.games} or legacy {@code event_mode.events}).
 */
public final class ChatGameSpec {
    public final String configKey;
    public final String displayName;
    public final boolean enabled;
    public final int coinReward;
    public final ChatGameKind kind;
    /** Optional; reserved for hub / cross-server announcements. */
    public final String targetServer;

    public ChatGameSpec(String configKey, String displayName, boolean enabled, int coinReward,
                        ChatGameKind kind, String targetServer) {
        this.configKey = configKey;
        this.displayName = displayName;
        this.enabled = enabled;
        this.coinReward = coinReward;
        this.kind = kind;
        this.targetServer = targetServer == null ? "" : targetServer;
    }
}
