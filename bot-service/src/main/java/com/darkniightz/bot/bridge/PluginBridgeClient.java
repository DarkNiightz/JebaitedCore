package com.darkniightz.bot.bridge;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Bot → Paper plugin HTTP (Bearer token). Same secret as integrations.discord.inbound.api_token.
 */
public final class PluginBridgeClient {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final String baseUrl;
    private final String bearerToken;

    public PluginBridgeClient(String baseUrl, String bearerToken) {
        String b = baseUrl == null ? "" : baseUrl.trim();
        if (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        this.baseUrl = b;
        this.bearerToken = bearerToken == null ? "" : bearerToken.trim();
    }

    public boolean isConfigured() {
        return !baseUrl.isBlank() && !bearerToken.isBlank();
    }

    public String getServerStatusJson() throws IOException, InterruptedException {
        if (!isConfigured()) {
            return "";
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/integrations/discord/status"))
                .timeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer " + bearerToken)
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new IOException("status http " + resp.statusCode());
        }
        return resp.body() == null ? "" : resp.body();
    }

    public void postBridgeIn(String bridge, String authorName, String authorId, String message)
            throws IOException, InterruptedException {
        if (!isConfigured()) {
            return;
        }
        String body =
                "{\"type\":\"bridge.in\",\"payload\":{"
                        + "\"bridge\":\""
                        + esc(bridge)
                        + "\",\"authorName\":\""
                        + esc(authorName)
                        + "\",\"authorId\":\""
                        + esc(authorId)
                        + "\",\"message\":\""
                        + esc(message)
                        + "\"}}";
        postRaw("/integrations/discord/bridge", body);
    }

    public void postConsoleCommand(String command, String discordUserId) throws IOException, InterruptedException {
        if (!isConfigured()) {
            return;
        }
        String body =
                "{\"command\":\""
                        + esc(command)
                        + "\",\"discordUserId\":\""
                        + esc(discordUserId)
                        + "\"}";
        postRaw("/integrations/discord/console", body);
    }

    private void postRaw(String path, String body) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + bearerToken)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() >= 400) {
            throw new IOException("bridge http " + resp.statusCode() + ": " + resp.body());
        }
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
