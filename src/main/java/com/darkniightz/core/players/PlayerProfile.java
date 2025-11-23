package com.darkniightz.core.players;

import java.util.UUID;

/**
 * Minimal player profile for hub MVP.
 * File-backed via ProfileStore.
 */
public class PlayerProfile {
    private UUID uuid;
    private String name;
    private String primaryRank; // rank key from ladder
    private int cosmeticTickets;
    private int messagesSent;
    private int commandsSent;
    private long createdAt;
    private long updatedAt;
    // Cosmetics
    private java.util.Set<String> cosmeticsUnlocked = new java.util.HashSet<>();
    private String equippedParticles;
    private String equippedTrail;
    private String equippedGadget;
    private int wardrobeOpens;
    private int cosmeticEquips;
    // Moderation history stored directly on profile (list of maps for simplicity in YAML)
    private java.util.List<java.util.Map<String, Object>> moderationLog = new java.util.ArrayList<>();
    // Active punishments
    private Long muteUntil; // epoch millis, null or <=0 means not muted
    private String muteReason;
    private String muteActor;
    private Long banUntil;
    private String banReason;
    private String banActor;

    public PlayerProfile() {}

    public PlayerProfile(UUID uuid, String name, String primaryRank) {
        this.uuid = uuid;
        this.name = name;
        this.primaryRank = primaryRank;
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getUuid() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; touch(); }
    public String getPrimaryRank() { return primaryRank; }
    public void setPrimaryRank(String primaryRank) { this.primaryRank = primaryRank; touch(); }
    public int getCosmeticTickets() { return cosmeticTickets; }
    public void setCosmeticTickets(int cosmeticTickets) { this.cosmeticTickets = cosmeticTickets; touch(); }
    public void addTickets(int delta) { this.cosmeticTickets += delta; touch(); }
    public int getMessagesSent() { return messagesSent; }
    public void setMessagesSent(int messagesSent) { this.messagesSent = messagesSent; touch(); }
    public void incMessages() { this.messagesSent++; touch(); }
    public int getCommandsSent() { return commandsSent; }
    public void setCommandsSent(int commandsSent) { this.commandsSent = commandsSent; touch(); }
    public void incCommands() { this.commandsSent++; touch(); }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public java.util.List<java.util.Map<String, Object>> getModerationLog() { return moderationLog; }
    public void setModerationLog(java.util.List<java.util.Map<String, Object>> moderationLog) { this.moderationLog = moderationLog != null ? moderationLog : new java.util.ArrayList<>(); touch(); }
    public void addModerationEntry(java.util.Map<String, Object> entry) { this.moderationLog.add(entry); touch(); }
    public Long getMuteUntil() { return muteUntil; }
    public void setMuteUntil(Long muteUntil) { this.muteUntil = muteUntil; touch(); }
    public String getMuteReason() { return muteReason; }
    public void setMuteReason(String muteReason) { this.muteReason = muteReason; touch(); }
    public String getMuteActor() { return muteActor; }
    public void setMuteActor(String muteActor) { this.muteActor = muteActor; touch(); }
    public Long getBanUntil() { return banUntil; }
    public void setBanUntil(Long banUntil) { this.banUntil = banUntil; touch(); }
    public String getBanReason() { return banReason; }
    public void setBanReason(String banReason) { this.banReason = banReason; touch(); }
    public String getBanActor() { return banActor; }
    public void setBanActor(String banActor) { this.banActor = banActor; touch(); }

    public void touch() { this.updatedAt = System.currentTimeMillis(); }

    // Cosmetics getters/setters
    public java.util.Set<String> getCosmeticsUnlocked() { return cosmeticsUnlocked; }
    public void setCosmeticsUnlocked(java.util.Set<String> set) { this.cosmeticsUnlocked = set != null ? set : new java.util.HashSet<>(); touch(); }
    public boolean hasUnlocked(String key) { return cosmeticsUnlocked != null && cosmeticsUnlocked.contains(key); }
    public void unlock(String key) { if (cosmeticsUnlocked == null) cosmeticsUnlocked = new java.util.HashSet<>(); cosmeticsUnlocked.add(key); touch(); }

    public String getEquippedParticles() { return equippedParticles; }
    public void setEquippedParticles(String key) { this.equippedParticles = key; touch(); }
    public String getEquippedTrail() { return equippedTrail; }
    public void setEquippedTrail(String key) { this.equippedTrail = key; touch(); }
    public String getEquippedGadget() { return equippedGadget; }
    public void setEquippedGadget(String key) { this.equippedGadget = key; touch(); }

    public int getWardrobeOpens() { return wardrobeOpens; }
    public void incWardrobeOpens() { this.wardrobeOpens++; touch(); }
    public int getCosmeticEquips() { return cosmeticEquips; }
    public void incCosmeticEquips() { this.cosmeticEquips++; touch(); }
}
