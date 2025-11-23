package com.darkniightz.core.commands;

import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TicketsCommand implements CommandExecutor {

    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;

    public TicketsCommand(ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // View own tickets
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§cConsole must specify a player: §e/" + label + " <player>");
                return true;
            }
            PlayerProfile prof = profiles.getOrCreate(p, ranks.getDefaultGroup());
            sender.sendMessage("§6Cosmetic Tickets§7: §e" + prof.getCosmeticTickets());
            return true;
        }

        String sub = args[0];

        // View other player's tickets: /tickets <player>
        if (args.length == 1 && !equalsAny(sub, "give", "take", "set")) {
            var target = Bukkit.getOfflinePlayer(args[0]);
            if (target.getUniqueId() == null) {
                sender.sendMessage("§cPlayer not found: §e" + args[0]);
                return true;
            }
            PlayerProfile tp = profiles.getOrCreate(target, ranks.getDefaultGroup());
            String name = target.getName() != null ? target.getName() : target.getUniqueId().toString().substring(0, 8);
            sender.sendMessage("§6Cosmetic Tickets for §e" + name + "§7: §e" + tp.getCosmeticTickets());
            return true;
        }

        // Management: give|take|set (Admin+ or DevMode bypass)
        if (args.length >= 3 && equalsAny(sub, "give", "take", "set")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§cOnly players with rank can manage tickets.");
                return true;
            }
            PlayerProfile actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
            boolean bypass = devMode != null && devMode.isActive(p.getUniqueId());
            if (!bypass && !ranks.isAtLeast(actor.getPrimaryRank(), "admin")) { sender.sendMessage(com.darkniightz.core.Messages.noPerm()); return true; }
            var target = Bukkit.getOfflinePlayer(args[1]);
            if (target.getUniqueId() == null) {
                sender.sendMessage("§cPlayer not found: §e" + args[1]);
                return true;
            }
            int amount;
            try { amount = Integer.parseInt(args[2]); } catch (NumberFormatException e) { sender.sendMessage("§cAmount must be a number."); return true; }
            if (amount < 0) { sender.sendMessage("§cAmount must be zero or positive."); return true; }

            PlayerProfile tp = profiles.getOrCreate(target, ranks.getDefaultGroup());
            int before = tp.getCosmeticTickets();
            int after = before;
            switch (sub.toLowerCase()) {
                case "give" -> after = before + amount;
                case "take" -> after = Math.max(0, before - amount);
                case "set" -> after = Math.max(0, amount);
            }
            tp.setCosmeticTickets(after);
            profiles.save(target.getUniqueId());

            String tName = target.getName() != null ? target.getName() : target.getUniqueId().toString().substring(0, 8);
            sender.sendMessage("§aUpdated tickets for §e" + tName + "§a: §7" + before + " §8→ §e" + after);
            if (target.isOnline()) {
                target.getPlayer().sendMessage("§aYour §6Cosmetic Tickets §7changed: §7" + before + " §8→ §e" + after);
            }
            return true;
        }

        sender.sendMessage("§eUsage: §7/" + label + " [player] | /" + label + " <give|take|set> <player> <amount>");
        return true;
    }

    private boolean equalsAny(String s, String... opts) {
        if (s == null) return false;
        for (String o : opts) if (s.equalsIgnoreCase(o)) return true;
        return false;
    }
}
