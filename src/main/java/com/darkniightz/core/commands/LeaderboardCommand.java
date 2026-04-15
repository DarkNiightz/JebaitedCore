package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.system.LeaderboardManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class LeaderboardCommand implements CommandExecutor {
    private final LeaderboardManager manager;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;

    public LeaderboardCommand(LeaderboardManager manager, ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
        this.manager = manager;
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can use /leaderboard."));
            return true;
        }
        var profile = profiles.getOrCreate(player, ranks.getDefaultGroup());
        boolean bypass = devMode != null && devMode.isActive(player.getUniqueId());
        if (!bypass && !ranks.isAtLeast(profile.getPrimaryRank(), "admin")) {
            sender.sendMessage(Messages.noPerm());
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " set <category> [id]"));
            player.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " remove <id>"));
            player.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " list"));
            player.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " refresh [id]"));
            player.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " debug <id>"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("list".equals(sub)) {
            var rows = manager.listDefinitionDetails();
            if (rows.isEmpty()) {
                player.sendMessage(Messages.prefixed("§dLeaderboards: §7none"));
                return true;
            }
            player.sendMessage(Messages.prefixed("§dLeaderboards §7(" + rows.size() + "):"));
            for (String row : rows) {
                player.sendMessage(Messages.prefixed("§8- §f" + row));
            }
            return true;
        }

        if ("refresh".equals(sub)) {
            if (args.length >= 2) {
                boolean ok = manager.refreshDefinition(args[1]);
                player.sendMessage(Messages.prefixed(ok ? "§aRefreshed leaderboard §f" + args[1] : "§cLeaderboard not found."));
            } else {
                int count = manager.refreshNow();
                player.sendMessage(Messages.prefixed("§aRefreshed §f" + count + "§a leaderboard(s). Auto-refresh is every §f" + manager.refreshIntervalSeconds() + "s§a."));
            }
            return true;
        }

        if ("debug".equals(sub)) {
            if (args.length < 2) {
                player.sendMessage(Messages.prefixed("§cUsage: §f/" + label + " debug <id>"));
                return true;
            }
            player.sendMessage(Messages.prefixed("§7Leaderboard debug: §f" + manager.debugDefinition(args[1])));
            return true;
        }

        if ("remove".equals(sub)) {
            if (args.length < 2) {
                player.sendMessage(Messages.prefixed("§cUsage: §f/" + label + " remove <id>"));
                return true;
            }
            boolean ok = manager.remove(args[1]);
            player.sendMessage(Messages.prefixed(ok ? "§aRemoved leaderboard §f" + args[1] : "§cLeaderboard not found."));
            return true;
        }

        if ("set".equals(sub)) {
            if (args.length < 2) {
                player.sendMessage(Messages.prefixed("§cUsage: §f/" + label + " set <category> [id]"));
                return true;
            }
            LeaderboardManager.Category category = LeaderboardManager.Category.fromInput(args[1]);
            if (category == null) {
                player.sendMessage(Messages.prefixed("\u00a7cUnknown category. Try: blocks_broken, crops_broken, fish_caught, mcmmo_level, playtime, kills, bosses_killed, event_wins_combat, event_wins_chat, event_wins_hardcore"));
                return true;
            }
            String id = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : category.name().toLowerCase(Locale.ROOT);
            boolean ok = manager.setAt(id, category, player.getLocation());
            player.sendMessage(Messages.prefixed(ok ? "§aLeaderboard §f" + id + " §aset at your location." : "§cFailed to set leaderboard."));
            return true;
        }

        // Alternate input style: /leaderboard <category> set [id]
        LeaderboardManager.Category category = LeaderboardManager.Category.fromInput(args[0]);
        if (category != null && args.length >= 2 && "set".equalsIgnoreCase(args[1])) {
            String id = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : category.name().toLowerCase(Locale.ROOT);
            boolean ok = manager.setAt(id, category, player.getLocation());
            player.sendMessage(ok ? "§aLeaderboard §f" + id + " §aset at your location." : "§cFailed to set leaderboard.");
            return true;
        }

        player.sendMessage(Messages.prefixed("§cUnknown subcommand."));
        return true;
    }
}
