package com.darkniightz.bot.discord;

import com.darkniightz.bot.bridge.PluginBridgeClient;
import com.darkniightz.bot.config.BotConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;

/** Periodically edits a channel message with live Paper /integrations/discord/status data. */
public final class StatusEmbedUpdater implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(StatusEmbedUpdater.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final JDA jda;
    private final BotConfig.Discord discord;
    private final PluginBridgeClient bridge;
    private final Path messageIdFile = Path.of(System.getProperty("user.dir", "."), "discord-status-embed.id");

    public StatusEmbedUpdater(JDA jda, BotConfig.Discord discord, PluginBridgeClient bridge) {
        this.jda = jda;
        this.discord = discord;
        this.bridge = bridge;
    }

    @Override
    public void run() {
        String chId = discord.statusEmbedChannelId();
        if (chId == null || chId.isBlank() || !bridge.isConfigured()) {
            return;
        }
        try {
            Guild g = resolveGuild();
            if (g == null) {
                return;
            }
            TextChannel ch = g.getTextChannelById(chId);
            if (ch == null) {
                return;
            }
            String raw = bridge.getServerStatusJson();
            JsonNode root = JSON.readTree(raw);
            int online = root.path("online").asInt(0);
            int max = root.path("max").asInt(0);
            double tps = root.path("tps").asDouble(20.0);
            StringBuilder names = new StringBuilder();
            JsonNode players = root.path("players");
            if (players.isArray()) {
                for (JsonNode p : players) {
                    if (names.length() > 0) {
                        names.append(", ");
                    }
                    names.append(p.path("name").asText("?"));
                }
            }
            if (names.length() > 800) {
                names.setLength(800);
                names.append("…");
            }
            if (names.isEmpty()) {
                names.append("—");
            }
            EmbedBuilder eb =
                    new EmbedBuilder()
                            .setTitle("Live server status")
                            .setColor(new Color(0x5865F2))
                            .addField("Online", online + " / " + max, true)
                            .addField("TPS", String.format(Locale.US, "%.2f", tps), true)
                            .addField("Players", names.toString(), false)
                            .setTimestamp(Instant.now());

            long mid = loadMessageId();
            if (mid <= 0) {
                ch.sendMessageEmbeds(eb.build())
                        .queue(
                                m -> {
                                    try {
                                        Files.writeString(messageIdFile, m.getId(), StandardCharsets.UTF_8);
                                    } catch (Exception ex) {
                                        LOG.warn("could not persist status embed id", ex);
                                    }
                                },
                                ex -> LOG.warn("could not send status embed", ex));
            } else {
                long finalMid = mid;
                ch.retrieveMessageById(finalMid)
                        .queue(
                                msg -> msg.editMessageEmbeds(eb.build()).queue(),
                                err -> recreate(ch, eb));
            }
        } catch (Exception e) {
            LOG.debug("status embed tick failed", e);
        }
    }

    private void recreate(TextChannel ch, EmbedBuilder eb) {
        try {
            Files.deleteIfExists(messageIdFile);
        } catch (Exception ignored) {
        }
        ch.sendMessageEmbeds(eb.build())
                .queue(
                        m -> {
                            try {
                                Files.writeString(messageIdFile, m.getId(), StandardCharsets.UTF_8);
                            } catch (Exception ex) {
                                LOG.warn("could not persist status embed id", ex);
                            }
                        });
    }

    private long loadMessageId() {
        try {
            if (!Files.isRegularFile(messageIdFile)) {
                return 0L;
            }
            String s = Files.readString(messageIdFile, StandardCharsets.UTF_8).trim();
            return Long.parseLong(s);
        } catch (Exception e) {
            return 0L;
        }
    }

    private Guild resolveGuild() {
        String gid = discord.guildId();
        if (gid == null || gid.isBlank() || "PUT_GUILD_ID_HERE".equalsIgnoreCase(gid)) {
            return jda.getGuilds().isEmpty() ? null : jda.getGuilds().get(0);
        }
        try {
            long id = Long.parseLong(gid.trim());
            return jda.getGuildById(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
