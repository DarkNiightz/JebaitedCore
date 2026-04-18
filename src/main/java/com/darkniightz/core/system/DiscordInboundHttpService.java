package com.darkniightz.core.system;

import com.darkniightz.core.permissions.PermissionConstants;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.main.JebaitedCore;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * Bot → Paper: signed-less Bearer API on a localhost bind for bridge, status, and remote console.
 */
public final class DiscordInboundHttpService {
    private final JavaPlugin plugin;
    private HttpServer server;

    public DiscordInboundHttpService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        boolean discordInboundEnabled = plugin.getConfig().getBoolean("integrations.discord.enabled", false)
                && plugin.getConfig().getBoolean("integrations.discord.inbound.enabled", false);
        String token = plugin.getConfig().getString("integrations.discord.inbound.api_token", "");
        if (token != null && "CHANGE_ME".equalsIgnoreCase(token.trim())) {
            token = "";
        }
        boolean needDiscord =
                discordInboundEnabled && token != null && !token.isBlank();
        if (discordInboundEnabled && !needDiscord) {
            plugin.getLogger().warning("Discord inbound is enabled but integrations.discord.inbound.api_token is not set.");
        }
        boolean needStripe = wantStripeWebhookRoute();

        if (!needDiscord && !needStripe) {
            return;
        }

        String host;
        int port;
        if (needDiscord) {
            host = plugin.getConfig().getString("integrations.discord.inbound.bind_host", "127.0.0.1");
            port = plugin.getConfig().getInt("integrations.discord.inbound.bind_port", 8789);
        } else {
            host = plugin.getConfig().getString("store.http.bind_host", "127.0.0.1");
            port = plugin.getConfig().getInt("store.http.bind_port", 8789);
        }

        try {
            server = HttpServer.create(new InetSocketAddress(host, port), 0);
            if (needDiscord) {
                String t = token.trim();
                server.createContext("/integrations/discord/status", ex -> handleStatus(ex, t));
                server.createContext("/integrations/discord/bridge", ex -> handleBridge(ex, t));
                server.createContext("/integrations/discord/console", ex -> handleConsole(ex, t));
            }
            if (needStripe) {
                server.createContext(
                        "/integrations/stripe/webhook",
                        ex -> {
                            try {
                                StripeWebhookHandler.handle(plugin, ex);
                            } catch (IOException e) {
                                plugin.getLogger().warning("Stripe webhook exchange failed: " + e.getMessage());
                            }
                        });
            }
            server.setExecutor(Executors.newFixedThreadPool(4));
            server.start();
            StringBuilder what = new StringBuilder();
            if (needDiscord) {
                what.append("discord inbound (status/bridge/console)");
            }
            if (needStripe) {
                if (what.length() > 0) {
                    what.append(" + ");
                }
                what.append("stripe webhook");
            }
            plugin.getLogger().info("HTTP API on http://" + host + ":" + port + " — " + what);
        } catch (IOException e) {
            plugin.getLogger().warning("Inbound HTTP failed to start: " + e.getMessage());
        }
    }

    /** True when store + Stripe are on and a webhook signing secret is configured (env or config). */
    private boolean wantStripeWebhookRoute() {
        if (!plugin.getConfig().getBoolean("store.enabled", false)) {
            return false;
        }
        if (!plugin.getConfig().getBoolean("store.stripe.enabled", false)) {
            return false;
        }
        String secret = System.getenv("STRIPE_WEBHOOK_SECRET");
        if (secret == null || secret.isBlank()) {
            secret = plugin.getConfig().getString("store.stripe.webhook_secret", "");
        }
        return secret != null && !secret.isBlank();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private boolean authorize(HttpExchange ex, String expectedToken) {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return false;
        }
        String got = auth.substring("Bearer ".length()).trim();
        return got.equals(expectedToken);
    }

    private void handleStatus(HttpExchange ex, String token) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            respond(ex, 405, "method_not_allowed");
            return;
        }
        if (!authorize(ex, token)) {
            respond(ex, 401, "unauthorized");
            return;
        }
        if (!(plugin instanceof JebaitedCore core)) {
            respond(ex, 500, "not_core");
            return;
        }
        String motd = PlainTextComponentSerializer.plainText().serialize(Bukkit.getServer().motd());
        double tps = resolveTps();
        double mspt = resolveMspt();
        StringBuilder players = new StringBuilder();
        players.append("[");
        boolean first = true;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!first) {
                players.append(",");
            }
            first = false;
            players.append("{\"name\":")
                    .append(jsonString(p.getName()))
                    .append(",\"ping\":")
                    .append(p.getPing())
                    .append("}");
        }
        players.append("]");
        String json =
                "{"
                        + "\"online\":"
                        + Bukkit.getOnlinePlayers().size()
                        + ",\"max\":"
                        + Bukkit.getMaxPlayers()
                        + ",\"motd\":"
                        + jsonString(motd)
                        + ",\"version\":"
                        + jsonString(Bukkit.getVersion())
                        + ",\"tps\":"
                        + round2(tps)
                        + ",\"mspt\":"
                        + round2(mspt)
                        + ",\"players\":"
                        + players
                        + "}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double resolveTps() {
        try {
            Method m = Bukkit.getServer().getClass().getMethod("getTPS");
            double[] arr = (double[]) m.invoke(Bukkit.getServer());
            if (arr != null && arr.length > 0) {
                return Math.min(20.0, arr[0]);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return 20.0;
    }

    private static double resolveMspt() {
        try {
            Method m = Bukkit.getServer().getClass().getMethod("getAverageTickTime");
            Object v = m.invoke(Bukkit.getServer());
            if (v instanceof Number n) {
                return n.doubleValue();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return 0.0;
    }

    private void handleBridge(HttpExchange ex, String token) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            respond(ex, 405, "method_not_allowed");
            return;
        }
        if (!authorize(ex, token)) {
            respond(ex, 401, "unauthorized");
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String bridge = extractNested(body, "payload", "bridge");
        if (bridge == null) {
            bridge = extractJsonString(body, "bridge");
        }
        String authorName = extractNested(body, "payload", "authorName");
        if (authorName == null) {
            authorName = extractJsonString(body, "authorName");
        }
        String message = extractNested(body, "payload", "message");
        if (message == null) {
            message = extractJsonString(body, "message");
        }
        if (bridge == null || authorName == null || message == null) {
            respond(ex, 400, "bad_json");
            return;
        }
        bridge = bridge.toLowerCase(Locale.ROOT).trim();
        String msg = ChatColor.translateAlternateColorCodes('&', message);
        if (msg.length() > 512) {
            msg = msg.substring(0, 512) + "…";
        }
        String formatted =
                ChatColor.DARK_AQUA
                        + "[Discord] "
                        + ChatColor.WHITE
                        + authorName
                        + ChatColor.GRAY
                        + " » "
                        + ChatColor.GRAY
                        + msg;
        String finalBridge = bridge;
        String finalFormatted = formatted;
        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            if (!(plugin instanceof JebaitedCore core)) {
                                return;
                            }
                            switch (finalBridge) {
                                case "staff" -> {
                                    for (Player p : Bukkit.getOnlinePlayers()) {
                                        PlayerProfile vp = core.getProfileStore().get(p.getUniqueId());
                                        String vr =
                                                vp == null || vp.getPrimaryRank() == null
                                                        ? core.getRankManager().getDefaultGroup()
                                                        : vp.getPrimaryRank();
                                        boolean staff =
                                                core.getRankManager().isAtLeast(vr, "helper")
                                                        || (core.getDevModeManager() != null
                                                                && core.getDevModeManager().isActive(p.getUniqueId()));
                                        if (staff) {
                                            p.sendMessage(finalFormatted);
                                        }
                                    }
                                }
                                case "faction" -> {
                                    for (Player p : Bukkit.getOnlinePlayers()) {
                                        if (p.hasPermission(PermissionConstants.DISCORD_BRIDGE_FACTION)) {
                                            p.sendMessage(finalFormatted);
                                        }
                                    }
                                }
                                default -> Bukkit.broadcastMessage(finalFormatted);
                            }
                        });
        respond(ex, 202, "accepted");
    }

    private void handleConsole(HttpExchange ex, String token) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            respond(ex, 405, "method_not_allowed");
            return;
        }
        if (!authorize(ex, token)) {
            respond(ex, 401, "unauthorized");
            return;
        }
        if (!plugin.getConfig().getBoolean("integrations.discord.console_commands_enabled", false)) {
            respond(ex, 403, "console_disabled");
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String command = extractJsonString(body, "command");
        if (command == null) {
            command = extractNested(body, "payload", "command");
        }
        if (command == null || command.isBlank()) {
            respond(ex, 400, "bad_json");
            return;
        }
        String line = command.trim();
        if (line.length() > 512) {
            respond(ex, 400, "command_too_long");
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line));
        respond(ex, 202, "accepted");
    }

    private static String extractNested(String json, String obj, String key) {
        int objIdx = json.indexOf("\"" + obj + "\"");
        if (objIdx < 0) {
            return null;
        }
        int brace = json.indexOf('{', objIdx);
        if (brace < 0) {
            return null;
        }
        int depth = 0;
        int end = -1;
        for (int i = brace; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    end = i;
                    break;
                }
            }
        }
        if (end < 0) {
            return null;
        }
        String inner = json.substring(brace, end + 1);
        return extractJsonString(inner, key);
    }

    private static String extractJsonString(String json, String key) {
        String needle = "\"" + key + "\"";
        int k = json.indexOf(needle);
        if (k < 0) {
            return null;
        }
        int colon = json.indexOf(':', k + needle.length());
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length() || json.charAt(i) != '"') {
            return null;
        }
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char n = json.charAt(i + 1);
                sb.append(switch (n) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case '"' -> '"';
                    case '\\' -> '\\';
                    default -> n;
                });
                i += 2;
                continue;
            }
            if (c == '"') {
                break;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static String jsonString(String s) {
        if (s == null) {
            return "\"\"";
        }
        String esc =
                s.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r");
        return "\"" + esc + "\"";
    }

    private void respond(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
