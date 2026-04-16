package com.darkniightz.core.achievements;

import com.darkniightz.core.achievements.AchievementDefinition.AchievementTier;
import com.darkniightz.core.gui.BaseMenu;
import com.darkniightz.core.gui.ItemBuilder;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * 27-slot per-achievement detail view.
 *
 * <pre>
 * Row 0 (slots 0–8):  border + slot 4 = achievement icon
 * Row 1 (slots 9–17): tier panes centred in the row
 * Row 2 (slots 18–26): back button at slot 22, rest = filler
 * </pre>
 *
 * <b>Pane colours:</b>
 * <ul>
 *   <li>GREEN  — tier completed</li>
 *   <li>LIME   — current in-progress tier (progress bar + reward visible)</li>
 *   <li>RED    — any locked tier (show ??? — reward hidden)</li>
 * </ul>
 */
public final class AchievementDetailMenu extends BaseMenu {

    private static final int SLOT_ICON = 4;
    private static final int SLOT_BACK = 22;

    // Tier pane positions for up to 9 tiers, centred in slots 9–17
    private static final int[][] TIER_SLOT_OFFSETS = {
        {13},               // 1 tier
        {12, 14},           // 2 tiers
        {11, 13, 15},       // 3 tiers
        {10, 12, 14, 16},   // 4 tiers
        {9, 11, 13, 15, 17},// 5 tiers
        {9, 10, 12, 13, 15, 16},       // 6
        {9, 10, 11, 13, 14, 15, 17},   // 7
        {9, 10, 11, 12, 14, 15, 16, 17},// 8
        {9, 10, 11, 12, 13, 14, 15, 16, 17}, // 9
    };

    private final AchievementManager   achievements;
    private final UUID                 targetUuid;
    private final String               targetName;
    private final AchievementDefinition def;
    private final BaseMenu             parent;

    public AchievementDetailMenu(
        JebaitedCore plugin,
        AchievementManager achievements,
        UUID targetUuid,
        String targetName,
        AchievementDefinition def,
        BaseMenu parent
    ) {
        super(plugin, "§e⭐ " + def.getDisplayName(), 27);
        this.achievements = achievements;
        this.targetUuid   = targetUuid;
        this.targetName   = targetName;
        this.def          = def;
        this.parent       = parent;
    }

    @Override
    protected void populate(Player viewer) {
        long progress    = achievements.getProgress(targetUuid, def.getId());
        int  tierReached = achievements.getTierReached(targetUuid, def.getId());
        int  tierCount   = def.tierCount();

        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();

        // Fill everything first
        for (int i = 0; i < 27; i++) inventory.setItem(i, filler);
        // Top border row
        for (int i = 0; i < 9; i++) inventory.setItem(i, border);

        // Achievement icon (slot 4)
        inventory.setItem(SLOT_ICON, buildSummaryIcon(def, progress, tierReached));

        // Tier panes in row 1 (slots 9–17)
        int clamped = Math.min(tierCount, 9);
        int[] slots = clamped >= 1 ? TIER_SLOT_OFFSETS[clamped - 1] : new int[0];
        for (int i = 0; i < clamped; i++) {
            inventory.setItem(slots[i], buildTierPane(def.tier(i), i, tierReached, progress));
        }

        // Back button
        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
            .name("§e◀ Back to Achievements")
            .lore(List.of("§8Click to go back"))
            .build());
    }

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
        if (slot == SLOT_BACK) {
            if (parent != null) {
                parent.open(who);
            } else {
                // Rebuild the parent menu if reference was lost
                new AchievementsMenu((JebaitedCore) plugin, achievements, targetUuid, targetName).open(who);
            }
        }
        return true;
    }

    // ── Icon builder ──────────────────────────────────────────────────────────

    private ItemStack buildSummaryIcon(AchievementDefinition def, long progress, int tierReached) {
        int     tierCount = def.tierCount();
        boolean complete  = tierReached >= tierCount;

        String nameColor = complete ? "§a§l" : (tierReached > 0 ? "§e" : "§7");
        List<String> lore = new ArrayList<>();
        lore.add("§7" + def.getDescription());
        lore.add("");
        lore.add("§8Tiers: §a" + tierReached + " §8/ §7" + tierCount);

        if (!complete) {
            AchievementTier current = def.tier(tierReached);
            String bar = AchievementsMenu.buildBar(progress, current.threshold(), 10);
            double pct = current.threshold() > 0
                ? Math.min(100.0, progress * 100.0 / current.threshold()) : 100.0;
            lore.add("§7Progress: " + bar + " §8(§b" + String.format("%.1f", pct) + "%§8)");
        } else {
            lore.add("§a✔ All tiers complete!");
        }

        lore.add("");
        lore.add("§8Viewing: §f" + targetName);

        return new ItemBuilder(AchievementsMenu.typeToMaterial(def.getType()))
            .name(nameColor + def.getDisplayName())
            .lore(lore)
            .glow(complete)
            .build();
    }

    // ── Tier pane builder ─────────────────────────────────────────────────────

    private ItemStack buildTierPane(AchievementTier tier, int tierIndex, int tierReached, long progress) {
        if (tierIndex < tierReached) {
            // ── COMPLETED (green) ─────────────────────────────────────────────
            List<String> lore = new ArrayList<>();
            lore.add("§8Threshold: §f" + AchievementsMenu.formatLong(tier.threshold()));
            lore.add("§7Reward: " + AchievementsMenu.rewardSummary(tier));
            lore.add("");
            lore.add("§a✔ Completed");

            return new ItemBuilder(Material.GREEN_STAINED_GLASS_PANE)
                .name("§a✔ Tier " + (tierIndex + 1) + " §8— §f" + tier.label())
                .lore(lore)
                .build();

        } else if (tierIndex == tierReached) {
            // ── IN PROGRESS (lime) ────────────────────────────────────────────
            long  threshold = tier.threshold();
            String bar  = AchievementsMenu.buildBar(progress, threshold, 10);
            double pct  = threshold > 0 ? Math.min(100.0, progress * 100.0 / threshold) : 100.0;

            List<String> lore = new ArrayList<>();
            lore.add("§7Progress: " + bar);
            lore.add("§f" + AchievementsMenu.formatLong(progress) + " §8/ §f" + AchievementsMenu.formatLong(threshold)
                + " §8(§b" + String.format("%.1f", pct) + "%§8)");
            lore.add("");
            lore.add("§7Reward: " + AchievementsMenu.rewardSummary(tier));
            lore.add("");
            lore.add("§eIn progress...");

            return new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name("§e⬆ In Progress §8— §f" + tier.label() + " §8(Tier " + (tierIndex + 1) + ")")
                .lore(lore)
                .build();

        } else {
            // ── LOCKED (red) ─ mystery ────────────────────────────────────────
            List<String> lore = new ArrayList<>();
            lore.add("§8???");
            lore.add("");
            lore.add("§8Keep progressing to reveal this tier");

            return new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name("§c✗ Locked §8(Tier " + (tierIndex + 1) + ")")
                .lore(lore)
                .build();
        }
    }
}
