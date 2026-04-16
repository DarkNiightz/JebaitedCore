package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.gui.FriendsMenu;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.system.FriendManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class FriendCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS =
            List.of("add", "remove", "accept", "deny", "list", "pending", "gui", "info");

    private final Plugin plugin;
    private final FriendManager friendManager;
    private final ProfileStore profileStore;

    public FriendCommand(Plugin plugin, FriendManager friendManager, ProfileStore profileStore) {
        this.plugin = plugin;
        this.friendManager = friendManager;
        this.profileStore = profileStore;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("\u00a7cOnly players can use this command."));
            return true;
        }

        // No subcommand — /friends and /fl open GUI; /friend shows clickable chat panel
        // (future: if player has settings.prefer_chat_over_gui, always send chat panel)
        if (args.length == 0) {
            if (label.equalsIgnoreCase("friends") || label.equalsIgnoreCase("fl")) {
                new FriendsMenu(plugin, friendManager, profileStore).open(player);
            } else {
                FriendChatUI.sendMainMenu(player, friendManager);
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "add", "request" -> {
                if (args.length < 2) {
                    player.sendMessage(Messages.prefixed("\u00a7cUsage: \u00a7f/friend add <player>"));
                    return true;
                }
                OfflinePlayer target = resolve(player, args[1]);
                if (target == null) return true;
                friendManager.sendRequest(player, target);
            }
            case "remove", "unfriend", "delete" -> {
                if (args.length < 2) {
                    player.sendMessage(Messages.prefixed("\u00a7cUsage: \u00a7f/friend remove <player>"));
                    return true;
                }
                OfflinePlayer target = resolve(player, args[1]);
                if (target == null) return true;
                friendManager.removeFriend(player, target);
            }
            case "accept" -> {
                if (args.length < 2) {
                    // No name  show pending list so they can click
                    FriendChatUI.sendPendingList(player, friendManager);
                    return true;
                }
                OfflinePlayer target = resolve(player, args[1]);
                if (target == null) return true;
                friendManager.acceptRequest(player, target);
            }
            case "deny", "decline", "reject" -> {
                if (args.length < 2) {
                    FriendChatUI.sendPendingList(player, friendManager);
                    return true;
                }
                OfflinePlayer target = resolve(player, args[1]);
                if (target == null) return true;
                friendManager.denyRequest(player, target);
            }
            case "pending" -> FriendChatUI.sendPendingList(player, friendManager);
            case "list" -> FriendChatUI.sendFriendsList(player, friendManager, profileStore);
            case "gui", "menu", "open" -> new FriendsMenu(plugin, friendManager, profileStore).open(player);
            case "info" -> {
                if (args.length < 2) {
                    player.sendMessage(Messages.prefixed("\u00a7cUsage: \u00a7f/friend info <player>"));
                    return true;
                }
                OfflinePlayer target = resolve(player, args[1]);
                if (target == null) return true;
                if (!friendManager.getFriends(player.getUniqueId()).contains(target.getUniqueId())) {
                    player.sendMessage(Messages.prefixed("\u00a7f" + target.getName() + " \u00a7cis not in your friends list."));
                    return true;
                }
                // Open FriendInfoMenu  stats loaded async inside the menu
                new com.darkniightz.core.gui.FriendInfoMenu(
                        plugin, friendManager, profileStore,
                        player, target.getUniqueId(), target.getName(), null).open(player);
            }
            default -> player.sendMessage(Messages.prefixed(
                    "\u00a7cUnknown subcommand. Use: \u00a7f/friend add|remove|accept|deny|list|pending|gui|info"));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            switch (sub) {
                case "add", "request" -> Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
                case "remove", "unfriend", "delete", "info" -> {
                    if (sender instanceof Player p) {
                        for (var u : friendManager.getFriends(p.getUniqueId())) {
                            var op = Bukkit.getOfflinePlayer(u);
                            if (op.getName() != null) names.add(op.getName());
                        }
                    }
                }
                case "accept", "deny", "decline", "reject" -> {
                    if (sender instanceof Player p) {
                        for (var u : friendManager.getInboundRequests(p.getUniqueId())) {
                            var op = Bukkit.getOfflinePlayer(u);
                            if (op.getName() != null) names.add(op.getName());
                        }
                    }
                }
            }
            String partial = args[1].toLowerCase(Locale.ROOT);
            return names.stream().filter(n -> n.toLowerCase(Locale.ROOT).startsWith(partial)).collect(Collectors.toList());
        }
        return List.of();
    }

    @SuppressWarnings("deprecation")
    private @Nullable OfflinePlayer resolve(Player sender, String name) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        if (op.getUniqueId() == null || op.getName() == null) {
            sender.sendMessage(Messages.prefixed("\u00a7cPlayer not found: \u00a7e" + name));
            return null;
        }
        return op;
    }
}