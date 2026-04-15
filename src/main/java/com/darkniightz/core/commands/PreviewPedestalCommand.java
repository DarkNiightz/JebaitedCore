package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.world.PreviewPedestalManager;
import com.darkniightz.core.world.WorldManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class PreviewPedestalCommand implements CommandExecutor {
    private final Plugin plugin;
    private final PreviewPedestalManager pedestalManager;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;
    private final WorldManager worldManager;

    public PreviewPedestalCommand(Plugin plugin, PreviewPedestalManager pedestalManager, ProfileStore profiles, RankManager ranks, DevModeManager devMode, WorldManager worldManager) {
        this.plugin = plugin;
        this.pedestalManager = pedestalManager;
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
        this.worldManager = worldManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can place the preview pedestal."));
            return true;
        }

        var profile = profiles.getOrCreate(player, ranks.getDefaultGroup());
        boolean bypass = devMode != null && devMode.isActive(player.getUniqueId());
        if (!worldManager.requireHub(player, devMode)) {
            return true;
        }
        if (!bypass && !ranks.isAtLeast(profile.getPrimaryRank(), "admin")) {
            sender.sendMessage(Messages.noPerm());
            return true;
        }

        String sub = args.length == 0 ? "set" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "set", "here" -> {
                pedestalManager.setConfiguredPedestal(player.getLocation());
                player.sendMessage(Messages.prefixed("§dPreview pedestal set §7at your current location."));
                return true;
            }
            case "clear", "remove" -> {
                pedestalManager.clearConfiguredPedestal();
                player.sendMessage(Messages.prefixed("§7Preview pedestal cleared."));
                return true;
            }
            case "status" -> {
                var loc = pedestalManager.getConfiguredPedestal();
                if (loc == null) {
                    player.sendMessage(Messages.prefixed("§7No preview pedestal is configured."));
                } else {
                    player.sendMessage(Messages.prefixed("§dPreview pedestal: §f" + loc.getWorld().getName() + " " +
                            String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", loc.getX(), loc.getY(), loc.getZ())));
                }
                return true;
            }
            default -> {
                player.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " [set|clear|status]"));
                return true;
            }
        }
    }
}
