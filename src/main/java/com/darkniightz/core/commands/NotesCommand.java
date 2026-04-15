package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.main.PlayerProfileDAO;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public class NotesCommand implements CommandExecutor {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final PlayerProfileDAO dao;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;

    public NotesCommand(PlayerProfileDAO dao, ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
        this.dao = dao;
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!hasAccess(sender)) {
            sender.sendMessage(Messages.noPerm());
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(Messages.prefixed("§eUsage: §f/notes <player>"));
            return true;
        }

        String lookup = args[0];
        UUID targetUuid = null;
        OfflinePlayer target = Bukkit.getOfflinePlayer(lookup);
        if (target != null && (target.isOnline() || target.hasPlayedBefore())) {
            targetUuid = target.getUniqueId();
            if (target.getName() != null) {
                lookup = target.getName();
            }
        }

        List<PlayerProfileDAO.NoteRecord> notes = dao.loadPlayerNotes(targetUuid, lookup, 25);
        if (notes.isEmpty()) {
            sender.sendMessage(Messages.prefixed("§7No staff notes found for §f" + lookup + "§7."));
            return true;
        }

        sender.sendMessage(Messages.prefixed("§fStaff notes for §b" + lookup + "§7 (latest " + notes.size() + ")"));
        for (PlayerProfileDAO.NoteRecord note : notes) {
            String when = note.createdAtMs() <= 0L ? "unknown" : TS.format(Instant.ofEpochMilli(note.createdAtMs()));
            sender.sendMessage(Messages.prefixed("§8#" + note.id() + " §7[" + when + "] §b" + note.author() + "§7: §f" + note.note()));
        }
        return true;
    }

    private boolean hasAccess(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (player.isOp() || (devMode != null && devMode.isActive(player.getUniqueId()))) {
            return true;
        }
        PlayerProfile profile = profiles.getOrCreate(player, ranks.getDefaultGroup());
        String rank = profile == null || profile.getPrimaryRank() == null ? ranks.getDefaultGroup() : profile.getPrimaryRank();
        return ranks.isAtLeast(rank, "helper");
    }
}
