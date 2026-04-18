package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.system.EventModeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class EventModeCommand implements CommandExecutor, TabCompleter {

    // KOTH-type events: cuboid hill marked with pos1 + pos2
    private static final List<String> KOTH_TYPES   = List.of("koth", "hardcore_koth");
    // Arena-type events: spawn points added one by one
    private static final List<String> ARENA_TYPES  = List.of("ffa", "duels", "hardcore_ffa", "hardcore_duels");
    // All setup-able types for tab-complete
    private static final List<String> SETUP_TYPES;
    static {
        List<String> all = new ArrayList<>(KOTH_TYPES);
        all.addAll(ARENA_TYPES);
        SETUP_TYPES = List.copyOf(all);
    }
    private static final List<String> KOTH_ACTIONS  = List.of("pos1", "pos2");
    private static final List<String> SPAWN_ACTIONS = List.of("addspawn", "clearspawns", "listspawns", "view");
    /** Tab completions for {@code /event setup koth|hardcore_koth …}. */
    private static final List<String> KOTH_SETUP_ACTIONS;
    static {
        List<String> k = new ArrayList<>(KOTH_ACTIONS);
        k.addAll(SPAWN_ACTIONS);
        KOTH_SETUP_ACTIONS = List.copyOf(k);
    }

    private final EventModeManager eventModeManager;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;

    public EventModeCommand(Plugin plugin, EventModeManager eventModeManager,
                            ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
        this.eventModeManager = eventModeManager;
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
    }

    //  Rank helpers 

    private boolean isAdmin(Player p) {
        if (devMode != null && devMode.isActive(p.getUniqueId())) return true;
        var profile = profiles.getOrCreate(p, ranks.getDefaultGroup());
        return profile != null && ranks.isAtLeast(profile.getPrimaryRank(), "admin");
    }

    private boolean isSrmod(Player p) {
        if (devMode != null && devMode.isActive(p.getUniqueId())) return true;
        var profile = profiles.getOrCreate(p, ranks.getDefaultGroup());
        return profile != null && ranks.isAtLeast(profile.getPrimaryRank(), "srmod");
    }

    private boolean isHelper(Player p) {
        if (devMode != null && devMode.isActive(p.getUniqueId())) return true;
        var profile = profiles.getOrCreate(p, ranks.getDefaultGroup());
        return profile != null && ranks.isAtLeast(profile.getPrimaryRank(), "helper");
    }

    //  Command 

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);

        //  Public player subcommands 

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

        if ("status".equals(sub)) {
            sender.sendMessage(Messages.prefixed(eventModeManager.getStatusLine()));
            return true;
        }

        if ("info".equals(sub)) {
            sender.sendMessage(Messages.prefixed(eventModeManager.getEventInfoSummary()));
            return true;
        }

        if ("spectate".equals(sub)) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Messages.prefixed("§cOnly players can spectate."));
                return true;
            }
            if (!isHelper(player)) {
                sender.sendMessage(Messages.noPerm());
                return true;
            }
            String spectSub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "enter";
            if ("leave".equals(spectSub) || "quit".equals(spectSub) || "exit".equals(spectSub)) {
                var r = eventModeManager.staffSpectateLeave(player);
                sender.sendMessage(Messages.prefixed(r.message()));
            } else {
                var r = eventModeManager.staffSpectateEnter(player);
                sender.sendMessage(Messages.prefixed(r.message()));
            }
            return true;
        }

        //  Staff gate: srmod+ 
        if (sender instanceof Player p && !isSrmod(p)) {
            sender.sendMessage(Messages.noPerm());
            return true;
        }

        //  Admin-only gate 
        boolean adminOnly = sub.equals("setup") || sub.equals("rebuildworld")
                || sub.equals("resetworld") || sub.equals("wipeworld")
                || sub.equals("tp") || sub.equals("teleport")
                || sub.equals("world") || sub.equals("edit")
                || sub.equals("setreward") || sub.equals("arenas");
        if (adminOnly && sender instanceof Player p && !isAdmin(p)) {
            sender.sendMessage(Messages.noPerm());
            return true;
        }

        switch (sub) {

            //  Info 

            case "list" -> {
                List<String> keys  = eventModeManager.getConfiguredEventKeys();
                List<String> names = eventModeManager.getConfiguredEventDisplayNames();
                if (keys.isEmpty()) {
                    sender.sendMessage(Messages.prefixed("§7No events are configured."));
                    return true;
                }
                sender.sendMessage(Component.empty()
                    .append(Component.text(Messages.prefix()))
                    .append(Component.text("Configured Events ").color(NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text("(" + keys.size() + ")").color(NamedTextColor.DARK_GRAY)));
                for (int i = 0; i < keys.size(); i++) {
                    String key  = keys.get(i);
                    String name = i < names.size() ? names.get(i) : key;
                    sender.sendMessage(Component.empty()
                        .append(Component.text("  §8§l" + name + " §8[" + key + "]")
                            .clickEvent(ClickEvent.suggestCommand("/" + label + " start " + key))
                            .hoverEvent(HoverEvent.showText(
                                Component.text("Click to suggest /" + label + " start " + key)
                                    .color(NamedTextColor.GRAY)))));
                }
                return true;
            }

            //  Lifecycle 

            case "start", "on", "enable" -> {
                if (args.length < 2) {
                    sendStartUsage(sender, label);
                    return true;
                }
                var result = args.length >= 3
                        ? eventModeManager.startEvent(args[1], args[2])
                        : eventModeManager.startEvent(args[1]);
                sender.sendMessage(Messages.prefixed(result.message()));
                return true;
            }

            case "setreward" -> {
                if (args.length < 2) {
                    sender.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " setreward <coins>"));
                    return true;
                }
                try {
                    int coins = Integer.parseInt(args[1]);
                    var result = eventModeManager.setRuntimeCoinReward(coins);
                    sender.sendMessage(Messages.prefixed(result.message()));
                } catch (NumberFormatException ex) {
                    sender.sendMessage(Messages.prefixed("§cCoins must be a number."));
                }
                return true;
            }

            case "arenas" -> {
                List<String> lines = eventModeManager.listArenaRegistryLines();
                if (lines.isEmpty()) {
                    sender.sendMessage(Messages.prefixed("§7No arenas in §fevent_mode.arena_registry§7."));
                } else {
                    sender.sendMessage(Messages.prefixed("§dArena registry §8(" + lines.size() + ")"));
                    for (String line : lines) {
                        sender.sendMessage(Messages.prefixed(line));
                    }
                }
                return true;
            }

            case "stop", "off", "disable" -> {
                var result = eventModeManager.stopEvent("stopped by " + sender.getName());
                sender.sendMessage(Messages.prefixed(result.message()));
                return true;
            }

            case "forcestart", "force" -> {
                var result = eventModeManager.forceStart();
                sender.sendMessage(Messages.prefixed(result.message()));
                return true;
            }

            case "complete", "finish" -> {
                if (args.length < 2) {
                    sender.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " complete <winner> [coins]"));
                    return true;
                }
                Player winner = Bukkit.getPlayerExact(args[1]);
                if (winner == null) {
                    sender.sendMessage(Messages.prefixed("§cWinner must be online: §e" + args[1]));
                    return true;
                }
                Integer rewardOverride = null;
                if (args.length >= 3) {
                    try { rewardOverride = Integer.parseInt(args[2]); }
                    catch (NumberFormatException ex) {
                        sender.sendMessage(Messages.prefixed("§cReward must be a number."));
                        return true;
                    }
                }
                var result = eventModeManager.completeEvent(winner, rewardOverride, "completed by " + sender.getName());
                sender.sendMessage(Messages.prefixed(result.message()));
                return true;
            }

            //  Admin: world travel 

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
                    sender.sendMessage(Messages.prefixed("§aTeleported to event world. Build access granted."));
                } else {
                    sender.sendMessage(Messages.prefixed("§cCould not teleport to the event world."));
                }
                return true;
            }

            case "rebuildworld", "resetworld", "wipeworld" -> {
                boolean confirmed = args.length >= 2 && "confirm".equalsIgnoreCase(args[1]);
                var result = eventModeManager.rebuildEventWorld(confirmed);
                sender.sendMessage(Messages.prefixed(result.message()));
                return true;
            }

            //  Admin: arena setup 

            case "setup" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Messages.prefixed("§cOnly players can run setup commands."));
                    return true;
                }
                if (args.length < 2) {
                    sendSetupHelp(p, label);
                    return true;
                }

                String type = args[1].toLowerCase(Locale.ROOT);
                // "hardcore" is an alias for hardcore_ffa  engine normalises it too
                if ("hardcore".equals(type)) type = "hardcore_ffa";

                if (KOTH_TYPES.contains(type)) {
                    if (args.length < 3) {
                        sender.sendMessage(Messages.prefixed(
                                "§eUsage: §f/" + label + " setup " + type
                                        + " <pos1|pos2|addspawn|clearspawns|listspawns|view [s]>"));
                        return true;
                    }
                    String action = args[2].toLowerCase(Locale.ROOT);
                    if ("pos1".equals(action) || "pos2".equals(action)) {
                        var result = eventModeManager.setupKothPosition(p, "pos1".equals(action));
                        sender.sendMessage(Messages.prefixed(result.message()));
                        return true;
                    }
                    if (SPAWN_ACTIONS.contains(action)) {
                        return handleArenaSetup(p, sender, label, type, action, args);
                    }
                    sender.sendMessage(Messages.prefixed(
                            "§cUnknown action. Use §fpos1§c/§fpos2§c for the hill box, or §faddspawn§c/§fclearspawns§c/§flistspawns§c/§fview§c for player spawns."));
                    return true;
                }

                if (ARENA_TYPES.contains(type)) {
                    if (args.length < 3) {
                        sender.sendMessage(Messages.prefixed(
                            "§eUsage: §f/" + label + " setup " + type
                            + " <addspawn|clearspawns|listspawns|view [s]>"));
                        return true;
                    }
                    return handleArenaSetup(p, sender, label, type,
                                            args[2].toLowerCase(Locale.ROOT), args);
                }

                sender.sendMessage(Messages.prefixed("§cUnknown event type: §e" + type));
                sendSetupHelp(p, label);
                return true;
            }

            default -> {
                sendHelp(sender, label);
                return true;
            }
        }
    }

    //  Arena spawn helper 

    private boolean handleArenaSetup(Player p, CommandSender sender, String label,
                                     String type, String action, String[] args) {
        String displayName = type.toUpperCase(Locale.ROOT).replace('_', ' ');
        switch (action) {
            case "addspawn" -> {
                var result = eventModeManager.setupArenaSpawn(p, type);
                sender.sendMessage(Messages.prefixed(result.message()));
            }
            case "clearspawns" -> {
                var result = eventModeManager.clearArenaSpawns(type);
                sender.sendMessage(Messages.prefixed(result.message()));
            }
            case "listspawns" -> {
                List<String> lines = eventModeManager.listArenaSpawns(type);
                if (lines.isEmpty()) {
                    sender.sendMessage(Messages.prefixed(
                        "§7No spawns configured for §f" + displayName + "§7."));
                } else {
                    sender.sendMessage(Messages.prefixed(
                        "§d" + displayName + " Spawns §8(§f" + lines.size() + "§8):"));
                    for (String line : lines) sender.sendMessage(Messages.prefixed(line));
                }
            }
            case "view" -> {
                int seconds = 30;
                if (args.length >= 4) {
                    try { seconds = Integer.parseInt(args[3]); }
                    catch (NumberFormatException ignored) {}
                }
                var result = eventModeManager.viewArenaSpawns(p, type, seconds);
                sender.sendMessage(Messages.prefixed(result.message()));
            }
            default -> sender.sendMessage(Messages.prefixed(
                "§eUsage: §f/" + label + " setup " + type
                + " <addspawn|clearspawns|listspawns|view [s]>"));
        }
        return true;
    }

    //  Help panels 

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(helpHeader("Event Control  /" + label));
        sender.sendMessage(helpSection("Info"));
        sender.sendMessage(helpEntry(label, "status",               "",               "Current event state"));
        sender.sendMessage(helpEntry(label, "info",                 "",               "Kind, arena, queue, participants"));
        sender.sendMessage(helpEntry(label, "spectate",             "[leave]",        "Helper+ visit running event as spectator"));
        sender.sendMessage(helpEntry(label, "list",                 "",               "All configured events (clickable)"));
        sender.sendMessage(helpSection("Queue"));
        sender.sendMessage(helpEntry(label, "join",                 "",               "Join the event queue"));
        sender.sendMessage(helpEntry(label, "join confirm",         "",               "Confirm HC join  items at risk"));
        sender.sendMessage(helpEntry(label, "leave",                "",               "Leave the event queue"));
        sender.sendMessage(helpSection("Lifecycle  (srmod+)"));
        sender.sendMessage(helpEntry(label, "start",                "<event> [arena]", "Open lobby (optional arena_registry key)"));
        sender.sendMessage(helpEntry(label, "stop",                 "",               "Stop the active event"));
        sender.sendMessage(helpEntry(label, "forcestart",           "",               "Skip lobby countdown"));
        sender.sendMessage(helpEntry(label, "complete",             "<name> [coins]", "Declare winner + optional coin override"));
        sender.sendMessage(helpEntry(label, "setreward",            "<coins>",        "Override coin reward (running event)"));
        sender.sendMessage(helpEntry(label, "arenas",               "",               "List YAML arena_registry entries"));
        sender.sendMessage(helpSection("Setup  (admin)"));
        sender.sendMessage(helpEntry(label, "setup",                "<type> ...",     "Arena/hill config  see /" + label + " setup"));
        sender.sendMessage(helpEntry(label, "tp",                   "",               "Teleport to event world"));
        sender.sendMessage(helpEntry(label, "rebuildworld confirm", "",               "Wipe + recreate event world as superflat"));
    }

    private void sendSetupHelp(CommandSender sender, String label) {
        sender.sendMessage(helpHeader("Event Setup  /" + label + " setup"));
        sender.sendMessage(helpSection("KOTH  hill cuboid (pos1/pos2) + ring spawns (addspawn…)"));
        sender.sendMessage(helpEntry(label, "setup koth",           "<pos1|pos2|spawns…>", "Hill corners and/or player spawn ring"));
        sender.sendMessage(helpEntry(label, "setup hardcore_koth",  "<pos1|pos2|spawns…>", "HC KOTH hill + spawns"));
        sender.sendMessage(helpSection("Arena  add/manage individual spawn points"));
        sender.sendMessage(helpEntry(label, "setup ffa",            "<addspawn|clearspawns|listspawns|view>", "FFA spawn points"));
        sender.sendMessage(helpEntry(label, "setup duels",          "<addspawn|clearspawns|listspawns|view>", "Duels spawn points"));
        sender.sendMessage(helpEntry(label, "setup hardcore_ffa",   "<addspawn|clearspawns|listspawns|view>", "Hardcore FFA spawns  items at risk"));
        sender.sendMessage(helpEntry(label, "setup hardcore_duels", "<addspawn|clearspawns|listspawns|view>", "Hardcore Duels spawns  items at risk"));
    }

    private void sendStartUsage(CommandSender sender, String label) {
        List<String> keys = eventModeManager.getConfiguredEventKeys();
        sender.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " start <event>"));
        if (!keys.isEmpty()) {
            sender.sendMessage(Messages.prefixed("§7Available: §f" + String.join(" §8| §f", keys)));
        }
    }

    //  Help component builders 

    private static Component helpHeader(String text) {
        return Component.text("§8§m                    ")
            .append(Component.text(" §d§l" + text + " ").color(NamedTextColor.LIGHT_PURPLE))
            .append(Component.text("§8§m                    "));
    }

    private static Component helpSection(String name) {
        return Component.text("  §e" + name);
    }

    private static Component helpEntry(String label, String sub, String argHint, String description) {
        return Component.empty()
            .append(Component.text("    §8 "))
            .append(Component.text("/" + label + " " + sub).color(NamedTextColor.WHITE)
                .clickEvent(ClickEvent.suggestCommand("/" + label + " " + sub))
                .hoverEvent(HoverEvent.showText(
                    Component.text(description).color(NamedTextColor.GRAY))))
            .append(argHint.isEmpty()
                ? Component.empty()
                : Component.text(" " + argHint).color(NamedTextColor.YELLOW))
            .append(Component.text("  §8 §7" + description));
    }

    //  Tab completer 

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        String typing = args.length > 0 ? args[args.length - 1].toLowerCase(Locale.ROOT) : "";

        if (args.length == 1) {
            List<String> opts = new ArrayList<>(List.of("join", "leave", "status", "info"));
            if (sender instanceof Player p) {
                if (isHelper(p)) {
                    opts.add("spectate");
                }
            }
            if (!(sender instanceof Player p) || isSrmod(p)) {
                opts.addAll(List.of("list", "start", "stop", "forcestart",
                                    "complete", "setup", "tp", "rebuildworld",
                                    "setreward", "arenas"));
            }
            return StringUtil.copyPartialMatches(typing, opts, new ArrayList<>());
        }

        if (args.length == 2 && "spectate".equalsIgnoreCase(args[0])
                && sender instanceof Player ph && isHelper(ph) && !isSrmod(ph)) {
            return StringUtil.copyPartialMatches(typing, List.of("leave", "quit", "exit"), new ArrayList<>());
        }

        // All deeper completions require srmod+
        if (sender instanceof Player p && !isSrmod(p)) return List.of();

        String sub  = args[0].toLowerCase(Locale.ROOT);
        String arg1 = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        String arg2 = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "";

        if (args.length == 2) {
            return switch (sub) {
                case "start", "on", "enable" ->
                    StringUtil.copyPartialMatches(typing,
                        eventModeManager.getConfiguredEventKeys(), new ArrayList<>());
                case "setup" ->
                    StringUtil.copyPartialMatches(typing, SETUP_TYPES, new ArrayList<>());
                case "complete", "finish" ->
                    StringUtil.copyPartialMatches(typing,
                        Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName).collect(Collectors.toList()),
                        new ArrayList<>());
                case "rebuildworld", "resetworld", "wipeworld" ->
                    StringUtil.copyPartialMatches(typing, List.of("confirm"), new ArrayList<>());
                case "join" ->
                    StringUtil.copyPartialMatches(typing, List.of("confirm"), new ArrayList<>());
                default -> List.of();
            };
        }

        if (args.length == 3 && "setup".equals(sub)) {
            String type = "hardcore".equals(arg1) ? "hardcore_ffa" : arg1;
            if (KOTH_TYPES.contains(type))
                return StringUtil.copyPartialMatches(typing, KOTH_SETUP_ACTIONS, new ArrayList<>());
            if (ARENA_TYPES.contains(type))
                return StringUtil.copyPartialMatches(typing, SPAWN_ACTIONS, new ArrayList<>());
        }

        if (args.length == 4 && "setup".equals(sub)
                && (ARENA_TYPES.contains(arg1) || KOTH_TYPES.contains(arg1)) && "view".equals(arg2)) {
            return StringUtil.copyPartialMatches(typing,
                List.of("30", "60", "120"), new ArrayList<>());
        }

        if (args.length == 3 && ("start".equals(sub) || "on".equals(sub) || "enable".equals(sub))) {
            return StringUtil.copyPartialMatches(typing,
                    eventModeManager.listArenaKeysForKind(arg1), new ArrayList<>());
        }

        return List.of();
    }
}