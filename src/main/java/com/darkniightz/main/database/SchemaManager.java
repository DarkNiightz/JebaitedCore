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
    public record MigrationResult(int total, int applied, String latestMigration) {}

    public MigrationResult runMigrations() {
        if (!db.isEnabled()) return new MigrationResult(0, 0, "n/a");

        try (Connection conn = db.getConnection()) {
            ensureMigrationsTable(conn);
            List<String> alreadyApplied = loadApplied(conn);
            List<String> pending = discoverMigrations();

            int ran = 0;
            for (String name : pending) {
                if (alreadyApplied.contains(name)) continue;
                logger.info("[Schema] Applying " + name + " ...");
                String sql = readResource(MIGRATIONS_DIR + name);
                applyMigration(conn, name, sql);
                alreadyApplied.add(name);
                ran++;
            }

            String latest = alreadyApplied.isEmpty() ? "none" : alreadyApplied.get(alreadyApplied.size() - 1);
            if (ran == 0) {
                logger.info("[Schema] Schema is up to date (" + alreadyApplied.size() + " migration(s) applied).");
            } else {
                logger.info("[Schema] Applied " + ran + " new migration(s). Total: " + alreadyApplied.size() + ".");
            }
            return new MigrationResult(alreadyApplied.size(), ran, latest);

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
     * Splits a SQL script on semicolons, skipping those inside single-quoted strings
     * and PL/pgSQL {@code $$} dollar-quoted blocks (e.g. {@code DO $$ ... $$;}).
     * Each {@code $$} token toggles dollar-quote mode — semicolons inside are ignored.
     */
    static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString      = false;
        boolean inLineComment = false;
        boolean inDollarQuote = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);

            // End line comment on newline
            if (inLineComment) {
                current.append(c);
                if (c == '\n') inLineComment = false;
                continue;
            }

            // Detect start of -- line comment (only when not inside a string or dollar-quote)
            if (!inString && !inDollarQuote && c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                inLineComment = true;
                current.append(c);
                continue;
            }

            // Detect $$ dollar-quote toggle (only outside single-quoted strings)
            if (!inString && c == '$' && i + 1 < sql.length() && sql.charAt(i + 1) == '$') {
                inDollarQuote = !inDollarQuote;
                current.append(c);
                current.append(sql.charAt(i + 1));
                i++; // consume both '$' chars
                continue;
            }

            // Toggle single-quoted string state (only outside dollar-quotes)
            if (!inDollarQuote && c == '\'' && (i == 0 || sql.charAt(i - 1) != '\\')) {
                inString = !inString;
            }

            if (c == ';' && !inString && !inDollarQuote) {
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
