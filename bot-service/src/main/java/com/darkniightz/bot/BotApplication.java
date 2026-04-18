package com.darkniightz.bot;

import com.darkniightz.bot.config.BotConfig;
import com.darkniightz.bot.config.ConfigLoader;
import com.darkniightz.bot.bridge.PluginBridgeClient;
import com.darkniightz.bot.db.ActivityChartDao;
import com.darkniightz.bot.db.BotDatabase;
import com.darkniightz.bot.db.BotStatsDao;
import com.darkniightz.bot.db.DiscordLinkDao;
import com.darkniightz.bot.db.IntegrationAuditDao;
import com.darkniightz.bot.db.PlayerLookupDao;
import com.darkniightz.bot.db.SqlHealthCheck;
import com.darkniightz.bot.discord.DiscordGatewayListener;
import com.darkniightz.bot.discord.DiscordIntegrationDispatchService;
import com.darkniightz.bot.discord.JdaFactory;
import com.darkniightz.bot.discord.LinkSlashCommandListener;
import com.darkniightz.bot.discord.MonitoringSlashCommandListener;
import com.darkniightz.bot.discord.RankRoleSyncService;
import com.darkniightz.bot.discord.StatusEmbedUpdater;
import com.darkniightz.bot.http.HealthController;
import com.darkniightz.bot.http.WebhookController;
import com.darkniightz.bot.observability.AuditLogger;
import com.darkniightz.bot.observability.CorrelationId;
import com.darkniightz.bot.observability.Metrics;
import com.darkniightz.bot.redis.RedisBus;
import com.darkniightz.bot.security.NonceStore;
import com.darkniightz.bot.security.WebhookSigner;
import com.darkniightz.bot.security.WebhookVerifier;
import com.sun.net.httpserver.HttpServer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class BotApplication {
    private BotApplication() {}

    public static void main(String[] args) throws Exception {
        BotConfig config = ConfigLoader.loadFromClasspath("bot-config.yml");
        BotDatabase db = new BotDatabase(config.database());
        BotStatsDao statsDao = new BotStatsDao(db.dataSource());
        PlayerLookupDao playerLookupDao = new PlayerLookupDao(db.dataSource());
        ActivityChartDao activityChartDao = new ActivityChartDao(db.dataSource());
        IntegrationAuditDao auditDao = new IntegrationAuditDao(db.dataSource());
        RedisBus redisBus = new RedisBus(config.redis().uri());
        AtomicReference<JDA> jdaRef = new AtomicReference<>();
        DiscordIntegrationDispatchService integrationDispatch = new DiscordIntegrationDispatchService(jdaRef::get, config.discord());
        JDA jda = null;
        ScheduledExecutorService presenceScheduler = null;
        ScheduledExecutorService statusEmbedScheduler = null;
        PluginBridgeClient pluginBridge =
                new PluginBridgeClient(config.discord().pluginInboundBaseUrl(), config.discord().pluginApiToken());
        String token = config.discord().token();
        if (token != null && !token.isBlank() && !"PUT_TOKEN_HERE".equalsIgnoreCase(token)) {
            jda = new JdaFactory().createOrNull(config.discord());
            if (jda != null) {
                jdaRef.set(jda);
                DiscordLinkDao linkDao = new DiscordLinkDao(db.dataSource());
                jda.addEventListener(new LinkSlashCommandListener(linkDao, new RankRoleSyncService(jda, config.discord()), statsDao));
                jda.addEventListener(
                        new MonitoringSlashCommandListener(statsDao, playerLookupDao, activityChartDao, pluginBridge, linkDao));
                jda.addEventListener(new DiscordGatewayListener(config.discord(), pluginBridge, linkDao));
                registerSlashCommands(jda, config.discord().guildId());
                presenceScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "jda-presence");
                    t.setDaemon(true);
                    return t;
                });
                ScheduledExecutorService sched = presenceScheduler;
                JDA jdaFinal = jda;
                sched.scheduleAtFixedRate(
                        () -> {
                            try {
                                BotStatsDao.NetworkStats s = statsDao.loadNetworkStats();
                                String line =
                                        "Jebaited · "
                                                + s.registeredPlayers()
                                                + " reg · "
                                                + s.linkedDiscordAccounts()
                                                + " linked";
                                jdaFinal.getPresence().setActivity(Activity.playing(line));
                            } catch (Exception ignored) {
                            }
                        },
                        15,
                        120,
                        TimeUnit.SECONDS);
                statusEmbedScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "status-embed");
                    t.setDaemon(true);
                    return t;
                });
                StatusEmbedUpdater updater = new StatusEmbedUpdater(jdaFinal, config.discord(), pluginBridge);
                statusEmbedScheduler.scheduleAtFixedRate(updater, 30, 90, TimeUnit.SECONDS);
            }
        } else {
            AuditLogger.info("bot.discord.disabled", CorrelationId.next(), "No token configured; Discord gateway not started.");
        }

        Metrics metrics = new Metrics();
        WebhookSigner signer = new WebhookSigner(config.security().webhookHmacSecret());
        WebhookVerifier verifier = new WebhookVerifier(signer, new NonceStore(), config.security().maxSkewSeconds());
        HealthController healthController = new HealthController(new SqlHealthCheck(db.dataSource()), redisBus);
        WebhookController webhookController = new WebhookController(verifier, metrics, integrationDispatch, auditDao);

        HttpServer server = HttpServer.create(new InetSocketAddress(config.http().host(), config.http().port()), 0);
        server.createContext("/health/live", healthController::live);
        server.createContext("/health/ready", healthController::ready);
        server.createContext("/webhooks/panel", webhookController::handle);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        redisBus.subscribe(config.redis().pubsubChannel(), (channel, payload) -> {
            String correlationId = CorrelationId.next();
            AuditLogger.info("redis.event.received", correlationId, "channel=" + channel + " bytes=" + payload.length());
            try {
                integrationDispatch.dispatch(payload);
            } catch (Exception e) {
                AuditLogger.info("redis.dispatch.failed", correlationId, e.getMessage() == null ? "error" : e.getMessage());
            }
        });

        final JDA jdaShutdown = jda;
        final ScheduledExecutorService presenceShutdown = presenceScheduler;
        final ScheduledExecutorService statusEmbedShutdown = statusEmbedScheduler;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0);
            if (statusEmbedShutdown != null) {
                statusEmbedShutdown.shutdownNow();
            }
            if (presenceShutdown != null) {
                presenceShutdown.shutdownNow();
            }
            if (jdaShutdown != null) {
                jdaShutdown.shutdownNow();
            }
            redisBus.close();
            db.close();
        }));

        AuditLogger.info("bot.started", CorrelationId.next(), "httpPort=" + config.http().port());
    }

    /**
     * Registers {@code /link} and {@code /status} synchronously so they exist before players test.
     * Guild commands apply immediately when the bot is in that server; otherwise falls back to global.
     */
    private static void registerSlashCommands(JDA jda, String guildId) {
        CommandData link = Commands.slash("link", "Link your Minecraft account using your one-time in-game code")
                .addOption(OptionType.STRING, "code", "One-time code from /link in Minecraft", true);
        CommandData status = Commands.slash("status", "Show basic Jebaited bot / DB status");
        CommandData ping = Commands.slash("ping", "Live TPS, players, and MOTD from the Paper server");
        CommandData serverCmd = Commands.slash("server", "Same as /ping — live Paper status");
        CommandData player =
                Commands.slash("player", "Look up a player (UUID, skin, stats, playtime)")
                        .addOption(OptionType.STRING, "name", "Minecraft username", true);
        CommandData activity =
                Commands.slash("activity", "24h online-count chart (requires DB samples from the plugin)");

        try {
            Guild guild = resolveGuild(jda, guildId);
            if (guild != null) {
                guild.updateCommands()
                        .addCommands(link, status, ping, serverCmd, player, activity)
                        .complete();
                AuditLogger.info("bot.slash", CorrelationId.next(), "scope=guild guildId=" + guild.getId());
            } else {
                jda.updateCommands().addCommands(link, status, ping, serverCmd, player, activity).complete();
                AuditLogger.info(
                        "bot.slash",
                        CorrelationId.next(),
                        "scope=global guildConfigured=" + (guildId != null && !guildId.isBlank()));
            }
        } catch (Exception e) {
            AuditLogger.info("bot.slash.failed", CorrelationId.next(), e.getMessage() == null ? e.getClass().getName() : e.getMessage());
        }
    }

    private static Guild resolveGuild(JDA jda, String guildId) {
        if (guildId == null || guildId.isBlank() || "PUT_GUILD_ID_HERE".equalsIgnoreCase(guildId)) {
            return null;
        }
        try {
            long id = Long.parseLong(guildId.trim());
            return jda.getGuildById(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
