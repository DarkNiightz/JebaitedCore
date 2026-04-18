package com.darkniightz.bot.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NonceStore {
    private final Map<String, Long> seen = new ConcurrentHashMap<>();

    public boolean markIfNew(String nonce, long ttlSeconds) {
        long now = Instant.now().getEpochSecond();
        long expiresAt = now + ttlSeconds;
        Long existing = seen.putIfAbsent(nonce, expiresAt);
        prune(now);
        return existing == null;
    }

    private void prune(long now) {
        seen.entrySet().removeIf(entry -> entry.getValue() < now);
    }
}
