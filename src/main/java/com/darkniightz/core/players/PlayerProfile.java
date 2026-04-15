package com.darkniightz.core.players;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a player's cached data, loaded from the database.
 * This is a Plain Old Java Object (POJO) used to hold state in memory.
 */
public class PlayerProfile {

    public static final String PREF_PRIVATE_MESSAGES = "private_messages";
    public static final String PREF_TELEPORT_REQUESTS = "teleport_requests";
    public static final String PREF_TRADE_REQUESTS = "trade_requests";
    public static final String PREF_DUEL_INVITES = "duel_invites";
    public static final String PREF_PARTY_INVITES = "party_invites";
    public static final String PREF_SOUND_CUES = "sound_cues";
    public static final String PREF_COSMETIC_VISIBILITY = "cosmetic_visibility";
    public static final String PREF_HEAD_NAMETAGS = "head_nametags";
    public static final String PREF_NAMETAG_EXTRA = "nametag_extra";
    public static final String PREF_SCOREBOARD = "scoreboard";
    public static final String PREF_SCOREBOARD_MODE = "scoreboard_mode";
    public static final String PREF_JOIN_LEAVE_MESSAGES = "join_leave_messages";
    public static final String PREF_DEATH_MESSAGES = "death_messages";
    public static final String PREF_EVENT_NOTIFICATIONS = "event_notifications";
    public static final String PREF_EVENT_PREFIX = "event_";

    private final UUID uuid;
    private final String name;

    // Data from 'players' table
    private String rank;
    // Tracks if the rank was modified in-memory since last load/save
    private boolean rankDirty = false;
    private String donorRank; // null = no donor rank assigned
    private boolean donorRankDirty = false;
    /** "primary" (default) or "donor" — which rank shows in chat prefix. */
    private String rankDisplayMode = "primary";
    private boolean rankDisplayModeDirty = false;
    private long firstJoined;
    private long lastJoined;

    // Data from 'player_stats' table
    private int commandsSent;
    private int messagesSent;
    private int kills;
    private int deaths;
    private int mobsKilled;
    private int bossesKilled;
    private int blocksBroken;
    private int cropsBroken;
    private int fishCaught;
    private int mcmmoLevel;
    private int eventWinsCombat;
    private int eventWinsChat;
    private int eventWinsHardcore;
    private long playtimeMs;
    private int cosmeticEquips;
    private int cosmeticTickets;
    private int wardrobeOpens;
    private int cosmeticCoins; // Wallet: cosmetic coins
    private double balance; // Main economy balance
    private String activeTag;
    private String language = "en";
    private final Map<String, Boolean> preferences = new LinkedHashMap<>();

    // Session-only timestamps for auto-off timers (not persisted yet)
    private Long particleActivatedAt;
    private Long trailActivatedAt;

    // Data from 'player_cosmetics' table
    private Set<String> unlockedCosmetics = new HashSet<>();
    private String activeParticle;
    private String activeTrail;
    private String activeGadget;
    private Set<String> favoriteCosmetics = new LinkedHashSet<>();
    private Set<String> previewedCosmetics = new LinkedHashSet<>();
    private Map<String, String> cosmeticLoadouts = new LinkedHashMap<>();

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
    public int getKills() { return kills; }
    public int getDeaths() { return deaths; }
    public int getMobsKilled() { return mobsKilled; }
    public int getBossesKilled() { return bossesKilled; }
    public int getBlocksBroken() { return blocksBroken; }
    public int getCropsBroken() { return cropsBroken; }
    public int getFishCaught() { return fishCaught; }
    public int getMcMMOLevel() { return mcmmoLevel; }
    public int getEventWinsCombat() { return eventWinsCombat; }
    public int getEventWinsChat() { return eventWinsChat; }
    public int getEventWinsHardcore() { return eventWinsHardcore; }
    public long getPlaytimeMs() { return playtimeMs; }
    public int getCosmeticEquips() { return cosmeticEquips; }
    public int getCosmeticTickets() { return cosmeticTickets; }
    public int getWardrobeOpens() { return wardrobeOpens; }
    public int getCosmeticCoins() { return cosmeticCoins; }
    public double getBalance() { return balance; }
    public String getActiveTag() { return activeTag; }
    public String getLanguage() { return language == null || language.isBlank() ? "en" : language; }
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

    public Set<String> getFavoriteCosmetics() {
        return favoriteCosmetics;
    }

    public Set<String> getPreviewedCosmetics() {
        return previewedCosmetics;
    }

    public Map<String, String> getCosmeticLoadouts() {
        return cosmeticLoadouts;
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

    public String getDonorRank() { return donorRank; }
    /** Sets donor rank and marks it dirty for persistence. */
    public void setDonorRank(String donorRank) { this.donorRank = donorRank; this.donorRankDirty = true; }
    /** DAO-only: loads donor rank without marking dirty. */
    public void setDonorRankLoaded(String donorRank) { this.donorRank = donorRank; this.donorRankDirty = false; }
    public boolean isDonorRankDirty() { return donorRankDirty; }
    public void clearDonorRankDirty() { this.donorRankDirty = false; }

    /** Returns the rank that should be shown in chat prefix. */
    public String getDisplayRank() {
        if ("donor".equals(rankDisplayMode) && donorRank != null) return donorRank;
        return rank;
    }
    public String getRankDisplayMode() { return rankDisplayMode == null ? "primary" : rankDisplayMode; }
    public void setRankDisplayMode(String mode) { this.rankDisplayMode = mode; this.rankDisplayModeDirty = true; }
    public void setRankDisplayModeLoaded(String mode) { this.rankDisplayMode = (mode == null ? "primary" : mode); this.rankDisplayModeDirty = false; }
    public boolean isRankDisplayModeDirty() { return rankDisplayModeDirty; }
    public void clearRankDisplayModeDirty() { this.rankDisplayModeDirty = false; }
    public void setFirstJoined(long firstJoined) { this.firstJoined = firstJoined; }
    public void setLastJoined(long lastJoined) { this.lastJoined = lastJoined; }
    public void setCommandsSent(int commandsSent) { this.commandsSent = commandsSent; }
    public void setMessagesSent(int messagesSent) { this.messagesSent = messagesSent; }
    public void setKills(int kills) { this.kills = Math.max(0, kills); }
    public void setDeaths(int deaths) { this.deaths = Math.max(0, deaths); }
    public void setMobsKilled(int mobsKilled) { this.mobsKilled = Math.max(0, mobsKilled); }
    public void setBossesKilled(int bossesKilled) { this.bossesKilled = Math.max(0, bossesKilled); }
    public void setBlocksBroken(int blocksBroken) { this.blocksBroken = Math.max(0, blocksBroken); }
    public void setCropsBroken(int cropsBroken) { this.cropsBroken = Math.max(0, cropsBroken); }
    public void setFishCaught(int fishCaught) { this.fishCaught = Math.max(0, fishCaught); }
    public void setMcMMOLevel(int mcmmoLevel) { this.mcmmoLevel = Math.max(0, mcmmoLevel); }
    public void setEventWinsCombat(int eventWinsCombat) { this.eventWinsCombat = Math.max(0, eventWinsCombat); }
    public void setEventWinsChat(int eventWinsChat) { this.eventWinsChat = Math.max(0, eventWinsChat); }
    public void setEventWinsHardcore(int eventWinsHardcore) { this.eventWinsHardcore = Math.max(0, eventWinsHardcore); }
    public void setPlaytimeMs(long playtimeMs) { this.playtimeMs = Math.max(0L, playtimeMs); }
    public void setCosmeticTickets(int tickets) { this.cosmeticTickets = tickets; }
    public void setWardrobeOpens(int opens) { this.wardrobeOpens = opens; }
    public void setCosmeticCoins(int coins) { this.cosmeticCoins = Math.max(0, coins); }
    public void setBalance(double balance) { this.balance = Math.max(0D, roundMoney(balance)); }
    public void setActiveTag(String activeTag) { this.activeTag = activeTag; }
    public void setLanguage(String language) {
        String normalized = language == null ? "en" : language.trim().toLowerCase(Locale.ROOT);
        this.language = normalized.isBlank() ? "en" : normalized;
    }
    public void setParticleActivatedAt(Long ts) { this.particleActivatedAt = ts; }
    public void setTrailActivatedAt(Long ts) { this.trailActivatedAt = ts; }

    public void setUnlockedCosmetics(Set<String> unlockedCosmetics) {
        this.unlockedCosmetics = unlockedCosmetics;
    }

    public void setFavoriteCosmetics(Set<String> favoriteCosmetics) {
        this.favoriteCosmetics = favoriteCosmetics == null ? new LinkedHashSet<>() : new LinkedHashSet<>(favoriteCosmetics);
    }

    public void setPreviewedCosmetics(Set<String> previewedCosmetics) {
        this.previewedCosmetics = previewedCosmetics == null ? new LinkedHashSet<>() : new LinkedHashSet<>(previewedCosmetics);
    }

    public void setCosmeticLoadouts(Map<String, String> cosmeticLoadouts) {
        this.cosmeticLoadouts = cosmeticLoadouts == null ? new LinkedHashMap<>() : new LinkedHashMap<>(cosmeticLoadouts);
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

    public void incKills() { this.kills++; }
    public void incDeaths() { this.deaths++; }
    public void incMobsKilled() { this.mobsKilled++; }
    public void incBossesKilled() { this.bossesKilled++; }
    public void incBlocksBroken() { this.blocksBroken++; }
    public void incCropsBroken() { this.cropsBroken++; }
    public void incFishCaught() { this.fishCaught++; }
    public void incEventWinsCombat() { this.eventWinsCombat++; }
    public void incEventWinsChat() { this.eventWinsChat++; }
    public void incEventWinsHardcore() { this.eventWinsHardcore++; }
    public void addPlaytimeMs(long delta) {
        if (delta <= 0L) return;
        this.playtimeMs += delta;
    }

    public void incCosmeticEquips() {
        this.cosmeticEquips++;
    }

    public void incMessages() { this.messagesSent++; }
    public void addCosmeticTickets(int delta) { this.cosmeticTickets += delta; }
    public void incWardrobeOpens() { this.wardrobeOpens++; }
    public void addCosmeticCoins(int delta) { this.cosmeticCoins = Math.max(0, this.cosmeticCoins + delta); }
    public void addBalance(double delta) { this.balance = Math.max(0D, roundMoney(this.balance + delta)); }
    public boolean spendBalance(double amount) {
        double rounded = roundMoney(amount);
        if (rounded <= 0D) return true;
        if (this.balance + 0.00001D < rounded) return false;
        this.balance = roundMoney(this.balance - rounded);
        return true;
    }
    public boolean spendCosmeticCoins(int amount) {
        if (amount <= 0) return true;
        if (this.cosmeticCoins < amount) return false;
        this.cosmeticCoins -= amount;
        return true;
    }

    public boolean toggleFavoriteCosmetic(String key, int maxFavorites) {
        if (key == null || key.isBlank()) {
            return false;
        }
        if (favoriteCosmetics.contains(key)) {
            favoriteCosmetics.remove(key);
            return true;
        }
        if (maxFavorites > 0 && favoriteCosmetics.size() >= maxFavorites) {
            return false;
        }
        favoriteCosmetics.add(key);
        return true;
    }

    public boolean isFavoriteCosmetic(String key) {
        return key != null && favoriteCosmetics.contains(key);
    }

    public void markPreviewedCosmetic(String key) {
        if (key == null || key.isBlank()) return;
        previewedCosmetics.add(key);
    }

    public boolean isPreviewedCosmetic(String key) {
        return key != null && previewedCosmetics.contains(key);
    }

    public boolean hasCosmeticLoadout(String name) {
        return name != null && cosmeticLoadouts.containsKey(name);
    }

    public String getCosmeticLoadout(String name) {
        return name == null ? null : cosmeticLoadouts.get(name);
    }

    public void putCosmeticLoadout(String name, String serialized) {
        if (name == null || name.isBlank()) return;
        cosmeticLoadouts.put(name.trim(), serialized == null ? "" : serialized);
    }

    public void removeCosmeticLoadout(String name) {
        if (name == null || name.isBlank()) return;
        cosmeticLoadouts.remove(name.trim());
    }

    public boolean getPreference(String key, boolean defaultValue) {
        if (key == null || key.isBlank()) return defaultValue;
        return preferences.getOrDefault(normalizePreferenceKey(key), defaultValue);
    }

    public void setPreference(String key, boolean enabled) {
        if (key == null || key.isBlank()) return;
        preferences.put(normalizePreferenceKey(key), enabled);
    }

    public boolean togglePreference(String key, boolean defaultValue) {
        boolean updated = !getPreference(key, defaultValue);
        setPreference(key, updated);
        return updated;
    }

    public boolean isPrivateMessagesEnabled() { return getPreference(PREF_PRIVATE_MESSAGES, true); }
    public boolean isTeleportRequestsEnabled() { return getPreference(PREF_TELEPORT_REQUESTS, true); }
    public boolean isTradeRequestsEnabled() { return getPreference(PREF_TRADE_REQUESTS, true); }
    public boolean isDuelInvitesEnabled() { return getPreference(PREF_DUEL_INVITES, true); }
    public boolean isPartyInvitesEnabled() { return getPreference(PREF_PARTY_INVITES, true); }
    public boolean isSoundCuesEnabled() { return getPreference(PREF_SOUND_CUES, true); }
    public boolean isCosmeticVisibilityEnabled() { return getPreference(PREF_COSMETIC_VISIBILITY, true); }
    public boolean isHeadNametagsEnabled() { return getPreference(PREF_HEAD_NAMETAGS, true); }
    public boolean isNametagExtraEnabled() { return getPreference(PREF_NAMETAG_EXTRA, false); }
    public boolean isScoreboardVisible() { return !"none".equalsIgnoreCase(getScoreboardMode()); }
    public String getScoreboardMode() {
        return switch (normalizePreferenceKey(preferencesTextValue(PREF_SCOREBOARD_MODE, "normal"))) {
            case "minimal" -> "minimal";
            case "none" -> "none";
            default -> "normal";
        };
    }
    public void setScoreboardMode(String mode) {
        setPreferenceText(PREF_SCOREBOARD_MODE, mode == null ? "normal" : mode);
    }
    public String cycleScoreboardMode() {
        return switch (getScoreboardMode()) {
            case "normal" -> { setScoreboardMode("minimal"); yield "minimal"; }
            case "minimal" -> { setScoreboardMode("none"); yield "none"; }
            default -> { setScoreboardMode("normal"); yield "normal"; }
        };
    }
    public boolean isJoinLeaveMessagesEnabled() { return getPreference(PREF_JOIN_LEAVE_MESSAGES, true); }
    public boolean isDeathMessagesEnabled() { return getPreference(PREF_DEATH_MESSAGES, true); }
    public boolean isEventNotificationsEnabled() { return getPreference(PREF_EVENT_NOTIFICATIONS, true); }
    public boolean isEventCategoryEnabled(String eventKey) {
        String normalized = normalizePreferenceKey(eventKey);
        if (normalized.startsWith("chat_")) {
            normalized = "chat";
        }
        return isEventNotificationsEnabled() && getPreference(PREF_EVENT_PREFIX + normalized, true);
    }

    public String cycleLanguage() {
        String[] options = {"en", "es", "fr", "de"};
        String current = getLanguage();
        for (int i = 0; i < options.length; i++) {
            if (options[i].equalsIgnoreCase(current)) {
                String next = options[(i + 1) % options.length];
                setLanguage(next);
                return next;
            }
        }
        setLanguage(options[0]);
        return options[0];
    }

    public void loadPreferences(String raw) {
        preferences.clear();
        if (raw == null || raw.isBlank()) return;
        for (String line : raw.split("\\r?\\n")) {
            if (line == null || line.isBlank()) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = normalizePreferenceKey(line.substring(0, eq));
            String value = line.substring(eq + 1).trim();
            if ("language".equals(key)) {
                setLanguage(value);
            } else if (PREF_SCOREBOARD_MODE.equals(key)) {
                setScoreboardMode(value);
            } else if (PREF_SCOREBOARD.equals(key) && !Boolean.parseBoolean(value)) {
                setScoreboardMode("none");
            } else {
                setPreference(key, "true".equalsIgnoreCase(value) || "1".equals(value) || "on".equalsIgnoreCase(value));
            }
        }
    }

    public String serializePreferences() {
        StringBuilder out = new StringBuilder();
        out.append("language=").append(getLanguage());
        out.append('\n').append(PREF_SCOREBOARD_MODE).append('=').append(getScoreboardMode());
        for (Map.Entry<String, Boolean> entry : preferences.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) continue;
            if (PREF_SCOREBOARD_MODE.equals(entry.getKey()) || entry.getKey().startsWith(PREF_SCOREBOARD_MODE + "_")) continue;
            out.append('\n').append(normalizePreferenceKey(entry.getKey())).append('=').append(Boolean.TRUE.equals(entry.getValue()));
        }
        return out.toString();
    }

    private void setPreferenceText(String key, String value) {
        if (key == null || key.isBlank()) return;
        preferences.put(normalizePreferenceKey(key), "minimal".equalsIgnoreCase(value));
        if (!PREF_SCOREBOARD_MODE.equals(normalizePreferenceKey(key))) {
            return;
        }
        preferences.remove(PREF_SCOREBOARD);
        preferences.put(PREF_SCOREBOARD_MODE + "_normal", "normal".equalsIgnoreCase(value));
        preferences.put(PREF_SCOREBOARD_MODE + "_minimal", "minimal".equalsIgnoreCase(value));
        preferences.put(PREF_SCOREBOARD_MODE + "_none", "none".equalsIgnoreCase(value));
    }

    private String preferencesTextValue(String key, String fallback) {
        if (PREF_SCOREBOARD_MODE.equals(normalizePreferenceKey(key))) {
            if (getPreference(PREF_SCOREBOARD_MODE + "_minimal", false)) return "minimal";
            if (getPreference(PREF_SCOREBOARD_MODE + "_none", false)) return "none";
            if (getPreference(PREF_SCOREBOARD_MODE + "_normal", true)) return "normal";
        }
        return fallback;
    }

    private String normalizePreferenceKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private double roundMoney(double value) {
        return Math.round(value * 100D) / 100D;
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
