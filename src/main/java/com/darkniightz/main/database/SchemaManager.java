package com.darkniightz.main.database;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Versioned schema migration runner.
 *
 * <p>Migrations are SQL files stored in {@code resources/db/} and listed in order
 * in {@code resources/db/migrations.index}. Each migration is recorded in the
 * {@code schema_migrations} table and runs exactly once — never again on restarts.
 *
 * <p>To add a new migration:
 * <ol>
 *   <li>Create {@code src/main/resources/db/VXXX__description.sql}</li>
 *   <li>Append the filename to {@code src/main/resources/db/migrations.index}</li>
 * </ol>
 */
public class SchemaManager {

    private static final String MIGRATIONS_DIR = "db/";
    private static final String MIGRATIONS_INDEX = MIGRATIONS_DIR + "migrations.index";

    private final DatabaseManager db;
    private final Logger logger;

    public SchemaManager(DatabaseManager db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }

    /**
     * Runs all pending migrations. Throws {@link RuntimeException} on failure so the
     * caller can abort startup — a partial schema is worse than not starting at all.
     */
    public void runMigrations() {
        if (!db.isEnabled()) return;

        try (Connection conn = db.getConnection()) {
            ensureMigrationsTable(conn);
            List<String> applied = loadApplied(conn);
            List<String> pending = discoverMigrations();

            int ran = 0;
            for (String name : pending) {
                if (applied.contains(name)) continue;
                logger.info("[Schema] Applying " + name + " ...");
                String sql = readResource(MIGRATIONS_DIR + name);
                applyMigration(conn, name, sql);
                ran++;
            }

            if (ran == 0) {
                logger.info("[Schema] Schema is up to date (" + applied.size() + " migration(s) already applied).");
            } else {
                logger.info("[Schema] Applied " + ran + " migration(s) successfully.");
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[Schema] Migration failed", e);
            throw new RuntimeException("Schema migration failed — aborting startup", e);
        }
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private void ensureMigrationsTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS schema_migrations (" +
                "  id         SERIAL      PRIMARY KEY," +
                "  name       VARCHAR(255) NOT NULL UNIQUE," +
                "  applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()" +
                ");"
            );
        }
    }

    private List<String> loadApplied(Connection conn) throws SQLException {
        List<String> applied = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM schema_migrations ORDER BY id ASC")) {
            while (rs.next()) {
                applied.add(rs.getString("name"));
            }
        }
        return applied;
    }

    /**
     * Reads the migration manifest (one filename per line, # = comment) and returns
     * migration filenames in the declared order.
     */
    private List<String> discoverMigrations() {
        List<String> names = new ArrayList<>();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(MIGRATIONS_INDEX)) {
            if (in == null) {
                logger.warning("[Schema] migrations.index not found — no migrations to apply.");
                return names;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        names.add(line);
                    }
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "[Schema] Failed to read migrations.index", e);
        }
        return names;
    }

    private void applyMigration(Connection conn, String name, String sql) throws SQLException {
        boolean prevAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (Statement stmt = conn.createStatement()) {
            for (String statement : splitStatements(sql)) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO schema_migrations (name) VALUES (?)")) {
                ins.setString(1, name);
                ins.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            conn.setAutoCommit(prevAutoCommit);
            throw new SQLException("Migration '" + name + "' failed and was rolled back: " + e.getMessage(), e);
        }
        conn.setAutoCommit(prevAutoCommit);
    }

    private String readResource(String path) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new RuntimeException("Migration resource not found on classpath: " + path);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read migration: " + path, e);
        }
    }

    /**
     * Splits a SQL script on semicolons, skipping those inside single-quoted strings.
     * Adequate for DDL/DML — does not handle PL/pgSQL dollar-quoting.
     */
    static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'' && (i == 0 || sql.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (c == ';' && !inString) {
                String stmt = current.toString().trim();
                if (!stmt.isEmpty()) statements.add(stmt);
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        String last = current.toString().trim();
        if (!last.isEmpty()) statements.add(last);
        return statements;
    }
}
