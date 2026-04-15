package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.chat.ChatUtil;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.system.MessageManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class ReplyCommand implements CommandExecutor {

    private final ProfileStore profiles;
    private final RankManager ranks;
    private final MessageManager messageManager;

    public ReplyCommand(ProfileStore profiles, RankManager ranks, MessageManager messageManager) {
        this.profiles = profiles;
        this.ranks = ranks;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can use this command."));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " <message>"));
            return true;
        }

        PlayerProfile profile = profiles.getOrCreate(player, ranks.getDefaultGroup());
        Long muteUntil = profile.getMuteUntil();
        if (muteUntil != null && muteUntil > System.currentTimeMillis()) {
            player.sendMessage(Messages.prefixed("§cYou are muted." + (muteUntil == Long.MAX_VALUE ? "" : " §7(ends in §e" + ((muteUntil - System.currentTimeMillis()) / 1000L) + "s§7)")));
            return true;
        }

        UUID targetId = messageManager.getLastMessaged(player.getUniqueId());
        if (targetId == null) {
            player.sendMessage(Messages.prefixed("§cYou have nobody to reply to."));
            return true;
        }

        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            player.sendMessage(Messages.prefixed("§cThat player is no longer online."));
            return true;
        }

        PlayerProfile targetProfile = profiles.getOrCreate(target, ranks.getDefaultGroup());
        if (targetProfile != null && !targetProfile.isPrivateMessagesEnabled()) {
            player.sendMessage(Messages.prefixed("§cThat player has private messages disabled."));
            return true;
        }

        String rawMessage = String.join(" ", args);
        if (rawMessage.isBlank()) {
            player.sendMessage(Messages.prefixed("§cMessage cannot be empty."));
            return true;
        }

        String message = formatPrivateMessage(player, profile, rawMessage);
        String senderName = formatDisplayName(player);
        String targetName = formatDisplayName(target);
        player.sendMessage(Messages.prefixed("§dReply sent §8→ §f" + targetName + "§7: " + message));
        target.sendMessage(Messages.prefixed("§dIncoming reply §8← §f" + senderName + "§7: " + message));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.35f);
        target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);

        profile.incMessages();
        profiles.saveDeferred(player.getUniqueId());
        messageManager.rememberConversation(player, target);
        JebaitedCore core = JebaitedCore.getInstance();
        if (core != null && core.getAuditLogService() != null) {
            core.getAuditLogService().logChat(player.getUniqueId(), player.getName(), "[DM -> " + target.getName() + "] " + ChatColor.stripColor(message));
        }
        return true;
    }

    private String formatDisplayName(Player player) {
        PlayerProfile profile = profiles.getOrCreate(player, ranks.getDefaultGroup());
        String rank = profile == null || profile.getPrimaryRank() == null ? ranks.getDefaultGroup() : profile.getPrimaryRank();
        var style = ranks.getStyle(rank);
        String base = org.bukkit.ChatColor.stripColor(player.getDisplayName());
        if (base == null || base.isBlank()) base = player.getName();
        String styled = ChatUtil.buildStyledName(base, style);
        JebaitedCore core = JebaitedCore.getInstance();
        if (core != null) {
            styled = core.decorateStyledNameWithTag(profile, styled, false);
        }
        return styled;
    }

    private String formatPrivateMessage(Player player, PlayerProfile profile, String rawMessage) {
        String sanitized = rawMessage.trim();
        if (canUsePrivateColors(player, profile)) {
            return ChatColor.translateAlternateColorCodes('&', sanitized);
        }
        return "§f" + sanitized;
    }

    private boolean canUsePrivateColors(Player player, PlayerProfile profile) {
        JebaitedCore core = JebaitedCore.getInstance();
        if (core == null) {
            return false;
        }
        if (!core.getConfig().getBoolean("private_messages.color_codes.enabled", true)) {
            return false;
        }
        if (player.isOp()) {
            return true;
        }
        String minRank = core.getConfig().getString("private_messages.color_codes.min_rank", "gold");
        String rank = profile == null || profile.getPrimaryRank() == null ? ranks.getDefaultGroup() : profile.getPrimaryRank();
        return ranks.isAtLeast(rank, minRank);
    }
}
