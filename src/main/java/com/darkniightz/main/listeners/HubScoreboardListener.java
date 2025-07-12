package com.darkniightz.main.listeners;

import com.darkniightz.main.util.RankUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scoreboard.*;

public class HubScoreboardListener implements Listener {

    private int colorIndex = 0;
    private final String[] rainbowColors = {"§4", "§c", "§6", "§e", "§2", "§a", "§b", "§3", "§1", "§9", "§d", "§5"};

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        updateScoreboard(event.getPlayer());
    }

    public void updateScoreboard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();
        Objective obj = board.registerNewObjective("hubinfo", "dummy", rainbowTitle());
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        obj.getScore("Rank: " + RankUtil.getRankName(player)).setScore(2);
        obj.getScore("Online: " + Bukkit.getOnlinePlayers().size()).setScore(1);

        player.setScoreboard(board);
    }

    private String rainbowTitle() {
        String title = "Jebaited";
        StringBuilder rainbow = new StringBuilder();
        for (int i = 0; i < title.length(); i++) {
            rainbow.append(rainbowColors[(colorIndex + i) % rainbowColors.length]).append(title.charAt(i));
        }
        colorIndex = (colorIndex + 1) % rainbowColors.length;
        return rainbow.toString();
    }
}