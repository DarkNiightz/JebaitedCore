package com.darkniightz.bot.db;

import com.darkniightz.bot.config.BotConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

public final class BotDatabase {
    private final HikariDataSource dataSource;

    public BotDatabase(BotConfig.Database cfg) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(cfg.jdbcUrl());
        hc.setUsername(cfg.username());
        hc.setPassword(cfg.password());
        hc.setMaximumPoolSize(cfg.maxPoolSize());
        hc.setPoolName("jebaited-bot-pool");
        this.dataSource = new HikariDataSource(hc);
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public void close() {
        dataSource.close();
    }
}
