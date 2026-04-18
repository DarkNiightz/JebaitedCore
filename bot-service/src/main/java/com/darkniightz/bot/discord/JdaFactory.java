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
            JDA jda = JDABuilder.createDefault(tok)
                    .enableIntents(
                            GatewayIntent.GUILD_MEMBERS,
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT)
                    .setStatus(OnlineStatus.ONLINE)
                    .setActivity(Activity.customStatus(cfg.statusText()))
                    .build();
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
}
