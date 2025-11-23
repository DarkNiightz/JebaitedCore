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
import com.darkniightz.core.gui.MenuListener;
import com.darkniightz.core.hub.HotbarNavigatorListener;
import com.darkniightz.core.hub.HubProtectionListener;
import com.darkniightz.core.commands.MenuCommand;
import com.darkniightz.core.cosmetics.CosmeticsManager;
import com.darkniightz.core.cosmetics.CosmeticsEngine;
import com.darkniightz.core.commands.CosmeticsCommand;
import com.darkniightz.main.PlayerProfileDAO;
import com.darkniightz.main.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;

public final class JebaitedCore extends JavaPlugin {

    private static JebaitedCore instance;

    private RankManager rankManager;
    private ProfileStore profileStore;
    private DevModeManager devModeManager;
    private ModerationManager moderationManager;
    private CosmeticsManager cosmeticsManager;
    private CosmeticsEngine cosmeticsEngine;
    private DatabaseManager databaseManager;
    private PlayerProfileDAO playerProfileDAO;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Initialize and connect to the database
        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.connect();

        // Initialize DAOs
        this.playerProfileDAO = new PlayerProfileDAO(databaseManager, getLogger());

        // Initialize file-backed stores/managers
        this.rankManager = new RankManager(this);
        this.profileStore = new ProfileStore(this);
        this.devModeManager = new DevModeManager(this);
        this.moderationManager = new ModerationManager(this);
        this.cosmeticsManager = new CosmeticsManager(this);
        this.cosmeticsEngine = new CosmeticsEngine(this, profileStore, moderationManager, rankManager);

        // Initialize database tables
        if (databaseManager.isEnabled()) {
            initializeDatabaseTables();
        }

        // Register listeners (chat renderer + first-join setup)
        Bukkit.getPluginManager().registerEvents(new ChatListener(this, rankManager, profileStore, moderationManager, devModeManager), this);
        Bukkit.getPluginManager().registerEvents(new JoinListener(this, rankManager, profileStore), this);
        Bukkit.getPluginManager().registerEvents(new CommandTrackingListener(profileStore, rankManager), this);
        Bukkit.getPluginManager().registerEvents(new ModerationListener(profileStore, rankManager, moderationManager, this), this);
        // GUI + Hub Hotbar
        Bukkit.getPluginManager().registerEvents(new MenuListener(), this);
        Bukkit.getPluginManager().registerEvents(new HotbarNavigatorListener(this), this);
        Bukkit.getPluginManager().registerEvents(new HubProtectionListener(this, profileStore, rankManager), this);
        // Start cosmetics engine (particles/trails)
        cosmeticsEngine.start();

        // Register commands
        getCommand("rank").setExecutor(new RankCommand(profileStore, rankManager, devModeManager));
        getCommand("setrank").setExecutor(new SetRankCommand(profileStore, rankManager, devModeManager));
        getCommand("tickets").setExecutor(new TicketsCommand(profileStore, rankManager, devModeManager));
        getCommand("stats").setExecutor(new StatsCommand(profileStore, rankManager));
        getCommand("devmode").setExecutor(new DevModeCommand(devModeManager));
        getCommand("jebaited").setExecutor(new JebaitedCommand(profileStore, rankManager, devModeManager));
        // Hub menu commands (aliases share executor)
        MenuCommand menuCmd = new MenuCommand(this);
        getCommand("menu").setExecutor(menuCmd);
        getCommand("servers").setExecutor(menuCmd);
        getCommand("navigator").setExecutor(menuCmd);
        // Wardrobe/cosmetics
        CosmeticsCommand cosCmd = new CosmeticsCommand(this, cosmeticsManager, profileStore);
        getCommand("cosmetics").setExecutor(cosCmd);
        getCommand("wardrobe").setExecutor(cosCmd);
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
        if (cosmeticsEngine != null) cosmeticsEngine.stop();
        // Disconnect from the database
        if (this.databaseManager != null) {
            this.databaseManager.disconnect();
        }
        getLogger().info("§cJebaitedCore §7DISABLED – All saved!");
    }

    public static JebaitedCore getInstance() { return instance; }

    public RankManager getRankManager() { return rankManager; }
    public ProfileStore getProfileStore() { return profileStore; }
    public DevModeManager getDevModeManager() { return devModeManager; }
    public ModerationManager getModerationManager() { return moderationManager; }
    public CosmeticsManager getCosmeticsManager() { return cosmeticsManager; }
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    public PlayerProfileDAO getPlayerProfileDAO() {
        return playerProfileDAO;
    }

    /**
     * Creates the necessary database tables if they don't exist.
     */
    private void initializeDatabaseTables() {
        // Using a try-with-resources statement to ensure the connection is closed automatically.
        String playersSql = "CREATE TABLE IF NOT EXISTS players (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "username VARCHAR(16) NOT NULL," +
                "rank VARCHAR(32) NOT NULL," +
                "first_joined BIGINT NOT NULL," +
                "last_joined BIGINT NOT NULL" +
                ");";

        String playerStatsSql = "CREATE TABLE IF NOT EXISTS player_stats (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "kills INT NOT NULL DEFAULT 0," +
                "deaths INT NOT NULL DEFAULT 0," +
                "commands_sent INT NOT NULL DEFAULT 0," +
                "CONSTRAINT fk_player FOREIGN KEY(uuid) REFERENCES players(uuid) ON DELETE CASCADE" +
                ");";

        String moderationHistorySql = "CREATE TABLE IF NOT EXISTS moderation_history (" +
                "id SERIAL PRIMARY KEY," +
                "target_uuid VARCHAR(36) NOT NULL," +
                "type VARCHAR(32) NOT NULL," +
                "actor VARCHAR(16)," +
                "actor_uuid VARCHAR(36)," +
                "reason VARCHAR(255)," +
                "duration_ms BIGINT," +
                "expires_at BIGINT," +
                "timestamp BIGINT NOT NULL" +
                ");";

        String playerCosmeticsSql = "CREATE TABLE IF NOT EXISTS player_cosmetics (" +
                "id SERIAL PRIMARY KEY," +
                "player_uuid VARCHAR(36) NOT NULL," +
                "cosmetic_id VARCHAR(64) NOT NULL," +
                "cosmetic_type VARCHAR(32) NOT NULL," +
                "is_active BOOLEAN NOT NULL DEFAULT false," +
                "CONSTRAINT fk_player_cosmetic FOREIGN KEY(player_uuid) REFERENCES players(uuid) ON DELETE CASCADE," +
                "UNIQUE(player_uuid, cosmetic_id, cosmetic_type)" +
                ");";

        try (Connection conn = databaseManager.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute(playersSql);
            stmt.execute(playerStatsSql);
            stmt.execute(moderationHistorySql);
            stmt.execute(playerCosmeticsSql);
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Could not create database tables!", e);
        }
    }
}