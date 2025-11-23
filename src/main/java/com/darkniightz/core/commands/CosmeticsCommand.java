package com.darkniightz.core.commands;

import com.darkniightz.core.cosmetics.CosmeticsManager;
import com.darkniightz.core.cosmetics.CosmeticsMenu;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
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

    public CosmeticsCommand(Plugin plugin, CosmeticsManager cosmetics, ProfileStore profiles) {
        this.plugin = plugin;
        this.cosmetics = cosmetics;
        this.profiles = profiles;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can open the cosmetics menu.");
            return true;
        }
        PlayerProfile prof = profiles.getOrCreate(p, plugin.getConfig().getString("ranks.default", "friend"));
        prof.incWardrobeOpens();
        profiles.save(p.getUniqueId());
        // Phase 2: open the new CosmeticsMenu container (Wardrobe still available as a tab)
        new CosmeticsMenu(plugin, cosmetics, profiles).open(p);
        return true;
    }
}
