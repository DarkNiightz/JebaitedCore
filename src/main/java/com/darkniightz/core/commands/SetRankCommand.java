package com.darkniightz.core.commands;

import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.UUID;

/**
 * Alias command for setting ranks: /setrank <player> <group>
 */
public class SetRankCommand implements CommandExecutor {

    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;

    public SetRankCommand(ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly in-game staff can set ranks in this MVP.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§eUsage: §7/" + label + " <player> <group>");
            return true;
        }
        String targetName = args[0];
        String newGroup = args[1].toLowerCase(Locale.ROOT);

        if (!ranks.getLadder().contains(newGroup)) {
            sender.sendMessage("§cUnknown group: §e" + newGroup);
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        UUID tuid = target.getUniqueId();
        if (tuid == null) {
            sender.sendMessage("§cPlayer not found: §e" + targetName);
            return true;
        }

        PlayerProfile actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
        PlayerProfile tp = profiles.getOrCreate(target, ranks.getDefaultGroup());

        String actorRank = actor.getPrimaryRank();
        String targetRank = tp.getPrimaryRank();

        boolean bypass = devMode != null && devMode.isActive(p.getUniqueId());
        boolean actorIsDevOrOwner = equalsAny(actorRank, "owner", "developer");
        boolean allowed = bypass || actorIsDevOrOwner || (ranks.outranksStrict(actorRank, targetRank) && ranks.outranksStrict(actorRank, newGroup));
        if (!allowed) {
            sender.sendMessage("§cYou must outrank the target and the destination group.");
            return true;
        }

        tp.setPrimaryRank(newGroup);
        profiles.save(tuid);
        sender.sendMessage("§aSet §e" + (target.getName() != null ? target.getName() : target.getUniqueId()) + "§a to group §b" + newGroup + "§a.");
        if (target.isOnline()) {
            target.getPlayer().sendMessage("§aYour rank has been set to §b" + newGroup + "§a.");
        }
        return true;
    }

    private boolean equalsAny(String s, String... opts) {
        if (s == null) return false;
        for (String o : opts) if (s.equalsIgnoreCase(o)) return true;
        return false;
    }
}
