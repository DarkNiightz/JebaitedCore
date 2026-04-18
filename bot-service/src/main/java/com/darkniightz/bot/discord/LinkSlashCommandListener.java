package com.darkniightz.bot.discord;

import com.darkniightz.bot.db.BotStatsDao;
import com.darkniightz.bot.db.DiscordLinkDao;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LinkSlashCommandListener extends ListenerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(LinkSlashCommandListener.class);
    private final DiscordLinkDao linkDao;
    private final RankRoleSyncService roleSyncService;
    private final BotStatsDao statsDao;

    public LinkSlashCommandListener(DiscordLinkDao linkDao, RankRoleSyncService roleSyncService, BotStatsDao statsDao) {
        this.linkDao = linkDao;
        this.roleSyncService = roleSyncService;
        this.statsDao = statsDao;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        try {
            handleSlash(event);
        } catch (Exception e) {
            LOG.warn("Slash command failed name={}", event.getName(), e);
            if (event.isAcknowledged()) {
                event.getHook().sendMessage("Something went wrong processing that command.").setEphemeral(true).queue();
            } else {
                event.reply("Something went wrong processing that command.").setEphemeral(true).setSuppressEmbeds(true).queue();
            }
        }
    }

    private void handleSlash(SlashCommandInteractionEvent event) {
        if ("status".equalsIgnoreCase(event.getName())) {
            BotStatsDao.NetworkStats s = statsDao.loadNetworkStats();
            String lines =
                    "**Jebaited — network snapshot**\n"
                            + "Registered players: **"
                            + s.registeredPlayers()
                            + "**\n"
                            + "Discord links (active): **"
                            + s.linkedDiscordAccounts()
                            + "**\n"
                            + "All-time joins / kills / deaths: **"
                            + s.totalJoins()
                            + "** / **"
                            + s.totalKills()
                            + "** / **"
                            + s.totalDeaths()
                            + "**\n"
                            + "Messages sent (tracked): **"
                            + s.totalMessages()
                            + "** · Playtime (sum): **~"
                            + s.totalPlaytimeHours()
                            + "h**\n"
                            + "Bot: **online** — in Minecraft run `/link` for a code, then use `/link` here with the `code` option.";
            event.reply(lines).setEphemeral(true).queue();
            return;
        }
        if (!"link".equalsIgnoreCase(event.getName())) {
            return;
        }
        String code = event.getOption("code") == null ? null : event.getOption("code").getAsString();
        if (code == null || code.isBlank()) {
            event.reply("Missing code. Paste the code from Minecraft into the `code` option.").setEphemeral(true).queue();
            return;
        }

        // Defer immediately so we stay inside Discord's 3s window even if DB or role sync is slow.
        event.deferReply(true).queue(hook -> {
            try {
                DiscordLinkDao.LinkResult result = linkDao.consumeCodeAndLink(code, event.getUser().getId());
                if (!result.success()) {
                    hook.editOriginal(result.message()).queue();
                    return;
                }
                try {
                    roleSyncService.syncRoles(event.getUser().getId(), result.primaryRank(), result.donorRank());
                    hook.editOriginal("Linked! Your Discord roles have been synced with your Jebaited rank.").queue();
                } catch (Exception e) {
                    LOG.warn("Role sync failed for discord user {}", event.getUser().getId(), e);
                    hook.editOriginal(
                            "Your Minecraft account is linked, but rank roles could not be synced. "
                                    + "Ask staff to check bot permissions (Manage Roles) and role hierarchy."
                    ).queue();
                }
            } catch (Exception e) {
                LOG.warn("Link transaction failed", e);
                hook.editOriginal("Link failed due to a server error. Try again in a moment.").queue();
            }
        }, failure -> LOG.warn("Failed to defer link reply", failure));
    }
}
