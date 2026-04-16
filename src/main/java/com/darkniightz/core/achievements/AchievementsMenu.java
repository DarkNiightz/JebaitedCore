package com.darkniightz.core.achievements;

import com.darkniightz.core.achievements.AchievementDefinition.AchievementTier;
import com.darkniightz.core.achievements.AchievementDefinition.AchievementType;
import com.darkniightz.core.gui.BaseMenu;
import com.darkniightz.core.gui.ItemBuilder;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * 54-slot paginated achievement list. Each achievement is displayed as an icon with
 * a progress bar and next-tier info in its lore. Clicking an achievement opens
 * {@link AchievementDetailMenu} which shows the full tier breakdown with stained glass.
 */
public final class AchievementsMenu extends BaseMenu {

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };
    private static final int SLOT_PREV  = 46;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_NEXT  = 52;
    private static final int SLOT_HEADER = 4;

    private static final Material BORDER  = Material.GRAY_STAINED_GLASS_PANE;
    private static final Material SPACER  = Material.BLACK_STAINED_GLASS_PANE;

    // ── State ─────────────────────────────────────────────────────────────────
    private final AchievementManager achievements;
    private final UUID               targetUuid;
    private final String             targetName;
    private       int                page;

    private List<AchievementDefinition> pages;

    public AchievementsMenu(
        JebaitedCore plugin,
        AchievementManager achievements,
        UUID targetUuid,
        String targetName
    ) {
        this(plugin, achievements, targetUuid, targetName, 0);
    }

    private AchievementsMenu(
        JebaitedCore plugin,
        AchievementManager achievements,
        UUID targetUuid,
        String targetName,
        int page
    ) {
        super(plugin, "§6⭐ Achievements" + (page > 0 ? " §7— §fPage " + (page + 1) : ""), 54);
        this.achievements = achievements;
        this.targetUuid   = targetUuid;
        this.targetName   = targetName;
        this.page         = page;
    }

    @Override
    protected void populate(Player viewer) {
        pages = new ArrayList<>(achievements.getDefinitions());
        int totalPages = Math.max(1, (int) Math.ceil(pages.size() / (double) CONTENT_SLOTS.length));
        page = Math.max(0, Math.min(page, totalPages - 1));

        // Border
        ItemStack border = new ItemBuilder(BORDER).name(" ").build();
        ItemStack spacer = new ItemBuilder(SPACER).name(" ").build();
        for (int i = 0; i < 54; i++) inventory.setItem(i, spacer);
        for (int i = 0; i < 9; i++) inventory.setItem(i, border);
        for (int i = 45; i < 54; i++) inventory.setItem(i, border);
        for (int i = 0; i < 54; i += 9) inventory.setItem(i, border);
        for (int i = 8; i < 54; i += 9) inventory.setItem(i, border);

        // Header
        inventory.setItem(SLOT_HEADER, new ItemBuilder(Material.NETHER_STAR)
            .name("§6⭐ Achievements")
            .lore(List.of(
                "§7Unlock milestones and earn rewards.",
                "§7Click any achievement for tier details.",
                "",
                "§8Viewing: §f" + targetName
            ))
            .build());

        // Achievement icons
        int start = page * CONTENT_SLOTS.length;
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            int idx = start + i;
            if (idx >= pages.size()) {
                inventory.setItem(CONTENT_SLOTS[i], spacer);
                continue;
            }
            inventory.setItem(CONTENT_SLOTS[i], buildAchievementIcon(pages.get(idx)));
        }

        // Navigation
        if (page > 0) {
            inventory.setItem(SLOT_PREV, new ItemBuilder(Material.ARROW)
                .name("§e◀ Previous Page").build());
        }
        inventory.setItem(SLOT_CLOSE, new ItemBuilder(Material.BARRIER)
            .name("§cClose").build());
        if ((page + 1) * CONTENT_SLOTS.length < pages.size()) {
            inventory.setItem(SLOT_NEXT, new ItemBuilder(Material.ARROW)
                .name("§e▶ Next Page").build());
        }
    }

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
        if (slot == SLOT_CLOSE) {
            who.closeInventory();
            return true;
        }
        if (slot == SLOT_PREV && page > 0) {
            new AchievementsMenu((JebaitedCore) plugin, achievements, targetUuid, targetName, page - 1).open(who);
            return true;
        }
        if (slot == SLOT_NEXT) {
            List<AchievementDefinition> defs = new ArrayList<>(achievements.getDefinitions());
            if ((page + 1) * CONTENT_SLOTS.length < defs.size()) {
                new AchievementsMenu((JebaitedCore) plugin, achievements, targetUuid, targetName, page + 1).open(who);
                return true;
            }
        }

        // Content slot?
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            if (CONTENT_SLOTS[i] == slot) {
                int idx = page * CONTENT_SLOTS.length + i;
                List<AchievementDefinition> defs = new ArrayList<>(achievements.getDefinitions());
                if (idx < defs.size()) {
                    new AchievementDetailMenu(
                        (JebaitedCore) plugin, achievements, targetUuid, targetName, defs.get(idx), this
                    ).open(who);
                }
                return true;
            }
        }
        return true;
    }

    // ── Icon builder ──────────────────────────────────────────────────────────

    private ItemStack buildAchievementIcon(AchievementDefinition def) {
        long   progress    = achievements.getProgress(targetUuid, def.getId());
        int    tierReached = achievements.getTierReached(targetUuid, def.getId());
        int    tierCount   = def.tierCount();
        boolean complete   = tierReached >= tierCount;

        AchievementTier nextTier = complete ? null : def.tier(tierReached);

        String nameColor = complete ? "§a" : (tierReached > 0 ? "§e" : "§7");
        String name = nameColor + (complete ? "§l" : "") + def.getDisplayName();

        List<String> lore = new ArrayList<>();
        lore.add("§7" + def.getDescription());
        lore.add("");

        if (complete) {
            lore.add("§a✔ All tiers complete!");
        } else {
            // Progress bar toward next tier
            long threshold = nextTier.threshold();
            String bar = buildBar(progress, threshold, 10);
            double pct = threshold > 0 ? Math.min(100.0, progress * 100.0 / threshold) : 100.0;
            lore.add("§7Progress: " + bar + " §f" + formatLong(progress) + " §8/ §f" + formatLong(threshold) + " §8(§b" + String.format("%.1f", pct) + "%§8)");
            lore.add("");
            lore.add("§eNext tier: §f" + nextTier.label());
            lore.add("§7Reward: " + rewardSummary(nextTier));
        }

        lore.add("");
        lore.add("§8Tiers: §a" + tierReached + " §8/ §7" + tierCount);
        lore.add("§8▶ Click for tier breakdown");

        return new ItemBuilder(typeToMaterial(def.getType()))
            .name(name)
            .lore(lore)
            .glow(complete)
            .build();
    }

    // ── Statics ───────────────────────────────────────────────────────────────

    static String buildBar(long progress, long max, int width) {
        if (max <= 0) return "§a" + "█".repeat(width);
        int filled = (int) Math.min(width, progress * width / max);
        return "§a" + "█".repeat(filled) + "§8" + "░".repeat(width - filled);
    }

    static String rewardSummary(AchievementTier tier) {
        return switch (tier.rewardType()) {
            case "tag"      -> "§b[" + tier.rewardValue() + "]";
            case "coins"    -> "§6" + tier.rewardValue() + " §eCosm. Coins";
            case "cosmetic" -> "§d" + tier.rewardValue() + " §8(Cosmetic)";
            default         -> "§f" + tier.rewardValue();
        };
    }

    static String formatLong(long v) {
        if (v >= 1_000_000) return String.format("%.1fM", v / 1_000_000.0);
        if (v >= 1_000)     return String.format("%.1fK", v / 1_000.0);
        return String.valueOf(v);
    }

    static Material typeToMaterial(AchievementType type) {
        return switch (type) {
            case KILLS                -> Material.IRON_SWORD;
            case DEATHS               -> Material.BONE;
            case MOBS_KILLED          -> Material.ZOMBIE_HEAD;
            case BLOCKS_BROKEN        -> Material.DIAMOND_PICKAXE;
            case BLOCKS_PLACED        -> Material.BRICKS;
            case FISH_CAUGHT          -> Material.FISHING_ROD;
            case CROPS_HARVESTED      -> Material.WHEAT;
            case MESSAGES_SENT        -> Material.PAPER;
            case COMMANDS_SENT        -> Material.COMMAND_BLOCK;
            case DISTANCE_TRAVELLED   -> Material.LEATHER_BOOTS;
            case EVENT_WINS_COMBAT    -> Material.GOLDEN_SWORD;
            case EVENT_WINS_CHAT      -> Material.BOOK;
            case EVENT_WINS_HARDCORE  -> Material.NETHERITE_SWORD;
            case PLAYTIME_MINUTES     -> Material.CLOCK;
            case COSMETIC_COINS_EARNED-> Material.GOLD_NUGGET;
            case PARTY_KILLS          -> Material.SHIELD;
            case FRIEND_COUNT         -> Material.PLAYER_HEAD;
        };
    }
}
