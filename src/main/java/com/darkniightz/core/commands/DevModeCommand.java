package com.darkniightz.core.commands;

import com.darkniightz.core.dev.DevModeManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.UUID;

public class DevModeCommand implements CommandExecutor {

    private final DevModeManager devMode;

    public DevModeCommand(DevModeManager devMode) {
        this.devMode = devMode;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player p) {
            UUID uuid = p.getUniqueId();
            if (!devMode.isAllowed(uuid)) {
                sender.sendMessage("§cYou are not allowed to use dev mode.");
                return true;
            }
            if (args.length == 0) {
                // Show current status immediately, then show usage (no need for explicit status subcommand)
                boolean active = devMode.isActive(uuid);
                sender.sendMessage("§aDeveloper mode: " + (active ? "§aENABLED" : "§cDISABLED"));
                sender.sendMessage("§eUsage: §7/" + label + " <on|off|toggle>");
                return true;
            }
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "on" -> {
                    devMode.setActive(uuid, true);
                    sender.sendMessage("§aDeveloper mode §2ENABLED§a for you.");
                }
                case "off" -> {
                    devMode.setActive(uuid, false);
                    sender.sendMessage("§aDeveloper mode §cDISABLED§a for you.");
                }
                case "toggle" -> {
                    devMode.toggle(uuid);
                    sender.sendMessage("§aDeveloper mode is now: §e" + (devMode.isActive(uuid) ? "ENABLED" : "DISABLED"));
                }
                case "status" -> { // kept for backwards-compatibility
                    sender.sendMessage("§aDeveloper mode: §e" + (devMode.isActive(uuid) ? "ENABLED" : "DISABLED"));
                }
                default -> sender.sendMessage("§eUsage: §7/" + label + " <on|off|toggle>");
            }
            return true;
        } else {
            // Console: allow manage for specific online player by name
            if (args.length < 2) {
                sender.sendMessage("§eConsole usage: §7/" + label + " <on|off|toggle|status> <player>");
                return true;
            }
            String sub = args[0].toLowerCase(Locale.ROOT);
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage("§cPlayer not found: §e" + args[1]); return true; }
            UUID uuid = target.getUniqueId();
            switch (sub) {
                case "on" -> { devMode.setActive(uuid, true); sender.sendMessage("§aEnabled dev mode for §e" + target.getName()); }
                case "off" -> { devMode.setActive(uuid, false); sender.sendMessage("§aDisabled dev mode for §e" + target.getName()); }
                case "toggle" -> { devMode.toggle(uuid); sender.sendMessage("§aToggled dev mode for §e" + target.getName()); }
                case "status" -> { sender.sendMessage("§e" + target.getName() + "§7 dev mode: §e" + (devMode.isActive(uuid) ? "ENABLED" : "DISABLED")); }
                default -> sender.sendMessage("§eConsole usage: §7/" + label + " <on|off|toggle|status> <player>");
            }
            return true;
        }
    }
}
