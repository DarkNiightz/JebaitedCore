package com.darkniightz.main.managers;

import com.darkniightz.main.Core;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RankManager {

    private static RankManager instance;
    private final Map<UUID, String> playerRanks = new HashMap<>();
    private final File ranksFile;
    private FileConfiguration ranksConfig;

    private RankManager(Plugin plugin) {
        ranksFile = new File(plugin.getDataFolder(), "ranks.yml");
        if (!ranksFile.exists()) {
            try {
                ranksFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create ranks.yml: " + e.getMessage());
            }
        }
        ranksConfig = YamlConfiguration.loadConfiguration(ranksFile);
        loadRanks();
    }

    public static RankManager getInstance(Plugin plugin) {
        if (instance == null) {
            instance = new RankManager(plugin);
        }
        return instance;
    }

    private void loadRanks() {
        for (String uuidStr : ranksConfig.getKeys(false)) {
            playerRanks.put(UUID.fromString(uuidStr), ranksConfig.getString(uuidStr));
        }
    }

    public void saveRanks() {
        for (Map.Entry<UUID, String> entry : playerRanks.entrySet()) {
            ranksConfig.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            ranksConfig.save(ranksFile);
        } catch (IOException e) {
            Core.getInstance().getLogger().warning("Failed to save ranks.yml: " + e.getMessage());
        }
    }

    public String getRank(Player player) {
        return playerRanks.getOrDefault(player.getUniqueId(), "default");
    }

    public void setRank(Player player, String rank) {
        playerRanks.put(player.getUniqueId(), rank);
        saveRanks();
    }

    public int getRankLevel(Player player) {
        String rank = getRank(player);
        switch (rank) {
            case "developer": return 6;
            case "admin": return 5;
            case "srmoderator": return 4;
            case "moderator": return 3;
            case "vip": return 2;
            case "friend": return 1;
            default: return 0;
        }
    }

    public String getRankName(Player player) {
        String rank = getRank(player);
        switch (rank) {
            case "srmoderator": return "Sr.Moderator";
            default: return rank.substring(0, 1).toUpperCase() + rank.substring(1);
        }
    }
}