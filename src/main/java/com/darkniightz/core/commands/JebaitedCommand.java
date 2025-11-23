package com.darkniightz.core.commands;

import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Adaptive help command for the core.
 * Shows available commands based on the viewer's rank and DevMode status.
 */
public class JebaitedCommand implements CommandExecutor {

    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;

    public JebaitedCommand(ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        boolean showAll = false;
        String actorRank = null;

        if (sender instanceof Player p) {
            PlayerProfile prof = profiles.getOrCreate(p, ranks.getDefaultGroup());
            actorRank = prof.getPrimaryRank();
            showAll = devMode != null && devMode.isActive(p.getUniqueId());
        } else {
            // Console sees everything
            showAll = true;
            actorRank = "owner"; // treat as highest for gating purposes
        }

        boolean isAdminPlus = showAll || ranks.isAtLeast(actorRank, "admin");

        sender.sendMessage("§6§lJebaited Core §7— Available Commands:");

        // General section
        sender.sendMessage("§eGeneral:");
        sender.sendMessage("§7 • §a/stats §7[player] §8- View hub stats (messages, commands, tickets)");
        sender.sendMessage("§7 • §a/tickets §7[player] §8- View Cosmetic Tickets (self or others)");

        // Staff section (Admin+ or DevMode)
        if (isAdminPlus) {
            sender.sendMessage("§eStaff:");
            sender.sendMessage("§7 • §c/setrank §e<player> <group> §8- Set a player's primary rank");
            sender.sendMessage("§7 • §c/rank get §e<player> §8- Check a player's rank");
            sender.sendMessage("§7 • §c/tickets give §e<player> <amount> §8- Grant tickets");
            sender.sendMessage("§7 • §c/tickets take §e<player> <amount> §8- Remove tickets");
            sender.sendMessage("§7 • §c/tickets set §e<player> <amount> §8- Set exact ticket count");
            sender.sendMessage("§7 • §c/kick §e<player> [reason] §8- Kick player");
            sender.sendMessage("§7 • §c/warn §e<player> <reason> §8- Warn player");
            sender.sendMessage("§7 • §c/mute §e<player> [reason] §8- Permanent mute (Mod+)");
            sender.sendMessage("§7 • §c/tempmute §e<player> <duration> [reason] §8- Timed mute (Mod+)");
            sender.sendMessage("§7 • §c/ban §e<player> [reason] §8- Permanent ban (Mod+)");
            sender.sendMessage("§7 • §c/tempban §e<player> <duration> [reason] §8- Timed ban (Helper+)");
            sender.sendMessage("§7 • §c/unmute §e<player> §8- Remove mute");
            sender.sendMessage("§7 • §c/unban §e<player> §8- Remove ban");
            sender.sendMessage("§7 • §c/freeze §e<player> §8- Toggle freeze");
            sender.sendMessage("§7 • §c/vanish §8- Toggle staff vanish");
            sender.sendMessage("§7 • §c/staffchat [msg] §8- Toggle or send to staff chat");
            sender.sendMessage("§7 • §c/clearchat §8- Clear chat for non-staff");
            sender.sendMessage("§7 • §c/slowmode §e<seconds|off> §8- Set channel slowmode");
            sender.sendMessage("§7 • §c/history §7[player] §8- View moderation history");
        }

        // Dev section (only if allowed and/or DevMode active)
        if (sender instanceof Player p) {
            boolean allowed = devMode != null && devMode.isAllowed(p.getUniqueId());
            if (allowed || showAll) {
                String state = (devMode != null && devMode.isActive(p.getUniqueId())) ? "§aENABLED" : "§cDISABLED";
                sender.sendMessage("§eDeveloper:");
                sender.sendMessage("§7 • §d/devmode §7<on|off|toggle> §8(§7alias: §d/dev§8) §m—§r §7Status: " + state);
            }
        }

        return true;
    }
}
