package com.darkniightz.core.system;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Single outbound connector boundary for plugin -> panel/service communication.
 * This keeps gameplay logic independent and preserves local fallback when the panel is unavailable.
 */
public class PanelConnectorService {
    public record ConnectorResult(boolean sent, int statusCode, String body) {}

    private final Plugin plugin;
    private final HttpClient client;

    public PanelConnectorService(Plugin plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public ConnectorResult postJson(String url, String payload, Map<String, String> headers) throws IOException, InterruptedException {
        if (url == null || url.isBlank()) {
            return new ConnectorResult(false, 0, "");
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload == null ? "{}" : payload));

        if (headers != null) {
            headers.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                    builder.header(key, value);
                }
            });
        }

        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new ConnectorResult(true, response.statusCode(), response.body() == null ? "" : response.body());
    }

    public void postJsonAsync(String url, String payload, Map<String, String> headers, Consumer<ConnectorResult> callback) {
        if (url == null || url.isBlank()) {
            if (callback != null) {
                callback.accept(new ConnectorResult(false, 0, ""));
            }
            return;
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload == null ? "{}" : payload));

        if (headers != null) {
            headers.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                    builder.header(key, value);
                }
            });
        }

        client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        plugin.getLogger().warning("Panel connector request failed: " + throwable.getMessage());
                        if (callback != null) {
                            callback.accept(new ConnectorResult(false, 0, throwable.getMessage() == null ? "" : throwable.getMessage()));
                        }
                        return;
                    }

                    ConnectorResult result = new ConnectorResult(true, response.statusCode(), response.body() == null ? "" : response.body());
                    if (callback != null) {
                        callback.accept(result);
                    }
                });
    }
}
