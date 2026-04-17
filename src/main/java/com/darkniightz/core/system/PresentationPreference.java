package com.darkniightz.core.system;

import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.entity.Player;

/**
 * Respects {@link PlayerProfile#getScreenEffectsMode()} for boss bars and titles:
 * {@code full} — all implemented presentation; {@code some} — high-signal only (e.g. event countdown);
 * {@code none} — no extra screen effects (chat/scoreboard/action bar may still apply).
 */
public final class PresentationPreference {

    private PresentationPreference() {}

    public static String modeFor(Player player) {
        if (player == null) return "full";
        JebaitedCore plugin = JebaitedCore.getInstance();
        if (plugin == null) return "full";
        PlayerProfile profile = plugin.getProfileStore().get(player.getUniqueId());
        if (profile == null) return "full";
        return profile.getScreenEffectsMode();
    }

    /** Event lobby countdown boss bar (shown to players who opt in). */
    public static boolean showEventCountdownBossBar(Player player) {
        String m = modeFor(player);
        return "full".equals(m) || "some".equals(m);
    }

    /** Per-player grave tracker boss bar (full only — "some" uses action bar only). */
    public static boolean showGraveTrackerBossBar(Player player) {
        return "full".equals(modeFor(player));
    }

    /** In-game titles/subtitles (e.g. combat). */
    public static boolean showTitle(Player player, boolean highSignificance) {
        String m = modeFor(player);
        if ("none".equals(m)) return false;
        if ("some".equals(m)) return highSignificance;
        return true;
    }
}
