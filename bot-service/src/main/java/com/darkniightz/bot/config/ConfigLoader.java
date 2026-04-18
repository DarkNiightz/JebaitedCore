package com.darkniightz.bot.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class ConfigLoader {
    private ConfigLoader() {}

    @SuppressWarnings("unchecked")
    public static BotConfig loadFromClasspath(String path) {
        InputStream in = ConfigLoader.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("Missing config resource: " + path);
        }

        Map<String, Object> root = new Yaml().load(in);
        Map<String, Object> discord = (Map<String, Object>) root.get("discord");
        Map<String, Object> database = (Map<String, Object>) root.get("database");
        Map<String, Object> redis = (Map<String, Object>) root.get("redis");
        Map<String, Object> http = (Map<String, Object>) root.get("http");
        Map<String, Object> security = (Map<String, Object>) root.get("security");
        Map<String, String> roleIds = parseRoleIds(discord);
        applyRoleEnvOverrides(roleIds);

        return new BotConfig(
                new BotConfig.Discord(
                        discordToken("JB_BOT_TOKEN", str(discord, "token")),
                        sanitizePlain(env("JB_BOT_GUILD_ID", str(discord, "guild_id"))),
                        env("JB_BOT_STATUS_TEXT", str(discord, "status_text")),
                        roleIds,
                        env("JB_MOD_LOGS_CHANNEL_ID", optStr(discord, "mod_logs_channel_id")),
                        env("JB_ANNOUNCEMENTS_CHANNEL_ID", optStr(discord, "announcements_channel_id")),
                        env("JB_EVENTS_PING_ROLE_ID", optStr(discord, "events_ping_role_id")),
                        env("JB_RELAY_OUT_CHANNEL_ID", optStr(discord, "relay_out_channel_id")),
                        env("JB_RELAY_STAFF_OUT_CHANNEL_ID", optStr(discord, "relay_staff_out_channel_id")),
                        env("JB_RELAY_FACTION_OUT_CHANNEL_ID", optStr(discord, "relay_faction_out_channel_id")),
                        env("JB_RELAY_GLOBAL_IN_CHANNEL_ID", optStr(discord, "relay_global_in_channel_id")),
                        env("JB_RELAY_STAFF_IN_CHANNEL_ID", optStr(discord, "relay_staff_in_channel_id")),
                        env("JB_RELAY_FACTION_IN_CHANNEL_ID", optStr(discord, "relay_faction_in_channel_id")),
                        env("JB_CONSOLE_CHANNEL_ID", optStr(discord, "console_channel_id")),
                        env("JB_CONSOLE_DEVELOPER_ROLE_ID", optStr(discord, "console_developer_role_id")),
                        env("JB_PLUGIN_INBOUND_BASE_URL", optStr(discord, "plugin_inbound_base_url")),
                        env("JB_PLUGIN_API_TOKEN", optStr(discord, "plugin_api_token")),
                        env("JB_STATUS_EMBED_CHANNEL_ID", optStr(discord, "status_embed_channel_id"))
                ),
                new BotConfig.Database(
                        env("JB_DB_JDBC_URL", str(database, "jdbc_url")),
                        env("JB_DB_USERNAME", str(database, "username")),
                        env("JB_DB_PASSWORD", str(database, "password")),
                        integer(env("JB_DB_MAX_POOL_SIZE", String.valueOf(integer(database, "max_pool_size"))))
                ),
                new BotConfig.Redis(
                        env("JB_REDIS_URI", str(redis, "uri")),
                        env("JB_REDIS_CHANNEL", str(redis, "pubsub_channel"))
                ),
                new BotConfig.Http(
                        env("JB_HTTP_HOST", str(http, "host")),
                        integer(env("JB_HTTP_PORT", String.valueOf(integer(http, "port"))))
                ),
                new BotConfig.Security(
                        env("JB_WEBHOOK_SECRET", str(security, "webhook_hmac_secret")),
                        longVal(env("JB_MAX_SKEW_SECONDS", String.valueOf(longVal(security, "max_skew_seconds"))))
                )
        );
    }

    private static String str(Map<String, Object> map, String key) {
        return String.valueOf(map.get(key));
    }

    private static String optStr(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            return "";
        }
        String s = String.valueOf(v).trim();
        return "null".equals(s) ? "" : s;
    }

    private static int integer(Map<String, Object> map, String key) {
        return Integer.parseInt(String.valueOf(map.get(key)));
    }

    private static long longVal(Map<String, Object> map, String key) {
        return Long.parseLong(String.valueOf(map.get(key)));
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    /**
     * Bot token from env or YAML, normalized so common .env mistakes don't break login
     * (surrounding quotes, trailing newline, UTF-8 BOM).
     */
    private static String discordToken(String key, String yamlFallback) {
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return normalizeDiscordToken(sanitizeToken(fromEnv));
        }
        // Local runs: JVM does not load repo .env — read it explicitly (same keys as Docker Compose).
        String fromDotEnv = readDotEnvValue(key);
        if (fromDotEnv != null && !fromDotEnv.isBlank()) {
            return normalizeDiscordToken(sanitizeToken(fromDotEnv));
        }
        String path = System.getenv("JB_BOT_TOKEN_FILE");
        if (path != null && !path.isBlank()) {
            try {
                return normalizeDiscordToken(sanitizeToken(Files.readString(Path.of(path.trim()), StandardCharsets.UTF_8)));
            } catch (Exception ignored) {
                // fall through to yaml
            }
        }
        return normalizeDiscordToken(sanitizeToken(yamlFallback));
    }

    /**
     * Reads {@code KEY=value} from {@code JB_BOT_ENV_FILE} if set, else {@code .env} in the JVM working directory.
     */
    private static String readDotEnvValue(String key) {
        String envPath = System.getenv("JB_BOT_ENV_FILE");
        Path cwd = Path.of(System.getProperty("user.dir", ".")).normalize();
        Path[] candidates;
        if (envPath != null && !envPath.isBlank()) {
            candidates = new Path[] {Path.of(envPath.trim())};
        } else {
            Path parent = cwd.getParent();
            candidates =
                    parent != null
                            ? new Path[] {cwd.resolve(".env"), parent.resolve(".env")}
                            : new Path[] {cwd.resolve(".env")};
        }
        for (Path p : candidates) {
            if (!Files.isRegularFile(p)) {
                continue;
            }
            try {
                for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                    if (line.startsWith("\uFEFF")) {
                        line = line.substring(1);
                    }
                    String s = line.trim();
                    if (s.isEmpty() || s.startsWith("#")) {
                        continue;
                    }
                    int eq = s.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String k = s.substring(0, eq).trim();
                    if (!key.equals(k)) {
                        continue;
                    }
                    String v = s.substring(eq + 1).trim();
                    if (v.length() >= 2
                            && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
                        v = v.substring(1, v.length() - 1);
                    }
                    return v;
                }
            } catch (IOException ignored) {
                // try next candidate
            }
        }
        return null;
    }

    /** Discord bot tokens never contain whitespace; strip accidental line breaks / spaces from copy-paste. */
    private static String normalizeDiscordToken(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.replaceAll("\\s+", "");
    }

    private static String sanitizePlain(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        if (s.startsWith("\uFEFF")) {
            s = s.substring(1).trim();
        }
        return s;
    }

    private static String sanitizeToken(String raw) {
        String s = sanitizePlain(raw);
        if ("null".equalsIgnoreCase(s)) {
            return "";
        }
        return s;
    }

    private static int integer(String raw) {
        return Integer.parseInt(raw);
    }

    private static long longVal(String raw) {
        return Long.parseLong(raw);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> parseRoleIds(Map<String, Object> discord) {
        Object roleIdsRaw = discord.get("role_ids");
        if (!(roleIdsRaw instanceof Map<?, ?> rawMap)) {
            return Collections.emptyMap();
        }
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            out.put(String.valueOf(entry.getKey()).toLowerCase(), String.valueOf(entry.getValue()));
        }
        return out;
    }

    /**
     * Optional per-rank overrides for Docker / prod without rebuilding the shaded JAR.
     * Example: JB_ROLE_DIAMOND=123456789012345678
     */
    private static void applyRoleEnvOverrides(Map<String, String> roleIds) {
        String[] ranks = {
                "pleb", "vip", "builder", "gold", "diamond", "legend", "grandmaster",
                "helper", "moderator", "srmod", "admin", "developer", "owner"
        };
        for (String rank : ranks) {
            String key = "JB_ROLE_" + rank.toUpperCase(Locale.ROOT);
            String value = System.getenv(key);
            if (value != null && !value.isBlank()) {
                roleIds.put(rank, value.trim());
            }
        }
    }
}
