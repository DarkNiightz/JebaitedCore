package com.darkniightz.core.gui;

import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.settings.SettingCategory;
import com.darkniightz.core.settings.SettingKey;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Main settings hub  one button per {@link SettingCategory}.
 *
 * Layout (54-slot):
 *   Row 0   glass border
 *   Row 1   [NOTIFICATIONS] [SOUNDS] [CHAT]
 *   Row 2   glass spacer
 *   Row 3   [SOCIAL] [EVENTS] [GAMEPLAY]
 *   Row 4   [RANK DISPLAY] donor only, centred
 *   Row 5   glass border with [CLOSE] at slot 49
 *
 * Adding a category: add to SettingCategory, map a slot constant, add to
 * populate() and handleClick(). Adding a setting: add to SettingKey only.
 */
public class SettingsMenu extends BaseMenu {

    //  Category -> hub slot mapping 
    private static final int SLOT_NOTIFICATIONS = 11;
    private static final int SLOT_SOUNDS        = 13;
    private static final int SLOT_CHAT          = 15;
    private static final int SLOT_SOCIAL        = 29;
    private static final int SLOT_EVENTS        = 31;
    private static final int SLOT_GAMEPLAY      = 33;
    private static final int SLOT_RANK_DISPLAY  = 40;
    private static final int SLOT_CLOSE         = 49;

    private final JebaitedCore plugin;

    public SettingsMenu(JebaitedCore plugin) {
        super(plugin, "§9Player Settings", 54);
        this.plugin = plugin;
    }

    @Override
    protected void populate(Player viewer) {
        if (inventory == null) return;
        inventory.clear();
        fillGlass();

        PlayerProfile profile = plugin.getProfileStore()
                .getOrCreate(viewer, plugin.getRankManager().getDefaultGroup());
        if (profile == null) return;

        inventory.setItem(4, new ItemBuilder(Material.NETHER_STAR)
                .name("§b§lPlayer Settings")
                .lore(List.of("§7Personalise your server experience.",
                              "§7Click a category to manage its settings."))
                .glow(true)
                .build());

        inventory.setItem(SLOT_NOTIFICATIONS, categoryButton(SettingCategory.NOTIFICATIONS, profile));
        inventory.setItem(SLOT_SOUNDS,        categoryButton(SettingCategory.SOUNDS,        profile));
        inventory.setItem(SLOT_CHAT,          categoryButton(SettingCategory.CHAT,          profile));
        inventory.setItem(SLOT_SOCIAL,        categoryButton(SettingCategory.SOCIAL,        profile));
        inventory.setItem(SLOT_EVENTS,        categoryButton(SettingCategory.EVENTS,        profile));
        inventory.setItem(SLOT_GAMEPLAY,      categoryButton(SettingCategory.GAMEPLAY,      profile));

        if (profile.getDonorRank() != null) {
            String displayRank = profile.getDisplayRank();
            RankManager.RankStyle style = plugin.getRankManager().getStyle(displayRank);
            String col = style.rainbow ? "§d" : (style.colorCode != null ? style.colorCode : "§7");
            inventory.setItem(SLOT_RANK_DISPLAY, new ItemBuilder(Material.NAME_TAG)
                    .name("§d§lRank Display")
                    .lore(List.of(
                            "§7Choose which rank shows in chat, tab & scoreboard.",
                            "",
                            "§7Primary rank: §f" + profile.getPrimaryRank(),
                            "§7Donor rank:   §b" + profile.getDonorRank(),
                            "",
                            "§7Displaying: " + col + style.prefix,
                            "§eClick to open selector"))
                    .build());
        }

        inventory.setItem(SLOT_CLOSE, new ItemBuilder(Material.BARRIER)
                .name("§cClose")
                .lore(List.of("§7Click to close this menu."))
                .build());
    }

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
        switch (slot) {
            case SLOT_NOTIFICATIONS -> openCategory(who, SettingCategory.NOTIFICATIONS);
            case SLOT_SOUNDS        -> openCategory(who, SettingCategory.SOUNDS);
            case SLOT_CHAT          -> openCategory(who, SettingCategory.CHAT);
            case SLOT_SOCIAL        -> openCategory(who, SettingCategory.SOCIAL);
            case SLOT_EVENTS        -> openCategory(who, SettingCategory.EVENTS);
            case SLOT_GAMEPLAY      -> openCategory(who, SettingCategory.GAMEPLAY);
            case SLOT_RANK_DISPLAY  -> {
                PlayerProfile p = plugin.getProfileStore()
                        .getOrCreate(who, plugin.getRankManager().getDefaultGroup());
                if (p != null && p.getDonorRank() != null) {
                    new RankDisplayMenu(plugin).open(who);
                }
            }
            case SLOT_CLOSE -> MenuService.get().close(who);
            default -> { /* glass pane click  ignore */ }
        }
        return true;
    }

    //  Helpers 

    private void openCategory(Player who, SettingCategory category) {
        new SettingsCategoryMenu(plugin, category, this).open(who);
    }

    /** Hub button showing category name, description, and enabled/total count. */
    private ItemStack categoryButton(SettingCategory category, PlayerProfile profile) {
        List<SettingKey> keys = SettingKey.forCategory(category);
        long enabled = keys.stream()
                .filter(k -> profile.getPreference(k.key, k.defaultValue))
                .count();
        List<String> lore = new ArrayList<>();
        lore.add(category.description);
        lore.add("");
        lore.add("§7Settings: §f" + keys.size() + "  §8|  §7Active: §a" + enabled);
        lore.add("");
        lore.add("§eClick to manage");
        return new ItemBuilder(category.material)
                .name(category.color + "§l" + category.displayName)
                .lore(lore)
                .build();
    }

    /** Fills every slot that is not a content slot with a gray glass pane. */
    private void fillGlass() {
        ItemStack glass = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("§8 ").build();
        Set<Integer> content = Set.of(
                4,
                SLOT_NOTIFICATIONS, SLOT_SOUNDS, SLOT_CHAT,
                SLOT_SOCIAL, SLOT_EVENTS, SLOT_GAMEPLAY,
                SLOT_RANK_DISPLAY, SLOT_CLOSE);
        for (int i = 0; i < 54; i++) {
            if (!content.contains(i)) inventory.setItem(i, glass);
        }
    }
}