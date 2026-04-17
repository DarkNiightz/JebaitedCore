package com.darkniightz.core.eventmode;

/**
 * Mutable state for one live chat game round.
 */
public final class ChatGameSession {
    public final ChatGameSpec spec;
    public volatile String chatAnswer;
    public volatile int persistenceSessionId;
    public final long startedAtMs;

    public ChatGameSession(ChatGameSpec spec, long startedAtMs) {
        this.spec = spec;
        this.startedAtMs = startedAtMs;
    }
}
