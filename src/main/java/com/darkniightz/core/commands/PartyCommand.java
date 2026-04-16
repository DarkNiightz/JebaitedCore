package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.gui.PartyMenu;
import com.darkniightz.core.party.Party;
import com.darkniightz.core.party.PartyManager;
import com.darkniightz.core.players.ProfileStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /party [sub]  full party management.
 * /p  <message>   quick party chat (also via /party <message> when in chat-toggle mode).
 *
 * Subcommands:
 *   create                 create a new party (auto-creates on /invite too)
 *   invite  <player>       invite a player
 *   accept  <player>       accept a pending invite
 *   deny    <player>       decline a pending invite
 *   join    <leader>       join an open party without an invite
 *   kick    <player>       kick a member (leader only)
 *   leave                  leave the party
 *   disband                disband the party (leader only)
 *   transfer <player>      transfer leadership (leader only)
 *   chat   [message]       toggle party-chat mode or send an inline message
 *   list                   list all members in chat
 *   warp                   teleport to party warp
 *   setwarp                set party warp at your location (leader only)
 *   clearwarp              remove party warp (leader only)
 *   open                   toggle open/invite-only (leader only)
 *   ff                     toggle friendly fire (leader only)
 *   gui / menu             open party GUI
 */
public final class PartyCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "invite", "accept", "deny", "join",
            "kick", "leave", "disband", "transfer",
            "chat", "list",
            "warp", "setwarp", "clearwarp",
            "open", "ff",
            "gui", "menu"
    );
    private static final List<String> TARGET_SUBS = List.of(
            "invite", "kick", "transfer", "accept", "deny", "join");

    private final Plugin       plugin;
    private final PartyManager partyManager;

    public PartyCommand(Plugin plugin, PartyManager partyManager, ProfileStore profileStore) {
        this.plugin       = plugin;
        this.partyManager = partyManager;
    }

    //  Command dispatch 

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("cOnly players can use this command."));
            return true;
        }

        // /p <message>  direct party chat shortcut
        if (label.equalsIgnoreCase("p")) {
            if (args.length == 0) {
                player.sendMessage(partyManager.partyMsg("cUsage: f/p <message>"));
                return true;
            }
            if (!partyManager.sendChat(player, String.join(" ", args))) {
                player.sendMessage(partyManager.partyMsg("cYou are not in a party."));
            }
            return true;
        }

        // /party with no args  open GUI if in party, show help if not
        if (args.length == 0) {
            if (partyManager.isInParty(player.getUniqueId())) {
                new PartyMenu(plugin, partyManager).open(player);
            } else {
                sendHelp(player);
            }
            return true;
        }

        // If player has party-chat toggled on and the "subcommand" is not a known keyword,
        // treat the whole thing as a chat message (/party hello goes to party chat).
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (partyManager.hasChatToggle(player.getUniqueId())
                && !SUBCOMMANDS.contains(sub)) {
            partyManager.sendChat(player, String.join(" ", args));
            return true;
        }

        switch (sub) {

            case "create"   -> partyManager.create(player);

            case "invite"   -> {
                if (args.length < 2) { usage(player, "/party invite <player>"); return true; }
                Player t = resolve(player, args[1]); if (t == null) return true;
                partyManager.invite(player, t);
            }

            case "accept"   -> {
                if (args.length < 2) { usage(player, "/party accept <player>"); return true; }
                partyManager.accept(player, args[1]);
            }

            case "deny"     -> {
                if (args.length < 2) { usage(player, "/party deny <player>"); return true; }
                partyManager.deny(player, args[1]);
            }

            case "join"     -> {
                if (args.length < 2) { usage(player, "/party join <leader>"); return true; }
                partyManager.join(player, args[1]);
            }

            case "kick"     -> {
                if (args.length < 2) { usage(player, "/party kick <player>"); return true; }
                Player t = resolve(player, args[1]); if (t == null) return true;
                partyManager.kick(player, t);
            }

            case "leave"    -> partyManager.leave(player);
            case "disband"  -> partyManager.disband(player);

            case "transfer" -> {
                if (args.length < 2) { usage(player, "/party transfer <player>"); return true; }
                Player t = resolve(player, args[1]); if (t == null) return true;
                partyManager.transfer(player, t);
            }

            case "chat"     -> {
                if (args.length == 1) {
                    // Toggle mode
                    partyManager.toggleChat(player);
                } else {
                    // Inline send: /party chat hello world
                    String msg = String.join(" ", args).substring("chat ".length());
                    if (!partyManager.sendChat(player, msg)) {
                        player.sendMessage(partyManager.partyMsg("cYou are not in a party."));
                    }
                }
            }

            case "list"     -> listParty(player);
            case "warp"     -> partyManager.warpToParty(player);
            case "setwarp"  -> partyManager.setWarp(player);
            case "clearwarp"-> partyManager.clearWarp(player);
            case "open"     -> partyManager.toggleOpen(player);
            case "ff"       -> partyManager.toggleFriendlyFire(player);

            case "gui", "menu" -> {
                if (partyManager.isInParty(player.getUniqueId())) {
                    new PartyMenu(plugin, partyManager).open(player);
                } else {
                    player.sendMessage(partyManager.partyMsg("cYou are not in a party."));
                }
            }

            default -> sendHelp(player);
        }
        return true;
    }

    //  Tab completion 

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        if (!(sender instanceof Player)) return List.of();
        if (label.equalsIgnoreCase("p")) return List.of();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && TARGET_SUBS.contains(args[0].toLowerCase(Locale.ROOT))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    //  Helpers 

    private void listParty(Player player) {
        Party party = partyManager.getParty(player.getUniqueId());
        if (party == null) { player.sendMessage(partyManager.partyMsg("cYou are not in a party.")); return; }

        // Header
        player.sendMessage(partyManager.partyMsg("dMembers 8(a" + party.size() + "8):"));
        for (UUID uuid : party.getMembers()) {
            Player m = Bukkit.getPlayer(uuid);
            boolean online = m != null;
            String colored = partyManager.coloredName(uuid);
            String tag = party.isLeader(uuid) ? " 6[Leader]" : "";
            String chatTag = party.hasChat(uuid) ? " d[Chat]" : "";
            player.sendMessage("  " + (online ? "a " : "8 ") + colored + tag + chatTag);
        }
        if (!party.getPendingInvites().isEmpty()) {
            player.sendMessage(partyManager.partyMsg("7Pending invites: e" + party.getPendingInvites().size()));
        }
        // Flags summary
        player.sendMessage(partyManager.partyMsg(
                "7FF: " + (party.isFriendlyFire() ? "cOn" : "aOff")
                + " 8| 7Access: " + (party.isOpen() ? "aOpen" : "cInvite-only")
                + " 8| 7Warp: " + (party.getWarpLocation() != null ? "aSet" : "7None")));
    }

    /**
     * Clickable help message  each subcommand is a clickable component that
     * inserts the command into the chat input, matching the server's custom feel.
     */
    private void sendHelp(Player player) {
        player.sendMessage(Component.text()
                .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                .append(Component.text(" Party Commands", NamedTextColor.LIGHT_PURPLE)
                        .decorate(TextDecoration.BOLD))
                .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                .build());

        record Entry(String cmd, String desc) {}
        var entries = List.of(
                new Entry("/party create",            "Create a new party"),
                new Entry("/party invite <player>",   "Invite a player to your party"),
                new Entry("/party join <leader>",     "Join an open party"),
                new Entry("/party accept <player>",   "Accept a party invite"),
                new Entry("/party deny <player>",     "Decline a party invite"),
                new Entry("/party leave",             "Leave your current party"),
                new Entry("/party disband",           "Disband the party (leader)"),
                new Entry("/party kick <player>",     "Kick a member (leader)"),
                new Entry("/party transfer <player>", "Transfer leadership (leader)"),
                new Entry("/party open",              "Toggle open/invite-only (leader)"),
                new Entry("/party ff",                "Toggle friendly fire (leader)"),
                new Entry("/party setwarp",           "Set party warp here (leader)"),
                new Entry("/party warp",              "Teleport to party warp"),
                new Entry("/party clearwarp",         "Remove party warp (leader)"),
                new Entry("/party chat [msg]",        "Toggle party chat or send a message"),
                new Entry("/p <message>",             "Quick party chat"),
                new Entry("/party list",              "List party members"),
                new Entry("/party gui",               "Open party menu")
        );
        for (var e : entries) {
            // Suggest the base command portion (up to first space) for click
            String suggest = e.cmd().contains("<") ? e.cmd().substring(0, e.cmd().indexOf('<')).trim() : e.cmd();
            player.sendMessage(Component.text()
                    .append(Component.text("  " + e.cmd(), NamedTextColor.YELLOW)
                            .clickEvent(ClickEvent.suggestCommand(suggest))
                            .hoverEvent(HoverEvent.showText(
                                    Component.text("Click to use ", NamedTextColor.GRAY)
                                            .append(Component.text(e.cmd(), NamedTextColor.YELLOW)))))
                    .append(Component.text("  " + e.desc(), NamedTextColor.GRAY))
                    .build());
        }
    }

    private void usage(Player player, String usage) {
        player.sendMessage(partyManager.partyMsg("cUsage: f" + usage));
    }

    private Player resolve(Player sender, String name) {
        if (name.equalsIgnoreCase(sender.getName())) {
            sender.sendMessage(partyManager.partyMsg("cYou cannot target yourself."));
            return null;
        }
        Player t = Bukkit.getPlayerExact(name);
        if (t == null) sender.sendMessage(partyManager.partyMsg("c" + name + " is not online."));
        return t;
    }
}