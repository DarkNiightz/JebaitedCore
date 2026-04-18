package com.darkniightz.core.system;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Plugin → Discord bot: signed JSON to {@code /webhooks/panel} and same JSON on Redis pub/sub.
 */
public final class DiscordIntegrationService {
    private final Plugin plugin;
    private final boolean enabled;
    private final boolean relayChat;
    private final int relayGlobalMinMs;
    private final int relayPerPlayerMinMs;
    private final boolean consoleMirror;
    private final String webhookUrl;
    private final String secret;
    private final int timeoutMs;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ConcurrentHashMap<UUID, Long> lastRelayByPlayer = new ConcurrentHashMap<>();
    private volatile long lastGlobalRelayMs;

    public DiscordIntegrationService(Plugin plugin) {
        this.plugin = plugin;
        var cfg = plugin.getConfig();
        this.enabled = cfg.getBoolean("integrations.discord.enabled", false);
        this.relayChat = cfg.getBoolean("integrations.discord.relay_chat", false);
        this.relayGlobalMinMs = Math.max(0, cfg.getInt("integrations.discord.relay_chat_global_min_ms", 200));
        this.relayPerPlayerMinMs = Math.max(0, cfg.getInt("integrations.discord.relay_chat_per_player_min_ms", 800));
        this.consoleMirror = cfg.getBoolean("integrations.discord.console_mirror_enabled", false);
        String base = cfg.getString("integrations.discord.bot_api_base_url", "http://127.0.0.1:8787");
        if (base == null || base.isBlank()) {
            base = "http://127.0.0.1:8787";
        }
        base = base.trim();
        String baseEnv = System.getenv("JB_DISCORD_BOT_API_BASE_URL");
        if (baseEnv != null && !baseEnv.isBlank()) {
            base = baseEnv.trim();
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.webhookUrl = base + "/webhooks/panel";
        String sec = cfg.getString("integrations.discord.webhook_hmac_secret", "");
        String secEnv = System.getenv("JB_WEBHOOK_SECRET");
        if (secEnv != null && !secEnv.isBlank()) {
            sec = secEnv.trim();
        }
        this.secret = sec == null ? "" : sec;
        this.timeoutMs = Math.max(500, Math.min(30000, cfg.getInt("integrations.webhooks.request_timeout_ms", 3000)));
        if (enabled) {
            if (isEnabled()) {
                plugin.getLogger().info("[Discord] Paper→bot webhooks ACTIVE → " + webhookUrl);
            } else {
                plugin.getLogger()
                        .warning(
                                "[Discord] integrations.discord.enabled=true but signing secret is invalid: set "
                                        + "integrations.discord.webhook_hmac_secret or env JB_WEBHOOK_SECRET to match the bot. "
                                        + "Relay, mod-log, and event announcements will not be sent.");
            }
        }
    }

    public boolean isEnabled() {
        return enabled && secret != null && !secret.isBlank() && !"CHANGE_ME".equalsIgnoreCase(secret.trim());
    }

    public boolean isConsoleMirrorEnabled() {
        return isEnabled() && consoleMirror;
    }

    private String sign(String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign webhook payload", e);
        }
    }

    public void notifyModerationMirror(Map<String, Object> entry) {
        if (!isEnabled() || entry == null) {
            return;
        }
        String actionType = String.valueOf(entry.getOrDefault("type", ""));
        String targetName = String.valueOf(entry.getOrDefault("targetName", ""));
        String actor = String.valueOf(entry.getOrDefault("actor", ""));
        String reason = entry.get("reason") != null ? String.valueOf(entry.get("reason")) : "";
        Long durationMs = entry.get("durationMs") instanceof Number n ? n.longValue() : null;
        Long expiresAt = entry.get("expiresAt") instanceof Number n ? n.longValue() : null;

        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"moderation.mirror\",\"payload\":{");
        sb.append("\"actionType\":\"").append(jsonEscape(actionType)).append("\",");
        sb.append("\"targetName\":\"").append(jsonEscape(targetName)).append("\",");
        sb.append("\"actor\":\"").append(jsonEscape(actor)).append("\",");
        sb.append("\"reason\":\"").append(jsonEscape(reason)).append("\"");
        if (durationMs != null) {
            sb.append(",\"durationMs\":").append(durationMs);
        }
        if (expiresAt != null) {
            sb.append(",\"expiresAt\":").append(expiresAt);
        }
        sb.append("}}");
        dispatchAsync(sb.toString());
    }

    public void notifyEventStarted(String eventKey, String displayName, int playerCount, String kindName) {
        if (!isEnabled()) {
            return;
        }
        String json = "{\"type\":\"event.announce\",\"payload\":{"
                + "\"phase\":\"start\","
                + "\"eventKey\":\"" + jsonEscape(eventKey) + "\","
                + "\"displayName\":\"" + jsonEscape(displayName) + "\","
                + "\"playerCount\":" + playerCount + ","
                + "\"kind\":\"" + jsonEscape(kindName) + "\""
                + "}}";
        dispatchAsync(json);
    }

    public void notifyEventEnded(
            String eventKey,
            String displayName,
            String kindName,
            String winnerName,
            int rewardCoins,
            String endReason) {
        if (!isEnabled()) {
            return;
        }
        String json =
                "{\"type\":\"event.announce\",\"payload\":{"
                        + "\"phase\":\"end\","
                        + "\"eventKey\":\""
                        + jsonEscape(eventKey)
                        + "\","
                        + "\"displayName\":\""
                        + jsonEscape(displayName)
                        + "\","
                        + "\"kind\":\""
                        + jsonEscape(kindName)
                        + "\","
                        + "\"winnerName\":\""
                        + jsonEscape(winnerName)
                        + "\","
                        + "\"rewardCoins\":"
                        + rewardCoins
                        + ","
                        + "\"endReason\":\""
                        + jsonEscape(endReason == null ? "" : endReason)
                        + "\""
                        + "}}";
        dispatchAsync(json);
    }

    public void notifyEventTie(
            String eventKey, String displayName, String kindName, String tieNamesPlain, int rewardTotal) {
        if (!isEnabled()) {
            return;
        }
        String json =
                "{\"type\":\"event.announce\",\"payload\":{"
                        + "\"phase\":\"tie\","
                        + "\"eventKey\":\""
                        + jsonEscape(eventKey)
                        + "\","
                        + "\"displayName\":\""
                        + jsonEscape(displayName)
                        + "\","
                        + "\"kind\":\""
                        + jsonEscape(kindName)
                        + "\","
                        + "\"tieDetail\":\""
                        + jsonEscape(tieNamesPlain)
                        + "\","
                        + "\"rewardCoins\":"
                        + rewardTotal
                        + "}}";
        dispatchAsync(json);
    }

    public void notifyEventCancelled(String eventKey, String displayName, String reason) {
        if (!isEnabled()) {
            return;
        }
        String json =
                "{\"type\":\"event.announce\",\"payload\":{"
                        + "\"phase\":\"cancelled\","
                        + "\"eventKey\":\""
                        + jsonEscape(eventKey)
                        + "\","
                        + "\"displayName\":\""
                        + jsonEscape(displayName)
                        + "\","
                        + "\"cancelReason\":\""
                        + jsonEscape(reason == null ? "" : reason)
                        + "\""
                        + "}}";
        dispatchAsync(json);
    }

    /** Public chat → Discord relay (rate-limited). {@code bridge} is global | staff | faction */
    public void notifyChatRelay(UUID playerUuid, String playerName, String plainMessage, String bridge) {
        if (!isEnabled() || !relayChat || playerUuid == null) {
            return;
        }
        if (plainMessage == null || plainMessage.isBlank()) {
            return;
        }
        String b = bridge == null || bridge.isBlank() ? "global" : bridge.toLowerCase();
        long now = System.currentTimeMillis();
        if (relayGlobalMinMs > 0 && now - lastGlobalRelayMs < relayGlobalMinMs) {
            return;
        }
        Long prev = lastRelayByPlayer.get(playerUuid);
        if (relayPerPlayerMinMs > 0 && prev != null && now - prev < relayPerPlayerMinMs) {
            return;
        }
        lastGlobalRelayMs = now;
        lastRelayByPlayer.put(playerUuid, now);

        String safe = sanitizeRelay(plainMessage);
        String who = playerName == null ? "?" : playerName;
        String json =
                "{\"type\":\"relay.chat\",\"payload\":{"
                        + "\"bridge\":\""
                        + jsonEscape(b)
                        + "\","
                        + "\"playerName\":\""
                        + jsonEscape(who)
                        + "\","
                        + "\"message\":\""
                        + jsonEscape(safe)
                        + "\""
                        + "}}";
        dispatchAsync(json);
    }

    public void notifyConsoleMirror(String line) {
        if (!isConsoleMirrorEnabled() || line == null || line.isBlank()) {
            return;
        }
        String json =
                "{\"type\":\"console.line\",\"payload\":{"
                        + "\"line\":\""
                        + jsonEscape(line)
                        + "\""
                        + "}}";
        dispatchAsync(json);
    }

    private static String sanitizeRelay(String s) {
        String t = s.length() > 400 ? s.substring(0, 400) + "…" : s;
        return t.replace("@everyone", "@ everyone").replace("@here", "@ here");
    }

    private static String jsonEscape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void dispatchAsync(String jsonBody) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long ts = Instant.now().getEpochSecond();
                String nonce = UUID.randomUUID().toString();
                String correlation = UUID.randomUUID().toString();
                String sig = sign(ts + "." + nonce + "." + jsonBody);
                HttpRequest req = HttpRequest.newBuilder(URI.create(webhookUrl))
                        .timeout(Duration.ofMillis(timeoutMs))
                        .header("Content-Type", "application/json")
                        .header("X-Jebaited-Signature", sig)
                        .header("X-Jebaited-Timestamp", String.valueOf(ts))
                        .header("X-Jebaited-Nonce", nonce)
                        .header("X-Correlation-Id", correlation)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 400) {
                    plugin.getLogger().warning("Discord webhook HTTP " + resp.statusCode() + ": " + resp.body());
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE, "Discord webhook failed", e);
            }
        });
    }
}
