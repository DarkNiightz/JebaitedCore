package com.darkniightz.main.listeners;

import com.darkniightz.main.Core;
import com.darkniightz.main.managers.LogManager;
import com.darkniightz.main.util.RankUtil;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class HubProtectionListener implements Listener {

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (RankUtil.getRankLevel(player) < 5) {  // Below admin
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You can't break blocks in the hub!");
        }
        if (Core.getInstance().getConfig().getBoolean("logging.log-moderation", true)) {
            LogManager.getInstance(Core.getInstance()).log("BLOCK_BREAK_DENIED: " + player.getName() + " tried to break " + event.getBlock().getType() + " at " + event.getBlock().getLocation());
        }
    }
}