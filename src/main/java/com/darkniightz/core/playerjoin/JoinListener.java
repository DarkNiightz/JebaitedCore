package com.darkniightz.core.playerjoin;

import com.darkniightz.core.chat.ChatUtil;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.ranks.RankManager.RankStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

public class JoinListener implements Listener {

    private final Plugin plugin;
    private final RankManager rankManager;
    private final ProfileStore profileStore;

    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();

    public JoinListener(Plugin plugin, RankManager rankManager, ProfileStore profileStore) {
        this.plugin = plugin;
        this.rankManager = rankManager;
        this.profileStore = profileStore;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var p = event.getPlayer();
        var profile = profileStore.getOrCreate(p, rankManager.getDefaultGroup());
        // Ensure rank default is applied when missing
        if (profile.getPrimaryRank() == null || profile.getPrimaryRank().isBlank()) {
            profile.setPrimaryRank(rankManager.getDefaultGroup());
            profileStore.save(p.getUniqueId());
        }

        if (plugin.getConfig().getBoolean("join_leave.enabled", true)) {
            String rank = profile.getPrimaryRank() == null ? rankManager.getDefaultGroup() : profile.getPrimaryRank().toLowerCase(Locale.ROOT);
            RankStyle style = rankManager.getStyle(rank);
            String styledName = ChatUtil.buildStyledName(p.getName(), style);
            String fmt = plugin.getConfig().getString("join_leave.join_format", "§9[§a+§9] {styled_name}");
            String msg = fmt.replace("{styled_name}", styledName);
            event.joinMessage(legacy.deserialize(msg));

            // Also apply styled prefix + name to the player list (tab list)
            String prefix = (style.prefix == null || style.prefix.isEmpty()) ? "" : style.prefix + " ";
            String tab = prefix + styledName;
            p.playerListName(legacy.deserialize(tab));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var p = event.getPlayer();
        var profile = profileStore.getOrCreate(p, rankManager.getDefaultGroup());
        if (plugin.getConfig().getBoolean("join_leave.enabled", true)) {
            String rank = profile.getPrimaryRank() == null ? rankManager.getDefaultGroup() : profile.getPrimaryRank().toLowerCase(Locale.ROOT);
            RankStyle style = rankManager.getStyle(rank);
            String styledName = ChatUtil.buildStyledName(p.getName(), style);
            String fmt = plugin.getConfig().getString("join_leave.quit_format", "§9[§c-§9] {styled_name}");
            String msg = fmt.replace("{styled_name}", styledName);
            event.quitMessage(legacy.deserialize(msg));
        }
    }
}
