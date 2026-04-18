package com.darkniightz.bot.http;

import com.darkniightz.bot.db.IntegrationAuditDao;
import com.darkniightz.bot.discord.DiscordIntegrationDispatchService;
import com.darkniightz.bot.observability.Metrics;
import com.darkniightz.bot.security.WebhookVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;


public final class WebhookController {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final WebhookVerifier verifier;
    private final Metrics metrics;
    private final DiscordIntegrationDispatchService dispatch;
    private final IntegrationAuditDao auditDao;

    public WebhookController(
            WebhookVerifier verifier,
            Metrics metrics,
            DiscordIntegrationDispatchService dispatch,
            IntegrationAuditDao auditDao) {
        this.verifier = verifier;
        this.metrics = metrics;
        this.dispatch = dispatch;
        this.auditDao = auditDao;
    }

    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "method_not_allowed");
            return;
        }

        String payload = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String signature = exchange.getRequestHeaders().getFirst("X-Jebaited-Signature");
        String timestampHeader = exchange.getRequestHeaders().getFirst("X-Jebaited-Timestamp");
        String nonce = exchange.getRequestHeaders().getFirst("X-Jebaited-Nonce");

        if (signature == null || timestampHeader == null || nonce == null) {
            metrics.incWebhookRejected();
            respond(exchange, 401, "missing_security_headers");
            return;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException e) {
            metrics.incWebhookRejected();
            respond(exchange, 401, "invalid_timestamp");
            return;
        }

        if (!verifier.verify(payload, signature, timestamp, nonce)) {
            metrics.incWebhookRejected();
            respond(exchange, 401, "invalid_signature");
            return;
        }

        metrics.incWebhookAccepted();
        String correlation =
                exchange.getRequestHeaders().getFirst("X-Correlation-Id");
        if (correlation == null || correlation.isBlank()) {
            correlation = UUID.randomUUID().toString();
        }
        String eventType = "unknown";
        try {
            JsonNode root = JSON.readTree(payload);
            eventType = root.path("type").asText("unknown");
        } catch (Exception ignored) {
        }
        auditDao.insert(eventType, correlation, payload);
        try {
            dispatch.dispatch(payload);
        } catch (Exception ignored) {
        }
        respond(exchange, 202, "accepted");
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
