package com.darkniightz.core.commands;

import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.system.FriendManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Rich clickable chat panel for the friends system.
 * All rendering is done via Adventure API (no legacy colour codes in Component chains).
 */
public final class FriendChatUI {

    private static final String BAR  = "\u00a78\u00a7m\u00a7r\u00a78\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500";
    private static final String TITLE = "\u00a78[ \u00a76\u00a7l\u2736 Friends \u00a78]";

    private FriendChatUI() {}

    //  Main menu 

    /**
     * Sends the main /friend panel  quick-action buttons + friend count.
     */
    public static void sendMainMenu(Player player, FriendManager friendManager) {
        UUID uuid = player.getUniqueId();
        Set<UUID> friends = friendManager.getFriends(uuid);
        Set<UUID> pending = friendManager.getInboundRequests(uuid);

        long online = friends.stream()
                .filter(u -> Bukkit.getPlayer(u) != null)
                .count();

        player.sendMessage(Component.empty());
        player.sendMessage(l(TITLE));
        player.sendMessage(l("\u00a77Friends: \u00a7a" + online + "\u00a77/\u00a7f" + friends.size() + " \u00a77online"));
        if (!pending.isEmpty()) {
            player.sendMessage(l("\u00a77Pending requests: \u00a7e" + pending.size()));
        }
        player.sendMessage(l(BAR));

        // Quick-action row
        Component row = l("\u00a7f ")
                .append(btn("\u00a7a[+ Add Friend]",
                        ClickEvent.suggestCommand("/friend add "),
                        "\u00a77Add a new friend\n\u00a78Suggests: \u00a7f/friend add <name>"))
                .append(l("  "))
                .append(btn("\u00a7e[\u2709 Pending" + (pending.isEmpty() ? "" : " \u00a76(" + pending.size() + ")\u00a7e") + "]",
                        ClickEvent.runCommand("/friend pending"),
                        "\u00a77View incoming friend requests"))
                .append(l("  "))
                .append(btn("\u00a7b[\u2261 List]",
                        ClickEvent.runCommand("/friend list"),
                        "\u00a77Show your friends list in chat"))
                .append(l("  "))
                .append(btn("\u00a7d[\u2756 GUI]",
                        ClickEvent.runCommand("/friend gui"),
                        "\u00a77Open the friends inventory menu"));

        player.sendMessage(row);
        player.sendMessage(l(BAR));
        player.sendMessage(Component.empty());
    }

    //  Friends list 

    /**
     * Sends an inline, rank-coloured friends list with per-entry action buttons.
     */
    public static void sendFriendsList(Player player, FriendManager friendManager, ProfileStore profiles) {
        Set<UUID> friends = friendManager.getFriends(player.getUniqueId());

        player.sendMessage(Component.empty());
        player.sendMessage(l(TITLE + " \u00a78\u2014 \u00a77List"));

        if (friends.isEmpty()) {
            player.sendMessage(l("\u00a77You don't have any friends yet."));
            player.sendMessage(l("\u00a78Use \u00a7a[+ Add Friend]\u00a78 to get started."));
            player.sendMessage(l(BAR));
            player.sendMessage(Component.empty());
            return;
        }

        // Sort: online first, then alphabetical
        List<UUID> sorted = new ArrayList<>(friends);
        sorted.sort((a, b) -> {
            boolean ao = Bukkit.getPlayer(a) != null;
            boolean bo = Bukkit.getPlayer(b) != null;
            if (ao != bo) return ao ? -1 : 1;
            String an = nameOf(a);
            String bn = nameOf(b);
            return an.compareToIgnoreCase(bn);
        });

        player.sendMessage(l(BAR));

        for (UUID fUuid : sorted) {
            String name   = nameOf(fUuid);
            boolean isOnline = Bukkit.getPlayer(fUuid) != null;
            String colored   = friendManager.coloredName(fUuid, name);
            String dot       = isOnline ? "\u00a7a\u25cf" : "\u00a78\u25cf";
            String statusTip = isOnline ? "\u00a7a\u25cf Online" : "\u00a78\u25cf Offline";

            // Profile stats for hover
            PlayerProfile prof = profiles.get(fUuid);
            String hoverLines = statusTip
                    + (prof != null
                    ? "\n\u00a77K/D: \u00a7c" + prof.getKills() + "\u00a78/\u00a77" + prof.getDeaths()
                    + "\n\u00a77Rank: \u00a7f" + prof.getDisplayRank()
                    : "");

            Component entry = l("\u00a78 \u25b6 ")
                    .append(l(dot + " "))
                    .append(l(colored)
                            .hoverEvent(HoverEvent.showText(l(hoverLines))))
                    .append(l("  "))
                    .append(btn("\u00a77[Info]",
                            ClickEvent.runCommand("/friend info " + name),
                            "\u00a77View friendship details"))
                    .append(l(" "))
                    .append(btn("\u00a7c[Remove]",
                            ClickEvent.suggestCommand("/friend remove " + name),
                            "\u00a7cSuggests removal command\n\u00a78Confirm by pressing Enter"));

            player.sendMessage(entry);
        }

        player.sendMessage(l(BAR));
        player.sendMessage(l("\u00a78Click a name to view details. \u00a7a[+ Add]")
                .append(l(" "))
                .append(btn("\u00a7a[+ Add Friend]",
                        ClickEvent.suggestCommand("/friend add "),
                        "\u00a77Add a new friend")));
        player.sendMessage(Component.empty());
    }

    //  Pending requests 

    /**
     * Sends inline pending friend requests with [Accept] / [Deny] buttons.
     */
    public static void sendPendingList(Player player, FriendManager friendManager) {
        Set<UUID> pending = friendManager.getInboundRequests(player.getUniqueId());

        player.sendMessage(Component.empty());
        player.sendMessage(l(TITLE + " \u00a78\u2014 \u00a7ePending"));

        if (pending.isEmpty()) {
            player.sendMessage(l("\u00a77No pending friend requests."));
            player.sendMessage(l(BAR));
            player.sendMessage(Component.empty());
            return;
        }

        player.sendMessage(l(BAR));

        for (UUID fromUuid : pending) {
            String name = nameOf(fromUuid);
            String colored = friendManager.coloredName(fromUuid, name);

            Component entry = l("\u00a78 \u25b6 ")
                    .append(l(colored))
                    .append(l("  "))
                    .append(btn("\u00a7a[\u2713 Accept]",
                            ClickEvent.runCommand("/friend accept " + name),
                            "\u00a7aAccept \u00a7f" + name + "\u00a7a's friend request"))
                    .append(l(" "))
                    .append(btn("\u00a7c[\u2717 Deny]",
                            ClickEvent.runCommand("/friend deny " + name),
                            "\u00a7cDeny \u00a7f" + name + "\u00a7c's friend request"));

            player.sendMessage(entry);
        }

        player.sendMessage(l(BAR));
        player.sendMessage(Component.empty());
    }

    //  Helpers 

    private static Component l(String legacy) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
    }

    private static Component btn(String label, ClickEvent click, String hoverLegacy) {
        return l(label)
                .clickEvent(click)
                .hoverEvent(HoverEvent.showText(l(hoverLegacy)));
    }

    @SuppressWarnings("deprecation")
    private static String nameOf(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);
    }
}