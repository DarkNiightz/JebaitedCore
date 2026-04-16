package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.permissions.PermissionConstants;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.system.KitManager;
import com.darkniightz.main.JebaitedCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /kit [tier] — claims a donor kit.
 *
 * <p>Players can only claim kits their donor rank qualifies for.
 * Each kit has a shared cooldown (configured via kits.cooldown_ms).</p>
 *
 * Rank requirements:
 * <ul>
 *   <li>gold kit       — Gold+ donor</li>
 *   <li>diamond kit    — Diamond+ donor</li>
 *   <li>legend kit     — Legend+ donor</li>
 *   <li>grandmaster kit — Grandmaster donor</li>
 * </ul>
 */
public class KitCommand implements CommandExecutor, TabCompleter {

    private final JebaitedCore plugin;

    public KitCommand(JebaitedCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("§cThis command can only be used in-game."));
            return true;
        }

        PlayerProfile profile = plugin.getProfileStore().get(player.getUniqueId());
        KitManager kitManager = plugin.getKitManager();

        if (args.length == 0) {
            sendKitList(player, profile, kitManager);
            return true;
        }

        String kitName = args[0].toLowerCase(Locale.ROOT);

        if (kitManager.requiredDonorRank(kitName) == null) {
            player.sendMessage(Messages.prefixed("§cUnknown kit. Available: §f"
                    + String.join("§c, §f", getAvailableKits(profile, kitManager))));
            return true;
        }

        if (!kitManager.canUseKit(profile, kitName)) {
            player.sendMessage(Messages.prefixed("§cYou do not have access to the §f"
                    + kitName + " §ckit."));
            return true;
        }

        if (kitManager.isOnCooldown(profile, kitName)) {
            long remaining = kitManager.getRemainingCooldownMs(profile, kitName);
            player.sendMessage(Messages.prefixed("§cKit §f" + kitName
                    + " §cis on cooldown. Ready in §f"
                    + KitManager.formatDuration(remaining) + "§c."));
            return true;
        }

        List<ItemStack> kitItems = kitManager.getKitItems(kitName);
        if (kitItems.isEmpty()) {
            player.sendMessage(Messages.prefixed("§cNo items defined for kit §f" + kitName + "§c."));
            return true;
        }

        // Check inventory space
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(
                kitItems.toArray(new ItemStack[0]));

        // Record use — in-memory first, then async persist
        long now = System.currentTimeMillis();
        profile.setKitLastUsed(kitName, now);
        plugin.getPlayerProfileDAO().saveKitCooldownsAsync(
                player.getUniqueId(), profile.getKitCooldowns());

        if (overflow.isEmpty()) {
            player.sendMessage(Messages.prefixed("§aYou received the §f" + kitName + " §akit!"));
        } else {
            // Drop overflowed items at feet
            overflow.values().forEach(item ->
                    player.getWorld().dropItemNaturally(player.getLocation(), item));
            player.sendMessage(Messages.prefixed("§aYou received the §f" + kitName
                    + " §akit! §eInventory full — some items dropped at your feet."));
        }

        return true;
    }

    // -----------------------------------------------------------------
    // Kit list panel
    // -----------------------------------------------------------------

    private void sendKitList(Player player, PlayerProfile profile, KitManager kitManager) {
        player.sendMessage(Messages.prefixed("§6§lAvailable Kits"));

        for (String kit : KitManager.allKitNames()) {
            if (!kitManager.canUseKit(profile, kit)) continue;

            boolean onCooldown = kitManager.isOnCooldown(profile, kit);
            long remaining     = kitManager.getRemainingCooldownMs(profile, kit);

            Component line = Component.text("  » ", NamedTextColor.DARK_GRAY)
                    .append(Component.text("[" + kit + "]",
                                    onCooldown ? NamedTextColor.RED : NamedTextColor.GREEN)
                            .decorate(TextDecoration.BOLD)
                            .clickEvent(ClickEvent.runCommand("/kit " + kit))
                            .hoverEvent(HoverEvent.showText(
                                    onCooldown
                                            ? Component.text("On cooldown — " + KitManager.formatDuration(remaining) + " remaining", NamedTextColor.RED)
                                            : Component.text("Click to claim the " + kit + " kit", NamedTextColor.GREEN))))
                    .append(onCooldown
                            ? Component.text(" (Ready in " + KitManager.formatDuration(remaining) + ")", NamedTextColor.DARK_GRAY)
                            : Component.text(" — click to claim", NamedTextColor.GRAY));
            player.sendMessage(line);
        }

        List<String> available = getAvailableKits(profile, kitManager);
        if (available.isEmpty()) {
            player.sendMessage(Messages.prefixed("§7You have no donor kits available."));
        }
    }

    // -----------------------------------------------------------------
    // Tab completion
    // -----------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (args.length != 1) return List.of();

        PlayerProfile profile = plugin.getProfileStore().get(player.getUniqueId());
        List<String> options = getAvailableKits(profile, plugin.getKitManager());
        List<String> completions = new ArrayList<>();
        StringUtil.copyPartialMatches(args[0], options, completions);
        return completions;
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private List<String> getAvailableKits(PlayerProfile profile, KitManager kitManager) {
        List<String> out = new ArrayList<>();
        for (String kit : KitManager.allKitNames()) {
            if (kitManager.canUseKit(profile, kit)) out.add(kit);
        }
        return out;
    }
}
