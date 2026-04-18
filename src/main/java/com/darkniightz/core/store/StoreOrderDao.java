package com.darkniightz.core.store;

import com.darkniightz.main.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class StoreOrderDao {
    private final DatabaseManager db;
    private final Logger log;

    public StoreOrderDao(DatabaseManager db, Logger log) {
        this.db = db;
        this.log = log;
    }

    /**
     * @return true if this webhook is the first time we see this session (idempotent guard).
     */
    public boolean tryClaimNewOrder(
            String sessionId,
            UUID player,
            String packageId,
            int amountCents,
            String currency,
            String paymentIntentId) {
        String sql =
                "INSERT INTO store_orders (stripe_session_id, stripe_payment_intent, minecraft_uuid, package_id, amount_cents, currency, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'paid') ON CONFLICT (stripe_session_id) DO NOTHING RETURNING id;";
        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setString(2, paymentIntentId);
            ps.setString(3, player.toString());
            ps.setString(4, packageId);
            ps.setInt(5, amountCents);
            ps.setString(6, currency);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "store order claim failed", e);
            return false;
        }
    }

    public void markFulfilled(String sessionId) {
        String sql = "UPDATE store_orders SET status = 'fulfilled', fulfilled_at = NOW() WHERE stripe_session_id = ?;";
        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.executeUpdate();
        } catch (Exception e) {
            log.log(Level.WARNING, "store order fulfill flag failed", e);
        }
    }

    public void markError(String sessionId, String err) {
        String sql = "UPDATE store_orders SET status = 'error', error_message = ? WHERE stripe_session_id = ?;";
        try (Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, err == null ? "error" : err.substring(0, Math.min(2000, err.length())));
            ps.setString(2, sessionId);
            ps.executeUpdate();
        } catch (Exception e) {
            log.log(Level.WARNING, "store order error update failed", e);
        }
    }
}
