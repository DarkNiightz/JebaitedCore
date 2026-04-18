package com.darkniightz.core.gui;

import com.darkniightz.core.eventmode.ChatGameManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.system.McMMOIntegration;
import com.darkniightz.main.PlayerProfileDAO;
import org.bukkit.ChatColor;
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

public class StatsMenu extends BaseMenu {
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final OfflinePlayer target;

    public StatsMenu(Plugin plugin, ProfileStore profiles, RankManager ranks, OfflinePlayer target) {
        super(plugin, "§8Player Stats", 45);
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

        inventory.setItem(13, buildHeadItem(profile));
        inventory.setItem(29, buildHubStatsItem(profile));
        inventory.setItem(31, buildSmpStatsItem(profile));
        inventory.setItem(33, buildEventStatsItem(profile));
    }

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
        PlayerProfile profile = profiles.getOrCreate(target, ranks.getDefaultGroup());
        if (profile == null) {
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§cCould not load stats right now."));
            return true;
        }

        if (slot == 13) {
            printAllStatsToChat(who, profile);
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Printed full stats in chat."));
            return true;
        }
        if (slot == 29 || slot == 31 || slot == 33) {
            printCategoryToChat(who, profile, slot);
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Hover over this item for the detailed breakdown."));
            return true;
        }
        return true;
    }

    private void printCategoryToChat(Player who, PlayerProfile profile, int slot) {
        if (slot == 29) {
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§8- §bHub"));
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Messages Sent: §a" + profile.getMessagesSent()));
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Commands Sent: §a" + profile.getCommandsSent()));
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Cosmetic Coins: §6" + profile.getCosmeticCoins()));
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Playtime: §f" + formatDuration(profile.getPlaytimeMs())));
            return;
        }
        if (slot == 31) {
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§8- §2SMP"));
            Integer power = McMMOIntegration.getPowerLevel(target);
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§7mcMMO Level: " + (power == null ? "§8N/A" : "§d" + power)));
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Kills: §c" + profile.getKills()));
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Deaths: §c" + profile.getDeaths()));
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Mobs Killed: §a" + profile.getMobsKilled()));
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Bosses Killed: §6" + profile.getBossesKilled()));
            return;
        }
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§8- §dEvents"));
        Map<String, PlayerProfileDAO.EventStatRecord> events = profiles.loadEventStats(profile.getUuid());
        if (events.isEmpty()) {
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§8No event participation recorded."));
            return;
        }
        for (var entry : events.entrySet()) {
            PlayerProfileDAO.EventStatRecord row = entry.getValue();
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§7" + humanize(entry.getKey()) + ": §fP§8=" + row.participated() + " §aW§8=" + row.won() + " §cL§8=" + row.lost()));
        }
    }

    private ItemStack buildHeadItem(PlayerProfile profile) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (target != null) {
            meta.setOwningPlayer(target);
        }
        String name = profile.getName() == null ? profile.getUuid().toString().substring(0, 8) : profile.getName();
        meta.setDisplayName("§e" + name + " §7(Click for full chat dump)");
        meta.setLore(List.of(
                "§7Rank: §b" + profile.getPrimaryRank(),
                "§7First Joined: §f" + formatDate(profile.getFirstJoined())
        ));
        skull.setItemMeta(meta);
        return skull;
    }

    private ItemStack buildHubStatsItem(PlayerProfile profile) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Messages Sent: §a" + profile.getMessagesSent());
        lore.add("§7Commands Sent: §a" + profile.getCommandsSent());
        lore.add("§7Cosmetic Coins: §6" + profile.getCosmeticCoins());
        lore.add("§7Playtime: §f" + formatDuration(profile.getPlaytimeMs()));
        lore.add("§7First Joined: §f" + formatDate(profile.getFirstJoined()));
        return new ItemBuilder(Material.BOOK)
                .name("§bHub Stats")
                .lore(lore)
                .build();
    }

    private ItemStack buildSmpStatsItem(PlayerProfile profile) {
        Integer power = McMMOIntegration.getPowerLevel(target);
        List<String> lore = new ArrayList<>();
        lore.add("§7mcMMO Level: " + (power == null ? "§8N/A" : "§d" + power));
        lore.add("§7Kills: §c" + profile.getKills());
        lore.add("§7Deaths: §c" + profile.getDeaths());
        lore.add("§7Mobs Killed: §a" + profile.getMobsKilled());
        lore.add("§7Bosses Killed: §6" + profile.getBossesKilled());
        return new ItemBuilder(Material.DIAMOND_SWORD)
                .name("§2SMP Stats")
                .lore(lore)
                .build();
    }

    private ItemStack buildEventStatsItem(PlayerProfile profile) {
        Map<String, PlayerProfileDAO.EventStatRecord> stored = profiles.loadEventStats(profile.getUuid());
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

        List<String> lore = new ArrayList<>();
        if (merged.isEmpty()) {
            lore.add("§8No event stats yet.");
        } else {
            for (var entry : merged.entrySet()) {
                String label = humanize(entry.getKey());
                PlayerProfileDAO.EventStatRecord row = entry.getValue();
                lore.add("§7" + label + ": §fP§8=" + row.participated() + " §aW§8=" + row.won() + " §cL§8=" + row.lost());
            }
        }
        return new ItemBuilder(Material.NETHER_STAR)
                .name("§dEvent Stats")
                .lore(lore)
                .build();
    }

    private void printAllStatsToChat(Player who, PlayerProfile profile) {
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§6§l=== Full Stats: §e" + profile.getName() + " §6§l==="));
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Rank: §b" + profile.getPrimaryRank()));
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§7First Joined: §f" + formatDate(profile.getFirstJoined())));
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Playtime: §f" + formatDuration(profile.getPlaytimeMs())));
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§8- §bHub"));
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Messages Sent: §a" + profile.getMessagesSent()));
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Commands Sent: §a" + profile.getCommandsSent()));
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Cosmetic Coins: §6" + profile.getCosmeticCoins()));
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§8- §2SMP"));
        Integer power = McMMOIntegration.getPowerLevel(target);
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§7mcMMO Level: " + (power == null ? "§8N/A" : "§d" + power)));
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Kills: §c" + profile.getKills() + " §8| §7Deaths: §c" + profile.getDeaths()));
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Mobs Killed: §a" + profile.getMobsKilled() + " §8| §7Bosses Killed: §6" + profile.getBossesKilled()));
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§8- §dEvents"));
        Map<String, PlayerProfileDAO.EventStatRecord> events = profiles.loadEventStats(profile.getUuid());
        if (events.isEmpty()) {
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§8No event participation recorded."));
            return;
        }
        for (var entry : events.entrySet()) {
            PlayerProfileDAO.EventStatRecord row = entry.getValue();
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§7" + humanize(entry.getKey()) + ": §fP§8=" + row.participated() + " §aW§8=" + row.won() + " §cL§8=" + row.lost()));
        }
    }

    private String formatDate(long epochMs) {
        if (epochMs <= 0L) return "N/A";
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
        return ChatColor.stripColor(out.toString());
    }
}