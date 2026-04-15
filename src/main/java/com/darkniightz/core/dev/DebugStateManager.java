package com.darkniightz.core.dev;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DebugStateManager {
    private final Set<UUID> previewMode = ConcurrentHashMap.newKeySet();
    private final java.util.Map<UUID, DebugFeedManager.Category> feedFilter = new ConcurrentHashMap<>();

    public boolean isPreviewMode(UUID uuid) {
        return previewMode.contains(uuid);
    }

    public boolean togglePreviewMode(UUID uuid) {
        if (previewMode.contains(uuid)) {
            previewMode.remove(uuid);
            return false;
        }
        previewMode.add(uuid);
        return true;
    }

    public void clear(UUID uuid) {
        previewMode.remove(uuid);
        feedFilter.remove(uuid);
    }

    public DebugFeedManager.Category getFeedFilter(UUID uuid) {
        return feedFilter.get(uuid);
    }

    public void setFeedFilter(UUID uuid, DebugFeedManager.Category category) {
        if (category == null) {
            feedFilter.remove(uuid);
        } else {
            feedFilter.put(uuid, category);
        }
    }
}
