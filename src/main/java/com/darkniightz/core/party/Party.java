package com.darkniightz.core.party;

import org.bukkit.Location;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory data container for a single party.
 * Party state is never persisted; only aggregate stats go to the DB.
 */
public final class Party {

    private final UUID id;
    private UUID leader;
    private final Set<UUID> members       = new LinkedHashSet<>();
    /** UUIDs that have been sent an invite but haven't accepted yet. */
    private final Set<UUID> pendingInvites = new LinkedHashSet<>();
    /** UUIDs currently in party-chat-send mode (/p toggle). */
    private final Set<UUID> chatToggle    = new LinkedHashSet<>();
    private final long createdAt;
    /** Epoch ms when each member joined this party (for {@code friendship_stats.party_time_ms}). */
    private final Map<UUID, Long> memberJoinedAtMs = new ConcurrentHashMap<>();

    /** Whether party members can damage each other. Default: off. */
    private boolean friendlyFire = false;
    /**
     * When {@code true} any player can join via {@code /party join <leader>}
     * without needing an explicit invite. Default: false (invite-only).
     */
    private boolean open = false;
    /** Optional party warp point set by the leader. Null if unset. */
    private Location warpLocation = null;

    public Party(UUID leader) {
        this.id        = UUID.randomUUID();
        this.leader    = leader;
        this.members.add(leader);
        this.createdAt = System.currentTimeMillis();
        this.memberJoinedAtMs.put(leader, this.createdAt);
    }

    //  Immutable getters 

    public UUID getId()        { return id; }
    public UUID getLeader()    { return leader; }
    public long getCreatedAt() { return createdAt; }

    public Set<UUID> getMembers()        { return Collections.unmodifiableSet(members); }
    public Set<UUID> getPendingInvites() { return Collections.unmodifiableSet(pendingInvites); }
    public Set<UUID> getChatToggle()     { return Collections.unmodifiableSet(chatToggle); }

    public int size() { return members.size(); }

    public boolean isMember(UUID uuid)         { return members.contains(uuid); }
    public boolean isLeader(UUID uuid)         { return leader.equals(uuid); }
    public boolean hasPendingInvite(UUID uuid) { return pendingInvites.contains(uuid); }
    public boolean hasChat(UUID uuid)          { return chatToggle.contains(uuid); }

    //  Feature flags 

    public boolean  isFriendlyFire()   { return friendlyFire; }
    public boolean  isOpen()           { return open; }
    public Location getWarpLocation()  { return warpLocation; }

    //  Mutations (package-private  PartyManager owns all state) 

    void addMember(UUID uuid) {
        members.add(uuid);
        pendingInvites.remove(uuid);
        memberJoinedAtMs.put(uuid, System.currentTimeMillis());
    }

    void removeMember(UUID uuid) {
        members.remove(uuid);
        chatToggle.remove(uuid);
        memberJoinedAtMs.remove(uuid);
    }

    long joinedAtMs(UUID uuid) {
        return memberJoinedAtMs.getOrDefault(uuid, createdAt);
    }

    /** Snapshot of current members for party-time flush (ordered iteration). */
    Set<UUID> memberSnapshot() {
        return Set.copyOf(members);
    }
    void addInvite(UUID uuid)          { pendingInvites.add(uuid); }
    void removeInvite(UUID uuid)       { pendingInvites.remove(uuid); }
    void setLeader(UUID uuid)          { this.leader = uuid; }
    void toggleChat(UUID uuid) {
        if (!chatToggle.remove(uuid)) chatToggle.add(uuid);
    }
    void toggleFriendlyFire()          { this.friendlyFire = !this.friendlyFire; }
    void toggleOpen()                  { this.open = !this.open; }
    void setWarpLocation(Location loc) { this.warpLocation = loc; }
    void clearWarpLocation()           { this.warpLocation = null; }
}