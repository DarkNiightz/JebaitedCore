package com.darkniightz.main.database;

import com.darkniightz.main.JebaitedCore;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;

/**
 * Manages the connection to the PostgreSQL database using HikariCP for connection pooling.
 */
public class DatabaseManager {

    private final JebaitedCore plugin;
    private HikariDataSource dataSource;
    private final boolean enabled;

    public DatabaseManager(JebaitedCore plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("database.enabled", false);
    }

    /**
     * Initializes the database connection pool.
     */
    public void connect() {
        if (!enabled) {
            plugin.getLogger().info("Database is disabled in the configuration. Skipping connection.");
            return;
        }

        try {
            FileConfiguration config = plugin.getConfig();
            HikariConfig hikariConfig = new HikariConfig();

            // Set JDBC URL for PostgreSQL
            hikariConfig.setJdbcUrl(String.format("jdbc:postgresql://%s:%d/%s",
                    config.getString("database.host"),
                    config.getInt("database.port"),
                    config.getString("database.database")));

            // Set credentials
            hikariConfig.setUsername(config.getString("database.username"));
            hikariConfig.setPassword(config.getString("database.password"));

            // Set connection pool settings from config
            hikariConfig.setMaximumPoolSize(config.getInt("database.pool-settings.maximum-pool-size", 10));
            hikariConfig.setMinimumIdle(config.getInt("database.pool-settings.minimum-idle", 5));
            hikariConfig.setConnectionTimeout(config.getInt("database.pool-settings.connection-timeout", 30000));
            hikariConfig.setIdleTimeout(config.getInt("database.pool-settings.idle-timeout", 600000));
            hikariConfig.setMaxLifetime(config.getInt("database.pool-settings.max-lifetime", 1800000));

            // Recommended settings for performance and reliability
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            dataSource = new HikariDataSource(hikariConfig);
            plugin.getLogger().info("Database connection pool successfully initialized.");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not initialize database connection pool!", e);
            dataSource = null;
        }
    }

    /**
     * Closes the database connection pool.
     */
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection pool closed.");
        }
    }

    /**
     * Gets a connection from the pool.
     *
     * @return A database connection.
     * @throws SQLException if a database access error occurs.
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Database is not connected or connection pool is not initialized.");
        }
        return dataSource.getConnection();
    }

    /**
     * @return true if the database is enabled in the config, false otherwise.
     */
    public boolean isEnabled() {
        return enabled;
    }
}
