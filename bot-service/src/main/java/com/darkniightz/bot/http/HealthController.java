package com.darkniightz.bot.http;

import com.darkniightz.bot.db.SqlHealthCheck;
import com.darkniightz.bot.redis.RedisBus;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class HealthController {
    private final SqlHealthCheck sqlHealthCheck;
    private final RedisBus redisBus;

    public HealthController(SqlHealthCheck sqlHealthCheck, RedisBus redisBus) {
        this.sqlHealthCheck = sqlHealthCheck;
        this.redisBus = redisBus;
    }

    public void live(HttpExchange exchange) throws IOException {
        respond(exchange, 200, "ok");
    }

    public void ready(HttpExchange exchange) throws IOException {
        boolean ready = sqlHealthCheck.isHealthy() && redisBus.ping();
        respond(exchange, ready ? 200 : 503, ready ? "ready" : "not_ready");
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
