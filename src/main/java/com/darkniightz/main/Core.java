package com.darkniightz.main;

import com.darkniightz.main.commands.*;
import com.darkniightz.main.listeners.*;
import com.darkniightz.main.managers.LogManager;

import com.darkniightz.main.commands.MuteCommand;
import com.darkniightz.main.commands.TeleportCommand;  // And your other commands
import com.darkniightz.main.managers.MuteManager;
import com.darkniightz.main.managers.RankManager;
import com.darkniightz.main.listeners.ChatListener;
import com.darkniightz.main.managers.SpawnManager;
import com.darkniightz.main.util.*;
import com.darkniightz.main.CoreUnbanCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class Core extends JavaPlugin {

    private static Core instance;

    public static Core getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {


        instance = this;
        saveDefaultConfig();
        reloadConfig();

        getLogger().info("Jebaited Core is now Enabled.");

        // Init managers (loads data)
        RankManager.getInstance(this);
        MuteManager.getInstance();
        LogManager.getInstance(this);
        SpawnManager.getInstance();

        // Register listeners
        Bukkit.getScheduler().runTaskLater(this, () -> {



        getServer().getPluginManager().registerEvents(new ChatListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerEventListener(), this);  // Updated name
        getServer().getPluginManager().registerEvents(new ServerListPingListener(), this);
        getServer().getPluginManager().registerEvents(new HubProtectionListener(), this);
        getServer().getPluginManager().registerEvents(new InventoryManagerListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerSurvivalListener(), this);
        getServer().getPluginManager().registerEvents(new PetsHandler(), this);
        getServer().getPluginManager().registerEvents(new PlayerSelectorGUI(), this);
        getServer().getPluginManager().registerEvents(new ModerationGUIHandler(), this);
        getServer().getPluginManager().registerEvents(new PlayerSurvivalListener(), this);
        getServer().getPluginManager().registerEvents(new HubBossBarListener(), this);


        // Register commands
        this.getCommand("mute").setExecutor(new MuteCommand());
        this.getCommand("unmute").setExecutor(new MuteCommand());
        this.getCommand("core").setExecutor(new CoreCommand(this));
        this.getCommand("corekill").setExecutor(new CoreKillCommand());
        this.getCommand("corekick").setExecutor(new CoreKickCommand());
        this.getCommand("coreban").setExecutor(new CoreBanCommand());
        this.getCommand("broadcast").setExecutor(new BroadcastCommand());
        this.getCommand("sc").setExecutor(new StaffChatCommand());
        this.getCommand("setrank").setExecutor(new SetRankCommand());
        this.getCommand("help").setExecutor(new HelpCommand());
        this.getCommand("spawn").setExecutor(new SpawnCommand());
        this.getCommand("setspawn").setExecutor(new SetSpawnCommand());
        this.getCommand("fly").setExecutor(new FlyCommand());
        this.getCommand("gamemode").setExecutor(new GamemodeCommand());
        this.getCommand("tp").setExecutor(new TeleportCommand());
        this.getCommand("unban").setExecutor(new CoreUnbanCommand());

        }, 5);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                new HubScoreboardListener().updateScoreboard(p);
            }
        }, 0, 20); //Every Second

        // Update tablist for online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setPlayerListName(RankUtil.getColoredTabName(player));
        }


        // Start auto broadcaster if enabled
        if (getConfig().getBoolean("broadcaster.enabled", true)) {
            int interval = getConfig().getInt("broadcaster.interval-seconds", 300) * 20;  // Seconds to ticks
            // Fixed: Call runTaskTimerAsynchronously on the BukkitRunnable instance, not the scheduler
            new AutoBroadcaster().runTaskTimerAsynchronously(this, interval, interval);
        }

    }


    @Override
    public void onDisable() {
        // Existing saves
        RankManager.getInstance(this).saveRanks();

        // Reset tablist
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setPlayerListName(player.getName());
        }

        // Unregister all event listeners (fixes dupes)
        HandlerList.unregisterAll(this);

        // Cancel all scheduled tasks (e.g., broadcaster, auto-unmutes)
        Bukkit.getScheduler().cancelTasks(this);

        // Close/reset managers if needed (e.g., clear maps)

        MuteManager.getInstance().mutedPlayers.clear();  // Example for mute map - add similar for others
        LogManager.getInstance(this);  // If it has open files, close 'em here

        getLogger().info("Jebaited Core is now Disabled.");
    }
}
