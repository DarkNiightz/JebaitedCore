package com.darkniightz.bot.config;

import java.util.Map;

public record BotConfig(
        Discord discord,
        Database database,
        Redis redis,
        Http http,
        Security security
) {
    public record Discord(
            String token,
            String guildId,
            String statusText,
            Map<String, String> roleIds,
            String modLogsChannelId,
            String announcementsChannelId,
            String eventsPingRoleId,
            String relayOutChannelId,
            String relayStaffOutChannelId,
            String relayFactionOutChannelId,
            String relayGlobalInChannelId,
            String relayStaffInChannelId,
            String relayFactionInChannelId,
            String consoleChannelId,
            String consoleDeveloperRoleId,
            String pluginInboundBaseUrl,
            String pluginApiToken,
            String statusEmbedChannelId
    ) {}

    public record Database(String jdbcUrl, String username, String password, int maxPoolSize) {}

    public record Redis(String uri, String pubsubChannel) {}

    public record Http(String host, int port) {}

    public record Security(String webhookHmacSecret, long maxSkewSeconds) {}
}
