package com.darkniightz.core.players;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a player's cached data, loaded from the database.
 * This is a Plain Old Java Object (POJO) used to hold state in memory.
 */
public class PlayerProfile {

    private final UUID uuid;
    private final String name;

    // Data from 'players' table
    private String rank;
    // Tracks if the rank was modified in-memory since last load/save
    private boolean rankDirty = false;
    private long firstJoined;
    private long lastJoined;

    // Data from 'player_stats' table
    private int commandsSent;
    private int messagesSent;
    private int cosmeticEquips;
    private int cosmeticTickets;
    private int wardrobeOpens;
    private int cosmeticCoins; // Wallet: cosmetic coins

    // Session-only timestamps for auto-off timers (not persisted yet)
    private Long particleActivatedAt;
    private Long trailActivatedAt;

    // Data from 'player_cosmetics' table
    private Set<String> unlockedCosmetics = new HashSet<>();
    private String activeParticle;
    private String activeTrail;
    private String activeGadget;

    // Moderation fields (not persisted in current DAO, but used by commands/runtime)
    private Long muteUntil; // null = not muted, Long.MAX_VALUE = permanent
    private String muteReason;
    private String muteActor;
    private Long banUntil; // null = not banned, Long.MAX_VALUE = permanent
    private String banReason;
    private String banActor;

    public PlayerProfile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    // Getters
    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public String getRank() { return rank; }
    // Backwards-compat aliases expected by various classes
    public String getPrimaryRank() { return rank; }
    public long getFirstJoined() { return firstJoined; }
    public long getLastJoined() { return lastJoined; }
    public int getCommandsSent() { return commandsSent; }
    public int getMessagesSent() { return messagesSent; }
    public int getCosmeticEquips() { return cosmeticEquips; }
    public int getCosmeticTickets() { return cosmeticTickets; }
    public int getWardrobeOpens() { return wardrobeOpens; }
    public int getCosmeticCoins() { return cosmeticCoins; }
    public Long getParticleActivatedAt() { return particleActivatedAt; }
    public Long getTrailActivatedAt() { return trailActivatedAt; }

    // Cosmetics
    public boolean hasUnlocked(String key) {
        return unlockedCosmetics.contains(key);
    }

    public String getEquippedParticles() { return activeParticle; }
    public String getEquippedTrail() { return activeTrail; }
    public String getEquippedGadget() { return activeGadget; }

    public Set<String> getUnlockedCosmetics() {
        return unlockedCosmetics;
    }

    public String getActiveParticle() {
        return activeParticle;
    }

    public String getActiveTrail() {
        return activeTrail;
    }

    // Setters
    public void setRank(String rank) { this.rank = rank; this.rankDirty = true; }
    public void setPrimaryRank(String rank) { this.rank = rank; this.rankDirty = true; }

    /**
     * Sets the rank when loading from the database without marking it as modified.
     * Use only from DAO layer.
     */
    public void setRankLoaded(String rank) { this.rank = rank; this.rankDirty = false; }
    public void setFirstJoined(long firstJoined) { this.firstJoined = firstJoined; }
    public void setLastJoined(long lastJoined) { this.lastJoined = lastJoined; }
    public void setCommandsSent(int commandsSent) { this.commandsSent = commandsSent; }
    public void setMessagesSent(int messagesSent) { this.messagesSent = messagesSent; }
    public void setCosmeticTickets(int tickets) { this.cosmeticTickets = tickets; }
    public void setWardrobeOpens(int opens) { this.wardrobeOpens = opens; }
    public void setCosmeticCoins(int coins) { this.cosmeticCoins = Math.max(0, coins); }
    public void setParticleActivatedAt(Long ts) { this.particleActivatedAt = ts; }
    public void setTrailActivatedAt(Long ts) { this.trailActivatedAt = ts; }

    public void setUnlockedCosmetics(Set<String> unlockedCosmetics) {
        this.unlockedCosmetics = unlockedCosmetics;
    }

    public void setActiveParticle(String activeParticle) {
        this.activeParticle = activeParticle;
    }

    public void setActiveTrail(String activeTrail) {
        this.activeTrail = activeTrail;
    }

    // Setters for equipped cosmetics
    public void setEquippedParticles(String key) {
        this.activeParticle = key;
    }

    public void setEquippedTrail(String key) {
        this.activeTrail = key;
    }

    public void setEquippedGadget(String key) {
        this.activeGadget = key;
    }

    // Increments
    public void incCommands() {
        this.commandsSent++;
    }

    public void incCosmeticEquips() {
        this.cosmeticEquips++;
    }

    public void incMessages() { this.messagesSent++; }
    public void addCosmeticTickets(int delta) { this.cosmeticTickets += delta; }
    public void incWardrobeOpens() { this.wardrobeOpens++; }
    public void addCosmeticCoins(int delta) { this.cosmeticCoins = Math.max(0, this.cosmeticCoins + delta); }
    public boolean spendCosmeticCoins(int amount) {
        if (amount <= 0) return true;
        if (this.cosmeticCoins < amount) return false;
        this.cosmeticCoins -= amount;
        return true;
    }

    // Moderation fields accessors
    public Long getMuteUntil() { return muteUntil; }
    public String getMuteReason() { return muteReason; }
    public String getMuteActor() { return muteActor; }
    public void setMuteUntil(Long until) { this.muteUntil = until; }
    public void setMuteReason(String reason) { this.muteReason = reason; }
    public void setMuteActor(String actor) { this.muteActor = actor; }

    public Long getBanUntil() { return banUntil; }
    public String getBanReason() { return banReason; }
    public String getBanActor() { return banActor; }
    public void setBanUntil(Long until) { this.banUntil = until; }
    public void setBanReason(String reason) { this.banReason = reason; }
    public void setBanActor(String actor) { this.banActor = actor; }

    // Dirty tracking helpers
    public boolean isRankDirty() { return rankDirty; }
    public void clearRankDirty() { this.rankDirty = false; }
}