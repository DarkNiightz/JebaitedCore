package com.darkniightz.main.util;

import com.darkniightz.main.Core;
import com.darkniightz.main.managers.RankManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;

public class RankUtil {

    public static int getRankLevel(Player player) {
        return RankManager.getInstance(Core.getInstance()).getRankLevel(player);
    }

    public static String getRankName(Player player) {
        return RankManager.getInstance(Core.getInstance()).getRankName(player);
    }

    // Updated for config colors
    public static String getColoredTabName(Player player) {
        String rank = RankManager.getInstance(Core.getInstance()).getRank(player);
        String name = player.getName();
        ConfigurationSection ranksSection = Core.getInstance().getConfig().getConfigurationSection("ranks");

        if (rank.equals("developer")) {
            char[] colorCodes = {'4', 'c', '6', 'e', '2', 'a', 'b', '3', '1', '9', 'd', '5'};
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < name.length(); i++) {
                char color = colorCodes[i % colorCodes.length];
                sb.append('§').append(color).append('§').append('l').append(name.charAt(i));
            }
            return sb.toString();
        } else {
            String colorCode = ranksSection.getString(rank, "§a");  // Default to green
            return ChatColor.translateAlternateColorCodes('&', colorCode) + name;
        }
    }
}