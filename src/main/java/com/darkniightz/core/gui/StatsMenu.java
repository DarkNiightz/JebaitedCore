package com.darkniightz.core.gui;

import com.darkniightz.core.achievements.AchievementDefinition;
import com.darkniightz.core.achievements.AchievementDefinition.AchievementTier;
import com.darkniightz.core.achievements.AchievementManager;
import com.darkniightz.core.achievements.AchievementsMenu;
import com.darkniightz.core.chat.ChatUtil;
import com.darkniightz.core.cosmetics.CosmeticsManager;
import com.darkniightz.core.eventmode.ChatGameKeys;
import com.darkniightz.core.eventmode.ChatGameManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.ranks.RankManager.RankStyle;
import com.darkniightz.core.system.McMMOIntegration;
import com.darkniightz.core.system.NetworkManager;
import com.darkniightz.main.JebaitedCore;
import com.darkniightz.main.PlayerProfileDAO;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Tabbed profile / stats GUI (roadmap: §F Player Profile Overhaul).
 */
public class StatsMenu extends BaseMenu {
    private static final int SIZE = 54;
    private static final int SLOT_HEAD = 13;
    private static final int[] TAB_SLOTS = {19, 20, 21, 22, 23, 24, 25};
    private static final int SLOT_CLOSE = 49;
    /** Profile tab → open {@link SettingsMenu} (viewer’s own settings). */
    private static final int SLOT_PROFILE_SETTINGS = 31;
    /** Full achievement browser (bottom-right). */
    private static final int SLOT_ACHIEVEMENTS_FULL = 43;
    private static final Material BORDER = Material.BLACK_STAINED_GLASS_PANE;
    private static final int[] SMP_SKILLS = {37, 38, 39};
    private static final int[] EVENT_GRID_R1 = {29, 30, 31, 32, 33, 34};
    private static final int[] EVENT_GRID_R2 = {37, 38, 39, 40, 41, 42, 43};
    /** Achievement progress cells (book uses slot {@link #SLOT_ACHIEVEMENTS_FULL}). */
    private static final int[] ACH_GRID = {29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42};
    private static final Material[] EVENT_ICONS = {
            Material.PAPER, Material.MAP, Material.FIREWORK_STAR, Material.GOLD_NUGGET,
            Material.SUNFLOWER, Material.COMPASS, Material.CLOCK, Material.COPPER_INGOT,
            Material.AMETHYST_SHARD, Material.GLOWSTONE_DUST, Material.PRISMARINE_SHARD,
            Material.ENDER_PEARL, Material.BLAZE_POWDER
    };

    private final ProfileStore profiles;
    private final RankManager ranks;
    private final AchievementManager achievements;
    private final OfflinePlayer target;
    private int tab;

    public StatsMenu(JebaitedCore plugin, ProfileStore profiles, RankManager ranks, AchievementManager achievements, OfflinePlayer target) {
        super(plugin, "§8⌁ §fProfile", SIZE);
        this.profiles = profiles;
        this.ranks = ranks;
        this.achievements = achievements;
        this.target = target;
    }

    @Override
    protected void populate(Player viewer) {
        if (inventory == null) {
            return;
        }
        inventory.clear();
        fillBorder();
        PlayerProfile profile = profiles.getOrCreate(target, ranks.getDefaultGroup());
        if (profile == null) {
            inventory.setItem(22, new ItemBuilder(Material.BARRIER)
                    .name("§cUnable to load profile")
                    .lore(List.of("§7Try again in a moment."))
                    .build());
            return;
        }

        inventory.setItem(SLOT_CLOSE, new ItemBuilder(Material.BARRIER)
                .name("§c§lClose")
                .lore(List.of("§7Close this menu."))
                .build());
        inventory.setItem(SLOT_HEAD, buildHeadItem(profile));

        String[] titles = {
                "§6§lProfile",
                "§b§lHub",
                "§2§lSMP",
                "§4§lHardcore",
                "§c§lArena",
                "§e§lChat Games",
                "§6§lAchievements"
        };
        Material[] mats = {
                Material.NAME_TAG,
                Material.BOOK,
                Material.DIAMOND_SWORD,
                Material.NETHERITE_SCRAP,
                Material.IRON_SWORD,
                Material.WRITABLE_BOOK,
                Material.GOLD_BLOCK
        };
        for (int i = 0; i < TAB_SLOTS.length; i++) {
            String name = (tab == i ? "§a▶ §r" : "§7") + titles[i];
            inventory.setItem(TAB_SLOTS[i], new ItemBuilder(mats[i])
                    .name(name)
                    .lore(List.of("§7Switch tab"))
                    .build());
        }

        switch (tab) {
            case 0 -> populateProfileTab(profile);
            case 1 -> populateHubTab(profile);
            case 2 -> populateSmpTab(profile);
            case 3 -> populateEventBucketTab(profile, EventBucket.HARDCORE);
            case 4 -> populateEventBucketTab(profile, EventBucket.NORMAL);
            case 5 -> populateEventBucketTab(profile, EventBucket.CHAT);
            case 6 -> populateAchievementsTab(profile);
            default -> populateProfileTab(profile);
        }
    }

    private enum EventBucket {
        HARDCORE,
        NORMAL,
        CHAT
    }

    private void fillBorder() {
        for (int i = 0; i < 9; i++) {
            pane(i);
            pane(45 + i);
        }
        pane(9);
        pane(17);
        pane(18);
        pane(26);
        pane(27);
        pane(35);
        pane(36);
        pane(44);
    }

    private void pane(int slot) {
        if (slot == SLOT_CLOSE) {
            return;
        }
        inventory.setItem(slot, new ItemBuilder(BORDER).name("§8").build());
    }

    private void populateProfileTab(PlayerProfile profile) {
        String rank = profile.getPrimaryRank() == null ? ranks.getDefaultGroup() : profile.getPrimaryRank();
        RankStyle style = ranks.getStyle(rank);
        String rawName = profile.getName() == null ? profile.getUuid().toString().substring(0, 8) : profile.getName();
        List<String> idLines = new ArrayList<>();
        idLines.add("§7Identity §8│ §r" + ChatUtil.buildStyledName(rawName, style));
        idLines.add("§7Station §8│ " + style.prefix
                + (profile.getDonorRank() == null || profile.getDonorRank().isBlank()
                ? ""
                : " §8· §7Donor §f" + profile.getDonorRank()));
        NetworkManager net = NetworkManager.getInstance();
        if (net != null) {
            idLines.add("§7Node §8│ §f" + net.getServerName() + " §8· §8" + net.getServerId());
        }
        idLines.add("§8ID §7" + profile.getUuid());
        inventory.setItem(
                28,
                new ItemBuilder(Material.NAME_TAG)
                        .name("§6§lIdentity")
                        .lore(idLines)
                        .build());
        List<String> netOnly = new ArrayList<>();
        if (net != null) {
            netOnly.add("§7Realm §8│ §f" + net.getServerName());
            netOnly.add("§7Server id §8│ §8" + net.getServerId());
        }
        inventory.setItem(
                30,
                new ItemBuilder(Material.RECOVERY_COMPASS)
                        .name("§3§lNetwork")
                        .lore(netOnly.isEmpty() ? List.of("§8—") : netOnly)
                        .build());
        inventory.setItem(
                32,
                new ItemBuilder(Material.CLOCK)
                        .name("§e§lTime")
                        .lore(List.of(
                                "§7Total playtime: §f" + formatDuration(profile.getPlaytimeMs()),
                                "§7First joined: §f" + formatDate(profile.getFirstJoined()),
                                "§7Last joined: §f" + formatDate(profile.getLastJoined())))
                        .build());
        inventory.setItem(SLOT_PROFILE_SETTINGS, new ItemBuilder(Material.COMPARATOR)
                .name("§9§lSettings")
                .lore(List.of(
                        "§7Your preferences and toggles.",
                        "",
                        "§8Opens the settings hub §7(/settings)§8."))
                .build());
    }

    private void populateHubTab(PlayerProfile profile) {
        List<String> netLine = new ArrayList<>();
        NetworkManager nm = NetworkManager.getInstance();
        if (nm != null) {
            netLine.add("§7Session §8│ §f" + nm.getServerName());
        }
        List<String> social = new ArrayList<>(netLine);
        social.add("§7Messages sent §8│ §a" + String.format(Locale.US, "%,d", profile.getMessagesSent()));
        social.add("§7Commands used §8│ §a" + String.format(Locale.US, "%,d", profile.getCommandsSent()));
        inventory.setItem(28, new ItemBuilder(Material.OAK_SIGN)
                .name("§b§lSocial")
                .lore(social)
                .build());
        inventory.setItem(30, buildHubCosmeticsItem(profile));
        inventory.setItem(32, new ItemBuilder(Material.CLOCK)
                .name("§e§lPlaytime")
                .lore(List.of(
                        "§7Total §8│ §f" + formatDuration(profile.getPlaytimeMs()),
                        "§7First seen §8│ §f" + formatDate(profile.getFirstJoined())))
                .build());
    }

    private ItemStack buildHubCosmeticsItem(PlayerProfile profile) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Coins §8│ §6" + String.format(Locale.US, "%,d", profile.getCosmeticCoins()));
        lore.add("§7Wardrobe opens §8│ §f" + String.format(Locale.US, "%,d", profile.getWardrobeOpens()));
        lore.add("§7Equips §8│ §f" + String.format(Locale.US, "%,d", profile.getCosmeticEquips()));
        lore.add("§7Tickets §8│ §f" + String.format(Locale.US, "%,d", profile.getCosmeticTickets()));
        lore.add("");
        if (plugin instanceof JebaitedCore core && core.getCosmeticsManager() != null) {
            CosmeticsManager cm = core.getCosmeticsManager();
            for (CosmeticsManager.Category cat : CosmeticsManager.Category.values()) {
                int[] ut = countCosmeticUnlocks(profile, cm, cat);
                lore.add("§7" + cosmeticCategoryLabel(cat) + " §8│ §f" + ut[0] + "§8/§7" + ut[1]);
            }
            lore.add("");
            lore.add("§7Active particle §8│ §f" + nullSafe(profile.getActiveParticle()));
            lore.add("§7Active gadget §8│ §f" + nullSafe(profile.getEquippedGadget()));
        } else {
            lore.add("§8Cosmetics catalog not loaded.");
        }
        return new ItemBuilder(Material.SUNFLOWER)
                .name("§d§lCosmetics")
                .lore(lore)
                .build();
    }

    private static String nullSafe(String s) {
        return s == null || s.isBlank() ? "§8—" : s;
    }

    private static String cosmeticCategoryLabel(CosmeticsManager.Category cat) {
        return switch (cat) {
            case PARTICLES -> "Particles";
            case TRAILS -> "Trials";
            case GADGETS -> "Gadgets";
            case TAGS -> "Tags";
        };
    }

    /** @return {unlocked, enabledTotal} */
    private static int[] countCosmeticUnlocks(PlayerProfile profile, CosmeticsManager cm, CosmeticsManager.Category cat) {
        int unlocked = 0;
        int total = 0;
        for (CosmeticsManager.Cosmetic c : cm.getByCategory(cat)) {
            if (!c.enabled) {
                continue;
            }
            total++;
            if (profile.hasUnlocked(c.key)) {
                unlocked++;
            }
        }
        return new int[] {unlocked, total};
    }

    private void populateSmpTab(PlayerProfile profile) {
        inventory.setItem(28, buildSmpCombatItem(profile));
        inventory.setItem(30, buildSmpWorldItem(profile));
        inventory.setItem(32, buildMcmmoSummaryItem(profile));
        placeSmpEconomy(profile);
        List<McMMOIntegration.SkillLevel> skillRows = McMMOIntegration.collectSkillLevels(target);
        skillRows.sort(Comparator.comparingInt(McMMOIntegration.SkillLevel::level).reversed());
        placeMcmmoSkillColumns(skillRows);
    }

    /** SMP tab: survival wallet (balance visible from any world; spending applies on SMP). */
    private void placeSmpEconomy(PlayerProfile profile) {
        inventory.setItem(34, new ItemBuilder(Material.GOLD_INGOT)
                .name("§6§lEconomy")
                .lore(List.of(
                        "§7Balance §8│ §a" + String.format(Locale.US, "%,.2f", profile.getBalance()),
                        "",
                        "§8SMP wallet — used on survival",
                        "§8(overworld, nether, end)."))
                .build());
    }

    private Map<String, PlayerProfileDAO.EventStatRecord> mergedEventStatMap(UUID uuid) {
        Map<String, PlayerProfileDAO.EventStatRecord> stored = profiles.loadEventStats(uuid);
        Map<String, PlayerProfileDAO.EventStatRecord> merged = new LinkedHashMap<>();

        var cfg = plugin.getConfig().getConfigurationSection("event_mode.events");
        if (cfg != null) {
            for (String key : cfg.getKeys(false)) {
                merged.put(key, stored.getOrDefault(key, new PlayerProfileDAO.EventStatRecord(0, 0, 0)));
            }
        }
        for (String cgKey : ChatGameManager.chatGameStatKeys(plugin)) {
            merged.putIfAbsent(cgKey, stored.getOrDefault(cgKey, new PlayerProfileDAO.EventStatRecord(0, 0, 0)));
        }
        for (var entry : stored.entrySet()) {
            merged.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return merged;
    }

    private static int[] concatEventGrid() {
        int totalGrid = EVENT_GRID_R1.length + EVENT_GRID_R2.length;
        int[] grid = new int[totalGrid];
        System.arraycopy(EVENT_GRID_R1, 0, grid, 0, EVENT_GRID_R1.length);
        System.arraycopy(EVENT_GRID_R2, 0, grid, EVENT_GRID_R1.length, EVENT_GRID_R2.length);
        return grid;
    }

    private static EventBucket classifyEventKey(String key, Set<String> chatNormKeys) {
        String norm = ChatGameKeys.normalize(key);
        if (ChatGameKeys.isChatGameConfigKey(norm) || chatNormKeys.contains(norm)) {
            return EventBucket.CHAT;
        }
        String low = key.toLowerCase(Locale.ROOT);
        if (low.contains("hardcore") || low.contains("hc_") || low.contains("_hc")) {
            return EventBucket.HARDCORE;
        }
        return EventBucket.NORMAL;
    }

    private void populateEventBucketTab(PlayerProfile profile, EventBucket bucket) {
        Set<String> chatNormKeys = new HashSet<>(ChatGameManager.chatGameStatKeys(plugin));
        Map<String, PlayerProfileDAO.EventStatRecord> merged = mergedEventStatMap(profile.getUuid());

        List<Map.Entry<String, PlayerProfileDAO.EventStatRecord>> entries = new ArrayList<>();
        for (var e : merged.entrySet()) {
            if (classifyEventKey(e.getKey(), chatNormKeys) == bucket) {
                entries.add(e);
            }
        }
        entries.sort(Comparator.comparing(Map.Entry::getKey));

        long sumP = 0;
        long sumW = 0;
        long sumL = 0;
        for (var e : entries) {
            var r = e.getValue();
            sumP += r.participated();
            sumW += r.won();
            sumL += r.lost();
        }

        String title = switch (bucket) {
            case HARDCORE -> "§4§lHardcore";
            case NORMAL -> "§c§lArena";
            case CHAT -> "§e§lChat games";
        };
        List<String> headLore = new ArrayList<>();
        headLore.add("§7Tracked events §8│ §f" + entries.size());
        headLore.add("§7Played §8│ §f" + sumP);
        headLore.add("§aWins §8│ §f" + sumW);
        headLore.add("§cLosses §8│ §f" + sumL);
        headLore.add("");
        headLore.add(switch (bucket) {
            case HARDCORE -> "§7Profile hardcore wins §8│ §f" + profile.getEventWinsHardcore();
            case NORMAL -> "§7Profile combat wins §8│ §f" + profile.getEventWinsCombat();
            case CHAT -> "§7Profile chat wins §8│ §f" + profile.getEventWinsChat();
        });
        inventory.setItem(28, new ItemBuilder(Material.FIRE_CHARGE)
                .name(title)
                .lore(headLore)
                .build());

        if (entries.isEmpty()) {
            int[] grid = concatEventGrid();
            for (int s : grid) {
                inventory.setItem(s, null);
            }
            inventory.setItem(31, new ItemBuilder(Material.BARRIER)
                    .name("§8No stats in this bucket")
                    .lore(List.of(
                            "§7Nothing is configured here yet,",
                            "§7or this player has no rows."))
                    .build());
            return;
        }
        int totalGrid = EVENT_GRID_R1.length + EVENT_GRID_R2.length;
        int[] grid = concatEventGrid();

        boolean overflow = entries.size() > totalGrid;
        int showCount = overflow ? totalGrid - 1 : Math.min(entries.size(), totalGrid);
        int iconRot = 0;
        for (int i = 0; i < showCount; i++) {
            var e = entries.get(i);
            inventory.setItem(grid[i], buildSingleEventIcon(e.getKey(), e.getValue(), iconRot++));
        }
        if (overflow) {
            int hidden = entries.size() - showCount;
            inventory.setItem(grid[totalGrid - 1], new ItemBuilder(Material.WRITABLE_BOOK)
                    .name("§7§l+" + hidden + " more events")
                    .lore(List.of(
                            "§8Not enough slots in this menu.",
                            "§8Skull click → §7chat transcript §8lists all.",
                            "",
                            "§7Extra keys §8│ §f" + hidden))
                    .build());
        } else {
            for (int i = showCount; i < totalGrid; i++) {
                inventory.setItem(grid[i], null);
            }
        }
    }

    private ItemStack buildSingleEventIcon(String key, PlayerProfileDAO.EventStatRecord row, int iconIdx) {
        Material mat = EVENT_ICONS[Math.floorMod(iconIdx, EVENT_ICONS.length)];
        String title = humanize(key);
        if (title.length() > 26) {
            title = title.substring(0, 23) + "…";
        }
        return new ItemBuilder(mat)
                .name("§f§l" + title)
                .lore(List.of(
                        "§7Played §8│ §f" + row.participated(),
                        "§aWins §8│ §f" + row.won(),
                        "§cLosses §8│ §f" + row.lost()))
                .build();
    }

    private static final Material[] ACH_CELL_ICONS = {
            Material.GOLD_INGOT, Material.EMERALD, Material.DIAMOND, Material.AMETHYST_SHARD,
            Material.BLAZE_POWDER, Material.NETHER_STAR, Material.HONEYCOMB, Material.ECHO_SHARD,
            Material.PRISMARINE_SHARD, Material.GLOWSTONE_DUST, Material.LAPIS_LAZULI, Material.COPPER_INGOT
    };

    private void populateAchievementsTab(PlayerProfile profile) {
        UUID uuid = profile.getUuid();
        for (int s : ACH_GRID) {
            inventory.setItem(s, null);
        }
        if (achievements == null) {
            inventory.setItem(28, new ItemBuilder(Material.BARRIER)
                    .name("§cAchievements unavailable")
                    .lore(List.of("§7Achievement data could not be loaded."))
                    .build());
            inventory.setItem(SLOT_ACHIEVEMENTS_FULL, null);
            return;
        }
        achievements.ensureLoadedSync(uuid);

        List<AchievementDefinition> defs = new ArrayList<>();
        for (AchievementDefinition def : achievements.getDefinitions()) {
            if (!def.isSecret()) {
                defs.add(def);
            }
        }
        defs.sort(Comparator
                .comparing((AchievementDefinition d) -> {
                    int tr = achievements.getTierReached(uuid, d.getId());
                    int tc = d.tierCount();
                    boolean done = tc > 0 && tr >= tc;
                    return done ? 1 : 0;
                })
                .thenComparing(AchievementDefinition::getDisplayName, String.CASE_INSENSITIVE_ORDER));

        int total = defs.size();
        int completed = 0;
        for (AchievementDefinition def : defs) {
            int tc = def.tierCount();
            int tr = achievements.getTierReached(uuid, def.getId());
            if (tc > 0 && tr >= tc) {
                completed++;
            }
        }
        int pct = total <= 0 ? 0 : (int) Math.round(100.0 * completed / (double) total);
        List<String> sumLore = new ArrayList<>();
        sumLore.add("§7Clears §8│ §e" + completed + "§8/§7" + total + " §8(" + pct + "%)");
        sumLore.add("");
        sumLore.add("§7Slots below §8│ §7snapshot per milestone");
        sumLore.add("§8Book §7opens the full browser.");
        inventory.setItem(28, new ItemBuilder(Material.GOLD_BLOCK)
                .name("§e§lAchievement summary")
                .lore(sumLore)
                .build());

        int iconRot = 0;
        int limit = Math.min(defs.size(), ACH_GRID.length);
        for (int i = 0; i < limit; i++) {
            inventory.setItem(ACH_GRID[i], buildAchievementCell(uuid, defs.get(i), iconRot++));
        }

        String dispName = profile.getName() == null ? uuid.toString().substring(0, 8) : profile.getName();
        inventory.setItem(SLOT_ACHIEVEMENTS_FULL, new ItemBuilder(Material.ENCHANTED_BOOK)
                .name("§6§lFull achievement book")
                .lore(List.of(
                        "§7Every milestone & reward row.",
                        "",
                        "§8Viewing §7" + dispName))
                .build());
    }

    private ItemStack buildAchievementCell(UUID uuid, AchievementDefinition def, int iconRot) {
        int tierReached = achievements.getTierReached(uuid, def.getId());
        int tierCount = def.tierCount();
        long progress = achievements.getProgress(uuid, def.getId());
        boolean done = tierCount > 0 && tierReached >= tierCount;

        Material mat;
        if (done) {
            mat = Material.GOLD_BLOCK;
        } else if (tierReached > 0 || progress > 0L) {
            mat = Material.LIME_STAINED_GLASS_PANE;
        } else {
            mat = ACH_CELL_ICONS[Math.floorMod(iconRot, ACH_CELL_ICONS.length)];
        }

        List<String> lore = new ArrayList<>();
        String desc = def.getDescription();
        if (desc != null && !desc.isBlank()) {
            String plain = ChatColor.stripColor(desc);
            if (plain.length() > 52) {
                plain = plain.substring(0, 49) + "…";
            }
            lore.add("§8" + plain);
        }
        lore.add("§8Stat §7" + humanizeStatType(def.getType()));
        lore.add("§8Tiers §a" + tierReached + " §8/ §7" + tierCount);
        if (tierCount > 0) {
            if (done) {
                lore.add("§aAll tiers cleared");
            } else {
                AchievementTier next = tierReached < tierCount ? def.tier(tierReached) : null;
                if (next != null) {
                    lore.add("§7Progress §8│ §f" + progress + "§8/§7" + next.threshold());
                } else {
                    lore.add("§7Progress §8│ §f" + progress);
                }
            }
        }

        String title = def.getDisplayName();
        if (title.length() > 28) {
            title = title.substring(0, 25) + "…";
        }
        return new ItemBuilder(mat)
                .name((done ? "§a§l" : "§f§l") + title)
                .lore(lore)
                .build();
    }

    private String humanizeStatType(AchievementDefinition.AchievementType type) {
        if (type == null) {
            return "?";
        }
        return humanize(type.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
        if (slot == SLOT_CLOSE) {
            MenuService.get().close(who);
            return true;
        }
        PlayerProfile profile = profiles.getOrCreate(target, ranks.getDefaultGroup());
        if (profile == null) {
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§cCould not load stats right now."));
            return true;
        }
        if (slot == SLOT_HEAD) {
            printAllStatsToChat(who, profile);
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Printed full stats in chat."));
            return true;
        }
        if (tab == 0 && slot == SLOT_PROFILE_SETTINGS && plugin instanceof JebaitedCore core) {
            MenuService.get().close(who);
            new SettingsMenu(core).open(who);
            return true;
        }
        for (int i = 0; i < TAB_SLOTS.length; i++) {
            if (slot == TAB_SLOTS[i]) {
                tab = i;
                populate(who);
                return true;
            }
        }
        if (tab == 6 && slot == SLOT_ACHIEVEMENTS_FULL && achievements != null && plugin instanceof JebaitedCore core) {
            achievements.ensureLoadedSync(profile.getUuid());
            String name = profile.getName() == null ? profile.getUuid().toString().substring(0, 8) : profile.getName();
            MenuService.get().close(who);
            new AchievementsMenu(core, achievements, profile.getUuid(), name).open(who);
            return true;
        }
        return true;
    }

    private ItemStack buildHeadItem(PlayerProfile profile) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(target);
        String rank = profile.getPrimaryRank() == null ? ranks.getDefaultGroup() : profile.getPrimaryRank();
        RankStyle style = ranks.getStyle(rank);
        String rawName = profile.getName() == null ? profile.getUuid().toString().substring(0, 8) : profile.getName();
        meta.setDisplayName(ChatUtil.buildStyledName(rawName, style));
        List<String> headLore = new ArrayList<>();
        headLore.add("§7Station §8│ " + style.prefix);
        NetworkManager net = NetworkManager.getInstance();
        if (net != null) {
            headLore.add("§7Node §8│ §f" + net.getServerName());
        }
        headLore.add("§8Click §7for transcript to chat");
        meta.setLore(headLore);
        skull.setItemMeta(meta);
        return skull;
    }

    private ItemStack buildSmpCombatItem(PlayerProfile profile) {
        int k = profile.getKills();
        int d = profile.getDeaths();
        List<String> lore = new ArrayList<>();
        lore.add("§7Kills §8│ §c" + String.format(Locale.US, "%,d", k));
        lore.add("§7Deaths §8│ §c" + String.format(Locale.US, "%,d", d));
        lore.add("§7K/D §8│ §f" + formatKdr(k, d));
        lore.add("");
        lore.add("§7Mobs §8│ §a" + String.format(Locale.US, "%,d", profile.getMobsKilled()));
        lore.add("§7Bosses §8│ §6" + String.format(Locale.US, "%,d", profile.getBossesKilled()));
        return new ItemBuilder(Material.DIAMOND_SWORD)
                .name("§c§lCombat")
                .lore(lore)
                .build();
    }

    private ItemStack buildSmpWorldItem(PlayerProfile profile) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Blocks broken §8│ §e" + String.format(Locale.US, "%,d", profile.getBlocksBroken()));
        lore.add("§7Crops harvested §8│ §e" + String.format(Locale.US, "%,d", profile.getCropsBroken()));
        lore.add("§7Fish caught §8│ §e" + String.format(Locale.US, "%,d", profile.getFishCaught()));
        return new ItemBuilder(Material.GOLDEN_SHOVEL)
                .name("§a§lWorld")
                .lore(lore)
                .build();
    }

    private ItemStack buildMcmmoSummaryItem(PlayerProfile profile) {
        List<String> lore = new ArrayList<>();
        if (!McMMOIntegration.isEnabled()) {
            lore.add("§8mcMMO not loaded.");
            return new ItemBuilder(Material.BARRIER).name("§8§lmcMMO").lore(lore).build();
        }
        Integer power = McMMOIntegration.getPowerLevel(target);
        Integer rk = McMMOIntegration.getOverallRank(profile.getUuid());
        lore.add("§7Power level §8│ §d" + (power == null ? "§8—" : String.valueOf(power)));
        lore.add("§7DB snapshot §8│ §7" + profile.getMcMMOLevel());
        lore.add("§7Overall rank §8│ §f#" + (rk == null ? "§8—" : rk));
        lore.add("");
        lore.add("§8Skills below · §7ExperienceAPI");
        return new ItemBuilder(Material.EXPERIENCE_BOTTLE)
                .name("§d§lmcMMO")
                .lore(lore)
                .build();
    }

    private void placeMcmmoSkillColumns(List<McMMOIntegration.SkillLevel> skills) {
        if (!McMMOIntegration.isEnabled()) {
            for (int s : SMP_SKILLS) {
                inventory.setItem(s, null);
            }
            return;
        }
        if (skills.isEmpty()) {
            inventory.setItem(SMP_SKILLS[1], new ItemBuilder(Material.BOOK)
                    .name("§8§lSkills")
                    .lore(List.of(
                            "§7No mcMMO skill rows.",
                            "§8Offline player or DB miss —",
                            "§8join SMP for live pull."))
                    .build());
            inventory.setItem(SMP_SKILLS[0], null);
            inventory.setItem(SMP_SKILLS[2], null);
            return;
        }
        List<List<McMMOIntegration.SkillLevel>> cols = new ArrayList<>(List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
        for (int i = 0; i < skills.size(); i++) {
            cols.get(i % 3).add(skills.get(i));
        }
        String[] titles = {"§d§lSkills §8· §71", "§d§lSkills §8· §72", "§d§lSkills §8· §73"};
        for (int c = 0; c < 3; c++) {
            inventory.setItem(SMP_SKILLS[c], buildSkillColumnItem(titles[c], cols.get(c)));
        }
    }

    private ItemStack buildSkillColumnItem(String title, List<McMMOIntegration.SkillLevel> slice) {
        List<String> lore = new ArrayList<>();
        final int maxLines = 8;
        for (McMMOIntegration.SkillLevel sl : slice) {
            if (lore.size() >= maxLines) {
                lore.add("§8…");
                break;
            }
            lore.add("§7" + prettySkillName(sl.skillName()) + " §8│ §f" + sl.level());
        }
        if (slice.isEmpty()) {
            lore.add("§8—");
        }
        return new ItemBuilder(Material.ENCHANTED_BOOK)
                .name(title)
                .lore(lore)
                .build();
    }

    private String prettySkillName(String enumName) {
        if (enumName == null || enumName.isBlank()) {
            return "?";
        }
        return humanize(enumName.toLowerCase(Locale.ROOT));
    }

    private static String formatKdr(int kills, int deaths) {
        if (deaths <= 0) {
            return kills <= 0 ? "§8—" : String.format(Locale.US, "%.2f", (double) kills);
        }
        return String.format(Locale.US, "%.2f", kills / (double) deaths);
    }

    private void printAllStatsToChat(Player who, PlayerProfile profile) {
        String rank = profile.getPrimaryRank() == null ? ranks.getDefaultGroup() : profile.getPrimaryRank();
        RankStyle style = ranks.getStyle(rank);
        String disp = profile.getName() == null ? profile.getUuid().toString().substring(0, 8) : profile.getName();
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§6§l=== §r" + ChatUtil.buildStyledName(disp, style) + " §6§l==="));
        NetworkManager net = NetworkManager.getInstance();
        if (net != null) {
            who.sendMessage(com.darkniightz.core.Messages.prefixed(
                    "§7Realm §8│ §f" + net.getServerName() + " §8· §8" + net.getServerId()));
        }
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Rank: §b" + profile.getPrimaryRank()));
        if (profile.getDonorRank() != null && !profile.getDonorRank().isBlank()) {
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Donor: §f" + profile.getDonorRank()));
        }
        who.sendMessage(com.darkniightz.core.Messages.prefixed(
                "§7Balance §8(SMP wallet)§7: §a" + String.format(Locale.US, "%,.2f", profile.getBalance())));
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§7First Joined: §f" + formatDate(profile.getFirstJoined())));
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Playtime: §f" + formatDuration(profile.getPlaytimeMs())));
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§8- §bHub"));
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Messages Sent: §a" + profile.getMessagesSent()));
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Commands Sent: §a" + profile.getCommandsSent()));
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Cosmetic Coins: §6" + profile.getCosmeticCoins()));
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§8- §2SMP"));
        Integer power = McMMOIntegration.getPowerLevel(target);
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§7mcMMO (stored / live): §d" + profile.getMcMMOLevel() + " §8/ "
                + (power == null ? "§8N/A" : "§d" + power)));
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Kills: §c" + profile.getKills() + " §8| §7Deaths: §c" + profile.getDeaths()));
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Mobs: §a" + profile.getMobsKilled() + " §8| §7Bosses: §6" + profile.getBossesKilled()));
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Blocks / Crops / Fish: §e" + profile.getBlocksBroken() + " §8/ §e"
                + profile.getCropsBroken() + " §8/ §e" + profile.getFishCaught()));
        if (McMMOIntegration.isEnabled()) {
            List<McMMOIntegration.SkillLevel> sk = new ArrayList<>(McMMOIntegration.collectSkillLevels(target));
            sk.sort(Comparator.comparingInt(McMMOIntegration.SkillLevel::level).reversed());
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§8  §dmcMMO skills"));
            int lim = Math.min(14, sk.size());
            for (int i = 0; i < lim; i++) {
                McMMOIntegration.SkillLevel s = sk.get(i);
                who.sendMessage(com.darkniightz.core.Messages.prefixed(
                        "§7  " + prettySkillName(s.skillName()) + " §8│ §d" + s.level()));
            }
        }
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Event wins (c / chat / hc): §f" + profile.getEventWinsCombat() + " §8/ §f"
                + profile.getEventWinsChat() + " §8/ §f" + profile.getEventWinsHardcore()));
        if (achievements != null) {
            achievements.ensureLoadedSync(profile.getUuid());
            int total = 0;
            int completed = 0;
            for (AchievementDefinition def : achievements.getDefinitions()) {
                if (def.isSecret()) {
                    continue;
                }
                total++;
                int tr = achievements.getTierReached(profile.getUuid(), def.getId());
                if (def.tierCount() > 0 && tr >= def.tierCount()) {
                    completed++;
                }
            }
            int pctChat = total <= 0 ? 0 : (int) Math.round(100.0 * completed / (double) total);
            who.sendMessage(com.darkniightz.core.Messages.prefixed(
                    "§8- §eAchievements §8│ §7" + completed + "§8/§7" + total + " §8(" + pctChat + "%) §7non-secret"));
        }
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§8- §dEvents (per-key)"));
        Map<String, PlayerProfileDAO.EventStatRecord> events = profiles.loadEventStats(profile.getUuid());
        if (events.isEmpty()) {
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§8No event participation recorded."));
            return;
        }
        for (var entry : events.entrySet()) {
            PlayerProfileDAO.EventStatRecord row = entry.getValue();
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§7" + humanize(entry.getKey()) + ": §fP§8=" + row.participated() + " §aW§8="
                    + row.won() + " §cL§8=" + row.lost()));
        }
    }

    private String formatDate(long epochMs) {
        if (epochMs <= 0L) {
            return "N/A";
        }
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
        df.setTimeZone(TimeZone.getDefault());
        return df.format(new Date(epochMs));
    }

    private String formatDuration(long ms) {
        if (ms <= 0L) {
            return "0m";
        }
        long seconds = ms / 1000L;
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    private String humanize(String key) {
        if (key == null || key.isBlank()) {
            return "Unknown";
        }
        String[] parts = key.split("[_\\-]");
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase(Locale.ROOT));
        }
        return ChatColor.stripColor(out.toString());
    }
}
