package com.darkniightz.core.chat;

import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.moderation.ModerationManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.ranks.RankManager.RankStyle;
import io.papermc.paper.event.player.AsyncChatEvent;
import com.darkniightz.core.chat.ChatUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

public class ChatListener implements Listener {

    private final Plugin plugin;
    private final RankManager rankManager;
    private final ProfileStore profileStore;
    private final ModerationManager moderation;
    private final DevModeManager devMode;

    private final LegacyComponentSerializer sectionSerializer = LegacyComponentSerializer.legacySection();

    public ChatListener(Plugin plugin, RankManager rankManager, ProfileStore profileStore,
                        ModerationManager moderation, DevModeManager devMode) {
        this.plugin = plugin;
        this.rankManager = rankManager;
        this.profileStore = profileStore;
        this.moderation = moderation;
        this.devMode = devMode;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncChat(AsyncChatEvent event) {
        if (!plugin.getConfig().getBoolean("chat.enabled", true)) return;

        final var sender = event.getPlayer();
        final PlayerProfile profile = profileStore.getOrCreate(sender, rankManager.getDefaultGroup());
        final String rank = profile.getPrimaryRank() == null ? rankManager.getDefaultGroup() : profile.getPrimaryRank().toLowerCase(Locale.ROOT);

        // Enforcement: mute and slowmode
        boolean bypassDev = devMode != null && devMode.isActive(sender.getUniqueId());
        // Mute enforcement (DevMode bypass only)
        Long muteUntil = profile.getMuteUntil();
        if (!bypassDev && muteUntil != null && muteUntil > System.currentTimeMillis()) {
            sender.sendMessage("§cYou are muted." + (muteUntil == Long.MAX_VALUE ? "" : " §7(ends in §e" + ((muteUntil - System.currentTimeMillis())/1000) + "s§7)"));
            event.setCancelled(true);
            return;
        }
        // StaffChat: route to staff if toggled
        if (moderation.inStaffChat(sender.getUniqueId())) {
            Component original = event.message();
            String plain = PlainTextComponentSerializer.plainText().serialize(original);
            String msg = "§d[Staff] §7" + sender.getName() + ": §f" + plain;
            event.setCancelled(true);
            for (var viewer : sender.getServer().getOnlinePlayers()) {
                // Staff = Helper+ or DevMode active
                var vp = profileStore.getOrCreate(viewer, rankManager.getDefaultGroup());
                boolean staff = rankManager.isAtLeast(vp.getPrimaryRank(), "helper") || (devMode != null && devMode.isActive(viewer.getUniqueId()));
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
                sender.sendMessage("§eSlowmode: §7wait §e" + (remain/1000 + 1) + "§7s");
                event.setCancelled(true);
                return;
            }
            moderation.markChatted(sender.getUniqueId());
        }

        // Build styled name (prefix + colored/rainbow name)
        RankStyle style = rankManager.getStyle(rank);
        String styledName = ChatUtil.buildStyledName(sender.getName(), style);

        // Separator
        String sep = plugin.getConfig().getString("chat.separator", "§7»§r");

        // Message color policy: helper+ aqua, else white
        String minAqua = plugin.getConfig().getString("chat.message_color_policy.min_rank_aqua", "helper");
        String aquaCode = plugin.getConfig().getString("chat.message_color_policy.aqua_code", "§b");
        String defaultCode = plugin.getConfig().getString("chat.message_color_policy.default_code", "§f");
        boolean isAqua = rankManager.isAtLeast(rank, minAqua);

        // Strip any formatting from original message, re-apply policy color
        Component original = event.message();
        String plain = PlainTextComponentSerializer.plainText().serialize(original);
        // Safety: if somehow a message starting with '/' reaches here, don't track as chat
        boolean looksLikeCommand = plain.startsWith("/");
        String coloredMessage = (isAqua ? aquaCode : defaultCode) + plain;

        // Tracking: increment message count and persist
        if (!looksLikeCommand) {
            profile.incMessages();
            profileStore.save(sender.getUniqueId());
        }

        // Prefix from style (+ space if non-empty)
        String prefix = (style.prefix == null || style.prefix.isEmpty()) ? "" : style.prefix + " ";

        // Final legacy string based on format
        String format = plugin.getConfig().getString("chat.format", "{prefix}{styled_name} {separator} {message_colored}");
        String legacy = format
                .replace("{prefix}", prefix)
                .replace("{styled_name}", styledName)
                .replace("{separator}", sep)
                .replace("{message_colored}", coloredMessage);

        Component finalComponent = sectionSerializer.deserialize(legacy);

        // Cancel default handling and broadcast our formatted message to current viewers
        event.setCancelled(true);
        event.viewers().forEach(audience -> audience.sendMessage(finalComponent));
    }


}
