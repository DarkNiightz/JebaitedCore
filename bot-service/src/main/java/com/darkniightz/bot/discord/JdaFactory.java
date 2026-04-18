package com.darkniightz.bot.discord;

import com.darkniightz.bot.config.BotConfig;
import com.darkniightz.bot.observability.AuditLogger;
import com.darkniightz.bot.observability.CorrelationId;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;

/**
 * Never throws: invalid tokens and login failures return {@code null} so the HTTP server can still run.
 */
public final class JdaFactory {
    public JDA createOrNull(BotConfig.Discord cfg) {
        String tok = cfg.token();
        if (tok == null || tok.isBlank()) {
            return null;
        }
        try {
            /*
             * Privileged gateway intents (Server Members + Message Content) require toggles in the Discord
             * Developer Portal. If JDA requests them without portal approval, login fails with 4014.
             * Opt-in via JB_DISCORD_PRIVILEGED_INTENTS=true after enabling intents for the application.
             */
            JDABuilder b = JDABuilder.createDefault(tok)
                    .setStatus(OnlineStatus.ONLINE)
                    .setActivity(Activity.customStatus(cfg.statusText()));
            boolean privileged = privilegedIntentsEnabled();
            AuditLogger.info(
                    "bot.discord.intent_mode",
                    CorrelationId.next(),
                    privileged ? "privileged (MEMBERS+MESSAGES+CONTENT — enable in Portal)" : "default (no extra privileged intents)");
            if (privileged) {
                b.enableIntents(
                        GatewayIntent.GUILD_MEMBERS,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT);
            }
            JDA jda = b.build();
            jda.awaitReady();
            return jda;
        } catch (InvalidTokenException e) {
            AuditLogger.info(
                    "bot.discord.invalid_token",
                    CorrelationId.next(),
                    "Discord rejected the token (wrong value, revoked, or not the Bot token).");
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            AuditLogger.info("bot.discord.interrupted", CorrelationId.next(), "JDA login interrupted");
            return null;
        } catch (RuntimeException e) {
            AuditLogger.info(
                    "bot.discord.login_failed",
                    CorrelationId.next(),
                    e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage()));
            return null;
        }
    }

    private static boolean privilegedIntentsEnabled() {
        String v = System.getenv("JB_DISCORD_PRIVILEGED_INTENTS");
        return v != null && ("1".equals(v) || "true".equalsIgnoreCase(v.trim()));
    }
}
