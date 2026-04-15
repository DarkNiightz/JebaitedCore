package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.cosmetics.CosmeticsEngine;
import com.darkniightz.core.cosmetics.CosmeticsManager;
import com.darkniightz.core.cosmetics.DebugMenu;
import com.darkniightz.core.cosmetics.ToyboxManager;
import com.darkniightz.core.dev.DeployStatusManager;
import com.darkniightz.core.dev.DebugFeedManager;
import com.darkniightz.core.dev.DebugStateManager;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.moderation.ModerationManager;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.system.BossBarManager;
import com.darkniightz.core.system.BroadcasterManager;
import com.darkniightz.core.world.SpawnManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class DebugCommand implements CommandExecutor {
    private final JebaitedCore plugin;
    private final DevModeManager devMode;
    private final DebugStateManager debugState;
    private final DebugFeedManager feed;
    private final DeployStatusManager deployStatus;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final CosmeticsManager cosmetics;
    private final CosmeticsEngine cosmeticsEngine;
    private final ToyboxManager toyboxManager;
    private final BroadcasterManager broadcasterManager;
    private final BossBarManager bossBarManager;
    private final SpawnManager spawnManager;
    private final ModerationManager moderationManager;

    public DebugCommand(JebaitedCore plugin) {
        this.plugin = plugin;
        this.devMode = plugin.getDevModeManager();
        this.debugState = plugin.getDebugStateManager();
        this.feed = plugin.getDebugFeedManager();
        this.deployStatus = plugin.getDeployStatusManager();
        this.profiles = plugin.getProfileStore();
        this.ranks = plugin.getRankManager();
        this.cosmetics = plugin.getCosmeticsManager();
        this.cosmeticsEngine = plugin.getCosmeticsEngine();
        this.toyboxManager = plugin.getToyboxManager();
        this.broadcasterManager = plugin.getBroadcasterManager();
        this.bossBarManager = plugin.getBossBarManager();
        this.spawnManager = plugin.getSpawnManager();
        this.moderationManager = plugin.getModerationManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("§cOnly players with active devmode can use the debug cockpit."));
            return true;
        }
        if (!devMode.isAllowed(player.getUniqueId())) {
            player.sendMessage(Messages.prefixed("§cYou are not allowed to use devmode."));
            return true;
        }
        if (!devMode.isActive(player.getUniqueId())) {
            player.sendMessage(Messages.prefixed("§cEnable devmode first with /devmode on."));
            return true;
        }

        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        DebugMenu menu = new DebugMenu(plugin, devMode, debugState, feed, deployStatus, profiles, ranks, cosmetics, cosmeticsEngine, toyboxManager, broadcasterManager, bossBarManager, spawnManager, moderationManager);
        switch (sub) {
            case "menu", "open" -> menu.open(player);
            case "system" -> menu.openSystem(player);
            case "commands" -> menu.openCommands(player);
            case "listeners" -> menu.openListeners(player);
            case "cosmetics" -> menu.openCosmetics(player);
            case "preview" -> menu.openPreview(player);
            case "actions", "tools" -> menu.openActions(player);
            case "events", "feed" -> menu.openEvents(player);
            case "health", "status" -> menu.openHealth(player);
            default -> {
                player.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " [menu|system|commands|listeners|cosmetics|preview|actions|events|health]"));
                return true;
            }
        }
        return true;
    }
}
