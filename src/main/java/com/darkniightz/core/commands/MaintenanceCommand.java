package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.system.MaintenanceManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public class MaintenanceCommand implements CommandExecutor {

    private final MaintenanceManager maintenanceManager;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;

    public MaintenanceCommand(MaintenanceManager maintenanceManager, ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
        this.maintenanceManager = maintenanceManager;
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

        boolean plain = !(sender instanceof Player);
        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            return sendStatus(sender, plain);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "on", "enable" -> {
                maintenanceManager.setEnabled(true, sender.getName());
                sender.sendMessage(plain ? "ON" : Messages.prefixed("§cMaintenance mode is now §fON§c."));
                return true;
            }
            case "off", "disable" -> {
                maintenanceManager.setEnabled(false, sender.getName());
                sender.sendMessage(plain ? "OFF" : Messages.prefixed("§aMaintenance mode is now §fOFF§a."));
                return true;
            }
            case "add", "allow" -> {
                if (args.length < 2) {
                    sender.sendMessage(plain ? "USAGE: /maintenance add <player>" : Messages.prefixed("§eUsage: §f/maintenance add <player>"));
                    return true;
                }
                maintenanceManager.addWhitelisted(args[1], sender.getName());
                sender.sendMessage(plain ? ("ADDED " + args[1]) : Messages.prefixed("§aAdded §f" + args[1] + " §ato the maintenance whitelist."));
                return true;
            }
            case "remove", "del", "delete" -> {
                if (args.length < 2) {
                    sender.sendMessage(plain ? "USAGE: /maintenance remove <player>" : Messages.prefixed("§eUsage: §f/maintenance remove <player>"));
                    return true;
                }
                maintenanceManager.removeWhitelisted(args[1]);
                sender.sendMessage(plain ? ("REMOVED " + args[1]) : Messages.prefixed("§eRemoved §f" + args[1] + " §efrom the maintenance whitelist."));
                return true;
            }
            case "list" -> {
                List<String> names = maintenanceManager.listWhitelisted();
                if (plain) {
                    sender.sendMessage(names.isEmpty() ? "EMPTY" : String.join(",", names));
                } else {
                    sender.sendMessage(Messages.prefixed(names.isEmpty()
                            ? "§7Maintenance whitelist is empty."
                            : "§fWhitelisted: §a" + String.join("§7, §a", names)));
                }
                return true;
            }
            default -> {
                sender.sendMessage(plain
                        ? "USAGE: /maintenance <on|off|status|add|allow|remove|list>"
                        : Messages.prefixed("§eUsage: §f/maintenance <on|off|status|add|allow|remove|list>"));
                return true;
            }
        }
    }

    private boolean sendStatus(CommandSender sender, boolean plain) {
        boolean enabled = maintenanceManager.isEnabled();
        sender.sendMessage(plain ? (enabled ? "ON" : "OFF")
                : Messages.prefixed("§7Maintenance mode: " + (enabled ? "§cON" : "§aOFF")));
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
        return ranks.isAtLeast(rank, "admin");
    }
}
