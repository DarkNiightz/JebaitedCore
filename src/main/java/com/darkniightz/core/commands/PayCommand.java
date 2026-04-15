package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.system.EconomyManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class PayCommand implements CommandExecutor {

    private final EconomyManager economy;

    public PayCommand(EconomyManager economy) {
        this.economy = economy;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can use this command."));
            return true;
        }
        if (!JebaitedCore.getInstance().getConfig().getBoolean("economy.pay.enabled", true)) {
            sender.sendMessage(Messages.prefixed("§c/pay is currently disabled."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " <player> <amount>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(Messages.prefixed("§cThat player must be online."));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Messages.prefixed("§cAmount must be a number."));
            return true;
        }
        double minAmount = Math.max(0.01D, JebaitedCore.getInstance().getConfig().getDouble("economy.pay.min_amount", 0.01D));
        if (amount < minAmount) {
            sender.sendMessage(Messages.prefixed("§cAmount must be at least §f" + economy.format(minAmount) + "§c."));
            return true;
        }

        if (player.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage(Messages.prefixed("§cYou cannot pay yourself."));
            return true;
        }

        if (!economy.pay(player, target, amount)) {
            sender.sendMessage(Messages.prefixed("§cYou do not have enough money."));
            return true;
        }

        String prettyAmount = economy.format(amount);
        player.sendMessage(Messages.prefixed("§aTransfer complete. Sent §f" + prettyAmount + " §ato §e" + target.getName() + "§a."));
        player.sendMessage(Messages.prefixed("§7Your new balance: §a" + economy.format(economy.getBalance(player))));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.25f);

        target.sendMessage(Messages.prefixed("§aYou received §f" + prettyAmount + " §afrom §e" + player.getName() + "§a."));
        target.sendMessage(Messages.prefixed("§7Your new balance: §a" + economy.format(economy.getBalance(target))));
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.15f);
        return true;
    }
}
