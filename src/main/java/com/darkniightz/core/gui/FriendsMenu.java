package com.darkniightz.core.gui;

import com.darkniightz.core.chat.ChatInputService;
import com.darkniightz.core.Messages;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.system.FriendDAO;
import com.darkniightz.core.system.FriendManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 54-slot friends list menu with rich per-friend stats.
 *
 * <p>Layout: rows 1 and 6 are borders. Rows 2-5, columns 2-8 = friend heads (28 per page).
 * Slot 45: prev page. Slot 49: info/pending item. Slot 53: next page.
 *
 * <p>Left-click a friend head → open {@link FriendInfoMenu}.
 * Right-click a friend head → sends chat removal instruction.
 */
public class FriendsMenu extends BaseMenu {

    private static final int PAGE_SIZE = 28;
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final FriendManager friendManager;
    private final ProfileStore profiles;
    private final Map<UUID, FriendDAO.FriendshipStats> statsCache = new ConcurrentHashMap<>();
    private int page = 0;
    private UUID viewerUuid;

    public FriendsMenu(Plugin plugin, FriendManager friendManager, ProfileStore profiles) {
        super(plugin, "\u00a78Friends", 54);
        this.friendManager = friendManager;
        this.profiles = profiles;
    }

    @Override
    protected void populate(Player viewer) {
        this.viewerUuid = viewer.getUniqueId();
        inventory.clear();
        renderBorders();
        renderFriends(viewer);
        renderNavigation(viewer);
        loadStatsAsync(viewer);
    }

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
        if (slot == 45 && page > 0) {
            page--;
            inventory.clear();
            populate(who);
            return true;
        }
        // Add Friend
        if (slot == 47) {
            who.closeInventory();
            ChatInputService.prompt(who,
                    "\u00a7e  Type the player name to add as a friend,\n\u00a7e  or type \u00a7fcancel\u00a7e to cancel:",
                    plugin,
                    input -> {
                        if (input.equalsIgnoreCase("cancel")) {
                            who.sendMessage(Messages.prefixed("\u00a77Cancelled."));
                            return;
                        }
                        org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayerIfCached(input);
                        if (target == null) target = org.bukkit.Bukkit.getPlayerExact(input);
                        if (target == null) {
                            who.sendMessage(Messages.prefixed("\u00a7cPlayer not found: \u00a7f" + input));
                            return;
                        }
                        friendManager.sendRequest(who, target);
                    });
            return true;
        }
        if (slot == 53) {
            Set<UUID> friends = friendManager.getFriends(who.getUniqueId());
            if ((page + 1) * PAGE_SIZE < friends.size()) {
                page++;
                inventory.clear();
                populate(who);
            }
            return true;
        }

        ItemStack clicked = inventory.getItem(slot);
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return true;
        SkullMeta meta = (SkullMeta) clicked.getItemMeta();
        if (meta == null || meta.getOwningPlayer() == null) return true;

        OfflinePlayer friendOp = meta.getOwningPlayer();
        UUID friendUuid = friendOp.getUniqueId();

        if (rightClick) {
            String name = friendOp.getName() != null ? friendOp.getName() : friendUuid.toString().substring(0, 8);
            who.closeInventory();
            who.sendMessage(Messages.prefixed("\u00a7cTo remove \u00a7f" + name + "\u00a7c type: \u00a7f/friend remove " + name));
            return true;
        }

        // Left-click → info menu
        FriendDAO.FriendshipStats stats = statsCache.get(friendUuid);
        new FriendInfoMenu(plugin, friendManager, profiles, who, friendUuid, friendOp.getName(), stats).open(who);
        return true;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private void renderBorders() {
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int s = 0; s < 9; s++) inventory.setItem(s, filler);
        for (int s = 45; s < 54; s++) inventory.setItem(s, filler);
        for (int row = 1; row <= 4; row++) {
            inventory.setItem(row * 9, filler);
            inventory.setItem(row * 9 + 8, filler);
        }
    }

    private void renderFriends(Player viewer) {
        List<UUID> friendList = new ArrayList<>(friendManager.getFriends(viewer.getUniqueId()));
        int start = page * PAGE_SIZE;

        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = start + i;
            if (idx >= friendList.size()) break;
            UUID friendUuid = friendList.get(idx);
            FriendDAO.FriendshipStats stats = statsCache.get(friendUuid);
            inventory.setItem(CONTENT_SLOTS[i], buildFriendHead(viewer.getUniqueId(), friendUuid, stats));
        }

        if (friendList.isEmpty()) {
            inventory.setItem(22, new ItemBuilder(Material.BARRIER)
                    .name("\u00a77No friends yet.")
                    .lore(List.of("\u00a78Use \u00a7f/friend add <player> \u00a78to add someone."))
                    .build());
        }
    }

    private void renderNavigation(Player viewer) {
        Set<UUID> friends = friendManager.getFriends(viewer.getUniqueId());
        Set<UUID> inbound = friendManager.getInboundRequests(viewer.getUniqueId());
        int totalPages = Math.max(1, (int) Math.ceil((double) friends.size() / PAGE_SIZE));

        if (page > 0) {
            inventory.setItem(45, new ItemBuilder(Material.ARROW)
                    .name("\u00a77Previous Page")
                    .lore(List.of("\u00a78Page " + page + " / " + totalPages))
                    .build());
        }
        if ((page + 1) * PAGE_SIZE < friends.size()) {
            inventory.setItem(53, new ItemBuilder(Material.ARROW)
                    .name("\u00a77Next Page")
                    .lore(List.of("\u00a78Page " + (page + 2) + " / " + totalPages))
                    .build());
        }

        // Slot 49 — summary + pending
        int friendLimit = friendManager.getFriendLimit(viewer.getUniqueId());
        List<String> lore = new ArrayList<>();
        lore.add("\u00a77Friends: \u00a7a" + friends.size() + " \u00a78/ \u00a77" + friendLimit);
        if (!inbound.isEmpty()) {
            lore.add("\u00a7ePending: \u00a7f" + inbound.size());
            lore.add(" ");
            for (UUID sender : inbound) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(sender);
                String name = op.getName() != null ? op.getName() : sender.toString().substring(0, 8);
                String colored = friendManager.coloredName(sender, name);
                lore.add("\u00a78\u2022 " + colored + " \u00a78\u2014 \u00a7f/friend accept " + name);
            }
        }
        inventory.setItem(49, new ItemBuilder(Material.NETHER_STAR)
                .name("\u00a7eFriend List")
                .lore(lore)
                .build());

        // Slot 47 — Add Friend button (always shown)
        inventory.setItem(47, new ItemBuilder(Material.GREEN_DYE)
                .name("\u00a7a\u00a7l+ Add Friend")
                .lore(List.of("\u00a77Click to add a new friend.",
                              "\u00a78Type their name in chat."))
                .build());
    }

    private ItemStack buildFriendHead(UUID viewerUuid, UUID friendUuid, FriendDAO.FriendshipStats stats) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        OfflinePlayer op = Bukkit.getOfflinePlayer(friendUuid);
        meta.setOwningPlayer(op);

        String name = op.getName() != null ? op.getName() : friendUuid.toString().substring(0, 8);
        boolean online = op.isOnline();
        String coloredName = friendManager.coloredName(friendUuid, name);

        meta.setDisplayName(coloredName);

        List<String> lore = new ArrayList<>();
        lore.add(online ? "\u00a7a\u2b24 Online" : "\u00a78\u2b24 Offline");

        if (stats != null) {
            lore.add("\u00a78\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");
            lore.add("\u00a77Since:    \u00a7f" + formatDuration(System.currentTimeMillis() - stats.createdAt()));
            lore.add("\u00a77XP:       \u00a7e" + formatBigNum(stats.xpTogether()));
            lore.add("\u00a77PvP:      \u00a7c" + stats.killsTogether() + " \u00a77kills together");
            PlayerProfile fp = profiles.get(friendUuid);
            if (fp != null) {
                lore.add("\u00a78\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");
                lore.add("\u00a77K/D:      \u00a7c" + fp.getKills() + " \u00a78/ \u00a77" + fp.getDeaths());
                lore.add("\u00a77Playtime:  \u00a7a" + formatPlaytime(fp.getPlaytimeMs()));
            }
            lore.add("\u00a78\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");
        } else {
            lore.add("\u00a78Loading...");
        }

        lore.add("\u00a7aLeft-click \u00a77for details");
        lore.add("\u00a7cRight-click \u00a77to remove");

        meta.setLore(lore);
        skull.setItemMeta(meta);
        return skull;
    }

    // ── Async stat loading ────────────────────────────────────────────────────

    private void loadStatsAsync(Player viewer) {
        List<UUID> friendList = new ArrayList<>(friendManager.getFriends(viewer.getUniqueId()));
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, friendList.size());
        if (start >= end) return;
        List<UUID> pageUuids = friendList.subList(start, end);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<UUID, FriendDAO.FriendshipStats> loaded = new HashMap<>();
            for (UUID friendUuid : pageUuids) {
                if (!statsCache.containsKey(friendUuid)) {
                    FriendDAO.FriendshipStats s = friendManager.loadStats(viewer.getUniqueId(), friendUuid);
                    if (s != null) loaded.put(friendUuid, s);
                }
            }
            if (loaded.isEmpty()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (viewerUuid == null) return;
                Player v = Bukkit.getPlayer(viewerUuid);
                if (v == null || !v.isOnline()) return;
                statsCache.putAll(loaded);
                renderFriends(v);
            });
        });
    }

    // ── Formatters (package-visible so FriendInfoMenu can reuse) ─────────────

    static String formatDuration(long ms) {
        if (ms < 0) ms = 0;
        long days = ms / 86_400_000L;
        long hours = (ms % 86_400_000L) / 3_600_000L;
        if (days > 0) return days + "d " + hours + "h";
        long mins = (ms % 3_600_000L) / 60_000L;
        if (hours > 0) return hours + "h " + mins + "m";
        return Math.max(mins, 1) + "m";
    }

    static String formatPlaytime(long ms) {
        long hours = ms / 3_600_000L;
        long mins = (ms % 3_600_000L) / 60_000L;
        if (hours > 0) return hours + "h " + mins + "m";
        return Math.max(mins, 1) + "m";
    }

    static String formatBigNum(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }
}
