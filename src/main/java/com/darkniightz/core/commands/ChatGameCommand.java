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

public class ChatGameCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final EventModeManager eventModeManager;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;

    public ChatGameCommand(Plugin plugin, EventModeManager eventModeManager,
                           ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
        this.plugin = plugin;
        this.eventModeManager = eventModeManager;
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
    }

    private boolean isSrmod(Player p) {
        if (devMode != null && devMode.isActive(p.getUniqueId())) {
            return true;
        }
        var profile = profiles.getOrCreate(p, ranks.getDefaultGroup());
        return profile != null && ranks.isAtLeast(profile.getPrimaryRank(), "srmod");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);

        if ("status".equals(sub)) {
            sender.sendMessage(Messages.prefixed(eventModeManager.getChatGameStatusLine()));
            return true;
        }

        if ("list".equals(sub)) {
            if (sender instanceof Player p && !isSrmod(p)) {
                sender.sendMessage(Messages.noPerm());
                return true;
            }
            List<String> keys = eventModeManager.getConfiguredChatGameKeys();
            List<String> names = eventModeManager.getConfiguredChatGameDisplayNames();
            if (keys.isEmpty()) {
                sender.sendMessage(Messages.prefixed("§7No chat games in §fchat_games.games§7."));
                return true;
            }
            sender.sendMessage(Component.empty()
                    .append(Component.text(Messages.prefix()))
                    .append(Component.text("Chat games ").color(NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text("(" + keys.size() + ")").color(NamedTextColor.DARK_GRAY)));
            for (int i = 0; i < keys.size(); i++) {
                String key = keys.get(i);
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

        if (sender instanceof Player p && !isSrmod(p)) {
            sender.sendMessage(Messages.noPerm());
            return true;
        }

        return switch (sub) {
            case "start", "on", "enable" -> {
                if (args.length < 2) {
                    sender.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " start <chat_math|chat_scrabble|chat_quiz>"));
                    yield true;
                }
                var r = eventModeManager.startChatGame(args[1]);
                sender.sendMessage(Messages.prefixed(r.message()));
                yield true;
            }
            case "stop", "off", "cancel" -> {
                String reason = args.length >= 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "staff";
                var r = eventModeManager.stopChatGame(reason);
                sender.sendMessage(Messages.prefixed(r.message()));
                yield true;
            }
            default -> {
                sendHelp(sender, label);
                yield true;
            }
        };
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Messages.prefixed("§6Chat games §8— §7parallel to §f/event §7(combat)"));
        sender.sendMessage(Messages.prefixed("§e/" + label + " status §8- §7current chat round"));
        sender.sendMessage(Messages.prefixed("§e/" + label + " list §8- §7srmod+ configured keys"));
        sender.sendMessage(Messages.prefixed("§e/" + label + " start <key> §8- §7srmod+"));
        sender.sendMessage(Messages.prefixed("§e/" + label + " stop [reason] §8- §7srmod+"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        String typing = args.length > 0 ? args[args.length - 1].toLowerCase(Locale.ROOT) : "";
        if (args.length == 1) {
            List<String> opts = new ArrayList<>(List.of("status"));
            if (!(sender instanceof Player p) || isSrmod(p)) {
                opts.addAll(List.of("list", "start", "stop"));
            }
            return StringUtil.copyPartialMatches(typing, opts, new ArrayList<>());
        }
        if (sender instanceof Player p && !isSrmod(p)) {
            return List.of();
        }
        if (args.length == 2 && ("start".equalsIgnoreCase(args[0]) || "on".equalsIgnoreCase(args[0])
                || "enable".equalsIgnoreCase(args[0]))) {
            return StringUtil.copyPartialMatches(typing, eventModeManager.getConfiguredChatGameKeys(), new ArrayList<>());
        }
        return List.of();
    }
}
