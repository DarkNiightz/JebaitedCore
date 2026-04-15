package com.darkniightz.core.system;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class OpsAlertService {
    private final Plugin plugin;
    private final String webhookUrl;
    private final boolean enabled;
    private final long cooldownMs;
    private final HttpClient client;
    private final PanelConnectorService connector;
    private volatile long lastSentAt = 0L;

    public OpsAlertService(Plugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("alerts.enabled", false);
        this.webhookUrl = plugin.getConfig().getString("alerts.webhook_url", "");
        this.cooldownMs = Math.max(0L, plugin.getConfig().getLong("alerts.cooldown_ms", 60000L));
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.connector = plugin instanceof com.darkniightz.main.JebaitedCore core && core.getPanelConnectorService() != null
                ? core.getPanelConnectorService()
                : new PanelConnectorService(plugin);
    }

    public boolean isEnabled() {
        return enabled && webhookUrl != null && !webhookUrl.isBlank();
    }

    public void sendAsync(String title, String detail) {
        if (!isEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (cooldownMs > 0L && now - lastSentAt < cooldownMs) {
            return;
        }
        lastSentAt = now;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String content = "[" + plugin.getName() + "] " + title + (detail == null || detail.isBlank() ? "" : " - " + detail);
            String payload = "{\"content\":\"" + escapeJson(content) + "\"}";
            connector.postJsonAsync(webhookUrl, payload, java.util.Map.of(), result -> {
                if (!result.sent()) {
                    plugin.getLogger().warning("Failed to send ops alert webhook.");
                    return;
                }
                int code = result.statusCode();
                if (code < 200 || code >= 300) {
                    plugin.getLogger().warning("Ops alert webhook returned HTTP " + code);
                }
            });
        });
    }

    private static String escapeJson(String in) {
        return in
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
