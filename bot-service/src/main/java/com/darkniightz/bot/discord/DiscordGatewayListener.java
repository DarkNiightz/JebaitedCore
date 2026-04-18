package com.darkniightz.bot.discord;

import com.darkniightz.bot.bridge.PluginBridgeClient;
import com.darkniightz.bot.config.BotConfig;
import com.darkniightz.bot.db.DiscordLinkDao;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Discord → Paper: bridge chat channels and remote console (developer role).
 */
public final class DiscordGatewayListener extends ListenerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(DiscordGatewayListener.class);
    private static final long LINK_HINT_COOLDOWN_MS = 120_000L;

    private final BotConfig.Discord discord;
    private final PluginBridgeClient bridge;
    private final DiscordLinkDao linkDao;
    private final Map<String, Long> linkHintAt = new ConcurrentHashMap<>();

    public DiscordGatewayListener(BotConfig.Discord discord, PluginBridgeClient bridge, DiscordLinkDao linkDao) {
        this.discord = discord;
        this.bridge = bridge;
        this.linkDao = linkDao;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot() || event.getMember() == null) {
            return;
        }
        if (!bridge.isConfigured()) {
            return;
        }
        GuildMatch g = resolveGuild(event);
        if (!g.matches()) {
            return;
        }
        String chId = event.getChannel().getId();
        String raw = event.getMessage().getContentDisplay().trim();
        if (raw.isEmpty()) {
            return;
        }

        String console = nz(discord.consoleChannelId());
        if (!console.isEmpty() && chId.equals(console)) {
            if (!(raw.startsWith(">") || raw.startsWith("!"))) {
                return;
            }
            if (!hasDeveloperRole(event.getMember())) {
                return;
            }
            String cmd = raw.substring(1).trim();
            if (cmd.isEmpty()) {
                return;
            }
            try {
                bridge.postConsoleCommand(cmd, event.getAuthor().getId());
            } catch (Exception e) {
                LOG.warn("console forward failed", e);
            }
            return;
        }

        String bridgeKind = mapInboundChannel(chId);
        if (bridgeKind == null) {
            return;
        }
        if (!linkDao.isLinked(event.getAuthor().getId())) {
            long now = System.currentTimeMillis();
            String uid = event.getAuthor().getId();
            Long prev = linkHintAt.get(uid);
            if (prev == null || now - prev >= LINK_HINT_COOLDOWN_MS) {
                linkHintAt.put(uid, now);
                event.getMessage()
                        .reply(
                                "You need to **link your Minecraft account** before using linked chat.\n"
                                        + "In Minecraft run `/link`, then use `/link` here with your code from the bot.")
                        .queue();
            }
            return;
        }
        String author = event.getMember().getEffectiveName();
        if (author == null || author.isBlank()) {
            author = event.getAuthor().getName();
        }
        try {
            bridge.postBridgeIn(bridgeKind, author, event.getAuthor().getId(), raw);
        } catch (Exception e) {
            LOG.warn("bridge in failed", e);
        }
    }

    private GuildMatch resolveGuild(MessageReceivedEvent event) {
        String want = discord.guildId() == null ? "" : discord.guildId().trim();
        if (want.isEmpty() || "PUT_GUILD_ID_HERE".equalsIgnoreCase(want)) {
            return new GuildMatch(true);
        }
        return new GuildMatch(event.getGuild() != null && want.equals(event.getGuild().getId()));
    }

    private record GuildMatch(boolean matches) {}

    private String mapInboundChannel(String channelId) {
        String g = nz(discord.relayGlobalInChannelId());
        String s = nz(discord.relayStaffInChannelId());
        String f = nz(discord.relayFactionInChannelId());
        if (!g.isEmpty() && channelId.equals(g)) {
            return "global";
        }
        if (!s.isEmpty() && channelId.equals(s)) {
            return "staff";
        }
        if (!f.isEmpty() && channelId.equals(f)) {
            return "faction";
        }
        return null;
    }

    private boolean hasDeveloperRole(Member member) {
        String rid = nz(discord.consoleDeveloperRoleId());
        if (rid.isEmpty()) {
            return false;
        }
        for (Role r : member.getRoles()) {
            if (rid.equals(r.getId())) {
                return true;
            }
        }
        return false;
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
