package com.darkniightz.core.eventmode;

import com.darkniightz.core.system.NetworkManager;
import com.darkniightz.core.system.PanelConnectorService;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;

/**
 * Fire-and-forget web panel hooks for chat game lifecycle ({@code /api/server/chat-game-event}).
 */
public final class ChatGamePanelNotifier {

    private ChatGamePanelNotifier() {}

    public static void post(Plugin plugin, String type, String configKey, String displayName,
                            int sessionId, UUID winnerUuidOrNull, int rewardCoins,
                            long startedAtMs, long endedAtMs) {
        if (!(plugin instanceof JebaitedCore core)) {
            return;
        }
        String base = panelUrl(core);
        if (base.isBlank()) {
            return;
        }
        String secret = core.getConfig().getString("webpanel.provision_secret", "");
        NetworkManager nm = NetworkManager.getInstance();
        String serverId = nm != null ? nm.getServerId() : core.getConfig().getString("network.server_id", "hub-01");

        String w = winnerUuidOrNull == null ? "" : winnerUuidOrNull.toString();
        String json = new StringBuilder(256)
                .append("{\"type\":\"").append(escape(type)).append('"')
                .append(",\"serverId\":\"").append(escape(serverId)).append('"')
                .append(",\"configKey\":\"").append(escape(configKey)).append('"')
                .append(",\"displayName\":\"").append(escape(displayName)).append('"')
                .append(",\"sessionId\":").append(sessionId)
                .append(",\"winnerUuid\":\"").append(escape(w)).append('"')
                .append(",\"rewardCoins\":").append(rewardCoins)
                .append(",\"startedAt\":").append(startedAtMs)
                .append(",\"endedAt\":").append(endedAtMs)
                .append('}')
                .toString();

        PanelConnectorService connector = core.getPanelConnectorService();
        if (connector == null) {
            return;
        }
        connector.postJsonAsync(
                base + "/api/server/chat-game-event",
                json,
                Map.of("X-Provision-Secret", secret == null ? "" : secret),
                result -> {
                    if (!result.sent() || result.statusCode() < 200 || result.statusCode() >= 300) {
                        core.getLogger().fine("[ChatGames] Panel notify '" + type + "' -> " + result.statusCode());
                    }
                });
    }

    private static String panelUrl(JebaitedCore plugin) {
        String url = plugin.getConfig().getString("webpanel.internal_url", "");
        if (url == null || url.isBlank()) {
            url = plugin.getConfig().getString("webpanel.url", "http://localhost:3001");
        }
        return url == null ? "" : url.replaceAll("/+$", "");
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
