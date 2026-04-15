package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.system.EconomyManager;
import com.darkniightz.main.PlayerProfileDAO;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class BalanceTopCommand implements CommandExecutor {

    private final ProfileStore profiles;
    private final EconomyManager economy;

    public BalanceTopCommand(ProfileStore profiles, EconomyManager economy) {
        this.profiles = profiles;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        int limit = 10;
        if (args.length >= 1) {
            try {
                limit = Math.max(1, Math.min(20, Integer.parseInt(args[0])));
            } catch (NumberFormatException ignored) {
                sender.sendMessage(Messages.prefixed("§cLimit must be a number between 1 and 20."));
                return true;
            }
        }

        List<PlayerProfileDAO.BalanceTopRecord> top = profiles.loadTopBalances(limit);
        if (top.isEmpty()) {
            sender.sendMessage(Messages.prefixed("§7No economy data found yet."));
            return true;
        }

        sender.sendMessage(Messages.prefixed("§6Balance Top §7(§f" + top.size() + "§7)"));
        int index = 1;
        for (PlayerProfileDAO.BalanceTopRecord row : top) {
            String name = row.username() == null || row.username().isBlank()
                    ? row.uuid().toString().substring(0, 8)
                    : row.username();
            sender.sendMessage(Messages.prefixed("§8#" + index + " §e" + name + " §7- §a" + economy.format(row.balance())));
            index++;
        }
        return true;
    }
}
