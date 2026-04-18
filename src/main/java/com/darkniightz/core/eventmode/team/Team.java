package com.darkniightz.core.eventmode.team;

import java.util.Set;
import java.util.UUID;

/**
 * A colour side for team events (CTF). Members are a subset of {@code EventSession.active}.
 */
public enum Team {
    RED,
    BLUE;

    public Team opposite() {
        return this == RED ? BLUE : RED;
    }

    public static boolean sameTeam(Set<UUID> teamMembers, UUID id) {
        return id != null && teamMembers != null && teamMembers.contains(id);
    }
}
