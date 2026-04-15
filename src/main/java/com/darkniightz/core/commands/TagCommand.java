package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.system.TagCustomizationManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class TagCommand implements CommandExecutor {
    private final Plugin plugin;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final TagCustomizationManager tagCustomization;

    public TagCommand(Plugin plugin, ProfileStore profiles, RankManager ranks, TagCustomizationManager tagCustomization) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.ranks = ranks;
        this.tagCustomization = tagCustomization;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can use /tag."));
            return true;
        }

        PlayerProfile profile = profiles.getOrCreate(player, ranks.getDefaultGroup());
        if (!profile.hasUnlocked(TagCustomizationManager.CUSTOM_TAG_KEY)) {
            player.sendMessage(Messages.prefixed("§cYou don't own a customizable tag yet."));
            player.sendMessage(Messages.prefixed("§7Open §e/cosmetics §7-> §eTags §7and buy the §dCustom Tag§7 with cosmetic coins."));
            return true;
        }

        if (args.length == 0) {
            String current = tagCustomization.getCustomTag(player.getUniqueId());
            player.sendMessage(Messages.prefixed("§7Custom tag: " + (current == null ? "§8none" : current)));
            player.sendMessage(Messages.prefixed("§7Use: §e/tag <text with & colors> §7or §e/tag clear"));
            return true;
        }

        if (args.length == 1 && (args[0].equalsIgnoreCase("clear") || args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("reset"))) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                tagCustomization.clearCustomTag(player.getUniqueId());
                if (TagCustomizationManager.CUSTOM_TAG_KEY.equalsIgnoreCase(profile.getActiveTag())) {
                    profile.setActiveTag(null);
                }
                profiles.save(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (JebaitedCore.getInstance() != null) {
                        JebaitedCore.getInstance().refreshPlayerPresentation(player);
                        JebaitedCore.getInstance().refreshAllPlayerPresentations();
                    }
                    player.sendMessage(Messages.prefixed("§aYour custom tag has been cleared."));
                });
            });
            return true;
        }

        String joined = String.join(" ", Arrays.asList(args));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean ok = tagCustomization.setCustomTag(player.getUniqueId(), joined);
            if (!ok) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) player.sendMessage(Messages.prefixed("§cInvalid custom tag. Keep it 1-24 visible chars."));
                });
                return;
            }
            profile.setActiveTag(TagCustomizationManager.CUSTOM_TAG_KEY);
            profiles.save(player.getUniqueId());
            String displayTag = tagCustomization.getCustomTag(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (JebaitedCore.getInstance() != null) {
                    JebaitedCore.getInstance().refreshPlayerPresentation(player);
                    JebaitedCore.getInstance().refreshAllPlayerPresentations();
                }
                player.sendMessage(Messages.prefixed("§aCustom tag updated and activated: §f" + displayTag));
            });
        });
        return true;
    }
}
