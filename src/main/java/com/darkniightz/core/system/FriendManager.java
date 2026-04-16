package com.darkniightz.core.system;

import com.darkniightz.core.Messages;
import com.darkniightz.core.chat.ChatUtil;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.main.database.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Orchestrates friend requests and friendships.
 *
 * <p>All DB writes are dispatched asynchronously.
 * Cache is populated on join and cleared on quit via {@link FriendListener}.
 */
public class FriendManager {

    private final Plugin plugin;
    private final FriendDAO dao;
    private final FriendCache cache;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final Logger log;

    public FriendManager(Plugin plugin, DatabaseManager db, ProfileStore profiles, RankManager ranks) {
        this.plugin = plugin;
        this.dao = new FriendDAO(db, plugin.getLogger());
        this.cache = new FriendCache();
        this.profiles = profiles;
        this.ranks = ranks;
        this.log = plugin.getLogger();
    }

    // ── Cache lifecycle ───────────────────────────────────────────────────────

    /** Called async on player join. */
    public void loadPlayer(UUID uuid) {
        Set<UUID> friends = dao.loadFriends(uuid);
        Set<UUID> inbound = dao.loadInboundRequests(uuid);
        Bukkit.getScheduler().runTask(plugin, () -> {
            cache.setFriends(uuid, friends);
            cache.setInbound(uuid, inbound);
        });
    }

    /** Called on player quit. */
    public void unloadPlayer(UUID uuid) {
        cache.unload(uuid);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Send a friend request from {@code sender} to {@code target}. */
    public void sendRequest(Player sender, OfflinePlayer target) {
        UUID senderUuid = sender.getUniqueId();
        UUID targetUuid = target.getUniqueId();

        if (targetUuid == null) {
            sender.sendMessage(Messages.prefixed("\u00a7cPlayer not found."));
            return;
        }
        if (senderUuid.equals(targetUuid)) {
            sender.sendMessage(Messages.prefixed("\u00a7cYou can't friend yourself."));
            return;
        }
        if (cache.isFriend(senderUuid, targetUuid)) {
            sender.sendMessage(Messages.prefixed("\u00a7c" + target.getName() + " \u00a77is already your friend."));
            return;
        }
        int senderLimit = getFriendLimit(senderUuid);
        if (cache.getFriends(senderUuid).size() >= senderLimit) {
            sender.sendMessage(Messages.prefixed("\u00a7cYou've reached your friend limit (\u00a7f" + senderLimit + "\u00a7c)."));
            return;
        }
        // If target already sent us a request, auto-accept
        if (cache.hasInbound(senderUuid, targetUuid)) {
            acceptRequest(sender, target);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean inserted = dao.insertRequest(senderUuid, targetUuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!inserted) {
                    sender.sendMessage(Messages.prefixed("\u00a77Request already pending."));
                    return;
                }
                cache.addInbound(targetUuid, senderUuid);
                sender.sendMessage(Messages.prefixed("\u00a7aFriend request sent to " + coloredName(targetUuid, target.getName()) + "\u00a7a."));
                Player onlineTarget = Bukkit.getPlayer(targetUuid);
                if (onlineTarget != null) {
                    String senderColored = coloredName(senderUuid, sender.getName());
                    Component msg = legacy(Messages.prefix() + " " + senderColored + " \u00a77sent you a friend request.  ")
                            .append(legacy("\u00a7e[Accept]")
                                    .clickEvent(ClickEvent.runCommand("/friend accept " + sender.getName()))
                                    .hoverEvent(HoverEvent.showText(legacy("\u00a77Click to accept"))))
                            .append(legacy(" \u00a78/ "))
                            .append(legacy("\u00a7c[Deny]")
                                    .clickEvent(ClickEvent.runCommand("/friend deny " + sender.getName()))
                                    .hoverEvent(HoverEvent.showText(legacy("\u00a77Click to deny"))));
                    onlineTarget.sendMessage(msg);
                }
            });
        });
    }

    /** Accept a pending request. The {@code requester} is the one who sent the original request. */
    public void acceptRequest(Player acceptor, OfflinePlayer requester) {
        UUID acceptorUuid = acceptor.getUniqueId();
        UUID requesterUuid = requester.getUniqueId();

        if (requesterUuid == null) {
            acceptor.sendMessage(Messages.prefixed("\u00a7cPlayer not found."));
            return;
        }
        if (!cache.hasInbound(acceptorUuid, requesterUuid)) {
            acceptor.sendMessage(Messages.prefixed("\u00a7cNo pending request from \u00a7f" + requester.getName() + "\u00a7c."));
            return;
        }
        int acceptorLimit = getFriendLimit(acceptorUuid);
        if (cache.getFriends(acceptorUuid).size() >= acceptorLimit) {
            acceptor.sendMessage(Messages.prefixed("\u00a7cYou've reached your friend limit (\u00a7f" + acceptorLimit + "\u00a7c)."));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            dao.deleteRequest(requesterUuid, acceptorUuid);
            dao.insertFriendship(acceptorUuid, requesterUuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                cache.removeInbound(acceptorUuid, requesterUuid);
                cache.addFriend(acceptorUuid, requesterUuid);
                acceptor.sendMessage(Messages.prefixed("\u00a7aYou are now friends with " + coloredName(requesterUuid, requester.getName()) + "\u00a7a!"));
                Player onlineRequester = Bukkit.getPlayer(requesterUuid);
                if (onlineRequester != null) {
                    Component msg = legacy(Messages.prefix() + " " + coloredName(acceptorUuid, acceptor.getName())
                            + " \u00a7aaccepted your friend request!  ")
                            .append(legacy("\u00a7e[View Friends]")
                                    .clickEvent(ClickEvent.runCommand("/friends"))
                                    .hoverEvent(HoverEvent.showText(legacy("\u00a77Open your friends list"))));
                    onlineRequester.sendMessage(msg);
                }
            });
        });
    }

    /** Deny a pending request from {@code requester}. */
    public void denyRequest(Player denier, OfflinePlayer requester) {
        UUID denierUuid = denier.getUniqueId();
        UUID requesterUuid = requester.getUniqueId();

        if (requesterUuid == null) {
            denier.sendMessage(Messages.prefixed("\u00a7cPlayer not found."));
            return;
        }
        if (!cache.hasInbound(denierUuid, requesterUuid)) {
            denier.sendMessage(Messages.prefixed("\u00a7cNo pending request from \u00a7f" + requester.getName() + "\u00a7c."));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            dao.deleteRequest(requesterUuid, denierUuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                cache.removeInbound(denierUuid, requesterUuid);
                denier.sendMessage(Messages.prefixed("\u00a77Friend request from " + coloredName(requesterUuid, requester.getName()) + " \u00a77denied."));
            });
        });
    }

    /** Remove a friend. */
    public void removeFriend(Player remover, OfflinePlayer target) {
        UUID removerUuid = remover.getUniqueId();
        UUID targetUuid = target.getUniqueId();

        if (targetUuid == null) {
            remover.sendMessage(Messages.prefixed("\u00a7cPlayer not found."));
            return;
        }
        if (!cache.isFriend(removerUuid, targetUuid)) {
            remover.sendMessage(Messages.prefixed("\u00a7f" + target.getName() + " \u00a77is not in your friend list."));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            dao.deleteFriendship(removerUuid, targetUuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                cache.removeFriend(removerUuid, targetUuid);
                remover.sendMessage(Messages.prefixed("\u00a77Removed " + coloredName(targetUuid, target.getName()) + " \u00a77from your friends."));
                Player onlineTarget = Bukkit.getPlayer(targetUuid);
                if (onlineTarget != null) {
                    onlineTarget.sendMessage(Messages.prefixed(coloredName(removerUuid, remover.getName()) + " \u00a77removed you from their friends."));
                }
            });
        });
    }

    // ── DAO passthrough (for GUI use) ─────────────────────────────────────────

    /**
     * Loads friendship stats (created_at, xp_together, kills_together) for a pair.
     * Blocks the calling thread — must be called from an async task.
     */
    public FriendDAO.FriendshipStats loadStats(UUID a, UUID b) {
        return dao.loadFriendshipStats(a, b);
    }

    /** Adds XP together for a friendship pair (async-safe, must call from async). */
    public void addXpTogether(UUID a, UUID b, long xp) {
        dao.addXpTogether(a, b, xp);
    }

    /** Records a PvP kill between two friends (async-safe, must call from async). */
    public void addKillTogether(UUID killer, UUID victim) {
        dao.addKillTogether(killer, victim);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public FriendCache getCache() {
        return cache;
    }

    /** Snapshot of friend UUIDs (from cache, may be empty if not yet loaded). */
    public Set<UUID> getFriends(UUID player) {
        return cache.getFriends(player);
    }

    /** Snapshot of inbound request UUIDs. */
    public Set<UUID> getInboundRequests(UUID player) {
        return cache.getInbound(player);
    }

    public boolean isFriend(UUID a, UUID b) {
        return cache.isFriend(a, b);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the friend cap for the given player based on their primary rank.
     * pleb=10, gold=25, diamond=50, legend=100, grandmaster+=250.
     */
    public int getFriendLimit(UUID uuid) {
        PlayerProfile p = profiles.get(uuid);
        String rank = (p != null && p.getPrimaryRank() != null)
                ? p.getPrimaryRank().toLowerCase(java.util.Locale.ROOT)
                : "pleb";
        return switch (rank) {
            case "grandmaster" -> 250;
            case "legend"      -> 100;
            case "diamond"     -> 50;
            case "gold"        -> 25;
            case "owner", "developer", "admin", "srmod", "moderator", "helper", "vip", "builder" -> 250;
            default            -> 10; // pleb
        };
    }

    /**
     * Returns a rank-colored legacy string for a player name.
     * Falls back to white if the profile is not cached.
     */
    public String coloredName(UUID uuid, String fallbackName) {
        String name = fallbackName != null ? fallbackName : uuid.toString().substring(0, 8);
        PlayerProfile profile = profiles.get(uuid);
        if (profile == null || ranks == null) return "\u00a7f" + name;
        RankManager.RankStyle style = ranks.getStyle(profile.getDisplayRank());
        return ChatUtil.buildStyledName(name, style);
    }

    /** Wraps a legacy-formatted string into an Adventure Component. */
    private static Component legacy(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }
}
