package com.darkniightz.core.gui;

import com.darkniightz.core.Messages;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.settings.SettingCategory;
import com.darkniightz.core.settings.SettingKey;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Generic settings category page — fully driven by {@link SettingKey}.
 *
 * <p>This menu renders every {@link SettingKey} that belongs to a given
 * {@link SettingCategory} as a green/red toggle item.  When a new setting is
 * added to the {@code SettingKey} enum it automatically appears here with no
 * further code changes. {@link SettingCategory#GAMEPLAY} also gets a
 * non-boolean "Screen effects" cycle (full / some / none) in the next free
 * content slot.
 *
 * <p>Content slots (avoiding borders and navigation):
 * <pre>
 *  Row 0 — border
 *  Row 1 — slots 10-16  (7 slots)
 *  Row 2 — slots 19-25  (7 slots)
 *  Row 3 — slots 28-34  (7 slots)
 *  Row 4 — slots 37-43  (7 slots)  ← overflow only; 28 total
 *  Row 5 — border + [BACK] 45 [CLOSE] 49
 * </pre>
 *
 * Up to 28 toggles per category.  If more are ever needed, the layout can
 * be extended by adding additional content rows to {@code CONTENT_SLOTS}.
 */
public class SettingsCategoryMenu extends BaseMenu {

    /** Ordered list of slots available for toggle items (left-to-right, top-to-bottom). */
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final int SLOT_BACK  = 45;
    private static final int SLOT_CLOSE = 49;

    private final JebaitedCore   plugin;
    private final SettingCategory category;
    /** Menu to return to when the player clicks Back. */
    private final BaseMenu        parent;
    /** Keys rendered in this menu, in declaration order. */
    private final List<SettingKey> keys;

    public SettingsCategoryMenu(JebaitedCore plugin, SettingCategory category, BaseMenu parent) {
        super(plugin, category.color + "§lSettings › " + category.displayName, 54);
        this.plugin   = plugin;
        this.category = category;
        this.parent   = parent;
        this.keys     = SettingKey.forCategory(category);
    }

    // ── BaseMenu implementation ───────────────────────────────────────────

    @Override
    protected void populate(Player viewer) {
        if (inventory == null) return;
        inventory.clear();
        fillGlass();

        PlayerProfile profile = plugin.getProfileStore()
                .getOrCreate(viewer, plugin.getRankManager().getDefaultGroup());
        if (profile == null) return;

        // Render each setting key into its content slot
        for (int i = 0; i < keys.size() && i < CONTENT_SLOTS.length; i++) {
            SettingKey k    = keys.get(i);
            boolean   state = profile.getPreference(k.key, k.defaultValue);
            inventory.setItem(CONTENT_SLOTS[i], toggleItem(k, state));
        }

        if (category == SettingCategory.GAMEPLAY && keys.size() < CONTENT_SLOTS.length) {
            inventory.setItem(CONTENT_SLOTS[keys.size()], screenEffectsItem(profile));
        }

        // Navigation
        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
                .name("§7§lBack")
                .lore(List.of("§7Return to Settings hub."))
                .build());
        inventory.setItem(SLOT_CLOSE, new ItemBuilder(Material.BARRIER)
                .name("§cClose")
                .lore(List.of("§7Click to close this menu."))
                .build());
    }

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
        if (slot == SLOT_BACK) {
            MenuService.get().open(who, parent);
            return true;
        }
        if (slot == SLOT_CLOSE) {
            MenuService.get().close(who);
            return true;
        }

        if (category == SettingCategory.GAMEPLAY && keys.size() < CONTENT_SLOTS.length) {
            int screenIdx = keys.size();
            if (CONTENT_SLOTS[screenIdx] == slot) {
                PlayerProfile profile = plugin.getProfileStore()
                        .getOrCreate(who, plugin.getRankManager().getDefaultGroup());
                if (profile != null) {
                    profile.cycleScreenEffectsMode();
                    plugin.getProfileStore().save(who.getUniqueId());
                    who.sendMessage(Messages.prefixed(
                            "§7Screen effects: §f" + labelScreenEffects(profile.getScreenEffectsMode())));
                    saveAndRefresh(who, profile);
                }
                return true;
            }
        }

        // Determine which SettingKey was clicked, if any
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            if (CONTENT_SLOTS[i] != slot) continue;
            if (i >= keys.size()) break;

            SettingKey k = keys.get(i);
            PlayerProfile profile = plugin.getProfileStore()
                    .getOrCreate(who, plugin.getRankManager().getDefaultGroup());
            if (profile == null) break;

            boolean newState = profile.togglePreference(k.key, k.defaultValue);
            saveAndRefresh(who, profile);
            who.sendMessage(Messages.prefixed(
                    "§a" + k.displayName + " " + (newState ? "§aenabled" : "§cdisabled") + "§a."));
            break;
        }
        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private ItemStack toggleItem(SettingKey k, boolean enabled) {
        return new ItemBuilder(k.material)
                .name((enabled ? "§a" : "§c") + k.displayName)
                .lore(List.of(
                        "§7" + k.description,
                        "",
                        "§7Status: " + (enabled ? "§aEnabled" : "§cDisabled"),
                        "§eClick to toggle"))
                .build();
    }

    private static String labelScreenEffects(String mode) {
        if (mode == null) return "Full";
        return switch (mode.toLowerCase()) {
            case "some" -> "Some (high-signal only)";
            case "none" -> "None";
            default -> "Full";
        };
    }

    private static ItemStack screenEffectsItem(PlayerProfile profile) {
        String mode = profile.getScreenEffectsMode();
        return new ItemBuilder(Material.DRAGON_HEAD)
                .name("§5§lScreen effects")
                .lore(List.of(
                        "§7Boss bars and full-screen titles (events, grave tracker, etc.).",
                        "",
                        "§7Current: §f" + labelScreenEffects(mode),
                        "§8Full §7— all · §8Some §7— important only · §8None §7— off",
                        "",
                        "§eClick to cycle"))
                .build();
    }

    private void saveAndRefresh(Player who, PlayerProfile profile) {
        plugin.getProfileStore().save(who.getUniqueId());
        if (profile.isSoundCuesEnabled()) {
            who.playSound(who.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        }
        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().refreshPlayer(who);
        }
        plugin.refreshAllPlayerPresentations();
        // Refresh items in-place so the player sees the state change immediately
        populate(who);
        who.updateInventory();
    }

    /** Fills all non-content, non-navigation slots with a gray glass pane. */
    private void fillGlass() {
        ItemStack glass = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("§8 ").build();
        outer:
        for (int i = 0; i < 54; i++) {
            if (i == SLOT_BACK || i == SLOT_CLOSE) continue;
            for (int cs : CONTENT_SLOTS) {
                if (cs == i) continue outer;
            }
            inventory.setItem(i, glass);
        }
    }
}
