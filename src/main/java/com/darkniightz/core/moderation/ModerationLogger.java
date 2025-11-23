package com.darkniightz.core.moderation;

import com.darkniightz.main.JebaitedCore;
import com.darkniightz.main.database.dao.PlayerProfileDAO;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ModerationLogger {
    public static void log(UUID target, Map<String, Object> entry) {
        PlayerProfileDAO dao = JebaitedCore.getInstance().getPlayerProfileDAO();
        if (dao == null) return; // Database might be disabled or not initialized
        dao.logModerationAction(target, entry);
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
