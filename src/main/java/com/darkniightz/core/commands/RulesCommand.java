package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RulesCommand implements CommandExecutor {

    private final Plugin plugin;

    public RulesCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> lines = plugin.getConfig().getStringList("rules.lines");
        if (lines == null || lines.isEmpty()) {
            sender.sendMessage(Messages.prefixed("§6Rules"));
            sender.sendMessage(Messages.prefixed("§7- Be respectful."));
            sender.sendMessage(Messages.prefixed("§7- No cheating or exploiting."));
            sender.sendMessage(Messages.prefixed("§7- No griefing or stealing."));
            return true;
        }

        String header = plugin.getConfig().getString("rules.header", "§6Server Rules");
        sender.sendMessage(Messages.prefixed(header));
        for (String line : lines) {
            sender.sendMessage(Messages.prefixed(line));
        }
        return true;
    }
}
