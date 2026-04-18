package com.darkniightz.core.settings;

import com.darkniightz.core.players.PlayerProfile;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

import static com.darkniightz.core.settings.SettingCategory.*;

/**
 * Registry of every player-facing toggle setting.
 *
 * <p><strong>To add a new setting:</strong> add one enum constant in the
 * appropriate category section below. That is the only change required — the
 * hub and category menus auto-populate from this registry.
 *
 * <p>Each constant maps directly to an existing {@link PlayerProfile}
 * preference key so no DB migration is needed.  If a future setting requires
 * a new persistence column, add it to {@code PlayerProfile} (+ DB migration)
 * and reference it here exactly like the existing entries.
 */
public enum SettingKey {

    // ── Notifications ──────────────────────────────────────────────────────
    // These keys control in-chat / on-join notifications.

    NOTIFY_JOIN_LEAVE   (NOTIFICATIONS, PlayerProfile.PREF_JOIN_LEAVE_MESSAGES, "Join/Leave Messages",
            "Show player join and quit notices in chat",          Material.OAK_SIGN,         true),
    NOTIFY_DEATH        (NOTIFICATIONS, PlayerProfile.PREF_DEATH_MESSAGES,      "Death Messages",
            "Show player death messages in chat",                 Material.TOTEM_OF_UNDYING, true),

    // ── Sounds ─────────────────────────────────────────────────────────────
    // Audio cues for UI interactions.

    SOUND_CUES          (SOUNDS,        PlayerProfile.PREF_SOUND_CUES,          "Sound Cues",
            "Play sounds for menu interactions and previews",     Material.NOTE_BLOCK,       true),

    // ── Chat ───────────────────────────────────────────────────────────────
    // Chat visibility and message filtering.

    CHAT_PRIVATE_MSGS   (CHAT,          PlayerProfile.PREF_PRIVATE_MESSAGES,    "Private Messages",
            "Allow direct messages from other players",           Material.PAPER,            true),

    // ── Social ─────────────────────────────────────────────────────────────
    // Incoming interaction requests.

    SOCIAL_PARTY_INVITES(SOCIAL,        PlayerProfile.PREF_PARTY_INVITES,       "Party Invites",
            "Allow party invitations from other players",         Material.PLAYER_HEAD,      true),
    SOCIAL_DUEL_INVITES (SOCIAL,        PlayerProfile.PREF_DUEL_INVITES,        "Duel Invites",
            "Allow duel invitations from other players",          Material.GOLDEN_SWORD,     true),
    SOCIAL_TRADE_REQS   (SOCIAL,        PlayerProfile.PREF_TRADE_REQUESTS,      "Trade Requests",
            "Allow incoming trade requests",                      Material.EMERALD,          true),
    SOCIAL_TP_REQS      (SOCIAL,        PlayerProfile.PREF_TELEPORT_REQUESTS,   "Teleport Requests",
            "Allow incoming teleport requests",                   Material.ENDER_PEARL,      true),

    // ── Events ─────────────────────────────────────────────────────────────
    // Per-event-type alert subscriptions.
    // EVENTS_MASTER is the top-level gate; individual toggles are advisory
    // sub-filters applied only when the master is on.

    EVENTS_MASTER       (EVENTS,        PlayerProfile.PREF_EVENT_NOTIFICATIONS,  "All Event Alerts",
            "Master switch — enables or disables all event alerts", Material.BELL,           true),
    EVENTS_KOTH         (EVENTS,        PlayerProfile.PREF_EVENT_PREFIX + "koth",  "KOTH Alerts",
            "Alerts for King of the Hill events",                 Material.IRON_SWORD,       true),
    EVENTS_FFA          (EVENTS,        PlayerProfile.PREF_EVENT_PREFIX + "ffa",   "FFA Alerts",
            "Alerts for free-for-all events",                     Material.DIAMOND_SWORD,    true),
    EVENTS_DUELS        (EVENTS,        PlayerProfile.PREF_EVENT_PREFIX + "duels", "Duels Alerts",
            "Alerts for scheduled duels events",                  Material.GOLDEN_SWORD,     true),
    EVENTS_CHAT_GAMES   (EVENTS,        PlayerProfile.PREF_EVENT_PREFIX + "chat",  "Chat Game Alerts",
            "Alerts for quiz, math, and scramble games",          Material.NAME_TAG,         true),

    // ── Gameplay ───────────────────────────────────────────────────────────
    // Visual and gameplay preference toggles.

    GAMEPLAY_COSMETICS  (GAMEPLAY,      PlayerProfile.PREF_COSMETIC_VISIBILITY, "Cosmetic Visibility",
            "Show other players' particle and trail effects",     Material.FIREWORK_ROCKET,  true),
    GAMEPLAY_HEAD_TAGS  (GAMEPLAY,      PlayerProfile.PREF_HEAD_NAMETAGS,       "Head Nametags",
            "Show your coloured rank name above your head",       Material.NAME_TAG,         true),
    GAMEPLAY_TAG_EXTRA  (GAMEPLAY,      PlayerProfile.PREF_NAMETAG_EXTRA,       "Extra Nametag Info",
            "Add rank label and extra detail to your nametag",    Material.WRITABLE_BOOK,    false),
    GAMEPLAY_SHOP_STACK_CONFIRM(GAMEPLAY, PlayerProfile.PREF_SHOP_STACK_CONFIRM, "Shop stack confirm",
            "Ask before buying a full stack (shift-click) in /shop", Material.CHEST, true),
    ;

    // ──────────────────────────────────────────────────────────────────────

    /** The category this setting belongs to. */
    public final SettingCategory category;
    /** Key used in {@link PlayerProfile#getPreference(String, boolean)}. */
    public final String key;
    /** Human-readable name for the toggle item. */
    public final String displayName;
    /** One-line description shown in the toggle item lore. */
    public final String description;
    /** Item material used for the toggle button in the GUI. */
    public final Material material;
    /** The default value when no preference has been saved. */
    public final boolean defaultValue;

    SettingKey(SettingCategory category, String key, String displayName,
               String description, Material material, boolean defaultValue) {
        this.category     = category;
        this.key          = key;
        this.displayName  = displayName;
        this.description  = description;
        this.material     = material;
        this.defaultValue = defaultValue;
    }

    /**
     * Returns all keys belonging to {@code category}, in declaration order.
     * Used by {@code SettingsCategoryMenu} to auto-populate the GUI.
     */
    public static List<SettingKey> forCategory(SettingCategory category) {
        List<SettingKey> result = new ArrayList<>();
        for (SettingKey k : values()) {
            if (k.category == category) result.add(k);
        }
        return result;
    }
}
