package com.darkniightz.core.shop;

import com.darkniightz.core.Messages;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.system.AuditLogService;
import com.darkniightz.core.system.EconomyManager;
import com.darkniightz.core.system.SoundCompat;
import com.darkniightz.core.world.WorldManager;
import com.darkniightz.main.JebaitedCore;
import com.darkniightz.main.database.DatabaseManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Server shop: DB-backed prices, buy/sell, rate limit, transaction log.
 */
public final class ShopManager {

    private final JebaitedCore plugin;
    private final EconomyManager economy;
    private final WorldManager worlds;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final Map<String, ShopPriceRow> byKey = new ConcurrentHashMap<>();
    private final Map<String, List<ShopPriceRow>> byCategory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastTxAt = new ConcurrentHashMap<>();

    public ShopManager(JebaitedCore plugin, EconomyManager economy, WorldManager worlds,
                       ProfileStore profiles, RankManager ranks) {
        this.plugin = plugin;
        this.economy = economy;
        this.worlds = worlds;
        this.profiles = profiles;
        this.ranks = ranks;
    }

    public void start() {
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null || !db.isEnabled()) {
            plugin.getLogger().warning("Shop: database disabled — /shop unavailable.");
            return;
        }
        try (Connection conn = db.getConnection()) {
            long count = countPrices(conn);
            if (count == 0 && plugin.getConfig().getBoolean("server_shop.seed_on_empty", true)) {
                seedDefaults(conn);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Shop: failed to init prices", e);
            return;
        }
        reload();
    }

    private long countPrices(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM server_shop_prices")) {
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private void seedDefaults(Connection conn) throws SQLException {
        List<ShopPriceRow> rows = ShopCatalog.buildDefaultRows();
        String sql = """
                INSERT INTO server_shop_prices (item_key, category, display_name, buy_price, sell_price, max_stack, sort_order, enabled)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (item_key) DO NOTHING
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (ShopPriceRow r : rows) {
                ps.setString(1, r.itemKey());
                ps.setString(2, r.category());
                ps.setString(3, r.displayName());
                ps.setDouble(4, r.buyPrice());
                ps.setDouble(5, r.sellPrice());
                ps.setInt(6, r.maxStack());
                ps.setInt(7, r.sortOrder());
                ps.setBoolean(8, r.enabled());
                ps.addBatch();
            }
            ps.executeBatch();
        }
        plugin.getLogger().info("Shop: seeded " + rows.size() + " default price rows.");
    }

    public void reload() {
        byKey.clear();
        byCategory.clear();
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null || !db.isEnabled()) return;
        validateRateLimitConfig();
        String sql = """
                SELECT item_key, category, display_name, buy_price, sell_price, max_stack, sort_order, enabled
                FROM server_shop_prices WHERE enabled = TRUE
                ORDER BY category, sort_order, item_key
                """;
        int loaded = 0;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ShopPriceRow row = new ShopPriceRow(
                        rs.getString("item_key"),
                        rs.getString("category"),
                        rs.getString("display_name"),
                        rs.getDouble("buy_price"),
                        rs.getDouble("sell_price"),
                        rs.getInt("max_stack"),
                        rs.getInt("sort_order"),
                        rs.getBoolean("enabled")
                );
                if (row.material() == null) continue;
                byKey.put(row.itemKey(), row);
                byCategory.computeIfAbsent(row.category().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(row);
                loaded++;
            }
            plugin.getLogger().info("Shop: loaded " + loaded + " price row(s) from database.");
            if (loaded == 0) {
                plugin.getLogger().warning("Shop: no enabled price rows — /shop unavailable until rows exist.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Shop: reload failed", e);
        }
        for (List<ShopPriceRow> list : byCategory.values()) {
            list.sort(Comparator.comparingInt(ShopPriceRow::sortOrder).thenComparing(ShopPriceRow::itemKey));
        }
    }

    /** Warn on silly config; effective limits still use {@link #minMsBetweenTx} (non-negative). */
    private void validateRateLimitConfig() {
        long def = plugin.getConfig().getLong("server_shop.rate_limit_ms", 120L);
        long donor = plugin.getConfig().getLong("server_shop.donor_rate_limit_ms", 0L);
        if (def < 0) {
            plugin.getLogger().warning("Shop: server_shop.rate_limit_ms is negative (" + def + "); effective minimum is 0.");
        }
        if (donor < 0) {
            plugin.getLogger().warning("Shop: server_shop.donor_rate_limit_ms is negative (" + donor + "); effective minimum is 0.");
        }
        if (def > 60_000L) {
            plugin.getLogger().warning("Shop: server_shop.rate_limit_ms is very large (" + def + " ms); confirm this is intentional.");
        }
        if (donor > 60_000L) {
            plugin.getLogger().warning("Shop: server_shop.donor_rate_limit_ms is very large (" + donor + " ms); confirm this is intentional.");
        }
    }

    public boolean isAvailable() {
        DatabaseManager db = plugin.getDatabaseManager();
        return db != null && db.isEnabled() && !byKey.isEmpty();
    }

    public ShopPriceRow getRow(String itemKey) {
        return byKey.get(itemKey);
    }

    public List<ShopPriceRow> rowsInCategory(String category) {
        if (category == null) return List.of();
        return List.copyOf(byCategory.getOrDefault(category.toLowerCase(Locale.ROOT), List.of()));
    }

    public static List<String> categoryIds() {
        return List.of("blocks", "farming", "mobs", "ores", "dyes", "music", "food", "decoration", "redstone");
    }

    public static String categoryTitle(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "blocks" -> "Blocks";
            case "farming" -> "Farming";
            case "mobs" -> "Mobs";
            case "ores" -> "Ores";
            case "dyes" -> "Dyes";
            case "music" -> "Music";
            case "food" -> "Food";
            case "decoration" -> "Decoration";
            case "redstone" -> "Redstone";
            default -> id;
        };
    }

    private long minMsBetweenTx(Player player) {
        long def = Math.max(0L, plugin.getConfig().getLong("server_shop.rate_limit_ms", 120L));
        long donor = Math.max(0L, plugin.getConfig().getLong("server_shop.donor_rate_limit_ms", 0L));
        if (player != null && profiles != null && ranks != null) {
            PlayerProfile p = profiles.getOrCreate(player, ranks.getDefaultGroup());
            if (p != null && p.getDonorRank() != null && !p.getDonorRank().isBlank()) {
                return donor;
            }
        }
        return def;
    }

    private boolean rateLimited(Player player) {
        long minGap = minMsBetweenTx(player);
        if (minGap <= 0L) {
            lastTxAt.put(player.getUniqueId(), System.currentTimeMillis());
            return false;
        }
        long now = System.currentTimeMillis();
        long prev = lastTxAt.put(player.getUniqueId(), now);
        return prev != 0L && now - prev < minGap;
    }

    public boolean canUseShop(Player player) {
        if (player == null) return false;
        if (!isAvailable()) {
            player.sendMessage(Messages.prefixed("§cThe shop is unavailable right now."));
            return false;
        }
        if (!worlds.isSmp(player)) {
            player.sendMessage(Messages.prefixed("§cYou can only use the shop in SMP."));
            return false;
        }
        GameMode gm = player.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) {
            player.sendMessage(Messages.prefixed("§cSwitch to Survival or Adventure to use the shop."));
            return false;
        }
        return true;
    }

    /**
     * @return false if action failed (message already sent)
     */
    public boolean buy(Player player, ShopPriceRow row, int requestedQty, boolean shiftLeft) {
        if (player == null || row == null) return false;
        if (!canUseShop(player)) return false;
        if (rateLimited(player)) {
            player.sendMessage(Messages.prefixed("§cYou're clicking too fast."));
            return false;
        }
        Material mat = row.material();
        if (mat == null) {
            player.sendMessage(Messages.prefixed("§cThat item is not available."));
            return false;
        }
        int max = Math.max(1, Math.min(row.maxStack(), 64));
        int qty = shiftLeft ? max : Math.min(requestedQty, max);
        if (qty < 1) qty = 1;
        double total = roundMoney(row.buyPrice() * qty);
        if (total <= 0D) {
            player.sendMessage(Messages.prefixed("§cInvalid price."));
            return false;
        }
        if (!economy.removeBalance(player, total)) {
            player.sendMessage(Messages.prefixed("§cYou don't have enough money. Need " + economy.format(total) + "."));
            return false;
        }
        ItemStack stack = new ItemStack(mat, qty);
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        if (!overflow.isEmpty()) {
            economy.addBalance(player, total);
            player.sendMessage(Messages.prefixed("§cNot enough inventory space."));
            return false;
        }
        playBuySound(player);
        logTransactionAsync(player.getUniqueId(), row.itemKey(), qty, row.buyPrice(), total, "BUY");
        auditShop(player, "[shop] BUY " + qty + "x " + row.itemKey() + " for " + economy.format(total));
        return true;
    }

    /**
     * @param sellAll if true, sell every matching stack in inventory
     */
    public boolean sell(Player player, ShopPriceRow row, boolean sellAll) {
        if (player == null || row == null) return false;
        if (!canUseShop(player)) return false;
        if (rateLimited(player)) {
            player.sendMessage(Messages.prefixed("§cYou're clicking too fast."));
            return false;
        }
        Material mat = row.material();
        if (mat == null) {
            player.sendMessage(Messages.prefixed("§cThat item is not available."));
            return false;
        }
        int have = countMaterial(player.getInventory(), mat);
        if (have <= 0) {
            player.sendMessage(Messages.prefixed("§cYou don't have any of that item."));
            return false;
        }
        int qty = sellAll ? have : 1;
        qty = Math.min(qty, have);
        double total = roundMoney(row.sellPrice() * qty);
        if (total <= 0D) {
            player.sendMessage(Messages.prefixed("§cCannot sell that item."));
            return false;
        }
        removeMaterial(player.getInventory(), mat, qty);
        economy.addBalance(player, total);
        playSellSound(player);
        logTransactionAsync(player.getUniqueId(), row.itemKey(), qty, row.sellPrice(), total, "SELL");
        auditShop(player, "[shop] SELL " + qty + "x " + row.itemKey() + " for " + economy.format(total));
        return true;
    }

    private static double roundMoney(double v) {
        return Math.round(Math.max(0D, v) * 100D) / 100D;
    }

    /** Main + hotbar + off-hand only (not armor). */
    private static int countMaterial(PlayerInventory inv, Material mat) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null && stack.getType() == mat) {
                n += stack.getAmount();
            }
        }
        ItemStack off = inv.getItemInOffHand();
        if (off != null && off.getType() == mat) {
            n += off.getAmount();
        }
        return n;
    }

    private static void removeMaterial(PlayerInventory inv, Material mat, int toRemove) {
        int left = toRemove;
        for (int i = 0; i < 36 && left > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType() != mat) continue;
            int take = Math.min(left, stack.getAmount());
            int na = stack.getAmount() - take;
            if (na <= 0) {
                inv.setItem(i, null);
            } else {
                stack.setAmount(na);
            }
            left -= take;
        }
        if (left > 0) {
            ItemStack off = inv.getItemInOffHand();
            if (off != null && off.getType() == mat) {
                int take = Math.min(left, off.getAmount());
                int na = off.getAmount() - take;
                if (na <= 0) {
                    inv.setItemInOffHand(null);
                } else {
                    off.setAmount(na);
                }
            }
        }
    }

    private void playBuySound(Player player) {
        SoundCompat.play(player.getWorld(), player.getLocation(), 0.35f, 1.2f,
                Sound.ENTITY_EXPERIENCE_ORB_PICKUP, "ENTITY_EXPERIENCE_ORB_PICKUP");
    }

    private void playSellSound(Player player) {
        SoundCompat.play(player.getWorld(), player.getLocation(), 0.35f, 1.0f,
                Sound.ENTITY_VILLAGER_TRADE, "ENTITY_VILLAGER_TRADE");
    }

    private void logTransactionAsync(UUID uuid, String itemKey, int qty, double unitPrice, double total, String action) {
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null || !db.isEnabled()) return;
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = """
                    INSERT INTO shop_transactions (player_uuid, item_key, quantity, unit_price, total, action)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, itemKey);
                ps.setInt(3, qty);
                ps.setDouble(4, unitPrice);
                ps.setDouble(5, total);
                ps.setString(6, action);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Shop: transaction log failed", e);
                var feed = plugin.getDebugFeedManager();
                if (feed != null) {
                    String detail = e.getMessage() == null ? "unknown error" : e.getMessage();
                    feed.recordSystem("Shop: transaction log failed", List.of("§7" + detail));
                }
            }
        });
    }

    private void auditShop(Player player, String detail) {
        AuditLogService audit = plugin.getAuditLogService();
        if (audit == null) return;
        try {
            audit.logCommand(player.getUniqueId(), player.getName(), detail);
        } catch (Exception ignored) {
        }
    }
}
