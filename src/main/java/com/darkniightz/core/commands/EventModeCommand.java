package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.system.EventModeManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class EventModeCommand implements CommandExecutor, TabCompleter {
    private final EventModeManager eventModeManager;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;

    public EventModeCommand(Plugin plugin, EventModeManager eventModeManager, ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
        this.eventModeManager = eventModeManager;
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);

        if ("join".equals(sub)) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Messages.prefixed("§cOnly players can join event queues."));
                return true;
            }
            boolean confirmed = args.length >= 2 && "confirm".equalsIgnoreCase(args[1]);
            var result = eventModeManager.joinQueue(player, confirmed);
            sender.sendMessage(Messages.prefixed(result.message()));
            return true;
        }

        if ("leave".equals(sub)) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Messages.prefixed("§cOnly players can leave event queues."));
                return true;
            }
            var result = eventModeManager.leaveQueue(player);
            sender.sendMessage(Messages.prefixed(result.message()));
            return true;
        }

        Player player = sender instanceof Player p ? p : null;
        if (player != null) {
            var profile = profiles.getOrCreate(player, ranks.getDefaultGroup());
            boolean bypass = devMode != null && devMode.isActive(player.getUniqueId());
            if (!bypass && !ranks.isAtLeast(profile.getPrimaryRank(), "srmod")) {
                sender.sendMessage(Messages.noPerm());
                return true;
            }
            // setup and rebuildworld are admin-only
            if (!bypass && !ranks.isAtLeast(profile.getPrimaryRank(), "admin")
                    && (sub.equals("setup") || sub.equals("rebuildworld") || sub.equals("resetworld") || sub.equals("wipeworld"))) {
                sender.sendMessage(Messages.noPerm());
                return true;
            }
        }

        switch (sub) {
            case "tp", "teleport", "world", "edit" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Messages.prefixed("§cOnly players can teleport to the event world."));
                    return true;
                }
                var spawn = eventModeManager.getAdminEditSpawn();
                if (spawn == null || spawn.getWorld() == null) {
                    sender.sendMessage(Messages.prefixed("§cEvent world is unavailable right now."));
                    return true;
                }
                if (p.teleport(spawn)) {
                    eventModeManager.grantAdminEditAccess(p);
                    sender.sendMessage(Messages.prefixed("§aTeleported to the event world. Edit access is enabled for this admin visit."));
                } else {
                    sender.sendMessage(Messages.prefixed("§cCould not teleport you to the event world."));
                }
                return true;
            }
            case "status" -> {
                sender.sendMessage(Messages.prefixed(eventModeManager.getStatusLine()));
                return true;
            }
            case "rebuildworld", "resetworld", "wipeworld" -> {
                boolean confirmed = args.length >= 2 && "confirm".equalsIgnoreCase(args[1]);
                var result = eventModeManager.rebuildEventWorld(confirmed);
                sender.sendMessage(Messages.prefixed(result.message()));
                return true;
            }
            case "list" -> {
                sender.sendMessage(Messages.prefixed("§dConfigured events: §f" + String.join(", ", eventModeManager.getConfiguredEventDisplayNames())));
                return true;
            }
            case "stop", "off", "disable" -> {
                var result = eventModeManager.stopEvent("stopped by " + sender.getName());
                sender.sendMessage(Messages.prefixed(result.message()));
                return true;
            }
            case "start", "on", "enable" -> {
                if (args.length < 2) {
                    sender.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " start <event>"));
                    return true;
                }
                var result = eventModeManager.startEvent(args[1]);
                sender.sendMessage(Messages.prefixed(result.message()));
                return true;
            }
            case "complete", "finish" -> {
                if (args.length < 2) {
                    sender.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " complete <winner> [reward]"));
                    return true;
                }
                Player winner = Bukkit.getPlayerExact(args[1]);
                if (winner == null) {
                    sender.sendMessage(Messages.prefixed("§cWinner must be online: §e" + args[1]));
                    return true;
                }
                Integer rewardOverride = null;
                if (args.length >= 3) {
                    try {
                        rewardOverride = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ex) {
                        sender.sendMessage(Messages.prefixed("§cReward must be a number."));
                        return true;
                    }
                }
                var result = eventModeManager.completeEvent(winner, rewardOverride, "completed by " + sender.getName());
                sender.sendMessage(Messages.prefixed(result.message()));
                return true;
            }
            case "setup" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Messages.prefixed("§cOnly players can run setup subcommands."));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " setup <koth|ffa|duels|hardcore> <...>"));
                    return true;
                }

                String type = args[1].toLowerCase(Locale.ROOT);
                String action = args[2].toLowerCase(Locale.ROOT);

                if ("koth".equals(type)) {
                    if (!"pos1".equals(action) && !"pos2".equals(action)) {
                        sender.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " setup koth <pos1|pos2>"));
                        return true;
                    }
                    var result = eventModeManager.setupKothPosition(p, "pos1".equals(action));
                    sender.sendMessage(Messages.prefixed(result.message()));
                    return true;
                }

                if ("ffa".equals(type) || "duels".equals(type) || "lms".equals(type) || "hardcore".equals(type)) {
                    if ("addspawn".equals(action)) {
                        var result = eventModeManager.setupArenaSpawn(p, type);
                        sender.sendMessage(Messages.prefixed(result.message()));
                        return true;
                    }
                    if ("clearspawns".equals(action)) {
                        var result = eventModeManager.clearArenaSpawns(type);
                        sender.sendMessage(Messages.prefixed(result.message()));
                        return true;
                    }
                    if ("listspawns".equals(action)) {
                        List<String> lines = eventModeManager.listArenaSpawns(type);
                        if (lines.isEmpty()) {
                            sender.sendMessage(Messages.prefixed("§7No spawns configured for §f" + type.toUpperCase(Locale.ROOT) + "§7."));
                        } else {
                            sender.sendMessage(Messages.prefixed("§d" + type.toUpperCase(Locale.ROOT) + " Spawns (§f" + lines.size() + "§d):"));
                            for (String line : lines) sender.sendMessage(Messages.prefixed(line));
                        }
                        return true;
                    }
                    if ("view".equals(action)) {
                        int seconds = 30;
                        if (args.length >= 4) {
                            try { seconds = Integer.parseInt(args[3]); } catch (NumberFormatException ignored) {}
                        }
                        var result = eventModeManager.viewArenaSpawns(p, type, seconds);
                        sender.sendMessage(Messages.prefixed(result.message()));
                        return true;
                    }
                    sender.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " setup " + type + " <addspawn|clearspawns|listspawns|view [seconds]>"));
                    return true;
                }

                sender.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " setup <koth|ffa|duels|hardcore> <...>"));
                return true;
            }
            default -> {
                sendHelp(sender, label);
                return true;
            }
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Messages.prefixed("§dEvent Control"));
        sender.sendMessage(Messages.prefixed("§f/" + label + " status"));
        sender.sendMessage(Messages.prefixed("§f/" + label + " list"));
        sender.sendMessage(Messages.prefixed("§f/" + label + " tp §8- admin travel to the event world"));
        sender.sendMessage(Messages.prefixed("§f/" + label + " rebuildworld confirm §8- wipe and recreate the event world as superflat"));
        sender.sendMessage(Messages.prefixed("§f/" + label + " start <event>"));
        sender.sendMessage(Messages.prefixed("§f/" + label + " stop"));
        sender.sendMessage(Messages.prefixed("§f/" + label + " complete <winner> [reward]"));
        sender.sendMessage(Messages.prefixed("§f/" + label + " setup koth <pos1|pos2>"));
        sender.sendMessage(Messages.prefixed("§f/" + label + " setup ffa <addspawn|clearspawns|listspawns|view [s]>"));
        sender.sendMessage(Messages.prefixed("§f/" + label + " setup duels <addspawn|clearspawns|listspawns|view [s]>"));
        sender.sendMessage(Messages.prefixed("§f/" + label + " setup hardcore <addspawn|clearspawns|listspawns|view [s]>"));
        sender.sendMessage(Messages.prefixed("§f/" + label + " join §8(or §f/" + label + " join confirm§8 for hardcore)"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        // Permission gate — must be an admin/dev/bypass player
        if (sender instanceof Player p) {
            var profile = profiles.getOrCreate(p, ranks.getDefaultGroup());
            boolean bypass = devMode != null && devMode.isActive(p.getUniqueId());
            if (!bypass && !ranks.isAtLeast(profile.getPrimaryRank(), "admin")) {
                return List.of();
            }
        }

        List<String> all = new ArrayList<>();

        if (args.length == 1) {
            all = Arrays.asList("status", "list", "tp", "start", "stop", "complete", "setup", "join", "leave", "rebuildworld");
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("start".equals(sub)) {
                all = new ArrayList<>(eventModeManager.getConfiguredEventKeys());
            } else if ("setup".equals(sub)) {
                all = Arrays.asList("ffa", "duels", "hardcore", "koth");
            } else if ("complete".equals(sub)) {
                all = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String arg1 = args[1].toLowerCase(Locale.ROOT);
            if ("setup".equals(sub)) {
                if ("koth".equals(arg1)) {
                    all = Arrays.asList("pos1", "pos2");
                } else if ("ffa".equals(arg1) || "duels".equals(arg1) || "hardcore".equals(arg1) || "lms".equals(arg1)) {
                    all = Arrays.asList("addspawn", "clearspawns", "listspawns", "view");
                }
            }
        }

        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return all.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                .collect(Collectors.toList());
    }
}
