package com.darkniightz.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public final class SqlHealthCheck {
    private final DataSource dataSource;

    public SqlHealthCheck(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean isHealthy() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        } catch (Exception ignored) {
            return false;
        }
    }
}
