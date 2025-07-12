package com.darkniightz.main.listeners;

import com.darkniightz.main.Core;
import com.darkniightz.main.managers.LogManager;
import com.darkniightz.main.managers.MuteManager;
import com.darkniightz.main.util.RankUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.ChatColor;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

public class ChatListener implements Listener {

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();

        // Check mute using the manager
        if (MuteManager.getInstance().isMuted(sender.getUniqueId())) {
            event.setCancelled(true);
            sender.sendMessage(ChatColor.RED + "You're muted—can't chat!");
            return;
        }

        // Cancel default broadcast
        event.setCancelled(true);

        // Get the colored name based on rank
        TextComponent coloredName = getColoredName(sender);

        // Add hover for rank "interaction"
        coloredName.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Rank: " + RankUtil.getRankName(sender))));

        // Colon and message component
        TextComponent colon = new TextComponent(": ");
        TextComponent messageComp = new TextComponent(event.getMessage());

        // If sender is staff, color their message from config
        if (sender.hasPermission("core.staff")) {
            String staffColorCode = Core.getInstance().getConfig().getString("staff-message-color", "&b");
            net.md_5.bungee.api.ChatColor staffColor = getBungeeColorFromCode(staffColorCode);
            messageComp.setColor(staffColor);
        }

        // Build base component: name + colon + message
        TextComponent baseComp = new TextComponent();
        baseComp.addExtra(coloredName);
        baseComp.addExtra(colon);
        baseComp.addExtra(messageComp);

        // Normal message for non-staff
        TextComponent normalMsg = baseComp;

        // [XKMB] prefix for staff
        TextComponent prefix = new TextComponent("[");

        // X: Kill
        TextComponent x = new TextComponent("X");
        x.setColor(net.md_5.bungee.api.ChatColor.RED);
        x.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/kill " + sender.getName()));
        x.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to kill " + sender.getName())));
        prefix.addExtra(x);

        // K: Kick (ensure staff has bukkit.command.kick perm)
        TextComponent k = new TextComponent("K");
        k.setColor(net.md_5.bungee.api.ChatColor.YELLOW);
        k.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/kick " + sender.getName() + " Chat issue"));
        k.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to kick " + sender.getName() + " (needs kick perm)")));
        prefix.addExtra(k);

        // M: Mute
        TextComponent m = new TextComponent("M");
        m.setColor(net.md_5.bungee.api.ChatColor.BLUE);
        m.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mute " + sender.getName()));
        m.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to mute " + sender.getName())));
        prefix.addExtra(m);

        // B: Ban (ensure staff has bukkit.command.ban.player perm)
        TextComponent b = new TextComponent("B");
        b.setColor(net.md_5.bungee.api.ChatColor.DARK_RED);
        b.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ban " + sender.getName() + " Chat violation"));
        b.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to ban " + sender.getName() + " (needs ban perm)")));
        prefix.addExtra(b);

        prefix.addExtra(new TextComponent("] "));

        // Staff message: prefix + baseComp
        TextComponent staffMsg = new TextComponent();
        staffMsg.addExtra(prefix);
        staffMsg.addExtra(baseComp);

        // Send to players
        for (Player recipient : Bukkit.getOnlinePlayers()) {
            if (recipient.hasPermission("core.staff")) {
                recipient.spigot().sendMessage(staffMsg);
            } else {
                recipient.spigot().sendMessage(normalMsg);
            }
        }

        // Log chat if enabled
        if (Core.getInstance().getConfig().getBoolean("logging.log-chat", true)) {
            LogManager.getInstance(Core.getInstance()).log("CHAT: " + sender.getName() + ": " + event.getMessage());
        }
    }

    private TextComponent getColoredName(Player player) {
        String rank = RankUtil.getRankName(player).toLowerCase().replace(".", "");
        TextComponent nameComp;

        if (rank.equals("developer")) {
            nameComp = new TextComponent();
            net.md_5.bungee.api.ChatColor[] colors = {
                    net.md_5.bungee.api.ChatColor.DARK_RED, net.md_5.bungee.api.ChatColor.RED,
                    net.md_5.bungee.api.ChatColor.GOLD, net.md_5.bungee.api.ChatColor.YELLOW,
                    net.md_5.bungee.api.ChatColor.DARK_GREEN, net.md_5.bungee.api.ChatColor.GREEN,
                    net.md_5.bungee.api.ChatColor.AQUA, net.md_5.bungee.api.ChatColor.DARK_AQUA,
                    net.md_5.bungee.api.ChatColor.DARK_BLUE, net.md_5.bungee.api.ChatColor.BLUE,
                    net.md_5.bungee.api.ChatColor.LIGHT_PURPLE, net.md_5.bungee.api.ChatColor.DARK_PURPLE
            };
            if (colors.length == 0) {  // Safety vs / by zero from earlier bugs
                nameComp.setColor(net.md_5.bungee.api.ChatColor.WHITE);
                nameComp.setBold(true);
                return nameComp;
            }
            String name = player.getName();
            for (int i = 0; i < name.length(); i++) {
                TextComponent charComp = new TextComponent(String.valueOf(name.charAt(i)));
                charComp.setColor(colors[i % colors.length]);
                charComp.setBold(true);
                nameComp.addExtra(charComp);
            }
        } else {
            // Single color - init here too for clarity
            nameComp = new TextComponent(player.getName());
            switch (rank) {
                case "default":
                    nameComp.setColor(net.md_5.bungee.api.ChatColor.GREEN);
                    break;
                case "friend":
                    nameComp.setColor(net.md_5.bungee.api.ChatColor.LIGHT_PURPLE);
                    break;
                case "vip":
                    nameComp.setColor(net.md_5.bungee.api.ChatColor.DARK_PURPLE);
                    break;
                case "moderator":
                    nameComp.setColor(net.md_5.bungee.api.ChatColor.RED);
                    break;
                case "srmoderator":
                    nameComp.setColor(net.md_5.bungee.api.ChatColor.DARK_RED);
                    break;
                case "admin":
                    nameComp.setColor(net.md_5.bungee.api.ChatColor.DARK_RED);
                    nameComp.setBold(true);
                    break;
                default:
                    // Unknown rank fallback - keeps it safe
                    nameComp.setColor(net.md_5.bungee.api.ChatColor.GRAY);
                    break;
            }
        }
        return nameComp;
    }

    // New helper method: Map & code to Bungee ChatColor constant
    private net.md_5.bungee.api.ChatColor getBungeeColorFromCode(String code) {
        // Translate & to §, then strip to letter (e.g., "&b" -> "b")
        code = ChatColor.translateAlternateColorCodes('&', code).replace("§", "").toLowerCase();
        return switch (code) {
            case "0" -> net.md_5.bungee.api.ChatColor.BLACK;
            case "1" -> net.md_5.bungee.api.ChatColor.DARK_BLUE;
            case "2" -> net.md_5.bungee.api.ChatColor.DARK_GREEN;
            case "3" -> net.md_5.bungee.api.ChatColor.DARK_AQUA;
            case "4" -> net.md_5.bungee.api.ChatColor.DARK_RED;
            case "5" -> net.md_5.bungee.api.ChatColor.DARK_PURPLE;
            case "6" -> net.md_5.bungee.api.ChatColor.GOLD;
            case "7" -> net.md_5.bungee.api.ChatColor.GRAY;
            case "8" -> net.md_5.bungee.api.ChatColor.DARK_GRAY;
            case "9" -> net.md_5.bungee.api.ChatColor.BLUE;
            case "a" -> net.md_5.bungee.api.ChatColor.GREEN;
            case "b" -> net.md_5.bungee.api.ChatColor.AQUA;
            case "c" -> net.md_5.bungee.api.ChatColor.RED;
            case "d" -> net.md_5.bungee.api.ChatColor.LIGHT_PURPLE;
            case "e" -> net.md_5.bungee.api.ChatColor.YELLOW;
            case "f" -> net.md_5.bungee.api.ChatColor.WHITE;
            default -> net.md_5.bungee.api.ChatColor.WHITE;  // Fallback if invalid
        };
    }
}