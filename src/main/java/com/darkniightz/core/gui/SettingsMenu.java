package com.darkniightz.core.gui;

import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.system.MaterialCompat;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

public class SettingsMenu extends BaseMenu {

    private final JebaitedCore plugin;

    public SettingsMenu(JebaitedCore plugin) {
        super(plugin, "§9Player Settings", 54);
        this.plugin = plugin;
    }

    @Override
    protected void populate(Player viewer) {
        if (inventory == null) {
            return;
        }
        inventory.clear();
        fillBorders();

        PlayerProfile profile = plugin.getProfileStore().getOrCreate(viewer, plugin.getRankManager().getDefaultGroup());
        if (profile == null) {
            return;
        }

        inventory.setItem(4, new ItemBuilder(Material.NETHER_STAR)
                .name("§b§lYour Settings")
                .lore(List.of("§7Personalize your server experience.", "§7Changes save automatically."))
                .glow(true)
                .build());

        inventory.setItem(10, new ItemBuilder(Material.BOOK)
                .name("§eLanguage")
                .lore(List.of("§7Current: §f" + prettyLanguage(profile.getLanguage()), "§8UI language support is ready for expansion.", "§eClick to cycle"))
                .build());

        inventory.setItem(11, toggleItem(Material.PAPER, "Private Messages", profile.isPrivateMessagesEnabled(), "Allow direct messages and replies"));
        inventory.setItem(12, toggleItem(Material.ENDER_PEARL, "Teleport Requests", profile.isTeleportRequestsEnabled(), "Used when teleport requests are enabled"));
        inventory.setItem(13, toggleItem(Material.EMERALD, "Trade Requests", profile.isTradeRequestsEnabled(), "Allow incoming trade requests"));
        inventory.setItem(14, toggleItem(Material.GOLDEN_SWORD, "Duel Invites", profile.isDuelInvitesEnabled(), "Allow duel invitations"));
        inventory.setItem(15, toggleItem(Material.PLAYER_HEAD, "Party Invites", profile.isPartyInvitesEnabled(), "Allow party invitations"));
        inventory.setItem(16, toggleItem(Material.NOTE_BLOCK, "Sound Cues", profile.isSoundCuesEnabled(), "Play menu and preview sounds"));

        inventory.setItem(19, cycleItem(Material.COMPASS, "Scoreboard Mode", prettyScoreboardMode(profile.getScoreboardMode()), "Cycle between normal, minimal, and none"));
        inventory.setItem(20, toggleItem(MaterialCompat.resolve(Material.COMPASS, "SPYGLASS", "COMPASS"), "Cosmetic Visibility", profile.isCosmeticVisibilityEnabled(), "Show particle and trail effects"));
        inventory.setItem(21, toggleItem(Material.OAK_SIGN, "Join/Leave Messages", profile.isJoinLeaveMessagesEnabled(), "Show player join and quit notices"));
        inventory.setItem(22, toggleItem(Material.TOTEM_OF_UNDYING, "Death Messages", profile.isDeathMessagesEnabled(), "Show player death notices"));
        inventory.setItem(23, toggleItem(Material.BELL, "Event Notifications", profile.isEventNotificationsEnabled(), "Master switch for event alerts"));
        inventory.setItem(24, toggleItem(Material.NAME_TAG, "Head Nametags", profile.isHeadNametagsEnabled(), "Show your colored name above your head"));
        inventory.setItem(25, toggleItem(MaterialCompat.resolve(Material.BOOK, "WRITABLE_BOOK", "BOOK_AND_QUILL", "BOOK"), "Extra Nametag Info", profile.isNametagExtraEnabled(), "Add rank and extra detail to your nametag"));

        inventory.setItem(28, toggleItem(Material.IRON_SWORD, "KOTH Alerts", profile.isEventCategoryEnabled("koth"), "Notify for King of the Hill"));
        inventory.setItem(29, toggleItem(Material.DIAMOND_SWORD, "FFA Alerts", profile.isEventCategoryEnabled("ffa"), "Notify for free-for-all events"));
        inventory.setItem(30, toggleItem(Material.GOLDEN_SWORD, "Duels Alerts", profile.isEventCategoryEnabled("duels"), "Notify for duel events"));
        inventory.setItem(31, toggleItem(Material.NAME_TAG, "Chat Game Alerts", profile.isEventCategoryEnabled("chat"), "Notify for quiz, math, and scramble"));
        inventory.setItem(32, new ItemBuilder(Material.MAP)
                .name("§bWorld-aware Scoreboard")
                .lore(List.of("§7Hub shows coins.", "§7SMP shows balance.", "§8Your chosen mode still applies."))
                .build());

        if (profile.getDonorRank() != null) {
            boolean isDonorMode = "donor".equals(profile.getRankDisplayMode());
            inventory.setItem(33, new ItemBuilder(Material.NAME_TAG)
                    .name("§dRank Display")
                    .lore(List.of(
                            "§7Choose which rank to show in chat, tab & scoreboard.",
                            "",
                            "§7Primary: §f" + profile.getPrimaryRank(),
                            "§7Donor:   §b" + profile.getDonorRank(),
                            "",
                            "§7Current: " + (isDonorMode ? "§bDonor" : "§fPrimary"),
                            "§eClick to toggle"))
                    .build());
        }

        inventory.setItem(49, new ItemBuilder(Material.BARRIER)
                .name("§cClose")
                .lore(List.of("§7Click to close this menu."))
                .build());
    }

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
        PlayerProfile profile = plugin.getProfileStore().getOrCreate(who, plugin.getRankManager().getDefaultGroup());
        if (profile == null) {
            return true;
        }

        switch (slot) {
            case 10 -> {
                String next = prettyLanguage(profile.cycleLanguage());
                saveAndRefresh(who, profile);
                who.sendMessage(com.darkniightz.core.Messages.prefixed("§aLanguage set to §f" + next + "§a."));
            }
            case 11 -> toggle(who, profile, PlayerProfile.PREF_PRIVATE_MESSAGES, true, "Private messages");
            case 12 -> toggle(who, profile, PlayerProfile.PREF_TELEPORT_REQUESTS, true, "Teleport requests");
            case 13 -> toggle(who, profile, PlayerProfile.PREF_TRADE_REQUESTS, true, "Trade requests");
            case 14 -> toggle(who, profile, PlayerProfile.PREF_DUEL_INVITES, true, "Duel invites");
            case 15 -> toggle(who, profile, PlayerProfile.PREF_PARTY_INVITES, true, "Party invites");
            case 16 -> toggle(who, profile, PlayerProfile.PREF_SOUND_CUES, true, "Sound cues");
            case 19 -> {
                String mode = profile.cycleScoreboardMode();
                saveAndRefresh(who, profile);
                who.sendMessage(com.darkniightz.core.Messages.prefixed("§aScoreboard mode set to §f" + prettyScoreboardMode(mode) + "§a."));
            }
            case 20 -> toggle(who, profile, PlayerProfile.PREF_COSMETIC_VISIBILITY, true, "Cosmetic visibility");
            case 21 -> toggle(who, profile, PlayerProfile.PREF_JOIN_LEAVE_MESSAGES, true, "Join/leave messages");
            case 22 -> toggle(who, profile, PlayerProfile.PREF_DEATH_MESSAGES, true, "Death messages");
            case 23 -> toggle(who, profile, PlayerProfile.PREF_EVENT_NOTIFICATIONS, true, "Event notifications");
            case 24 -> toggle(who, profile, PlayerProfile.PREF_HEAD_NAMETAGS, true, "Head nametags");
            case 25 -> toggle(who, profile, PlayerProfile.PREF_NAMETAG_EXTRA, false, "Extra nametag info");
            case 28 -> toggle(who, profile, PlayerProfile.PREF_EVENT_PREFIX + "koth", true, "KOTH alerts");
            case 29 -> toggle(who, profile, PlayerProfile.PREF_EVENT_PREFIX + "ffa", true, "FFA alerts");
            case 30 -> toggle(who, profile, PlayerProfile.PREF_EVENT_PREFIX + "duels", true, "Duels alerts");
            case 31 -> toggle(who, profile, PlayerProfile.PREF_EVENT_PREFIX + "chat", true, "Chat game alerts");
            case 33 -> {
                if (profile.getDonorRank() != null) {
                    boolean isDonorMode = "donor".equals(profile.getRankDisplayMode());
                    profile.setRankDisplayMode(isDonorMode ? "primary" : "donor");
                    saveAndRefresh(who, profile);
                    who.sendMessage(com.darkniightz.core.Messages.prefixed("\u00a7aRank display set to \u00a7f" + (isDonorMode ? "primary" : "donor") + "\u00a7a."));
                }
            }
            case 49 -> MenuService.get().close(who);
            default -> {
            }
        }
        return true;
    }

    private void toggle(Player who, PlayerProfile profile, String key, boolean defaultValue, String label) {
        boolean enabled = profile.togglePreference(key, defaultValue);
        saveAndRefresh(who, profile);
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§a" + label + " " + (enabled ? "enabled" : "disabled") + "§a."));
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
        populate(who);
        who.updateInventory();
    }

    private void fillBorders() {
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("§8 ").build();
        int[] borderSlots = {0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 18, 25, 26, 27, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 50, 51, 52, 53};
        for (int slot : borderSlots) {
            inventory.setItem(slot, filler);
        }
    }

    private ItemStack toggleItem(Material material, String name, boolean enabled, String description) {
        return new ItemBuilder(material)
                .name((enabled ? "§a" : "§c") + name)
                .lore(List.of("§7" + description, "", "§7Current: " + (enabled ? "§aEnabled" : "§cDisabled"), "§eClick to toggle"))
                .build();
    }

    private ItemStack cycleItem(Material material, String name, String current, String description) {
        return new ItemBuilder(material)
                .name("§b" + name)
                .lore(List.of("§7" + description, "", "§7Current: §f" + current, "§eClick to cycle"))
                .build();
    }

    private String prettyLanguage(String key) {
        return switch (key == null ? "en" : key.toLowerCase(Locale.ROOT)) {
            case "es" -> "Español";
            case "fr" -> "Français";
            case "de" -> "Deutsch";
            default -> "English";
        };
    }

    private String prettyScoreboardMode(String mode) {
        return switch (mode == null ? "normal" : mode.toLowerCase(Locale.ROOT)) {
            case "minimal" -> "Minimal";
            case "none" -> "None";
            default -> "Normal";
        };
    }
}
