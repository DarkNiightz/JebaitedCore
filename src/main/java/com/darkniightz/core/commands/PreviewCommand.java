package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.cosmetics.CollectionBookMenu;
import com.darkniightz.core.cosmetics.CosmeticPreviewService;
import com.darkniightz.core.cosmetics.CosmeticsManager;
import com.darkniightz.core.cosmetics.ToyboxManager;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.world.WorldManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class PreviewCommand implements CommandExecutor {

    private final Plugin plugin;
    private final CosmeticsManager cosmetics;
    private final ProfileStore profiles;
    private final CosmeticPreviewService previewService;
    private final ToyboxManager toyboxManager;
    private final WorldManager worldManager;

    public PreviewCommand(Plugin plugin, CosmeticsManager cosmetics, ProfileStore profiles, CosmeticPreviewService previewService, ToyboxManager toyboxManager, WorldManager worldManager) {
        this.plugin = plugin;
        this.cosmetics = cosmetics;
        this.profiles = profiles;
        this.previewService = previewService;
        this.toyboxManager = toyboxManager;
        this.worldManager = worldManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can use /preview."));
            return true;
        }
        if (!worldManager.isHub(player)) {
            player.sendMessage(Messages.prefixed("§cCosmetic preview is only available in the hub."));
            return true;
        }

        if (args.length == 0) {
            new CollectionBookMenu(plugin, cosmetics, profiles, previewService, toyboxManager).open(player);
            return true;
        }

        if ("featured".equalsIgnoreCase(args[0])) {
            previewService.showcaseFeatured(player);
            return true;
        }

        String key = args[0].toLowerCase(java.util.Locale.ROOT);
        if (cosmetics.get(key) == null) {
            player.sendMessage(Messages.prefixed("§cUnknown cosmetic. Open /preview to browse the collection book."));
            return true;
        }

        previewService.previewByKey(player, key);
        return true;
    }
}
