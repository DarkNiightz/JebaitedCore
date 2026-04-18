package com.darkniightz.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public final class ActivityChartDao {
    private final DataSource dataSource;

    public ActivityChartDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record Point(long sampledAtEpochMs, int online) {}

    public List<Point> last24Hours() {
        List<Point> out = new ArrayList<>();
        String sql =
                "SELECT EXTRACT(EPOCH FROM sampled_at) * 1000 AS ms, online_count FROM discord_activity_sample "
                        + "WHERE sampled_at > NOW() - INTERVAL '24 hours' ORDER BY sampled_at ASC;";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new Point(rs.getLong("ms"), rs.getInt("online_count")));
            }
        } catch (Exception ignored) {
        }
        return out;
    }
}
