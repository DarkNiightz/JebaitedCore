package com.darkniightz.main.commands;

import com.darkniightz.main.Core;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class HelpCommand implements CommandExecutor {

    private static final int COMMANDS_PER_PAGE = 5;

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid page number!");
                return true;
            }
        }

        // Get commands from plugin.yml (no reflection needed!)
        Map<String, Map<String, Object>> commandsMap = Core.getInstance().getDescription().getCommands();
        if (commandsMap == null || commandsMap.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No commands found.");
            return true;
        }

        // Build list of "commands" with usage/desc
        List<CommandInfo> commandList = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : commandsMap.entrySet()) {
            String name = entry.getKey();
            Map<String, Object> data = entry.getValue();
            String usage = (String) data.getOrDefault("usage", "/" + name);
            String desc = (String) data.getOrDefault("description", "No description");
            commandList.add(new CommandInfo(name, usage, desc));
        }

        // Sort by name
        commandList.sort(Comparator.comparing(c -> c.name));

        int totalPages = (int) Math.ceil((double) commandList.size() / COMMANDS_PER_PAGE);
        if (page < 1 || page > totalPages) {
            sender.sendMessage(ChatColor.RED + "Page must be between 1 and " + totalPages);
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "Core Commands (Page " + page + "/" + totalPages + "):");

        int start = (page - 1) * COMMANDS_PER_PAGE;
        int end = Math.min(start + COMMANDS_PER_PAGE, commandList.size());
        for (int i = start; i < end; i++) {
            CommandInfo info = commandList.get(i);
            sender.sendMessage(ChatColor.AQUA + info.usage + ChatColor.WHITE + " - " + info.desc);
        }

        sender.sendMessage(ChatColor.YELLOW + "Use /help <page> for more.");
        return true;
    }

    // Helper class for info
    private static class CommandInfo {
        String name;
        String usage;
        String desc;

        CommandInfo(String name, String usage, String desc) {
            this.name = name;
            this.usage = usage;
            this.desc = desc;
        }
    }
}