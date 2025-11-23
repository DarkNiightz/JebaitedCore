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
    private long firstJoined;
    private long lastJoined;

    // Data from 'player_stats' table
    private int commandsSent;
    private int cosmeticEquips;

    // Data from 'player_cosmetics' table
    private Set<String> unlockedCosmetics = new HashSet<>();
    private String activeParticle;
    private String activeTrail;
    private String activeGadget;

    public PlayerProfile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    // Getters
    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public String getRank() { return rank; }
    public long getFirstJoined() { return firstJoined; }
    public long getLastJoined() { return lastJoined; }
    public int getCommandsSent() { return commandsSent; }
    public int getCosmeticEquips() { return cosmeticEquips; }

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
    public void setRank(String rank) { this.rank = rank; }
    public void setFirstJoined(long firstJoined) { this.firstJoined = firstJoined; }
    public void setLastJoined(long lastJoined) { this.lastJoined = lastJoined; }
    public void setCommandsSent(int commandsSent) { this.commandsSent = commandsSent; }

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
}