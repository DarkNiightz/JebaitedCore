package com.darkniightz.core.moderation;

import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ModerationLogger {
    private ModerationLogger() {}

    public static void log(ProfileStore store, UUID target, Map<String, Object> entry) {
        PlayerProfile p = store.get(target);
        if (p == null) return; // should be present during online moderation
        p.addModerationEntry(entry);
        store.save(target);
    }

    public static Map<String, Object> entry(String type, String actorName, UUID actorId, String reason, Long durationMs, Long expiresAt) {
        Map<String, Object> m = new HashMap<>();
        m.put("ts", System.currentTimeMillis());
        m.put("type", type);
        if (actorName != null) m.put("actor", actorName);
        if (actorId != null) m.put("actorUuid", actorId.toString());
        if (reason != null && !reason.isEmpty()) m.put("reason", reason);
        if (durationMs != null) m.put("durationMs", durationMs);
        if (expiresAt != null) m.put("expiresAt", expiresAt);
        return m;
    }
}
