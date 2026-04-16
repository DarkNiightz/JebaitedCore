package com.darkniightz.core.commands;

import com.darkniightz.core.achievements.AchievementManager;
import com.darkniightz.core.achievements.AchievementsMenu;
import com.darkniightz.core.permissions.PermissionConstants;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /achievements [player]
 * No args: own profile. helper+ can view other players.
 */
public final class AchievementsCommand implements CommandExecutor, TabCompleter {

    private final JebaitedCore       plugin;
    private final AchievementManager achievements;
    private final ProfileStore       profiles;
    private final RankManager        ranks;

    public AchievementsCommand(
        JebaitedCore plugin,
        AchievementManager achievements,
        ProfileStore profiles,
        RankManager ranks
    ) {
        this.plugin       = plugin;
        this.achievements = achievements;
        this.profiles     = profiles;
        this.ranks        = ranks;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }

        // No args: open own achievements — available to everyone
        if (args.length == 0) {
            loadAndOpen(viewer, viewer);
            return true;
        }

        // With a player arg: helper+ only
        com.darkniightz.core.players.PlayerProfile viewerProfile = profiles.get(viewer.getUniqueId());
        if (viewerProfile == null || !ranks.isAtLeast(viewerProfile.getPrimaryRank(), "helper")) {
            viewer.sendMessage(com.darkniightz.core.Messages.noPerm());
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            viewer.sendMessage("§cPlayer not found: §f" + args[0]);
            return true;
        }

        loadAndOpen(viewer, target);
        return true;
    }

    private void loadAndOpen(Player viewer, Player target) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            achievements.loadPlayer(target.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () ->
                new AchievementsMenu(plugin, achievements, target.getUniqueId(), target.getName())
                    .open(viewer)
            );
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player viewer)) return List.of();
        if (!viewer.hasPermission(PermissionConstants.CMD_ACHIEVEMENTS)) return List.of();
        // Player-name completions only for helper+
        com.darkniightz.core.players.PlayerProfile vp = profiles.get(viewer.getUniqueId());
        if (vp == null || !ranks.isAtLeast(vp.getPrimaryRank(), "helper")) return List.of();

        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            String partial = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(partial)) names.add(p.getName());
            }
            return names;
        }
        return List.of();
    }
}
