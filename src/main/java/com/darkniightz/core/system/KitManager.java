package com.darkniightz.core.system;

import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.ranks.RankManager;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Manages donor kit definitions and cooldown logic.
 *
 * <p>Kit names match donor rank names: gold, diamond, legend, grandmaster.
 * Each higher tier can also claim kits from lower tiers (cumulative).</p>
 *
 * <p>Cooldown is per-kit-name and stored in PlayerProfile.kitCooldowns
 * (persisted to players.kit_cooldowns JSONB via PlayerProfileDAO).</p>
 */
public class KitManager {

    /** Validated range: 0 ms to 7 days. */
    public static final long MIN_COOLDOWN_MS = 0L;
    public static final long MAX_COOLDOWN_MS = 7L * 24 * 60 * 60 * 1000L;
    public static final long DEFAULT_COOLDOWN_MS = 24L * 60 * 60 * 1000L; // 24 h

    private final Plugin plugin;
    private final RankManager rankManager;

    /** Cooldown per kit in ms — loaded from config at startup. */
    private long cooldownMs;

    public KitManager(Plugin plugin, RankManager rankManager) {
        this.plugin = plugin;
        this.rankManager = rankManager;
        reload();
    }

    public void reload() {
        FileConfiguration cfg = plugin.getConfig();
        long raw = cfg.getLong("kits.cooldown_ms", DEFAULT_COOLDOWN_MS);
        if (raw < MIN_COOLDOWN_MS || raw > MAX_COOLDOWN_MS) {
            plugin.getLogger().warning("[KitManager] kits.cooldown_ms=" + raw
                    + " is out of range [0, " + MAX_COOLDOWN_MS + "]. Using default "
                    + DEFAULT_COOLDOWN_MS + " ms.");
            cooldownMs = DEFAULT_COOLDOWN_MS;
        } else {
            cooldownMs = raw;
        }
    }

    // -----------------------------------------------------------------
    // Access checks
    // -----------------------------------------------------------------

    /**
     * Returns the minimum donor rank required to use the named kit.
     * Returns {@code null} for unknown kit names.
     */
    public String requiredDonorRank(String kitName) {
        return switch (kitName == null ? "" : kitName.toLowerCase(Locale.ROOT)) {
            case "gold"        -> "gold";
            case "diamond"     -> "diamond";
            case "legend"      -> "legend";
            case "grandmaster" -> "grandmaster";
            default            -> null;
        };
    }

    /**
     * Returns true if {@code profile} is allowed to claim the given kit.
     * A player can claim a kit if their donor rank is at least the required rank.
     */
    public boolean canUseKit(PlayerProfile profile, String kitName) {
        String required = requiredDonorRank(kitName);
        if (required == null) return false;
        if (profile == null) return false;
        String donorRank = profile.getDonorRank();
        if (donorRank == null) return false;
        return rankManager.isAtLeast(donorRank, required);
    }

    // -----------------------------------------------------------------
    // Cooldown helpers
    // -----------------------------------------------------------------

    public long getCooldownMs() { return cooldownMs; }

    /**
     * Returns ms remaining on the cooldown for the given kit, or ≤ 0 if ready.
     */
    public long getRemainingCooldownMs(PlayerProfile profile, String kitName) {
        if (profile == null || kitName == null) return 0L;
        long lastUsed = profile.getKitLastUsed(kitName);
        if (lastUsed <= 0L) return 0L;
        long elapsed = System.currentTimeMillis() - lastUsed;
        return cooldownMs - elapsed;
    }

    public boolean isOnCooldown(PlayerProfile profile, String kitName) {
        return getRemainingCooldownMs(profile, kitName) > 0L;
    }

    // -----------------------------------------------------------------
    // Kit item definitions (hardcoded defaults — configurable via config)
    // -----------------------------------------------------------------

    /**
     * Returns the items for the given kit, or an empty list for unknown kits.
     * Items include a small food/recovery stack in every kit.
     */
    public List<ItemStack> getKitItems(String kitName) {
        return switch (kitName == null ? "" : kitName.toLowerCase(Locale.ROOT)) {
            case "gold"        -> buildGoldKit();
            case "diamond"     -> buildDiamondKit();
            case "legend"      -> buildLegendKit();
            case "grandmaster" -> buildGrandmasterKit();
            default            -> Collections.emptyList();
        };
    }

    // ----- Gold kit (iron-tier) -----
    private List<ItemStack> buildGoldKit() {
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(Material.IRON_HELMET));
        items.add(new ItemStack(Material.IRON_CHESTPLATE));
        items.add(new ItemStack(Material.IRON_LEGGINGS));
        items.add(new ItemStack(Material.IRON_BOOTS));
        items.add(new ItemStack(Material.IRON_SWORD));
        items.add(new ItemStack(Material.COOKED_BEEF, 8));
        items.add(new ItemStack(Material.BREAD, 8));
        return items;
    }

    // ----- Diamond kit (diamond + light enchants) -----
    private List<ItemStack> buildDiamondKit() {
        List<ItemStack> items = new ArrayList<>();
        items.add(enchant(new ItemStack(Material.DIAMOND_HELMET),     Enchantment.PROTECTION, 1, Enchantment.UNBREAKING, 1));
        items.add(enchant(new ItemStack(Material.DIAMOND_CHESTPLATE), Enchantment.PROTECTION, 1, Enchantment.UNBREAKING, 1));
        items.add(enchant(new ItemStack(Material.DIAMOND_LEGGINGS),   Enchantment.PROTECTION, 1, Enchantment.UNBREAKING, 1));
        items.add(enchant(new ItemStack(Material.DIAMOND_BOOTS),      Enchantment.PROTECTION, 1, Enchantment.UNBREAKING, 1));
        items.add(enchant(new ItemStack(Material.DIAMOND_SWORD),      Enchantment.SHARPNESS, 1));
        items.add(new ItemStack(Material.COOKED_BEEF, 16));
        items.add(new ItemStack(Material.GOLDEN_APPLE, 2));
        return items;
    }

    // ----- Legend kit (diamond + strong enchants) -----
    private List<ItemStack> buildLegendKit() {
        List<ItemStack> items = new ArrayList<>();
        items.add(enchant(new ItemStack(Material.DIAMOND_HELMET),     Enchantment.PROTECTION, 3, Enchantment.UNBREAKING, 2));
        items.add(enchant(new ItemStack(Material.DIAMOND_CHESTPLATE), Enchantment.PROTECTION, 3, Enchantment.UNBREAKING, 2));
        items.add(enchant(new ItemStack(Material.DIAMOND_LEGGINGS),   Enchantment.PROTECTION, 3, Enchantment.UNBREAKING, 2));
        items.add(enchant(new ItemStack(Material.DIAMOND_BOOTS),      Enchantment.PROTECTION, 3, Enchantment.UNBREAKING, 2));
        items.add(enchant(new ItemStack(Material.DIAMOND_SWORD),      Enchantment.SHARPNESS, 3, Enchantment.LOOTING, 2));
        // Bow with Infinity — needs 1 arrow
        ItemStack bow = new ItemStack(Material.BOW);
        bow.addEnchantment(Enchantment.POWER, 2);
        bow.addEnchantment(Enchantment.INFINITY, 1);
        items.add(bow);
        items.add(new ItemStack(Material.ARROW, 1));
        items.add(new ItemStack(Material.COOKED_BEEF, 16));
        items.add(new ItemStack(Material.GOLDEN_APPLE, 4));
        return items;
    }

    // ----- Grandmaster kit (netherite god-tier) -----
    private List<ItemStack> buildGrandmasterKit() {
        List<ItemStack> items = new ArrayList<>();
        items.add(enchant(new ItemStack(Material.NETHERITE_HELMET),
                Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3));
        items.add(enchant(new ItemStack(Material.NETHERITE_CHESTPLATE),
                Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3));
        items.add(enchant(new ItemStack(Material.NETHERITE_LEGGINGS),
                Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3));
        items.add(enchant(new ItemStack(Material.NETHERITE_BOOTS),
                Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.FEATHER_FALLING, 4));
        items.add(enchant(new ItemStack(Material.NETHERITE_SWORD),
                Enchantment.SHARPNESS, 5, Enchantment.LOOTING, 3, Enchantment.UNBREAKING, 3));
        ItemStack bow = new ItemStack(Material.BOW);
        bow.addEnchantment(Enchantment.POWER, 5);
        bow.addEnchantment(Enchantment.INFINITY, 1);
        bow.addEnchantment(Enchantment.FLAME, 1);
        items.add(bow);
        items.add(new ItemStack(Material.ARROW, 1));
        items.add(new ItemStack(Material.COOKED_BEEF, 16));
        items.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 2));
        return items;
    }

    // -----------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------

    /** Adds enchantments in (enc, lvl) pairs to an ItemStack and returns it. */
    private ItemStack enchant(ItemStack item, Object... pairs) {
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i] instanceof Enchantment enc && pairs[i + 1] instanceof Integer lvl) {
                item.addUnsafeEnchantment(enc, lvl);
            }
        }
        return item;
    }

    /** Returns the ordered list of available kit names for tab completion. */
    public static List<String> allKitNames() {
        return List.of("gold", "diamond", "legend", "grandmaster");
    }

    /** Formats a remaining cooldown duration as a human-readable string (e.g. "23h 14m"). */
    public static String formatDuration(long ms) {
        if (ms <= 0) return "0s";
        long seconds = ms / 1000L;
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }
}
