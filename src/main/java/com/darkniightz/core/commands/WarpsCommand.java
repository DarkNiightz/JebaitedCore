package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.system.EconomyManager;
import com.darkniightz.core.system.WarpsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class WarpsCommand implements CommandExecutor {

    private final WarpsManager warps;
    private final EconomyManager economy;

    public WarpsCommand(WarpsManager warps, EconomyManager economy) {
        this.warps = warps;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can use this command."));
            return true;
        }

        List<String> names = warps.listWarps();
        if (names.isEmpty()) {
            player.sendMessage(Messages.prefixed("§7No warps are configured yet."));
            return true;
        }

        player.sendMessage(Messages.prefixed("§6Available warps §7(" + names.size() + "):"));
        for (String name : names) {
            double cost = warps.getCost(name);
            String suffix = cost > 0D ? " §8(" + economy.format(cost) + ")" : "";
            player.sendMessage(Component.text("- " + name + suffix, NamedTextColor.YELLOW)
                    .append(Component.text(" (click)", NamedTextColor.GRAY))
                    .clickEvent(ClickEvent.runCommand("/warp " + name)));
        }
        return true;
    }
}
