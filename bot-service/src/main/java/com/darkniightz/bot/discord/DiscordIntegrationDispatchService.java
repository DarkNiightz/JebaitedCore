package com.darkniightz.bot.discord;

import com.darkniightz.bot.config.BotConfig;
import com.darkniightz.bot.observability.AuditLogger;
import com.darkniightz.bot.observability.CorrelationId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

import java.awt.Color;
import java.time.Instant;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Routes signed JSON payloads (HTTP webhook + Redis) into Discord messages.
 */
public final class DiscordIntegrationDispatchService {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Supplier<JDA> jdaSupplier;
    private final BotConfig.Discord discord;

    public DiscordIntegrationDispatchService(Supplier<JDA> jdaSupplier, BotConfig.Discord discord) {
        this.jdaSupplier = jdaSupplier;
        this.discord = discord;
    }

    public void dispatch(String jsonPayload) {
        JDA jda = jdaSupplier.get();
        if (jda == null) {
            AuditLogger.info("discord.dispatch.skipped", CorrelationId.next(), "jda=null");
            return;
        }
        JsonNode root;
        try {
            root = JSON.readTree(jsonPayload);
        } catch (Exception e) {
            AuditLogger.info("discord.dispatch.bad_json", CorrelationId.next(), e.getMessage());
            return;
        }
        String type = root.path("type").asText("");
        JsonNode payload = root.path("payload");
        switch (type) {
            case "moderation.mirror" -> postModeration(jda, payload);
            case "event.announce" -> postEvent(jda, payload);
            case "relay.chat" -> postRelay(jda, payload);
            case "console.line" -> postConsoleLine(jda, payload);
            default -> AuditLogger.info("discord.dispatch.unknown_type", CorrelationId.next(), "type=" + type);
        }
    }

    private void postModeration(JDA jda, JsonNode p) {
        String chId = discord.modLogsChannelId();
        if (chId == null || chId.isBlank()) {
            return;
        }
        TextChannel ch = resolveTextChannel(jda, chId);
        if (ch == null) {
            return;
        }
        String action = p.path("actionType").asText("?");
        String target = p.path("targetName").asText("?");
        String actor = p.path("actor").asText("?");
        String reason = p.path("reason").asText("");
        Long durationMs = p.has("durationMs") && !p.get("durationMs").isNull() ? p.get("durationMs").asLong() : null;
        Long expiresAt = p.has("expiresAt") && !p.get("expiresAt").isNull() ? p.get("expiresAt").asLong() : null;

        Color color = switch (action.toLowerCase()) {
            case "ban", "tempban" -> Color.RED;
            case "mute", "tempmute" -> Color.ORANGE;
            case "kick" -> new Color(0xE67E22);
            case "warn" -> new Color(0xF1C40F);
            default -> Color.GRAY;
        };

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("In-game moderation")
                .setColor(color)
                .addField("Action", action, true)
                .addField("Target", target, true)
                .addField("Staff", actor, true)
                .setTimestamp(Instant.now());
        if (!reason.isBlank()) {
            eb.addField("Reason", reason, false);
        }
        if (durationMs != null && durationMs > 0) {
            eb.addField("Duration", formatDuration(durationMs), true);
        }
        if (expiresAt != null && expiresAt > 0) {
            eb.addField("Expires", "<t:" + (expiresAt / 1000) + ":R>", true);
        }
        ch.sendMessageEmbeds(eb.build()).queue();
    }

    private void postEvent(JDA jda, JsonNode p) {
        String chId = discord.announcementsChannelId();
        if (chId == null || chId.isBlank()) {
            return;
        }
        TextChannel ch = resolveTextChannel(jda, chId);
        if (ch == null) {
            return;
        }
        String phase = p.path("phase").asText("start").toLowerCase(Locale.ROOT);
        String display = p.path("displayName").asText("Event");
        String kind = p.path("kind").asText("");
        int players = p.path("playerCount").asInt(0);
        String key = p.path("eventKey").asText("");
        String winner = p.path("winnerName").asText("");
        String tieDetail = p.path("tieDetail").asText("");
        String cancelReason = p.path("cancelReason").asText("");
        int rewardCoins = p.path("rewardCoins").asInt(0);
        String endReason = p.path("endReason").asText("");

        String title = switch (phase) {
            case "end" -> "Event finished";
            case "tie" -> "Event — tie";
            case "cancelled" -> "Event cancelled";
            default -> "Event started";
        };
        Color color =
                switch (phase) {
                    case "end" -> new Color(0x2ECC71);
                    case "tie" -> new Color(0xE67E22);
                    case "cancelled" -> new Color(0xE74C3C);
                    default -> new Color(0x9B59B6);
                };

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(title)
                .setColor(color)
                .setDescription("**" + display + "**")
                .addField("Mode", kind.isBlank() ? "—" : kind, true)
                .setFooter(key, null)
                .setTimestamp(Instant.now());

        if ("start".equals(phase)) {
            eb.addField("Players", String.valueOf(players), true);
        }
        if ("end".equals(phase) && !winner.isBlank()) {
            eb.addField("Winner", winner, true);
            if (rewardCoins > 0) {
                eb.addField("Reward", rewardCoins + " coins", true);
            }
            if (!endReason.isBlank()) {
                eb.addField("Note", endReason, false);
            }
        }
        if ("tie".equals(phase) && !tieDetail.isBlank()) {
            eb.addField("Tie", tieDetail, false);
            if (rewardCoins > 0) {
                eb.addField("Pool", rewardCoins + " coins (split)", true);
            }
        }
        if ("cancelled".equals(phase) && !cancelReason.isBlank()) {
            eb.addField("Reason", cancelReason, false);
        }

        String rolePing = discord.eventsPingRoleId();
        boolean ping = "start".equals(phase);
        String mention = ping && rolePing != null && !rolePing.isBlank() ? "<@&" + rolePing + ">" : "";
        MessageCreateBuilder mb = new MessageCreateBuilder().addEmbeds(eb.build());
        if (!mention.isBlank()) {
            mb.setContent(mention);
        }
        ch.sendMessage(mb.build()).queue();
    }

    private void postRelay(JDA jda, JsonNode p) {
        String bridge = p.path("bridge").asText("global").toLowerCase(Locale.ROOT);
        String chId = resolveRelayOutChannelId(bridge);
        if (chId == null || chId.isBlank()) {
            return;
        }
        TextChannel ch = resolveTextChannel(jda, chId);
        if (ch == null) {
            return;
        }
        String who = p.path("playerName").asText("?");
        String msg = p.path("message").asText("");
        String prefix =
                switch (bridge) {
                    case "staff" -> "[Staff] ";
                    case "faction" -> "[Faction] ";
                    default -> "";
                };
        ch.sendMessage(prefix + "**[" + who + "]** " + msg).queue();
    }

    private String resolveRelayOutChannelId(String bridge) {
        return switch (bridge) {
            case "staff" -> firstNonBlank(discord.relayStaffOutChannelId(), discord.relayOutChannelId());
            case "faction" -> firstNonBlank(discord.relayFactionOutChannelId(), discord.relayOutChannelId());
            default -> discord.relayOutChannelId();
        };
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b == null ? "" : b;
    }

    private void postConsoleLine(JDA jda, JsonNode p) {
        String chId = discord.consoleChannelId();
        if (chId == null || chId.isBlank()) {
            return;
        }
        TextChannel ch = resolveTextChannel(jda, chId);
        if (ch == null) {
            return;
        }
        String line = p.path("line").asText("");
        if (line.isBlank()) {
            return;
        }
        String body = line.length() > 1800 ? line.substring(0, 1800) + "…" : line;
        String safe = body.replace("```", "`\u200B``");
        ch.sendMessage("```\n" + safe + "\n```").queue();
    }

    private TextChannel resolveTextChannel(JDA jda, String channelId) {
        Guild g = jda.getGuildById(discord.guildId());
        if (g == null) {
            return null;
        }
        return g.getChannelById(TextChannel.class, channelId);
    }

    private static String formatDuration(long ms) {
        long sec = ms / 1000L;
        if (sec < 60) {
            return sec + "s";
        }
        long min = sec / 60;
        if (min < 60) {
            return min + "m";
        }
        long h = min / 60;
        long m = min % 60;
        return h + "h " + m + "m";
    }
}
