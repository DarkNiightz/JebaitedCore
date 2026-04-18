package com.darkniightz.core.eventmode;

import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Shared event / chat-game broadcast respecting {@link PlayerProfile#isEventNotificationsEnabled()}
 * and {@link PlayerProfile#isEventCategoryEnabled(String)} (e.g. {@code chat_math} → {@code events.chat}).
 */
public final class EventNotifications {

    private EventNotifications() {}

    public static void broadcastCategory(Plugin plugin, String categoryKey, String legacyBodyAfterPrefix) {
        String prefix = plugin.getConfig().getString("event_mode.broadcast_prefix", "&9[&dEVENT&9] &f");
        String message = ChatColor.translateAlternateColorCodes('&', prefix + legacyBodyAfterPrefix);
        if (plugin instanceof JebaitedCore core) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerProfile profile = core.getProfileStore().getOrCreate(player, core.getRankManager().getDefaultGroup());
                if (profile != null && !profile.isEventNotificationsEnabled()) {
                    continue;
                }
                if (profile != null && !profile.isEventCategoryEnabled(categoryKey)) {
                    continue;
                }
                player.sendMessage(message);
            }
        } else {
            Bukkit.broadcastMessage(message);
        }
    }

    /** When {@code categoryKey} is null, only the master event-notification gate applies (e.g. post-stop when session is cleared). */
    public static void broadcastCategoryOptional(Plugin plugin, String categoryKeyOrNull, String legacyBodyAfterPrefix) {
        if (categoryKeyOrNull == null || categoryKeyOrNull.isBlank()) {
            String prefix = plugin.getConfig().getString("event_mode.broadcast_prefix", "&9[&dEVENT&9] &f");
            String message = ChatColor.translateAlternateColorCodes('&', prefix + legacyBodyAfterPrefix);
            if (plugin instanceof JebaitedCore core) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    PlayerProfile profile = core.getProfileStore().getOrCreate(player, core.getRankManager().getDefaultGroup());
                    if (profile != null && !profile.isEventNotificationsEnabled()) {
                        continue;
                    }
                    player.sendMessage(message);
                }
            } else {
                Bukkit.broadcastMessage(message);
            }
            return;
        }
        broadcastCategory(plugin, categoryKeyOrNull, legacyBodyAfterPrefix);
    }
}
