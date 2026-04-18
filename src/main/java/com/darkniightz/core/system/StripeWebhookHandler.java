package com.darkniightz.core.system;

import com.darkniightz.core.store.StoreService;
import com.darkniightz.main.JebaitedCore;
import com.sun.net.httpserver.HttpExchange;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.logging.Level;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class StripeWebhookHandler {
    private StripeWebhookHandler() {}

    public static void handle(JavaPlugin plugin, HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            respond(ex, 405, "method_not_allowed");
            return;
        }
        if (!(plugin instanceof JebaitedCore core)) {
            respond(ex, 500, "not_core");
            return;
        }
        StoreService store = core.getStoreService();
        if (store == null || !store.isStoreEnabled()) {
            respond(ex, 503, "store_disabled");
            return;
        }
        byte[] raw = ex.getRequestBody().readAllBytes();
        String sig = ex.getRequestHeaders().getFirst("Stripe-Signature");
        if (sig == null || sig.isBlank()) {
            respond(ex, 400, "missing_signature");
            return;
        }
        try {
            String result = store.handleStripeWebhook(raw, sig);
            if ("duplicate".equals(result)) {
                respond(ex, 200, "ok");
                return;
            }
            if ("ok".equals(result) || "ignored".equals(result)) {
                respond(ex, 200, result);
                return;
            }
            respond(ex, 400, result);
        } catch (com.stripe.exception.SignatureVerificationException e) {
            respond(ex, 400, "bad_signature");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Stripe webhook", e);
            respond(ex, 500, "error");
        }
    }

    private static void respond(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
