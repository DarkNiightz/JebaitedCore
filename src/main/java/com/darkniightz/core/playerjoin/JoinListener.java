package com.darkniightz.core.playerjoin;

import com.darkniightz.core.chat.ChatUtil;
import com.darkniightz.core.dev.DebugFeedManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.ranks.RankManager.RankStyle;
import com.darkniightz.main.JebaitedCore;
import com.darkniightz.main.PlayerProfileDAO;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public class JoinListener implements Listener {

    private final Plugin plugin;
    private final RankManager rankManager;
    private final ProfileStore profileStore;
    private final PlayerProfileDAO playerProfileDAO;
    private final DebugFeedManager feed;

    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();

    public JoinListener(Plugin plugin, RankManager rankManager, ProfileStore profileStore, PlayerProfileDAO playerProfileDAO, DebugFeedManager feed) {
        this.plugin = plugin;
        this.rankManager = rankManager;
        this.profileStore = profileStore;
        this.playerProfileDAO = playerProfileDAO;
        this.feed = feed;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var p = event.getPlayer();
        boolean joinLeaveEnabled = plugin.getConfig().getBoolean("join_leave.enabled", true);
        String joinFmt = plugin.getConfig().getString("join_leave.join_format", "§9[§a+§9] {styled_name}");
        // Suppress default join message now \u2014 we broadcast our own after async load
        if (joinLeaveEnabled) event.joinMessage(null);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Profile load \u2014 may hit DB on cache miss
                PlayerProfile profile = profileStore.get(p.getUniqueId());
                if (profile == null) {
                    profile = profileStore.getOrCreate(p, rankManager.getDefaultGroup());
                }
                if (profile == null) return;

                if (profile.getPrimaryRank() == null || profile.getPrimaryRank().isBlank()) {
                    profile.setPrimaryRank(rankManager.getDefaultGroup());
                    profileStore.saveDeferred(p.getUniqueId());
                }

                // Rank-request check (DB reads/writes \u2014 safe on async)
                checkAndApplyRankRequest(p, profile);

                // Nickname lookup (DB read)
                String rank = profile.getPrimaryRank() == null ? rankManager.getDefaultGroup() : profile.getPrimaryRank().toLowerCase(Locale.ROOT);
                RankStyle style = rankManager.getStyle(rank);
                String baseName = plugin instanceof JebaitedCore core && core.getNicknameManager() != null
                        ? core.getNicknameManager().displayName(p.getName(), p.getUniqueId())
                        : p.getName();
                String styledName = ChatUtil.buildStyledName(baseName, style);

                String joinMsg = null;
                if (joinLeaveEnabled) {
                    String msg = joinFmt.replace("{styled_name}", styledName);
                    joinMsg = ChatColor.translateAlternateColorCodes('&', msg);
                }

                final PlayerProfile fp = profile;
                final String finalRank = rank;
                final String finalStyledName = styledName;
                final String finalJoinMsg = joinMsg;

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!p.isOnline()) return;
                    if (finalJoinMsg != null) broadcastJoinLeaveMessage(finalJoinMsg);
                    notifyStaffIfWatchlisted(p);
                    notifyVersionUpdateIfNeeded(p, fp);
                    if (plugin instanceof JebaitedCore core) {
                        Bukkit.getScheduler().runTask(plugin, () -> core.refreshPlayerPresentation(p));
                    }
                    sendMotdLines(p, finalRank, finalStyledName);
                    if (feed != null) {
                        feed.recordJoin(p, "Player joined", List.of("§7Rank: §f" + finalRank, "§7Styled name: §f" + finalStyledName));
                    }
                });
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Join pipeline error for " + p.getName(), ex);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var p = event.getPlayer();
        boolean joinLeaveEnabled = plugin.getConfig().getBoolean("join_leave.enabled", true);
        String quitFmt = plugin.getConfig().getString("join_leave.quit_format", "§9[§c-§9] {styled_name}");
        if (joinLeaveEnabled) event.quitMessage(null);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PlayerProfile profile = profileStore.get(p.getUniqueId());
                if (profile == null) {
                    profile = profileStore.getOrCreate(p, rankManager.getDefaultGroup());
                }
                if (profile == null) return;

                final PlayerProfile fp = profile;
                String quitMsg = null;
                if (joinLeaveEnabled) {
                    String rank = profile.getPrimaryRank() == null ? rankManager.getDefaultGroup() : profile.getPrimaryRank().toLowerCase(Locale.ROOT);
                    RankStyle style = rankManager.getStyle(rank);
                    String baseName = plugin instanceof JebaitedCore core && core.getNicknameManager() != null
                            ? core.getNicknameManager().displayName(p.getName(), p.getUniqueId())
                            : p.getName();
                    String styledName = ChatUtil.buildStyledName(baseName, style);
                    String msg = quitFmt.replace("{styled_name}", styledName);
                    quitMsg = ChatColor.translateAlternateColorCodes('&', msg);
                }

                final String finalQuitMsg = quitMsg;
                final String finalRank = fp.getPrimaryRank() == null ? rankManager.getDefaultGroup() : fp.getPrimaryRank();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (finalQuitMsg != null) broadcastJoinLeaveMessage(finalQuitMsg);
                    if (feed != null) {
                        feed.recordJoin(p, "Player left", List.of("§7Rank: §f" + finalRank));
                    }
                });
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Quit pipeline error for " + p.getName(), ex);
            }
        });
    }

    /** Called from async thread \u2014 DB reads/writes are safe, Bukkit messages scheduled back to main. */
    private void checkAndApplyRankRequest(org.bukkit.entity.Player p, PlayerProfile profile) {
        if (!(plugin instanceof JebaitedCore core) || playerProfileDAO == null || !core.getDatabaseManager().isEnabled()) {
            return;
        }

        var pending = playerProfileDAO.findApprovedPendingRankRequest(p.getUniqueId(), p.getName());
        if (pending == null) {
            return;
        }

        String requestedRank = pending.requestedRank() == null ? null : pending.requestedRank().toLowerCase(Locale.ROOT);
        if (requestedRank == null || !rankManager.getLadder().contains(requestedRank)) {
            return;
        }

        if (!requestedRank.equalsIgnoreCase(profile.getPrimaryRank())) {
            profile.setPrimaryRank(requestedRank);
            profileStore.save(p.getUniqueId());
        }

        playerProfileDAO.markRankRequestApplied(pending.id());
        final String appliedRank = requestedRank;
        final long pendingId = pending.id();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) {
                p.sendMessage(com.darkniightz.core.Messages.prefixed("§aYour approved rank request has been applied: §b" + appliedRank + "§a."));
            }
            if (feed != null) {
                feed.recordJoin(p, "Approved rank request applied", List.of("§7Rank: §f" + appliedRank, "§7Request ID: §f" + pendingId));
            }
        });
    }

    private void notifyStaffIfWatchlisted(org.bukkit.entity.Player player) {
        if (playerProfileDAO == null) {
            return;
        }
        int matches = playerProfileDAO.countWatchlistEntries(player.getUniqueId(), player.getName());
        if (matches <= 0) {
            return;
        }
        for (org.bukkit.entity.Player viewer : Bukkit.getOnlinePlayers()) {
            PlayerProfile viewerProfile = profileStore.get(viewer.getUniqueId());
            String viewerRank = viewerProfile == null || viewerProfile.getPrimaryRank() == null
                    ? rankManager.getDefaultGroup()
                    : viewerProfile.getPrimaryRank();
            boolean staff = rankManager.isAtLeast(viewerRank, "helper")
                    || (plugin instanceof JebaitedCore core && core.getDevModeManager() != null && core.getDevModeManager().isActive(viewer.getUniqueId()));
            if (staff) {
                viewer.sendMessage(com.darkniightz.core.Messages.prefixed("§c[WATCH] §f" + player.getName() + " §7has connected. They are on the staff watchlist."));
            }
        }
    }

    private void notifyVersionUpdateIfNeeded(org.bukkit.entity.Player player, PlayerProfile profile) {
        if (!(plugin instanceof JebaitedCore core) || core.getMinecraftVersionMonitor() == null || !core.getMinecraftVersionMonitor().isOutdated()) {
            return;
        }
        if (profile == null) {
            return;
        }
        String rank = profile.getPrimaryRank() == null ? rankManager.getDefaultGroup() : profile.getPrimaryRank();
        boolean shouldNotify = player.isOp() || rankManager.isAtLeast(rank, plugin.getConfig().getString("minecraft_support.version_alerts.notify_min_rank", "developer"));
        if (!shouldNotify) {
            return;
        }
        player.sendMessage(com.darkniightz.core.Messages.prefixed("§eServer runtime is on §f" + core.getMinecraftVersionMonitor().getCurrentServerVersion()
                + "§e while the latest known release is §f" + core.getMinecraftVersionMonitor().getLatestKnownVersion() + "§e."));
    }

    private void broadcastJoinLeaveMessage(String msg) {
        var component = legacy.deserialize(msg);
        for (org.bukkit.entity.Player viewer : Bukkit.getOnlinePlayers()) {
            PlayerProfile viewerProfile = profileStore.get(viewer.getUniqueId());
            if (viewerProfile != null && !viewerProfile.isJoinLeaveMessagesEnabled()) {
                continue;
            }
            viewer.sendMessage(component);
        }
    }

    private void sendMotdLines(org.bukkit.entity.Player player, String rank, String styledName) {
        if (!plugin.getConfig().getBoolean("motd.enabled", true)) {
            return;
        }
        List<String> lines = plugin.getConfig().getStringList("motd.player-join");
        if (lines == null || lines.isEmpty()) {
            return;
        }

        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        for (String raw : lines) {
            String rendered = raw
                    .replace("{player}", player.getName())
                    .replace("{rank}", rank)
                    .replace("{styled_name}", styledName)
                    .replace("{online}", Integer.toString(online))
                    .replace("{max}", Integer.toString(max));
            player.sendMessage(legacy.deserialize(ChatColor.translateAlternateColorCodes('&', rendered)));
        }
    }
}
