package com.darkniightz.core.shop;

import com.darkniightz.main.database.DatabaseManager;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persistence for I2 player shop listings (ROADMAP). Minimal insert/list API — GUIs and purchase flow come later.
 */
public final class PlayerShopListingDAO {

    public record ListingRow(
            long id,
            UUID sellerUuid,
            String world,
            int x,
            int y,
            int z,
            long priceCoins,
            int quantity,
            byte[] itemBytes,
            boolean active
    ) {}

    private final DatabaseManager db;
    private final Logger log;

    public PlayerShopListingDAO(DatabaseManager db, Logger log) {
        this.db = db;
        this.log = log;
    }

    /** Serializes the item for storage (Paper {@link ItemStack#serializeAsBytes()}). */
    public long insertListing(UUID seller, Location chest, ItemStack offer, long priceCoins, int quantity) {
        if (seller == null || chest == null || offer == null || offer.getType().isAir()) {
            return -1L;
        }
        byte[] bytes = offer.serializeAsBytes();
        String world = chest.getWorld() != null ? chest.getWorld().getName() : "";
        String sql =
                "INSERT INTO player_shop_listings (seller_uuid, chest_world, chest_x, chest_y, chest_z, price_coins, quantity, item_bytes, active) "
                        + "VALUES (?,?,?,?,?,?,?,?,TRUE) RETURNING id;";
        try (Connection c = db.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, seller.toString());
            ps.setString(2, world);
            ps.setInt(3, chest.getBlockX());
            ps.setInt(4, chest.getBlockY());
            ps.setInt(5, chest.getBlockZ());
            ps.setLong(6, Math.max(0L, priceCoins));
            ps.setInt(7, Math.max(0, quantity));
            ps.setBytes(8, bytes);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            log.log(Level.WARNING, "player_shop_listings insert failed: " + e.getMessage(), e);
        }
        return -1L;
    }

    public List<ListingRow> listActiveForSeller(UUID seller) {
        List<ListingRow> out = new ArrayList<>();
        if (seller == null) {
            return out;
        }
        String sql =
                "SELECT id, seller_uuid, chest_world, chest_x, chest_y, chest_z, price_coins, quantity, item_bytes, active "
                        + "FROM player_shop_listings WHERE seller_uuid = ? AND active = TRUE ORDER BY created_at DESC;";
        try (Connection c = db.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, seller.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(
                            new ListingRow(
                                    rs.getLong("id"),
                                    UUID.fromString(rs.getString("seller_uuid")),
                                    rs.getString("chest_world"),
                                    rs.getInt("chest_x"),
                                    rs.getInt("chest_y"),
                                    rs.getInt("chest_z"),
                                    rs.getLong("price_coins"),
                                    rs.getInt("quantity"),
                                    rs.getBytes("item_bytes"),
                                    rs.getBoolean("active")));
                }
            }
        } catch (SQLException e) {
            log.log(Level.WARNING, "player_shop_listings listActiveForSeller: " + e.getMessage(), e);
        }
        return out;
    }

    public boolean deactivate(long listingId, UUID seller) {
        String sql = "UPDATE player_shop_listings SET active = FALSE WHERE id = ? AND seller_uuid = ?;";
        try (Connection c = db.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, listingId);
            ps.setString(2, seller.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.log(Level.WARNING, "player_shop_listings deactivate: " + e.getMessage(), e);
            return false;
        }
    }
}
