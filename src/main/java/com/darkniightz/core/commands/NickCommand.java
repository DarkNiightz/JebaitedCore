package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.system.NicknameManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class NickCommand implements CommandExecutor {

    private final Plugin plugin;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;
    private final NicknameManager nicknames;

    public NickCommand(Plugin plugin, ProfileStore profiles, RankManager ranks, DevModeManager devMode, NicknameManager nicknames) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
        this.nicknames = nicknames;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player actor)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can use this command."));
            return true;
        }

        PlayerProfile actorProfile = profiles.getOrCreate(actor, ranks.getDefaultGroup());
        boolean bypass = devMode != null && devMode.isActive(actor.getUniqueId());

        if (args.length == 0) {
            String current = nicknames.getNickname(actor.getUniqueId());
            actor.sendMessage(Messages.prefixed("§7Current nickname: " + (current == null ? "§8none" : "§e" + current)));
            actor.sendMessage(Messages.prefixed("§7Usage: §e/" + label + " <name|off> §7or §e/" + label + " <player> <name|off>"));
            return true;
        }

        if (args.length == 1) {
            if (!bypass && !ranks.isAtLeast(actorProfile.getPrimaryRank(), plugin.getConfig().getString("nick.self_min_rank", "pleb"))) {
                actor.sendMessage(Messages.noPerm());
                return true;
            }
            return setNick(actor, actor, args[0]);
        }

        if (!bypass && !ranks.isAtLeast(actorProfile.getPrimaryRank(), plugin.getConfig().getString("nick.others_min_rank", "helper"))) {
            actor.sendMessage(Messages.noPerm());
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.isOnline() && !target.hasPlayedBefore()) {
            actor.sendMessage(Messages.prefixed("§cPlayer not found: §e" + args[0]));
            return true;
        }

        Player targetOnline = target.getPlayer();
        if (targetOnline == null) {
            actor.sendMessage(Messages.prefixed("§cThat player must be online to update display names."));
            return true;
        }

        return setNick(actor, targetOnline, args[1]);
    }

    private boolean setNick(Player actor, Player target, String value) {
        if (value.equalsIgnoreCase("off") || value.equalsIgnoreCase("reset") || value.equalsIgnoreCase("clear")) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                nicknames.clearNickname(target.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!actor.isOnline()) return;
                    target.setDisplayName(target.getName());
                    if (JebaitedCore.getInstance() != null) {
                        JebaitedCore.getInstance().refreshPlayerPresentation(target);
                        JebaitedCore.getInstance().refreshAllPlayerPresentations();
                    }
                    actor.sendMessage(Messages.prefixed("§aNickname cleared for §e" + target.getName()));
                    if (!target.getUniqueId().equals(actor.getUniqueId())) {
                        target.sendMessage(Messages.prefixed("§7Your nickname was reset."));
                    }
                });
            });
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean ok = nicknames.setNickname(target.getUniqueId(), value);
            if (!ok) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (actor.isOnline()) actor.sendMessage(Messages.prefixed("§cInvalid nickname. Use 1-16 chars [A-Za-z0-9_]."));
                });
                return;
            }
            String display = nicknames.displayName(target.getName(), target.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!actor.isOnline()) return;
                target.setDisplayName(display);
                if (JebaitedCore.getInstance() != null) {
                    JebaitedCore.getInstance().refreshPlayerPresentation(target);
                    JebaitedCore.getInstance().refreshAllPlayerPresentations();
                }
                actor.sendMessage(Messages.prefixed("§aNickname set for §e" + target.getName() + "§a: §f" + display));
                if (!target.getUniqueId().equals(actor.getUniqueId())) {
                    target.sendMessage(Messages.prefixed("§7Your nickname is now §f" + display));
                }
            });
        });
        return true;
    }
}
