package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Reloads the plugin's config and refreshes the in-memory cache from the database.
 * Restricted to developer and above; console is always allowed.
 */
public class ReloadCommand implements CommandExecutor {

    private final JebaitedCore plugin;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;

    public ReloadCommand(JebaitedCore plugin, ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Allow console
        if (sender instanceof Player p) {
            PlayerProfile actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
            String actorRank = actor == null ? null : actor.getPrimaryRank();
            boolean allowed = ranks.isAtLeast(actorRank, "developer");
            // DevMode can also allow, but requirement says developers+ only; still allow if active dev mode.
            if (!allowed && devMode != null && devMode.isActive(p.getUniqueId())) {
                allowed = true;
            }
            if (!allowed) {
                sender.sendMessage(Messages.prefixed("§cThis command is restricted to developers and above."));
                return true;
            }
        }

        long start = System.currentTimeMillis();
        try {
            plugin.reloadCore();
            long took = System.currentTimeMillis() - start;
            sender.sendMessage(Messages.prefixed("§aJebaitedCore reloaded. §7(" + took + "ms)"));
        } catch (Exception ex) {
            sender.sendMessage(Messages.prefixed("§cReload failed: §7" + ex.getMessage()));
            plugin.getLogger().severe("Reload failed");
            ex.printStackTrace();
        }
        return true;
    }
}
