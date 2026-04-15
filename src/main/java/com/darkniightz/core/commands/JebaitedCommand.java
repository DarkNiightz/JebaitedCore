package com.darkniightz.core.commands;

import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.world.WorldManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class JebaitedCommand implements CommandExecutor {
    private static final int PAGE_SIZE = 8;

    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;
    private final WorldManager worldManager;

    public JebaitedCommand(ProfileStore profiles, RankManager ranks, DevModeManager devMode, WorldManager worldManager) {
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
        this.worldManager = worldManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Player player = sender instanceof Player p ? p : null;
        String actorRank = "owner";
        boolean bypass = true;
        boolean inHub = true;
        boolean inSmp = false;

        if (player != null) {
            PlayerProfile prof = profiles.getOrCreate(player, ranks.getDefaultGroup());
            actorRank = prof != null && prof.getPrimaryRank() != null ? prof.getPrimaryRank() : ranks.getDefaultGroup();
            bypass = devMode != null && devMode.isActive(player.getUniqueId());
            inHub = worldManager != null && worldManager.isHub(player);
            inSmp = worldManager != null && worldManager.isSmp(player);
        }

        List<HelpEntry> entries = buildEntries(actorRank, bypass, inHub, inSmp, player == null);
        int totalPages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = parsePage(args, totalPages);
        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(entries.size(), from + PAGE_SIZE);

        sender.sendMessage(Component.text("Jebaited Help", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.text("Commands shown for your permissions and current location.", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Page " + page + "/" + totalPages + " • " + locationLabel(inHub, inSmp), NamedTextColor.DARK_GRAY));

        if (entries.isEmpty()) {
            sender.sendMessage(Component.text("No commands available right now.", NamedTextColor.GRAY));
            return true;
        }

        for (int i = from; i < to; i++) {
            HelpEntry entry = entries.get(i);
            sender.sendMessage(Component.text("• ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(entry.command(), entry.color())
                            .decorate(TextDecoration.BOLD)
                            .clickEvent(ClickEvent.suggestCommand(entry.command())))
                    .append(Component.text(" — " + entry.description(), NamedTextColor.GRAY)));
        }

        if (totalPages > 1) {
            Component prev = page > 1
                    ? Component.text("« Prev", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/help " + (page - 1)))
                    : Component.text("« Prev", NamedTextColor.DARK_GRAY);
            Component next = page < totalPages
                    ? Component.text("Next »", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/help " + (page + 1)))
                    : Component.text("Next »", NamedTextColor.DARK_GRAY);
            sender.sendMessage(prev.append(Component.text("  |  ", NamedTextColor.DARK_GRAY)).append(next));
        }
        return true;
    }

    private List<HelpEntry> buildEntries(String actorRank, boolean bypass, boolean inHub, boolean inSmp, boolean console) {
        List<HelpEntry> entries = new ArrayList<>();

        add(entries, true, "/help [page]", "Open this paged help menu", NamedTextColor.GOLD);
        add(entries, true, "/settings", "Personal settings and toggles", NamedTextColor.LIGHT_PURPLE);
        add(entries, true, "/stats [player]", "View player stats", NamedTextColor.GREEN);
        add(entries, true, "/coins [player]", "View cosmetic coins", NamedTextColor.GREEN);
        add(entries, true, "/balance [player]", "View money balance", NamedTextColor.GREEN);
        add(entries, true, "/pay <player> <amount>", "Send money", NamedTextColor.GREEN);
        add(entries, true, "/msg <player> <message>", "Private message", NamedTextColor.GREEN);
        add(entries, true, "/reply <message>", "Reply to the last message", NamedTextColor.GREEN);
        add(entries, true, "/nick <name|off>", "Set or clear your nickname", NamedTextColor.GREEN);
        add(entries, isAtLeast(actorRank, "gold", bypass), "/near [radius]", "Nearby players", NamedTextColor.GREEN);
        add(entries, true, "/rules", "Show the server rules", NamedTextColor.GREEN);

        if (console || inHub) {
            add(entries, true, "/menu", "Open the hub navigator", NamedTextColor.LIGHT_PURPLE);
            add(entries, true, "/cosmetics", "Open the cosmetics browser", NamedTextColor.LIGHT_PURPLE);
            add(entries, true, "/preview", "Preview cosmetics in the hub", NamedTextColor.LIGHT_PURPLE);
            add(entries, true, "/smp", "Travel to the SMP world", NamedTextColor.LIGHT_PURPLE);
        }

        if (console || inSmp || !inHub) {
            add(entries, true, "/sethome [name]", "Set one of your homes", NamedTextColor.GREEN);
            add(entries, true, "/home [name]", "Teleport to a saved home", NamedTextColor.GREEN);
            add(entries, true, "/delhome <name>", "Delete a saved home", NamedTextColor.GREEN);
            add(entries, true, "/homes", "List your homes", NamedTextColor.GREEN);
            add(entries, true, "/rtp", "Random teleport in SMP", NamedTextColor.GREEN);
            add(entries, true, "/warp <name>", "Teleport to a public warp", NamedTextColor.GREEN);
            add(entries, true, "/warps", "List public warps", NamedTextColor.GREEN);
            add(entries, true, "/grave", "Track your latest grave", NamedTextColor.LIGHT_PURPLE);
            add(entries, true, "/hub", "Return to the hub", NamedTextColor.LIGHT_PURPLE);
            add(entries, true, "/spawn", "Teleport to world spawn", NamedTextColor.GREEN);
        }

        add(entries, isAtLeast(actorRank, "helper", bypass), "/whois <player>", "Player diagnostics", NamedTextColor.YELLOW);
        add(entries, isAtLeast(actorRank, "helper", bypass), "/kick <player>", "Kick a player", NamedTextColor.YELLOW);
        add(entries, isAtLeast(actorRank, "helper", bypass), "/warn <player> <reason>", "Warn a player", NamedTextColor.YELLOW);
        add(entries, isAtLeast(actorRank, "helper", bypass), "/staffchat [message]", "Staff chat", NamedTextColor.YELLOW);
        add(entries, isAtLeast(actorRank, "helper", bypass), "/notes <player>", "View staff notes", NamedTextColor.YELLOW);
        add(entries, isAtLeast(actorRank, "moderator", bypass), "/mute <player>", "Mute a player", NamedTextColor.YELLOW);
        add(entries, isAtLeast(actorRank, "moderator", bypass), "/ban <player>", "Ban a player", NamedTextColor.YELLOW);
        add(entries, isAtLeast(actorRank, "moderator", bypass), "/freeze <player>", "Freeze a player", NamedTextColor.YELLOW);
        add(entries, isAtLeast(actorRank, "moderator", bypass), "/slowmode <seconds|off>", "Control chat slowmode", NamedTextColor.YELLOW);

        add(entries, isAtLeast(actorRank, "admin", bypass), "/setspawn", "Set this world's spawn", NamedTextColor.RED);
        add(entries, isAtLeast(actorRank, "admin", bypass), "/setrank <player> <rank>", "Change a player's rank", NamedTextColor.RED);
        add(entries, isAtLeast(actorRank, "admin", bypass), "/eco <give|take|set>", "Manage balances", NamedTextColor.RED);
        add(entries, isAtLeast(actorRank, "admin", bypass), "/setwarp <name>", "Create or update a warp", NamedTextColor.RED);
        add(entries, isAtLeast(actorRank, "admin", bypass), "/delwarp <name>", "Delete a public warp", NamedTextColor.RED);
        add(entries, isAtLeast(actorRank, "admin", bypass), "/event status", "Event control panel", NamedTextColor.RED);
        add(entries, isAtLeast(actorRank, "admin", bypass), "/event tp", "Teleport to the event world", NamedTextColor.RED);
        add(entries, isAtLeast(actorRank, "admin", bypass), "/worldstatus", "World and routing diagnostics", NamedTextColor.RED);
        add(entries, isAtLeast(actorRank, "admin", bypass), "/maintenance <on|off|status>", "Control maintenance mode", NamedTextColor.RED);

        if (console || bypass || isAtLeast(actorRank, "developer", false)) {
            add(entries, true, "/devmode", "Toggle developer bypass", NamedTextColor.DARK_PURPLE);
            add(entries, true, "/debug", "Open the debug cockpit", NamedTextColor.DARK_PURPLE);
            add(entries, true, "/jreload", "Reload config and caches", NamedTextColor.DARK_PURPLE);
        }
        return entries;
    }

    private void add(List<HelpEntry> entries, boolean allowed, String command, String description, NamedTextColor color) {
        if (allowed) {
            entries.add(new HelpEntry(command, description, color));
        }
    }

    private int parsePage(String[] args, int totalPages) {
        if (args == null || args.length == 0) {
            return 1;
        }
        try {
            int page = Integer.parseInt(args[0]);
            if (page < 1) return 1;
            return Math.min(page, totalPages);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private String locationLabel(boolean inHub, boolean inSmp) {
        if (inHub) return "Hub";
        if (inSmp) return "SMP";
        return "Global";
    }

    private boolean isAtLeast(String actorRank, String minRank, boolean bypass) {
        return bypass || ranks.isAtLeast(actorRank, minRank);
    }

    private record HelpEntry(String command, String description, NamedTextColor color) {}
}
