package com.darkniightz.bot.discord;

import com.darkniightz.bot.bridge.PluginBridgeClient;
import com.darkniightz.bot.config.BotConfig;
import com.darkniightz.bot.db.ActivityChartDao;
import com.darkniightz.bot.db.BotStatsDao;
import com.darkniightz.bot.db.DiscordLinkDao;
import com.darkniightz.bot.db.PlayerLookupDao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class MonitoringSlashCommandListener extends ListenerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(MonitoringSlashCommandListener.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String LINK_REQUIRED =
            "Link your Minecraft account first: in-game `/link`, then `/link` here with your code.";

    private final BotStatsDao statsDao;
    private final PlayerLookupDao playerLookupDao;
    private final ActivityChartDao activityDao;
    private final PluginBridgeClient bridgeClient;
    private final DiscordLinkDao linkDao;

    public MonitoringSlashCommandListener(
            BotStatsDao statsDao,
            PlayerLookupDao playerLookupDao,
            ActivityChartDao activityDao,
            PluginBridgeClient bridgeClient,
            DiscordLinkDao linkDao) {
        this.statsDao = statsDao;
        this.playerLookupDao = playerLookupDao;
        this.activityDao = activityDao;
        this.bridgeClient = bridgeClient;
        this.linkDao = linkDao;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String n = event.getName().toLowerCase(Locale.ROOT);
        if (!(n.equals("ping")
                || n.equals("server")
                || n.equals("player")
                || n.equals("activity"))) {
            return;
        }
        try {
            switch (n) {
                case "ping", "server" -> handleServerPing(event);
                case "player" -> handlePlayer(event);
                case "activity" -> handleActivity(event);
                default -> {}
            }
        } catch (Exception e) {
            LOG.warn("slash {}", n, e);
            if (event.isAcknowledged()) {
                event.getHook().sendMessage("Command failed.").setEphemeral(true).queue();
            } else {
                event.reply("Command failed.").setEphemeral(true).queue();
            }
        }
    }

    private void handleServerPing(SlashCommandInteractionEvent event) {
        if (!linkDao.isLinked(event.getUser().getId())) {
            event.reply(LINK_REQUIRED).setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        if (!bridgeClient.isConfigured()) {
            event.getHook().sendMessage("Plugin bridge is not configured (set `plugin_inbound_base_url` + `plugin_api_token`).").queue();
            return;
        }
        try {
            String raw = bridgeClient.getServerStatusJson();
            JsonNode root = JSON.readTree(raw);
            int online = root.path("online").asInt(0);
            int max = root.path("max").asInt(0);
            String motd = root.path("motd").asText("—");
            String ver = root.path("version").asText("—");
            double tps = root.path("tps").asDouble(20.0);
            double mspt = root.path("mspt").asDouble(0.0);
            StringBuilder list = new StringBuilder();
            JsonNode players = root.path("players");
            if (players.isArray()) {
                for (JsonNode p : players) {
                    if (list.length() > 0) {
                        list.append(", ");
                    }
                    String name = p.path("name").asText("?");
                    int ping = p.path("ping").asInt(0);
                    list.append(name).append(" (").append(ping).append("ms)");
                }
            }
            if (list.length() > 1800) {
                list.setLength(1800);
                list.append("…");
            }
            if (list.isEmpty()) {
                list.append("—");
            }
            EmbedBuilder eb =
                    new EmbedBuilder()
                            .setTitle("Server status")
                            .setColor(new Color(0x3498DB))
                            .addField("Online", online + " / " + max, true)
                            .addField("TPS / MSPT", String.format(Locale.US, "%.2f / %.2f", tps, mspt), true)
                            .addField("MOTD", motd.length() > 256 ? motd.substring(0, 256) + "…" : motd, false)
                            .addField("Version", ver.length() > 1024 ? ver.substring(0, 1024) : ver, false)
                            .addField("Players", list.toString(), false)
                            .setTimestamp(Instant.now());
            event.getHook().sendMessageEmbeds(eb.build()).queue();
        } catch (Exception e) {
            event.getHook().sendMessage("Could not reach Paper plugin API: " + e.getMessage()).queue();
        }
    }

    private void handlePlayer(SlashCommandInteractionEvent event) {
        if (!linkDao.isLinked(event.getUser().getId())) {
            event.reply(LINK_REQUIRED).setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        OptionMapping om = event.getOption("name");
        String name = om == null ? null : om.getAsString();
        if (name == null || name.isBlank()) {
            event.getHook().sendMessage("Provide a Minecraft name.").queue();
            return;
        }
        Optional<PlayerLookupDao.PlayerRow> row = playerLookupDao.findByUsername(name.trim());
        if (row.isEmpty()) {
            event.getHook().sendMessage("No player row for `" + name + "` (they may never have joined).").queue();
            return;
        }
        PlayerLookupDao.PlayerRow p = row.get();
        long hours = p.playtimeMs() / 3_600_000L;
        String skin = "https://crafatar.com/avatars/" + p.uuid() + "?size=128&overlay";
        EmbedBuilder eb =
                new EmbedBuilder()
                        .setTitle(p.username())
                        .setColor(new Color(0x1ABC9C))
                        .setThumbnail(skin)
                        .addField("UUID", "`" + p.uuid() + "`", false)
                        .addField("Rank", p.rank(), true)
                        .addField("Playtime (approx)", hours + "h", true)
                        .addField("K / D / Msg", p.kills() + " / " + p.deaths() + " / " + p.messagesSent(), true)
                        .addField(
                                "First joined",
                                LocalDateTime.ofInstant(
                                                Instant.ofEpochMilli(p.firstJoinedMs()),
                                                ZoneId.systemDefault())
                                        .format(DateTimeFormatter.ISO_LOCAL_DATE),
                                false)
                        .addField("Past names", "Not stored (single username row per UUID)", false)
                        .setTimestamp(Instant.now());
        event.getHook().sendMessageEmbeds(eb.build()).queue();
    }

    private void handleActivity(SlashCommandInteractionEvent event) {
        if (!linkDao.isLinked(event.getUser().getId())) {
            event.reply(LINK_REQUIRED).setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        List<ActivityChartDao.Point> pts = activityDao.last24Hours();
        if (pts.isEmpty()) {
            event.getHook()
                    .sendMessage(
                            "No samples yet — ensure the plugin has run with DB migrations and `discord.activity_sample_interval_ticks`.")
                    .queue();
            return;
        }
        int max = 1;
        for (ActivityChartDao.Point pt : pts) {
            max = Math.max(max, pt.online());
        }
        String blocks = "▁▂▃▄▅▆▇█";
        StringBuilder bar = new StringBuilder();
        int step = Math.max(1, pts.size() / 24);
        for (int i = 0; i < pts.size(); i += step) {
            int v = pts.get(i).online();
            int idx = (int) Math.round((v / (double) max) * (blocks.length() - 1));
            idx = Math.min(blocks.length() - 1, Math.max(0, idx));
            bar.append(blocks.charAt(idx));
        }
        ActivityChartDao.Point last = pts.get(pts.size() - 1);
        ActivityChartDao.Point first = pts.get(0);
        BotStatsDao.NetworkStats nw = statsDao.loadNetworkStats();
        EmbedBuilder eb =
                new EmbedBuilder()
                        .setTitle("24h player activity (samples)")
                        .setColor(new Color(0x16A085))
                        .setDescription("```\n" + bar + "\n```")
                        .addField("Now (last sample)", String.valueOf(last.online()), true)
                        .addField("Window", formatMs(first.sampledAtEpochMs()) + " → " + formatMs(last.sampledAtEpochMs()), true)
                        .addField("DB registered", String.valueOf(nw.registeredPlayers()), false)
                        .setTimestamp(Instant.now());
        event.getHook().sendMessageEmbeds(eb.build()).queue();
    }

    private static String formatMs(long ms) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    }
}
