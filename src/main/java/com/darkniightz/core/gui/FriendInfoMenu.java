package com.darkniightz.core.gui;

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
import java.util.List;
import java.util.UUID;

/**
 * 27-slot friend info panel — shows friendship duration, XP together, PvP kills together,
 * plus the friend's own stats. Opened from FriendsMenu on left-click.
 *
 * <p>Layout (row 1 content slots):
 * <pre>
 *  [B][B][B][B][B][B][B][B][B]   row 0 — border
 *  [B][B][HEAD][B][STATS][B][REMOVE][B][B]   row 1
 *  [B][B][B][BACK][B][B][B][B][B]   row 2 — border + back button
 * </pre>
 */
public class FriendInfoMenu extends BaseMenu {

    private static final int SLOT_HEAD   = 11;
    private static final int SLOT_STATS  = 13;
    private static final int SLOT_REMOVE = 15;
    private static final int SLOT_BACK   = 22;

    private final FriendManager friendManager;
    private final ProfileStore profiles;
    private final Player viewer;
    private final UUID friendUuid;
    private final String friendName;
    private FriendDAO.FriendshipStats stats;

    /** @param stats pre-loaded stats snapshot, or null to load async on open. */
    public FriendInfoMenu(Plugin plugin, FriendManager friendManager, ProfileStore profiles,
                          Player viewer, UUID friendUuid, String friendName,
                          FriendDAO.FriendshipStats stats) {
        super(plugin, "\u00a78" + (friendName != null ? friendName : "Friend") + " \u2014 Info", 27);
        this.friendManager = friendManager;
        this.profiles = profiles;
        this.viewer = viewer;
        this.friendUuid = friendUuid;
        this.friendName = friendName != null ? friendName : friendUuid.toString().substring(0, 8);
        this.stats = stats;
    }

    @Override
    protected void populate(Player v) {
        inventory.clear();
        renderBorders();
        renderHead();
        renderStats();
        renderActions();
        if (stats == null) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                FriendDAO.FriendshipStats loaded = friendManager.loadStats(viewer.getUniqueId(), friendUuid);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!v.isOnline()) return;
                    this.stats = loaded;
                    renderStats();
                });
            });
        }
    }

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
        if (slot == SLOT_BACK) {
            new FriendsMenu(plugin, friendManager, profiles).open(who);
            return true;
        }
        if (slot == SLOT_REMOVE) {
            who.closeInventory();
            OfflinePlayer op = Bukkit.getOfflinePlayer(friendUuid);
            friendManager.removeFriend(who, op);
            return true;
        }
        return true;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private void renderBorders() {
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int s = 0; s < 9; s++) inventory.setItem(s, filler);
        for (int s = 18; s < 27; s++) inventory.setItem(s, filler);
        inventory.setItem(9, filler);
        inventory.setItem(10, filler);
        inventory.setItem(12, filler);
        inventory.setItem(14, filler);
        inventory.setItem(16, filler);
        inventory.setItem(17, filler);
    }

    private void renderHead() {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        OfflinePlayer op = Bukkit.getOfflinePlayer(friendUuid);
        meta.setOwningPlayer(op);
        boolean online = op.isOnline();
        String coloredName = friendManager.coloredName(friendUuid, friendName);
        meta.setDisplayName(coloredName);
        List<String> lore = new ArrayList<>();
        lore.add(online ? "\u00a7a\u2b24 Online" : "\u00a78\u2b24 Offline");
        meta.setLore(lore);
        skull.setItemMeta(meta);
        inventory.setItem(SLOT_HEAD, skull);
    }

    private void renderStats() {
        List<String> lore = new ArrayList<>();
        if (stats != null) {
            lore.add("\u00a77Friends since: \u00a7f" + FriendsMenu.formatDuration(System.currentTimeMillis() - stats.createdAt()));
            lore.add("\u00a77XP Together:  \u00a7e" + FriendsMenu.formatBigNum(stats.xpTogether()));
            lore.add("\u00a77PvP Together: \u00a7c" + stats.killsTogether() + " \u00a77kills");
        } else {
            lore.add("\u00a78Loading stats...");
        }
        PlayerProfile fp = profiles.get(friendUuid);
        if (fp != null) {
            lore.add("\u00a78\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");
            lore.add("\u00a77K/D:      \u00a7c" + fp.getKills() + " \u00a78/ \u00a77" + fp.getDeaths());
            lore.add("\u00a77Playtime:  \u00a7a" + FriendsMenu.formatPlaytime(fp.getPlaytimeMs()));
            lore.add("\u00a77Rank:      \u00a7f" + fp.getDisplayRank());
        }
        inventory.setItem(SLOT_STATS, new ItemBuilder(Material.BOOK)
                .name("\u00a7e" + friendName + "'s Stats")
                .lore(lore)
                .build());
    }

    private void renderActions() {
        inventory.setItem(SLOT_REMOVE, new ItemBuilder(Material.BARRIER)
                .name("\u00a7cRemove Friend")
                .lore(List.of("\u00a77Click to remove \u00a7f" + friendName + " \u00a77from your friends."))
                .build());
        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
                .name("\u00a77\u2190 Back to Friends List")
                .build());
    }
}
