package com.darkniightz.core.achievements;

import java.util.Collections;
import java.util.List;

/**
 * Immutable definition of a single achievement/milestone, loaded from config.yml at startup.
 * Instances are created by {@link AchievementManager#loadDefinitions()}.
 */
public final class AchievementDefinition {

    /** Stat-tracking categories that achievements can be based on. */
    public enum AchievementType {
        KILLS,
        DEATHS,
        MOBS_KILLED,
        BLOCKS_BROKEN,
        BLOCKS_PLACED,
        FISH_CAUGHT,
        CROPS_HARVESTED,
        MESSAGES_SENT,
        COMMANDS_SENT,
        DISTANCE_TRAVELLED,
        EVENT_WINS_COMBAT,
        EVENT_WINS_CHAT,
        EVENT_WINS_HARDCORE,
        PLAYTIME_MINUTES,
        COSMETIC_COINS_EARNED,
        PARTY_KILLS,
        FRIEND_COUNT
    }

    /**
     * A single unlock threshold within an achievement.
     *
     * @param index       0-based tier index
     * @param label       Human-readable label (e.g. "Apprentice", "Legend")
     * @param threshold   Progress value required to unlock this tier
     * @param tagText     Non-null if the reward is a cosmetic tag string; otherwise null
     * @param rewardType  "tag", "coins", or "cosmetic"
     * @param rewardValue Type-specific value: tag string, coin amount, or cosmetic ID
     */
    public record AchievementTier(
        int    index,
        String label,
        long   threshold,
        String tagText,
        String rewardType,
        String rewardValue
    ) {}

    private final String              id;
    private final String              displayName;
    private final String              description;
    private final AchievementType     type;
    private final List<AchievementTier> tiers;
    private final boolean             secret;

    public AchievementDefinition(
        String id,
        String displayName,
        String description,
        AchievementType type,
        List<AchievementTier> tiers,
        boolean secret
    ) {
        this.id          = id;
        this.displayName = displayName;
        this.description = description;
        this.type        = type;
        this.tiers       = Collections.unmodifiableList(tiers);
        this.secret      = secret;
    }

    public String getId()            { return id; }
    public String getDisplayName()   { return displayName; }
    public String getDescription()   { return description; }
    public AchievementType getType() { return type; }
    public List<AchievementTier> getTiers() { return tiers; }
    public boolean isSecret()        { return secret; }
    public int tierCount()           { return tiers.size(); }

    /** Returns tier at 0-based index, or null if out of range. */
    public AchievementTier tier(int i) {
        return (i >= 0 && i < tiers.size()) ? tiers.get(i) : null;
    }
}
