package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.permissions.PermissionConstants;
import com.darkniightz.core.system.DiscordLinkService;
import com.darkniightz.main.JebaitedCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class DiscordLinkCommand implements CommandExecutor, TabCompleter {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final JebaitedCore plugin;

    public DiscordLinkCommand(JebaitedCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can use this command."));
            return true;
        }
        if (!player.hasPermission(PermissionConstants.CMD_DISCORD_LINK)) {
            player.sendMessage(Messages.noPerm());
            return true;
        }
        DiscordLinkService service = plugin.getDiscordLinkService();
        if (service == null) {
            player.sendMessage(Messages.prefixed("§cDiscord linking is not available right now."));
            return true;
        }
        if (service.hasActiveLink(player.getUniqueId())) {
            player.sendMessage(Messages.prefixed("§aYour account is already linked to Discord."));
            return true;
        }

        long ttlSeconds = Math.max(300L, Math.min(1800L, plugin.getConfig().getLong("integrations.discord.link_code_ttl_seconds", 900L)));
        player.sendMessage(Messages.prefixed("§7Generating your one-time Discord link code..."));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String code = service.issueCode(player.getUniqueId(), ttlSeconds);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (code == null) {
                    player.sendMessage(Messages.prefixed("§cCould not generate a link code. Please try again shortly."));
                    return;
                }
                sendLinkComponents(player, code, ttlSeconds);
            });
        });
        return true;
    }

    /**
     * Discord slash is registered as {@code /link} with a required string option {@code code}
     * (see bot-service). Pasting {@code /link code:XXXX} into Discord often expands to the slash UI.
     */
    private void sendLinkComponents(Player player, String code, long ttlSeconds) {
        Component prefix = LEGACY.deserialize(Messages.prefix());
        String discordSlash = "/link code:" + code;

        Component codePart = Component.text(code, NamedTextColor.WHITE, TextDecoration.BOLD)
                .clickEvent(ClickEvent.copyToClipboard(code))
                .hoverEvent(HoverEvent.showText(Component.text("Copy code (paste into Discord /link)", NamedTextColor.GRAY)));

        Component line1 = prefix.append(Component.text("Discord link code: ", NamedTextColor.GRAY))
                .append(codePart)
                .append(Component.text(" (valid " + (ttlSeconds / 60) + " min)", NamedTextColor.GRAY));

        Component copySlash = Component.text("copy full Discord command", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.copyToClipboard(discordSlash))
                .hoverEvent(HoverEvent.showText(Component.text("Copies: " + discordSlash, NamedTextColor.WHITE)));

        Component line2 = prefix.append(Component.text("Click the code to copy it, or ", NamedTextColor.GRAY))
                .append(copySlash)
                .append(Component.text(" to paste into Discord.", NamedTextColor.GRAY));

        player.sendMessage(line1);
        player.sendMessage(line2);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
