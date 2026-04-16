package com.darkniightz.core.gui;

import com.darkniightz.core.Messages;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.system.McMMOIntegration;
import com.darkniightz.main.PlayerProfileDAO;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * 54-slot tabbed player profile viewer. Replaces StatsMenu.
 *
 * <p>Layout:
 * <pre>
 *  Row 0: [HUB][SMP][EVT][---][HEAD][---][---][---][SET]
 *  Row 1-4: dynamic content per tab
 *  Row 5: footer filler
 * </pre>
 *
 * <p>Tabs: HUB, SMP, EVENTS, SETTINGS (placeholder).
 */
public class PlayerProfileMenu extends BaseMenu {

    public enum Tab { HUB, SMP, EVENTS, SETTINGS }

    // Slots for tab buttons (must match handleClick switch)
    private static final int SLOT_TAB_HUB     = 0;
    private static final int SLOT_TAB_SMP     = 1;
    private static final int SLOT_TAB_EVENTS  = 2;
    private static final int SLOT_TAB_SETTINGS = 8;
    private static final int SLOT_HEAD         = 4;

    // Footer row slots (row 5)
    private static final int[] FOOTER_SLOTS = {45, 46, 47, 48, 49, 50, 51, 52, 53};
    // Header filler (between tabs and head)
    private static final int[] HEADER_FILLER = {3, 5, 6, 7};

    private final ProfileStore profiles;
    private final RankManager ranks;
    private final OfflinePlayer target;
    private Tab activeTab = Tab.HUB;

    public PlayerProfileMenu(Plugin plugin, ProfileStore profiles, RankManager ranks, OfflinePlayer target) {
        super(plugin, "§8Player Profile", 54);
        this.profiles = profiles;
        this.ranks = ranks;
        this.target = target;
    }

    @Override
    protected void populate(Player viewer) {
        inventory.clear();
        PlayerProfile profile = profiles.getOrCreate(target, ranks.getDefaultGroup());
        if (profile == null) {
            inventory.setItem(22, new ItemBuilder(Material.BARRIER)
                    .name("§cUnable to load profile")
                    .lore(List.of("§7Try again in a moment."))
                    .build());
            return;
        }
        renderHeader(profile);
        renderContent(profile);
        renderFooter();
    }

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
        Tab target;
        switch (slot) {
            case SLOT_TAB_HUB     -> target = Tab.HUB;
            case SLOT_TAB_SMP     -> target = Tab.SMP;
            case SLOT_TAB_EVENTS  -> target = Tab.EVENTS;
            case SLOT_TAB_SETTINGS -> {
                who.sendMessage(Messages.prefixed("§7Settings will be available in a future update."));
                return true;
            }
            default -> { return true; }
        }
        if (activeTab == target) return true;
        activeTab = target;
        PlayerProfile profile = profiles.getOrCreate(this.target, ranks.getDefaultGroup());
        if (profile == null) return true;
        inventory.clear();
        renderHeader(profile);
        renderContent(profile);
        renderFooter();
        return true;
    }

    // ── Rendering ───────────────────────────────────────────────────────────

    private void renderHeader(PlayerProfile profile) {
        inventory.setItem(SLOT_TAB_HUB,      tabItem(Material.BOOK,         "§bHub",      activeTab == Tab.HUB));
        inventory.setItem(SLOT_TAB_SMP,      tabItem(Material.DIAMOND_SWORD, "§2SMP",     activeTab == Tab.SMP));
        inventory.setItem(SLOT_TAB_EVENTS,   tabItem(Material.NETHER_STAR,  "§dEvents",   activeTab == Tab.EVENTS));
        inventory.setItem(SLOT_TAB_SETTINGS, tabItem(Material.COMPARATOR,   "§7Settings", activeTab == Tab.SETTINGS));
        inventory.setItem(SLOT_HEAD, buildHeadItem(profile));

        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int s : HEADER_FILLER) inventory.setItem(s, filler);
    }

    private void renderContent(PlayerProfile profile) {
        switch (activeTab) {
            case HUB     -> renderHub(profile);
            case SMP     -> renderSmp(profile);
            case EVENTS  -> renderEvents(profile);
            case SETTINGS -> renderSettingsPlaceholder();
        }
    }

    private void renderFooter() {
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int s : FOOTER_SLOTS) inventory.setItem(s, filler);
    }

    // ── Tab content ─────────────────────────────────────────────────────────

    private void renderHub(PlayerProfile profile) {
        // Slot 13: Playtime
        inventory.setItem(13, new ItemBuilder(Material.CLOCK)
                .name("§bPlaytime")
                .lore(List.of(
                        "§7Total: §f" + formatDuration(profile.getPlaytimeMs()),
                        "§7First Joined: §f" + formatDate(profile.getFirstJoined()),
                        "§7Last Seen: §f" + formatDate(profile.getLastJoined())
                ))
                .build());

        // Slot 15: Activity
        inventory.setItem(15, new ItemBuilder(Material.PAPER)
                .name("§eActivity")
                .lore(List.of(
                        "§7Messages Sent: §a" + profile.getMessagesSent(),
                        "§7Commands Sent: §a" + profile.getCommandsSent()
                ))
                .build());

        // Slot 22: Balance
        inventory.setItem(22, new ItemBuilder(Material.GOLD_INGOT)
                .name("§6Balance")
                .lore(List.of("§7$§a" + String.format("%,.2f", profile.getBalance())))
                .build());

        // Slot 29: Cosmetics
        inventory.setItem(29, new ItemBuilder(Material.ENDER_CHEST)
                .name("§dCosmetics")
                .lore(List.of("§7Cosmetic Coins: §6" + profile.getCosmeticCoins()))
                .build());
    }

    private void renderSmp(PlayerProfile profile) {
        Integer power = McMMOIntegration.getPowerLevel(target);

        double kdr = profile.getDeaths() == 0
                ? (double) profile.getKills()
                : (double) profile.getKills() / profile.getDeaths();

        // Slot 13: Combat
        inventory.setItem(13, new ItemBuilder(Material.DIAMOND_SWORD)
                .name("§cCombat")
                .lore(List.of(
                        "§7Kills: §c" + profile.getKills(),
                        "§7Deaths: §c" + profile.getDeaths(),
                        String.format("§7KDR: §e%.2f", kdr)
                ))
                .build());

        // Slot 15: Gathering
        inventory.setItem(15, new ItemBuilder(Material.IRON_PICKAXE)
                .name("§7Gathering")
                .lore(List.of(
                        "§7Blocks Broken: §a" + profile.getBlocksBroken(),
                        "§7Crops Broken: §a" + profile.getCropsBroken(),
                        "§7Fish Caught: §3" + profile.getFishCaught()
                ))
                .build());

        // Slot 22: Mobs
        inventory.setItem(22, new ItemBuilder(Material.ROTTEN_FLESH)
                .name("§aMobs")
                .lore(List.of(
                        "§7Mobs Killed: §a" + profile.getMobsKilled(),
                        "§7Bosses Killed: §6" + profile.getBossesKilled()
                ))
                .build());

        // Slot 31: mcMMO
        inventory.setItem(31, new ItemBuilder(Material.EXPERIENCE_BOTTLE)
                .name("§5mcMMO")
                .lore(List.of("§7Power Level: " + (power == null ? "§8N/A" : "§d" + power)))
                .build());
    }

    private void renderEvents(PlayerProfile profile) {
        Map<String, PlayerProfileDAO.EventStatRecord> stored = profiles.loadEventStats(profile.getUuid());
        Map<String, PlayerProfileDAO.EventStatRecord> merged = new LinkedHashMap<>();

        var cfg = plugin.getConfig().getConfigurationSection("event_mode.events");
        if (cfg != null) {
            for (String key : cfg.getKeys(false)) {
                merged.put(key, stored.getOrDefault(key, new PlayerProfileDAO.EventStatRecord(0, 0, 0)));
            }
        }
        for (var entry : stored.entrySet()) {
            merged.putIfAbsent(entry.getKey(), entry.getValue());
        }

        if (merged.isEmpty()) {
            inventory.setItem(22, new ItemBuilder(Material.BARRIER)
                    .name("§7No event data yet.")
                    .lore(List.of("§8Participate in server events to see stats here."))
                    .build());
            return;
        }

        // Content slots: rows 1-4, avoiding border columns
        int[] contentSlots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
        };

        int idx = 0;
        for (var entry : merged.entrySet()) {
            if (idx >= contentSlots.length) break;
            PlayerProfileDAO.EventStatRecord row = entry.getValue();
            String label = humanize(entry.getKey());
            inventory.setItem(contentSlots[idx++], new ItemBuilder(Material.NETHER_STAR)
                    .name("§d" + label)
                    .lore(List.of(
                            "§7Participated: §f" + row.participated(),
                            "§7Won: §a" + row.won(),
                            "§7Lost: §c" + row.lost()
                    ))
                    .build());
        }
    }

    private void renderSettingsPlaceholder() {
        inventory.setItem(22, new ItemBuilder(Material.COMPARATOR)
                .name("§7Settings")
                .lore(List.of("§8Coming soon."))
                .build());
    }

    // ── Item builders ────────────────────────────────────────────────────────

    private ItemStack buildHeadItem(PlayerProfile profile) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (target != null) meta.setOwningPlayer(target);
        String name = profile.getName() == null ? "Unknown" : profile.getName();
        meta.setDisplayName("§e" + name);
        List<String> lore = new ArrayList<>();
        lore.add("§7Rank: §b" + profile.getDisplayRank());
        if (profile.getDonorRank() != null && !profile.getDonorRank().isBlank()) {
            lore.add("§7Donor: §6" + profile.getDonorRank());
        }
        lore.add("§7First Joined: §f" + formatDate(profile.getFirstJoined()));
        meta.setLore(lore);
        skull.setItemMeta(meta);
        return skull;
    }

    private ItemStack tabItem(Material mat, String label, boolean active) {
        String displayName = active ? "§f§l" + stripColor(label) : label;
        List<String> lore = active ? List.of("§a§l▶ Active") : List.of("§7Click to view");
        ItemBuilder builder = new ItemBuilder(mat).name(displayName).lore(lore);
        if (active) builder.glow(true);
        return builder.build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String formatDate(long epochMs) {
        if (epochMs <= 0L) return "§8N/A";
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
        df.setTimeZone(TimeZone.getDefault());
        return df.format(new Date(epochMs));
    }

    private String formatDuration(long ms) {
        if (ms <= 0L) return "0m";
        long seconds = ms / 1000L;
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    private String humanize(String key) {
        if (key == null || key.isBlank()) return "Unknown";
        String[] parts = key.split("[_\\-]");
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    private String stripColor(String s) {
        if (s == null) return "";
        return s.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }
}
