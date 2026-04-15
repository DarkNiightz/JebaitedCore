package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.cosmetics.CollectionBookMenu;
import com.darkniightz.core.cosmetics.CosmeticsManager;
import com.darkniightz.core.cosmetics.CosmeticPreviewService;
import com.darkniightz.core.cosmetics.ToyboxManager;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.world.WorldManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class CosmeticsCommand implements CommandExecutor {
    private final Plugin plugin;
    private final CosmeticsManager cosmetics;
    private final ProfileStore profiles;
    private final ToyboxManager toyboxManager;
    private final CosmeticPreviewService previewService;
    private final DevModeManager devMode;
    private final WorldManager worldManager;

    public CosmeticsCommand(Plugin plugin, CosmeticsManager cosmetics, ProfileStore profiles, ToyboxManager toyboxManager) {
        this(plugin, cosmetics, profiles, toyboxManager, null, null, null);
    }

    public CosmeticsCommand(Plugin plugin, CosmeticsManager cosmetics, ProfileStore profiles, ToyboxManager toyboxManager, CosmeticPreviewService previewService) {
        this(plugin, cosmetics, profiles, toyboxManager, previewService, null, null);
    }

    public CosmeticsCommand(Plugin plugin, CosmeticsManager cosmetics, ProfileStore profiles, ToyboxManager toyboxManager, CosmeticPreviewService previewService, DevModeManager devMode, WorldManager worldManager) {
        this.plugin = plugin;
        this.cosmetics = cosmetics;
        this.profiles = profiles;
        this.toyboxManager = toyboxManager;
        this.previewService = previewService;
        this.devMode = devMode;
        if (worldManager != null) {
            this.worldManager = worldManager;
        } else if (plugin instanceof JebaitedCore core) {
            this.worldManager = core.getWorldManager();
        } else {
            this.worldManager = new WorldManager(plugin);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can open the cosmetics menu."));
            return true;
        }
        if (!worldManager.requireHub(p, devMode)) {
            return true;
        }
        PlayerProfile prof = profiles.getOrCreate(p, plugin.getConfig().getString("ranks.default", "pleb"));
        prof.incWardrobeOpens();
        profiles.save(p.getUniqueId());
        new CollectionBookMenu(plugin, cosmetics, profiles, previewService, toyboxManager).open(p);
        return true;
    }
}
