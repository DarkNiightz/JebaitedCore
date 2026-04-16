package com.darkniightz.core.achievements;

import com.darkniightz.core.achievements.AchievementDAO.AchievementRow;
import com.darkniightz.core.achievements.AchievementDefinition.AchievementTier;
import com.darkniightz.core.achievements.AchievementDefinition.AchievementType;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.system.TagCustomizationManager;
import com.darkniightz.main.JebaitedCore;
import com.darkniightz.main.PlayerProfileDAO;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Central manager for the achievement / milestone system.
 *
 * <p>Definitions are loaded from config.yml at startup. Per-player progress lives in a
 * write-through cache: increments happen synchronously on the main thread; DB flushes
 * are done asynchronously on a periodic schedule and on player quit.</p>
 */
public final class AchievementManager {

    // ── Constants ────────────────────────────────────────────────────────────
    static final int  MAX_REWARD_COINS  = 5_000;
    static final int  MAX_TAG_TEXT_LEN  = 48;
    /** Flush dirty rows every 5 minutes. */
    private static final long FLUSH_INTERVAL_TICKS = 20L * 60 * 5;

    // ── State ─────────────────────────────────────────────────────────────────
    private final JebaitedCore  plugin;
    private final AchievementDAO dao;
    private final ProfileStore  profiles;
    private final RankManager   ranks;
    private final Logger        log;

    /** All loaded achievement definitions, keyed by id. Insertion-ordered for menu. */
    private final LinkedHashMap<String, AchievementDefinition> definitions = new LinkedHashMap<>();

    /** Per-player progress cache. UUID → (achievementId → row). */
    private final ConcurrentHashMap<UUID, Map<String, AchievementRow>> cache = new ConcurrentHashMap<>();

    /** UUIDs with unsaved changes. */
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

    private int flushTaskId = -1;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public AchievementManager(JebaitedCore plugin, AchievementDAO dao, ProfileStore profiles, RankManager ranks) {
        this.plugin   = plugin;
        this.dao      = dao;
        this.profiles = profiles;
        this.ranks    = ranks;
        this.log      = plugin.getLogger();
        loadDefinitions();
    }

    public void start() {
        if (flushTaskId != -1) return;
        flushTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin,
            this::flushAllDirty,
            FLUSH_INTERVAL_TICKS,
            FLUSH_INTERVAL_TICKS
        ).getTaskId();
    }

    public void stop() {
        if (flushTaskId != -1) {
            Bukkit.getScheduler().cancelTask(flushTaskId);
            flushTaskId = -1;
        }
        flushAll();
    }

    // ── Cache management ──────────────────────────────────────────────────────

    /** Async-load a player's rows from DB into cache (no-op if already cached). */
    public void loadPlayer(UUID uuid) {
        if (cache.containsKey(uuid)) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, AchievementRow> rows = dao.loadAll(uuid);
            cache.put(uuid, rows);
        });
    }

    /** Flush any dirty rows then evict from cache. */
    public void unloadPlayer(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            flushPlayer(uuid);
            cache.remove(uuid);
            dirty.remove(uuid);
        });
    }

    // ── Progress API ──────────────────────────────────────────────────────────

    /**
     * Increments progress for the given type by {@code amount}. Must be called on the main thread.
     * Checks tier thresholds and fires rewards if thresholds are crossed.
     */
    public void increment(UUID uuid, AchievementType type, long amount) {
        if (amount <= 0) return;
        for (AchievementDefinition def : definitions.values()) {
            if (def.getType() != type) continue;
            Map<String, AchievementRow> playerRows = cache.get(uuid);
            if (playerRows == null) continue; // player not loaded yet

            AchievementRow row = playerRows.getOrDefault(
                def.getId(),
                new AchievementRow(def.getId(), 0, 0, 0)
            );

            // Already fully completed — nothing to do
            if (row.tierReached() >= def.tierCount()) continue;

            long newProgress = row.progress() + amount;
            AchievementRow updated = row.withProgress(newProgress);

            // Check if any new tiers were crossed
            for (int i = row.tierReached(); i < def.tierCount(); i++) {
                AchievementTier tier = def.tier(i);
                if (newProgress >= tier.threshold()) {
                    long unlockAt = System.currentTimeMillis();
                    updated = updated.withTier(i + 1, unlockAt);
                    handleTierUnlock(uuid, def, tier);
                } else {
                    break; // thresholds are ascending
                }
            }

            playerRows.put(def.getId(), updated);
            dirty.add(uuid);
        }
    }

    /** Returns current progress for a specific achievement. 0 if not cached/started. */
    public long getProgress(UUID uuid, String achievementId) {
        Map<String, AchievementRow> playerRows = cache.get(uuid);
        if (playerRows == null) return 0L;
        AchievementRow row = playerRows.get(achievementId);
        return row == null ? 0L : row.progress();
    }

    /** Returns the tier index (1-based count of tiers reached, 0 = none). */
    public int getTierReached(UUID uuid, String achievementId) {
        Map<String, AchievementRow> playerRows = cache.get(uuid);
        if (playerRows == null) return 0;
        AchievementRow row = playerRows.get(achievementId);
        return row == null ? 0 : row.tierReached();
    }

    // ── Definitions ───────────────────────────────────────────────────────────

    public Collection<AchievementDefinition> getDefinitions() {
        return definitions.values();
    }

    public AchievementDefinition getDefinition(String id) {
        return definitions.get(id);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void loadDefinitions() {
        definitions.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("achievements.categories");
        if (root == null) {
            // Server's config.yml predates the achievements section — fall back to jar defaults
            log.info("[Achievements] 'achievements.categories' missing from config.yml — loading from jar defaults.");
            try (java.io.InputStream is = plugin.getResource("config.yml")) {
                if (is != null) {
                    org.bukkit.configuration.file.YamlConfiguration jarCfg =
                        org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                            new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8));
                    root = jarCfg.getConfigurationSection("achievements.categories");
                }
            } catch (Exception e) {
                log.warning("[Achievements] Could not read jar defaults: " + e.getMessage());
            }
        }
        if (root == null) {
            log.warning("[Achievements] No 'achievements.categories' found in config or jar defaults — no achievements loaded.");
            return;
        }

        int loaded = 0;
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;

            String displayName  = sec.getString("display_name", id);
            String description  = sec.getString("description", "");
            String typeStr      = sec.getString("type", "");
            boolean secret      = sec.getBoolean("secret", false);

            AchievementType type;
            try {
                type = AchievementType.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warning("[Achievements] Unknown type '" + typeStr + "' for achievement '" + id + "' — skipping.");
                continue;
            }

            List<?> tierList = sec.getList("tiers");
            if (tierList == null || tierList.isEmpty()) {
                log.warning("[Achievements] Achievement '" + id + "' has no tiers — skipping.");
                continue;
            }

            List<AchievementTier> tiers = new ArrayList<>();
            long prevThreshold = 0;
            boolean valid = true;
            for (int i = 0; i < tierList.size(); i++) {
                Object obj = tierList.get(i);
                if (!(obj instanceof Map)) { valid = false; break; }
                Map<?, ?> m = (Map<?, ?>) obj;

                String label      = getString(m, "label", "Tier " + (i + 1));
                long   threshold  = getLong(m, "threshold", 0);
                String rewardType = getString(m, "reward_type", "coins").toLowerCase();
                String rewardValue= getString(m, "reward_value", "0");

                if (threshold <= 0) {
                    log.warning("[Achievements] Tier " + i + " of '" + id + "' has threshold <= 0 — skipping achievement.");
                    valid = false; break;
                }
                if (threshold <= prevThreshold) {
                    log.warning("[Achievements] Tier " + i + " of '" + id + "' threshold not ascending — skipping.");
                    valid = false; break;
                }
                if (!rewardType.equals("tag") && !rewardType.equals("coins") && !rewardType.equals("cosmetic")) {
                    log.warning("[Achievements] Unknown reward_type '" + rewardType + "' for '" + id + "' tier " + i + " — skipping.");
                    valid = false; break;
                }
                if (rewardType.equals("tag") && rewardValue.length() > MAX_TAG_TEXT_LEN) {
                    log.warning("[Achievements] Tag too long (" + rewardValue.length() + " > " + MAX_TAG_TEXT_LEN + ") for '" + id + "' tier " + i + " — truncating.");
                    rewardValue = rewardValue.substring(0, MAX_TAG_TEXT_LEN);
                }
                if (rewardType.equals("coins")) {
                    try {
                        int coins = Integer.parseInt(rewardValue);
                        if (coins > MAX_REWARD_COINS) {
                            log.warning("[Achievements] Coins reward capped at " + MAX_REWARD_COINS + " for '" + id + "' tier " + i);
                            rewardValue = String.valueOf(MAX_REWARD_COINS);
                        }
                    } catch (NumberFormatException e) {
                        log.warning("[Achievements] Invalid coins value '" + rewardValue + "' for '" + id + "' tier " + i + " — skipping.");
                        valid = false; break;
                    }
                }

                String tagText = rewardType.equals("tag") ? rewardValue : null;
                tiers.add(new AchievementTier(i, label, threshold, tagText, rewardType, rewardValue));
                prevThreshold = threshold;
            }

            if (!valid || tiers.isEmpty()) continue;
            definitions.put(id, new AchievementDefinition(id, displayName, description, type, tiers, secret));
            loaded++;
        }
        log.info("[Achievements] Loaded " + loaded + " achievement(s).");
    }

    private void handleTierUnlock(UUID uuid, AchievementDefinition def, AchievementTier tier) {
        // Grant reward
        switch (tier.rewardType()) {
            case "tag" -> {
                TagCustomizationManager tcm = plugin.getTagCustomizationManager();
                if (tcm != null) tcm.unlockAchievementTag(uuid, tier.tagText());
            }
            case "coins" -> {
                try {
                    int coins = Integer.parseInt(tier.rewardValue());
                    PlayerProfile profile = profiles.get(uuid);
                    if (profile != null) {
                        profile.addCosmeticCoins(coins);
                        profiles.saveDeferred(uuid);
                    }
                } catch (NumberFormatException ignored) {}
            }
            case "cosmetic" -> {
                final String cosmeticKey = tier.rewardValue();
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        plugin.getPlayerProfileDAO().unlockCosmetic(uuid, cosmeticKey, "tag");
                    } catch (Exception e) {
                        log.warning("[Achievements] Failed to unlock cosmetic '" + cosmeticKey + "' for " + uuid + ": " + e.getMessage());
                    }
                });
            }
        }

        // Record voucher async
        final AchievementTier ftier = tier;
        final String fid = def.getId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                dao.grantVoucher(uuid, fid, ftier.index(), ftier.rewardType(), ftier.rewardValue());
            } catch (Exception ignored) {}
        });

        // Notify player (must be on main thread — caller is already on main thread)
        notifyUnlock(uuid, def, tier);

        // Broadcast non-secret achievements
        if (!def.isSecret()) {
            Player player = Bukkit.getPlayer(uuid);
            String name = player != null ? player.getName() : uuid.toString();
            Component broadcast = Component.text()
                .append(Component.text("✦ ", NamedTextColor.GOLD))
                .append(Component.text(name, NamedTextColor.YELLOW))
                .append(Component.text(" unlocked ", NamedTextColor.GRAY))
                .append(Component.text(def.getDisplayName(), NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(" — " + tier.label(), NamedTextColor.WHITE))
                .build();
            Bukkit.broadcast(broadcast);
        }
    }

    private void notifyUnlock(UUID uuid, AchievementDefinition def, AchievementTier tier) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;

        player.sendMessage(Component.text()
            .append(Component.text("✦ Achievement Unlocked! ", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.text(def.getDisplayName() + " — " + tier.label(), NamedTextColor.YELLOW))
            .build());

        try {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        } catch (Exception ignored) {}
    }

    // ── Flush helpers ──────────────────────────────────────────────────────────

    private void flushPlayer(UUID uuid) {
        Map<String, AchievementRow> rows = cache.get(uuid);
        if (rows == null || !dirty.contains(uuid)) return;
        dao.upsertAll(uuid, rows);
        dirty.remove(uuid);
    }

    private void flushAllDirty() {
        for (UUID uuid : new HashSet<>(dirty)) {
            flushPlayer(uuid);
        }
    }

    private void flushAll() {
        // Called on shutdown — plugin is disabled, cannot schedule tasks. Flush synchronously.
        for (UUID uuid : cache.keySet()) {
            flushPlayer(uuid);
        }
    }

    // ── Config helpers (avoid Map<?,?> wildcard pain) ─────────────────────────

    private static String getString(Map<?, ?> m, String key, String def) {
        Object v = m.get(key);
        return v != null ? v.toString() : def;
    }

    private static long getLong(Map<?, ?> m, String key, long def) {
        Object v = m.get(key);
        if (v == null) return def;
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return def; }
    }
}
