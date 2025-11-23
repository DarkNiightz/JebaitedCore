package com.darkniightz.main;

import com.darkniightz.core.chat.ChatListener;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.playerjoin.JoinListener;
import com.darkniightz.core.commands.RankCommand;
import com.darkniightz.core.commands.SetRankCommand;
import com.darkniightz.core.commands.TicketsCommand;
import com.darkniightz.core.commands.StatsCommand;
import com.darkniightz.core.commands.JebaitedCommand;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.commands.DevModeCommand;
import com.darkniightz.core.tracking.CommandTrackingListener;
import com.darkniightz.core.moderation.ModerationManager;
import com.darkniightz.core.moderation.ModerationListener;
import com.darkniightz.core.commands.mod.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class JebaitedCore extends JavaPlugin {

    private static JebaitedCore instance;

    private RankManager rankManager;
    private ProfileStore profileStore;
    private DevModeManager devModeManager;
    private ModerationManager moderationManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Initialize file-backed stores/managers
        this.rankManager = new RankManager(this);
        this.profileStore = new ProfileStore(this);
        this.devModeManager = new DevModeManager(this);
        this.moderationManager = new ModerationManager(this);

        // Register listeners (chat renderer + first-join setup)
        Bukkit.getPluginManager().registerEvents(new ChatListener(this, rankManager, profileStore, moderationManager, devModeManager), this);
        Bukkit.getPluginManager().registerEvents(new JoinListener(this, rankManager, profileStore), this);
        Bukkit.getPluginManager().registerEvents(new CommandTrackingListener(profileStore, rankManager), this);
        Bukkit.getPluginManager().registerEvents(new ModerationListener(profileStore, rankManager, moderationManager, this), this);

        // Register commands
        getCommand("rank").setExecutor(new RankCommand(profileStore, rankManager, devModeManager));
        getCommand("setrank").setExecutor(new SetRankCommand(profileStore, rankManager, devModeManager));
        getCommand("tickets").setExecutor(new TicketsCommand(profileStore, rankManager, devModeManager));
        getCommand("stats").setExecutor(new StatsCommand(profileStore, rankManager));
        getCommand("devmode").setExecutor(new DevModeCommand(devModeManager));
        getCommand("jebaited").setExecutor(new JebaitedCommand(profileStore, rankManager, devModeManager));
        // Moderation
        getCommand("kick").setExecutor(new KickCommand(profileStore, rankManager, devModeManager));
        getCommand("warn").setExecutor(new WarnCommand(profileStore, rankManager, devModeManager));
        getCommand("mute").setExecutor(new MuteCommand(profileStore, rankManager, devModeManager, true));
        getCommand("tempmute").setExecutor(new MuteCommand(profileStore, rankManager, devModeManager, false));
        getCommand("unmute").setExecutor(new UnmuteCommand(profileStore, rankManager, devModeManager));
        getCommand("ban").setExecutor(new BanCommand(profileStore, rankManager, devModeManager, true));
        getCommand("tempban").setExecutor(new BanCommand(profileStore, rankManager, devModeManager, false));
        getCommand("unban").setExecutor(new UnbanCommand(profileStore, rankManager, devModeManager));
        getCommand("freeze").setExecutor(new FreezeCommand(profileStore, rankManager, devModeManager, moderationManager));
        getCommand("vanish").setExecutor(new VanishCommand(profileStore, rankManager, devModeManager, moderationManager));
        getCommand("staffchat").setExecutor(new StaffChatCommand(profileStore, rankManager, devModeManager, moderationManager));
        getCommand("clearchat").setExecutor(new ClearChatCommand(profileStore, rankManager, devModeManager));
        getCommand("slowmode").setExecutor(new SlowmodeCommand(profileStore, rankManager, devModeManager, moderationManager));
        getCommand("history").setExecutor(new HistoryCommand(profileStore, rankManager, devModeManager));

        getLogger().info("§6JebaitedCore v1.0.0 §aENABLED! §7Hub/core foundation loaded on Paper 1.21.8");
    }

    @Override
    public void onDisable() {
        // Persist any pending caches
        if (profileStore != null) profileStore.flushAll();
        getLogger().info("§cJebaitedCore §7DISABLED – All saved!");
    }

    public static JebaitedCore getInstance() { return instance; }

    public RankManager getRankManager() { return rankManager; }
    public ProfileStore getProfileStore() { return profileStore; }
    public DevModeManager getDevModeManager() { return devModeManager; }
    public ModerationManager getModerationManager() { return moderationManager; }
}