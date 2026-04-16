package com.darkniightz.core.gui;

import com.darkniightz.core.Messages;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 27-slot sub-menu that lets a player with a donor rank choose which rank to
 * display in chat, tab, and the scoreboard.
 *
 * <p>Eligible choices = every rank on the ladder that is equal to or below the
 * player's primary rank (lower power, i.e. higher ladder index).  Staff can
 * display as any rank below their primary; donor-only players (primary rank
 * set to their donor rank) follow the same rule.
 *
 * <p>The selected choice is stored in {@code PlayerProfile#rankDisplayMode} as
 * the plain rank name string (e.g. {@code "gold"}).  The special value
 * {@code "primary"} restores the real primary rank display.
 */
public class RankDisplayMenu extends BaseMenu {

    private static final int ROWS = 3; // 27 slots
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16
    };

    private final JebaitedCore plugin;

    public RankDisplayMenu(JebaitedCore plugin) {
        super(plugin, "\u00a79Rank Display", 27);
        this.plugin = plugin;
    }

    @Override
    protected void populate(Player viewer) {
        if (inventory == null) return;
        inventory.clear();
        fillBorder();

        PlayerProfile profile = plugin.getProfileStore().getOrCreate(viewer, plugin.getRankManager().getDefaultGroup());
        if (profile == null || profile.getDonorRank() == null) {
            // No donor rank — shouldn't be open
            viewer.closeInventory();
            return;
        }

        String primaryRank = profile.getPrimaryRank() != null
                ? profile.getPrimaryRank().toLowerCase(Locale.ROOT) : "pleb";
        String currentDisplay = profile.getRankDisplayMode() != null
                ? profile.getRankDisplayMode().toLowerCase(Locale.ROOT) : "primary";

        RankManager ranks = plugin.getRankManager();
        List<String> ladder = ranks.getLadder(); // descending power order (owner first)
        int primaryIdx = ranks.indexOf(primaryRank);

        // Build eligible ranks: every rank with index >= primaryIdx (equal or below in power)
        List<String> eligible = new ArrayList<>();
        for (int i = primaryIdx; i < ladder.size(); i++) {
            eligible.add(ladder.get(i).toLowerCase(Locale.ROOT));
        }

        int contentPtr = 0;
        for (String rankKey : eligible) {
            if (contentPtr >= CONTENT_SLOTS.length) break;

            RankManager.RankStyle style = ranks.getStyle(rankKey);
            String nameColor = style.rainbow ? "\u00a7d" : (style.colorCode != null ? style.colorCode : "\u00a77");
            String prefix = style.prefix != null ? style.prefix : rankKey.toUpperCase(Locale.ROOT);

            boolean isSelected = rankKey.equals(currentDisplay)
                    || ("primary".equals(currentDisplay) && rankKey.equals(primaryRank));

            Material icon = rankToMaterial(rankKey);
            ItemStack item = new ItemBuilder(icon)
                    .name(nameColor + (style.bold ? "\u00a7l" : "") + prefix)
                    .lore(buildLore(rankKey, primaryRank, isSelected))
                    .glow(isSelected)
                    .build();

            inventory.setItem(CONTENT_SLOTS[contentPtr++], item);
        }

        // Slot 22 — back button
        inventory.setItem(22, new ItemBuilder(Material.ARROW)
                .name("\u00a77\u2190 Back to Settings")
                .lore(List.of("\u00a78Return without changing."))
                .build());
    }

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
        PlayerProfile profile = plugin.getProfileStore().getOrCreate(who, plugin.getRankManager().getDefaultGroup());
        if (profile == null || profile.getDonorRank() == null) return true;

        if (slot == 22) {
            new SettingsMenu(plugin).open(who);
            return true;
        }

        // Check if clicked slot is one of the content slots
        String primaryRank = profile.getPrimaryRank() != null
                ? profile.getPrimaryRank().toLowerCase(Locale.ROOT) : "pleb";
        RankManager ranks = plugin.getRankManager();
        List<String> ladder = ranks.getLadder();
        int primaryIdx = ranks.indexOf(primaryRank);

        List<String> eligible = new ArrayList<>();
        for (int i = primaryIdx; i < ladder.size(); i++) {
            eligible.add(ladder.get(i).toLowerCase(Locale.ROOT));
        }

        for (int i = 0; i < CONTENT_SLOTS.length && i < eligible.size(); i++) {
            if (CONTENT_SLOTS[i] == slot) {
                String chosen = eligible.get(i);
                profile.setRankDisplayMode(chosen);
                plugin.getProfileStore().save(who.getUniqueId());

                if (profile.isSoundCuesEnabled()) {
                    who.playSound(who.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                }
                if (plugin.getScoreboardManager() != null) plugin.getScoreboardManager().refreshPlayer(who);
                plugin.refreshAllPlayerPresentations();

                RankManager.RankStyle style = ranks.getStyle(chosen);
                String nameColor = style.rainbow ? "\u00a7d" : (style.colorCode != null ? style.colorCode : "\u00a77");
                who.sendMessage(Messages.prefixed("\u00a7aDisplay rank set to " + nameColor + style.prefix + "\u00a7a."));

                // Refresh menu so selected glow updates
                populate(who);
                who.updateInventory();
                return true;
            }
        }

        return true;
    }

    private List<String> buildLore(String rankKey, String primaryRank, boolean isSelected) {
        List<String> lore = new ArrayList<>();
        if (rankKey.equals(primaryRank)) {
            lore.add("\u00a78Your actual rank.");
        }
        lore.add(isSelected ? "\u00a7a\u2714 Currently displayed" : "\u00a77Click to display as this rank.");
        return lore;
    }

    private void fillBorder() {
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("\u00a78 ").build();
        for (int i = 0; i < 9; i++) inventory.setItem(i, filler);           // top row
        for (int i = 18; i < 27; i++) inventory.setItem(i, filler);         // bottom row
        inventory.setItem(9, filler);
        inventory.setItem(17, filler);
    }

    /** Maps a rank key to a representative material icon. */
    private Material rankToMaterial(String rank) {
        return switch (rank.toLowerCase(Locale.ROOT)) {
            case "grandmaster" -> Material.NETHERITE_INGOT;
            case "legend"      -> Material.DRAGON_EGG;
            case "diamond"     -> Material.DIAMOND;
            case "gold"        -> Material.GOLD_INGOT;
            case "vip"         -> Material.EMERALD;
            case "builder"     -> Material.BRICKS;
            case "helper"      -> Material.BLUE_DYE;
            case "moderator"   -> Material.IRON_SWORD;
            case "srmod"       -> Material.GOLDEN_SWORD;
            case "admin"       -> Material.DIAMOND_SWORD;
            case "developer"   -> Material.COMMAND_BLOCK;
            case "owner"       -> Material.NETHER_STAR;
            default            -> Material.GRAY_DYE; // pleb / unknown
        };
    }
}
