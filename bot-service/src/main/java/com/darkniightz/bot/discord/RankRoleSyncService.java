package com.darkniightz.bot.discord;

import com.darkniightz.bot.config.BotConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RankRoleSyncService {
    private static final Logger LOG = LoggerFactory.getLogger(RankRoleSyncService.class);

    private final JDA jda;
    private final BotConfig.Discord cfg;

    public RankRoleSyncService(JDA jda, BotConfig.Discord cfg) {
        this.jda = jda;
        this.cfg = cfg;
    }

    public void syncRoles(String discordUserId, String primaryRank, String donorRank) {
        if (discordUserId == null || discordUserId.isBlank()) {
            return;
        }
        if (cfg.guildId() == null || cfg.guildId().isBlank() || "PUT_GUILD_ID_HERE".equalsIgnoreCase(cfg.guildId())) {
            return;
        }

        Guild guild;
        try {
            long gid = Long.parseLong(cfg.guildId().trim());
            guild = jda.getGuildById(gid);
        } catch (NumberFormatException e) {
            LOG.warn("Invalid guild_id: {}", cfg.guildId());
            return;
        }
        if (guild == null) {
            LOG.warn("Guild not in cache — is the bot invited? guild_id={}", cfg.guildId());
            return;
        }
        Member member;
        try {
            member = guild.retrieveMemberById(discordUserId).complete();
        } catch (Exception e) {
            LOG.warn("Could not load member {} in guild {}: {}", discordUserId, cfg.guildId(), e.getMessage());
            return;
        }
        if (member == null) {
            LOG.warn("Member not found for discord_user_id={}", discordUserId);
            return;
        }

        Map<String, String> roleIds = cfg.roleIds();
        List<Role> removeRoles = new ArrayList<>();
        for (String roleId : roleIds.values()) {
            if (roleId == null || roleId.isBlank()) continue;
            Role role = guild.getRoleById(roleId);
            if (role != null && member.getRoles().contains(role)) {
                removeRoles.add(role);
            }
        }

        String selectedRank = pickDisplayRank(primaryRank, donorRank);
        String selectedRoleId = roleIds.getOrDefault(selectedRank.toLowerCase(Locale.ROOT), "");
        Role selectedRole = selectedRoleId.isBlank() ? null : guild.getRoleById(selectedRoleId);

        if (!removeRoles.isEmpty()) {
            guild.modifyMemberRoles(member, selectedRole == null ? List.of() : List.of(selectedRole), removeRoles).queue();
            return;
        }
        if (selectedRole != null && !member.getRoles().contains(selectedRole)) {
            guild.addRoleToMember(member, selectedRole).queue();
        }
    }

    private String pickDisplayRank(String primaryRank, String donorRank) {
        if (donorRank != null && !donorRank.isBlank()) {
            return donorRank;
        }
        if (primaryRank != null && !primaryRank.isBlank()) {
            return primaryRank;
        }
        return "pleb";
    }
}
