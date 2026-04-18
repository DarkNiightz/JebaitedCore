package com.darkniightz.core.chat;

import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.moderation.ModerationManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.ranks.RankManager.RankStyle;
import com.darkniightz.core.permissions.PermissionConstants;
import com.darkniightz.core.system.NicknameManager;
import com.darkniightz.main.JebaitedCore;
import io.papermc.paper.event.player.AsyncChatEvent;
import com.darkniightz.core.chat.ChatUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Supported Paper range: 1.21.9-1.21.11.
 */
public class ChatListener implements Listener {

    private final Plugin plugin;
    private final RankManager rankManager;
    private final ProfileStore profileStore;
    private final ModerationManager moderation;
    private final DevModeManager devMode;
    private final NicknameManager nicknames;

    private final LegacyComponentSerializer sectionSerializer = LegacyComponentSerializer.legacySection();

    public ChatListener(Plugin plugin, RankManager rankManager, ProfileStore profileStore,
                        ModerationManager moderation, DevModeManager devMode, NicknameManager nicknames) {
        this.plugin = plugin;
        this.rankManager = rankManager;
        this.profileStore = profileStore;
        this.moderation = moderation;
        this.devMode = devMode;
        this.nicknames = nicknames;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncChat(AsyncChatEvent event) {
        if (!plugin.getConfig().getBoolean("chat.enabled", true)) return;

        final var sender = event.getPlayer();

        // One-shot chat input capture (e.g. "Add Friend" GUI prompt)
        String rawInput = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(event.message());
        if (ChatInputService.intercept(sender, rawInput)) {
            event.setCancelled(true);
            return;
        }

        PlayerProfile profile = profileStore.get(sender.getUniqueId());
        final String rank = profile == null || profile.getPrimaryRank() == null
                ? rankManager.getDefaultGroup()
                : profile.getPrimaryRank().toLowerCase(Locale.ROOT);

        // Rank used for chat prefix display (may differ from primaryRank if player chose donor display)
        final String displayRank = profile == null ? rank
                : (profile.getDisplayRank() == null ? rank : profile.getDisplayRank().toLowerCase(Locale.ROOT));

        // Enforcement: mute and slowmode
        boolean bypassDev = devMode != null && devMode.isActive(sender.getUniqueId());
        // Mute enforcement (DevMode bypass only)
        Long muteUntil = profile.getMuteUntil();
        if (!bypassDev && muteUntil != null && muteUntil > System.currentTimeMillis()) {
            sender.sendMessage(com.darkniightz.core.Messages.prefixed("§cYou are muted." + (muteUntil == Long.MAX_VALUE ? "" : " §7(ends in §e" + ((muteUntil - System.currentTimeMillis())/1000) + "s§7)")));
            event.setCancelled(true);
            return;
        }
        // StaffChat: route to staff if toggled
        if (moderation.inStaffChat(sender.getUniqueId())) {
            Component original = event.message();
            String plain = PlainTextComponentSerializer.plainText().serialize(original);
            boolean looksLikeStaffCmd = plain.startsWith("/");
            if (!looksLikeStaffCmd && plugin instanceof JebaitedCore core) {
                var discord = core.getDiscordIntegrationService();
                if (discord != null) {
                    discord.notifyChatRelay(sender.getUniqueId(), sender.getName(), plain, "staff");
                }
            }
            String msg = "§d[Staff] §7" + sender.getName() + ": §f" + plain;
            event.setCancelled(true);
            for (var viewer : sender.getServer().getOnlinePlayers()) {
                // Staff = Helper+ or DevMode active
                PlayerProfile vp = profileStore.get(viewer.getUniqueId());
                String viewerRank = (vp == null || vp.getPrimaryRank() == null) ? rankManager.getDefaultGroup() : vp.getPrimaryRank();
                boolean staff = rankManager.isAtLeast(viewerRank, "helper") || (devMode != null && devMode.isActive(viewer.getUniqueId()));
                if (staff) viewer.sendMessage(msg);
            }
            return;
        }
        // Slowmode (helper+ bypass)
        int slow = moderation.getSlowmodeSeconds();
        if (slow > 0) {
            boolean staffBypass = rankManager.isAtLeast(rank, "helper");
            long remain = moderation.getRemainingSlow(sender.getUniqueId());
            if (!staffBypass && remain > 0) {
                sender.sendMessage(com.darkniightz.core.Messages.prefixed("§eSlowmode: §7wait §e" + (remain/1000 + 1) + "§7s"));
                event.setCancelled(true);
                return;
            }
            moderation.markChatted(sender.getUniqueId());
        }

        // Build styled name (prefix + colored/rainbow name)
        RankStyle style = rankManager.getStyle(displayRank);
        String baseName = nicknames == null ? sender.getName() : nicknames.displayName(sender.getName(), sender.getUniqueId());
        String styledName = ChatUtil.buildStyledName(baseName, style);
        if (profile != null && plugin instanceof JebaitedCore core) {
            styledName = core.decorateStyledNameWithTag(profile, styledName, false);
        }

        // Separator
        String sep = plugin.getConfig().getString("chat.separator", "§7»§r");

        // Message color policy: helper+ aqua, else white
        String minAqua = plugin.getConfig().getString("chat.message_color_policy.min_rank_aqua", "helper");
        String aquaCode = plugin.getConfig().getString("chat.message_color_policy.aqua_code", "§b");
        String defaultCode = plugin.getConfig().getString("chat.message_color_policy.default_code", "§f");
        boolean isAqua = rankManager.isAtLeast(rank, minAqua);

        // Strip any formatting from original message, re-apply policy color
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        // Safety: if somehow a message starting with '/' reaches here, don't track as chat
        boolean looksLikeCommand = plain.startsWith("/");
        String coloredMessage = (isAqua ? aquaCode : defaultCode) + plain;

        if (!looksLikeCommand && plugin instanceof JebaitedCore core) {
            var discord = core.getDiscordIntegrationService();
            if (discord != null) {
                String bridge = "global";
                if (plugin.getConfig().getBoolean("integrations.discord.relay_faction_channel_enabled", false)
                        && sender.hasPermission(PermissionConstants.DISCORD_BRIDGE_FACTION)) {
                    bridge = "faction";
                }
                discord.notifyChatRelay(sender.getUniqueId(), sender.getName(), plain, bridge);
            }
        }

        // Tracking: increment on the main thread to avoid async/cache race misses.
        if (!looksLikeCommand && plugin instanceof org.bukkit.plugin.java.JavaPlugin jp) {
            org.bukkit.Bukkit.getScheduler().runTask(jp, () -> {
                PlayerProfile tracked = profileStore.getOrCreate(sender, rankManager.getDefaultGroup());
                if (tracked != null) {
                    tracked.incMessages();
                    profileStore.saveDeferred(sender.getUniqueId());
                    if (plugin instanceof JebaitedCore core && core.getOverallStatsManager() != null) {
                        core.getOverallStatsManager().increment(com.darkniightz.core.system.OverallStatsManager.TOTAL_MESSAGES, 1);
                    }
                }
            });
        }

        // Prefix from style (+ space if non-empty)
        String prefix = (style.prefix == null || style.prefix.isEmpty()) ? "" : style.prefix + " ";

        Component prefixComp = sectionSerializer.deserialize(prefix);
        Component nameComp = sectionSerializer.deserialize(styledName)
            .hoverEvent(HoverEvent.showText(buildHoverSummary(sender, profile)))
            .clickEvent(ClickEvent.suggestCommand("/msg " + sender.getName() + " "));
        Component sepComp = sectionSerializer.deserialize(" " + sep + " ");
        Component msgComp = sectionSerializer.deserialize(coloredMessage)
            .hoverEvent(HoverEvent.showText(Component.text("Click to message " + sender.getName())))
            .clickEvent(ClickEvent.suggestCommand("/msg " + sender.getName() + " "));
        Component finalComponent = Component.empty().append(prefixComp).append(nameComp).append(sepComp).append(msgComp);

        event.setCancelled(true);
        event.viewers().forEach(audience -> audience.sendMessage(finalComponent));
    }

    private Component buildHoverSummary(Player sender, PlayerProfile profile) {
        if (profile == null) {
            return Component.text()
                    .append(Component.text("§7Player§8: §f" + sender.getName() + "\n"))
                    .append(Component.text("§7Rank§8: §f" + rankManager.getDefaultGroup() + "\n"))
                    .append(Component.text("§7Profile§8: §eloading"))
                    .build();
        }

        String rank = profile.getPrimaryRank() == null ? rankManager.getDefaultGroup() : profile.getPrimaryRank();
        String firstJoined = profile.getFirstJoined() <= 0
                ? "Unknown"
                : DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(profile.getFirstJoined()));
        String tag = profile.getActiveTag() == null || profile.getActiveTag().isBlank() ? "None" : profile.getActiveTag();
        long minutes = Math.max(0L, profile.getPlaytimeMs() / 60000L);
        long hours = minutes / 60L;
        long remMinutes = minutes % 60L;

        return Component.text()
                .append(Component.text("§7Player§8: §f" + sender.getName() + "\n"))
                .append(Component.text("§7Rank§8: §f" + rank + "\n"))
                .append(Component.text("§7First Joined§8: §f" + firstJoined + "\n"))
                .append(Component.text("§7Tag§8: §f" + tag + "\n"))
                .append(Component.text("§7Playtime§8: §f" + hours + "h " + remMinutes + "m\n"))
                .append(Component.text("§7Kills/Deaths§8: §f" + profile.getKills() + "/" + profile.getDeaths()))
                .build();
    }


}
