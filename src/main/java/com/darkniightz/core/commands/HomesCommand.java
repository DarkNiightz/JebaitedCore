package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.system.HomesManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class HomesCommand implements CommandExecutor {

    private final Plugin plugin;
    private final HomesManager homes;

    public HomesCommand(Plugin plugin, HomesManager homes) {
        this.plugin = plugin;
        this.homes = homes;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can use this command."));
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String> names = homes.getHomeNames(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (names.isEmpty()) {
                    player.sendMessage(Messages.prefixed("§7You do not have any homes yet. Use §e/sethome [name]§7."));
                    return;
                }
                player.sendMessage(Messages.prefixed("§6Your homes §7(" + names.size() + "):"));
                for (String name : names) {
                    player.sendMessage(Component.text("- " + name, NamedTextColor.YELLOW)
                            .append(Component.text(" (click)", NamedTextColor.GRAY))
                            .clickEvent(ClickEvent.runCommand("/home " + name)));
                }
            });
        });
        return true;
    }
}
