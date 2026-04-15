package com.darkniightz.core.system;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MessageManager {

    private final Map<UUID, UUID> lastMessaged = new ConcurrentHashMap<>();

    public void rememberConversation(Player a, Player b) {
        if (a == null || b == null) return;
        lastMessaged.put(a.getUniqueId(), b.getUniqueId());
        lastMessaged.put(b.getUniqueId(), a.getUniqueId());
    }

    public UUID getLastMessaged(UUID playerId) {
        if (playerId == null) return null;
        return lastMessaged.get(playerId);
    }
}
