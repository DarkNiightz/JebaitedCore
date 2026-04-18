package com.darkniightz.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;

/** Persists signed webhook deliveries for ops / panel (best-effort, never throws). */
public final class IntegrationAuditDao {
    private final DataSource dataSource;

    public IntegrationAuditDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void insert(String eventType, String correlationId, String payloadJson) {
        String sql =
                "INSERT INTO integration_audit_log (source_service, event_type, correlation_id, actor_id, target_id, payload_json) "
                        + "VALUES ('discord_bot', ?, ?, NULL, NULL, ?)";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventType);
            ps.setString(2, correlationId == null ? "" : correlationId);
            String body = payloadJson == null ? "" : payloadJson;
            if (body.length() > 8000) {
                body = body.substring(0, 8000) + "…";
            }
            ps.setString(3, body);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }
}
