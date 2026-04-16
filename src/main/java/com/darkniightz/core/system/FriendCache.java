package com.darkniightz.core.system;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache for the friends system.
 *
 * <p>Stores friend sets and pending requests per player.
 * Populated on player join, cleared on player quit.
 */
public class FriendCache {

    // uuid → set of friend UUIDs
    private final Map<UUID, Set<UUID>> friends = new ConcurrentHashMap<>();
    // uuid → set of UUIDs who have sent this player a pending request
    private final Map<UUID, Set<UUID>> inbound = new ConcurrentHashMap<>();

    // ── Friends ─────────────────────────────────────────────────────────────

    public void setFriends(UUID player, Set<UUID> friendSet) {
        friends.put(player, ConcurrentHashMap.newKeySet());
        friends.get(player).addAll(friendSet);
    }

    public Set<UUID> getFriends(UUID player) {
        return Collections.unmodifiableSet(friends.getOrDefault(player, Set.of()));
    }

    public boolean isFriend(UUID a, UUID b) {
        Set<UUID> set = friends.get(a);
        return set != null && set.contains(b);
    }

    public void addFriend(UUID a, UUID b) {
        friends.computeIfAbsent(a, k -> ConcurrentHashMap.newKeySet()).add(b);
        friends.computeIfAbsent(b, k -> ConcurrentHashMap.newKeySet()).add(a);
    }

    public void removeFriend(UUID a, UUID b) {
        Set<UUID> sa = friends.get(a);
        if (sa != null) sa.remove(b);
        Set<UUID> sb = friends.get(b);
        if (sb != null) sb.remove(a);
    }

    public void unload(UUID player) {
        friends.remove(player);
        inbound.remove(player);
    }

    // ── Inbound requests ─────────────────────────────────────────────────────

    public void setInbound(UUID receiver, Set<UUID> senders) {
        inbound.put(receiver, ConcurrentHashMap.newKeySet());
        inbound.get(receiver).addAll(senders);
    }

    public Set<UUID> getInbound(UUID receiver) {
        return Collections.unmodifiableSet(inbound.getOrDefault(receiver, Set.of()));
    }

    public boolean hasInbound(UUID receiver, UUID sender) {
        Set<UUID> set = inbound.get(receiver);
        return set != null && set.contains(sender);
    }

    public void addInbound(UUID receiver, UUID sender) {
        inbound.computeIfAbsent(receiver, k -> ConcurrentHashMap.newKeySet()).add(sender);
    }

    public void removeInbound(UUID receiver, UUID sender) {
        Set<UUID> set = inbound.get(receiver);
        if (set != null) set.remove(sender);
    }
}
