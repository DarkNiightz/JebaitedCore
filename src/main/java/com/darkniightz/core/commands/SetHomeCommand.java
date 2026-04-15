package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.system.HomesManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class SetHomeCommand implements CommandExecutor {

    private final Plugin plugin;
    private final HomesManager homes;
    private final ProfileStore profiles;
    private final RankManager ranks;

    public SetHomeCommand(Plugin plugin, HomesManager homes, ProfileStore profiles, RankManager ranks) {
        this.plugin = plugin;
        this.homes = homes;
        this.profiles = profiles;
        this.ranks = ranks;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can use this command."));
            return true;
        }

        String homeName = args.length >= 1 ? args[0] : "home";
        Location loc = player.getLocation(); // capture on main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerProfile profile = profiles.getOrCreate(player, ranks.getDefaultGroup());
            int primaryLimit = homes.getHomeLimit(profile.getPrimaryRank());
            int donorLimit = profile.getDonorRank() != null ? homes.getHomeLimit(profile.getDonorRank()) : 0;
            int limit = Math.max(primaryLimit, donorLimit);
            HomesManager.SetHomeResult result = homes.setHome(player.getUniqueId(), homeName, loc, limit);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                switch (result) {
                    case CREATED -> player.sendMessage(Messages.prefixed("§aHome set: §e" + homeName.toLowerCase()));
                    case UPDATED -> player.sendMessage(Messages.prefixed("§aHome updated: §e" + homeName.toLowerCase()));
                    case LIMIT_REACHED -> player.sendMessage(Messages.prefixed("§cHome limit reached. §7(" + limit + ")"));
                    case INVALID -> player.sendMessage(Messages.prefixed("§cInvalid home name. Use 1-16 chars [a-z0-9_-]."));
                }
            });
        });
        return true;
    }
}
