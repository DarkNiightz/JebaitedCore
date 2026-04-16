package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.permissions.PermissionConstants;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.main.JebaitedCore;
import com.darkniightz.core.system.PrivateVaultManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PvCommand implements CommandExecutor, TabCompleter {

    private final JebaitedCore plugin;
    private final PrivateVaultManager vaultManager;

    public PvCommand(JebaitedCore plugin, PrivateVaultManager vaultManager) {
        this.plugin = plugin;
        this.vaultManager = vaultManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        if (plugin.getWorldManager() != null && plugin.getWorldManager().isHub(player)) {
            player.sendMessage(Messages.prefixed("§cPrivate vaults are only accessible in SMP."));
            return true;
        }

        // /pv <player> [page]  — moderator+ inspection
        if (args.length > 0) {
            String firstArg = args[0];
            boolean isPageNumber = firstArg.matches("\\d+");

            if (!isPageNumber) {
                // Staff inspection: /pv <player> [page]
                PlayerProfile staffProfile = plugin.getProfileStore().get(player.getUniqueId());
                if (staffProfile == null || !plugin.getRankManager().isAtLeast(staffProfile.getPrimaryRank(), "helper")) {
                    player.sendMessage(Messages.prefixed("§cYou don't have permission to inspect other players' vaults."));
                    return true;
                }

                @SuppressWarnings("deprecation")
                OfflinePlayer target = Bukkit.getOfflinePlayer(firstArg);
                if (target == null || (!target.isOnline() && !target.hasPlayedBefore())) {
                    player.sendMessage(Messages.prefixed("§cPlayer §f" + firstArg + " §cnot found."));
                    return true;
                }

                int page = 0;
                if (args.length > 1) {
                    try {
                        page = Integer.parseInt(args[1]) - 1;
                        if (page < 0) page = 0;
                    } catch (NumberFormatException ignored) {}
                }

                // Moderators can edit; helpers can only view
                boolean readOnly = plugin.getRankManager().isAtLeast(staffProfile.getPrimaryRank(), "moderator")
                        ? false : true;

                vaultManager.openVaultForStaff(player, target.getUniqueId(), page, readOnly);
                return true;
            }

            // It's a page number — fall through to own vault
            int page = 0;
            try {
                page = Integer.parseInt(firstArg) - 1;
                if (page < 0) page = 0;
            } catch (NumberFormatException ignored) {}
            vaultManager.openVault(player, page);
            return true;
        }

        vaultManager.openVault(player, 0);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return List.of();

        if (args.length == 1) {
            PlayerProfile profile = plugin.getProfileStore().get(player.getUniqueId());
            List<String> completions = new ArrayList<>();
            // Page numbers for own vault
            StringUtil.copyPartialMatches(args[0], List.of("1", "2", "3"), completions);
            // Player names for staff inspection (helper+)
            if (profile != null && plugin.getRankManager().isAtLeast(profile.getPrimaryRank(), "helper")) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!online.getUniqueId().equals(player.getUniqueId())) {
                        StringUtil.copyPartialMatches(args[0], List.of(online.getName()), completions);
                    }
                }
            }
            return completions;
        }

        return List.of();
    }
}
