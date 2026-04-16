package com.darkniightz.core.settings;

import org.bukkit.Material;

/**
 * The 6 settings categories shown in the hub menu.
 *
 * <p>To add a new category: add one enum constant here, then add
 * {@link SettingKey} entries that reference it. The hub and category menus
 * are fully driven by this registry — no GUI code changes needed.
 */
public enum SettingCategory {

    NOTIFICATIONS("Notifications",  "§e", Material.BELL,           "§7Control which notifications you receive"),
    SOUNDS       ("Sounds",         "§b", Material.NOTE_BLOCK,      "§7Manage sound effects and audio cues"),
    CHAT         ("Chat",           "§a", Material.WRITABLE_BOOK,   "§7Chat visibility and filter options"),
    SOCIAL       ("Social",         "§d", Material.PLAYER_HEAD,     "§7Control who can interact with you"),
    EVENTS       ("Events",         "§c", Material.IRON_SWORD,      "§7Event alert subscriptions"),
    GAMEPLAY     ("Gameplay",       "§6", Material.COMPASS,         "§7Visual and gameplay preferences");

    /** Human-readable name shown in the GUI. */
    public final String displayName;
    /** Legacy colour code prefix used in item names (e.g. {@code §e}). */
    public final String color;
    /** Representative material for the category button in the hub. */
    public final Material material;
    /** One-line description shown in the category button lore. */
    public final String description;

    SettingCategory(String displayName, String color, Material material, String description) {
        this.displayName = displayName;
        this.color       = color;
        this.material    = material;
        this.description = description;
    }
}
