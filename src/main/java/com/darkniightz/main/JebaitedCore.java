package com.darkniightz.main;

import com.darkniightz.core.chat.ChatListener;
import com.darkniightz.core.dev.DebugFeedManager;
import com.darkniightz.core.dev.DebugStateManager;
import com.darkniightz.core.dev.DeployStatusManager;
import com.darkniightz.core.commands.CoinsCommand;
import com.darkniightz.core.commands.BalanceCommand;
import com.darkniightz.core.commands.BalanceTopCommand;
import com.darkniightz.core.commands.CosmeticsCommand;
import com.darkniightz.core.commands.DevModeCommand;
import com.darkniightz.core.commands.DelHomeCommand;
import com.darkniightz.core.commands.SetWarpCommand;
import com.darkniightz.core.commands.DelWarpCommand;
import com.darkniightz.core.commands.EcoCommand;
import com.darkniightz.core.commands.GeneratePasswordCommand;
import com.darkniightz.core.commands.HomeCommand;
import com.darkniightz.core.commands.HomesCommand;
import com.darkniightz.core.commands.HubCommand;
import com.darkniightz.core.commands.JebaitedCommand;
import com.darkniightz.core.commands.MenuCommand;
import com.darkniightz.core.commands.MaintenanceCommand;
import com.darkniightz.core.commands.MessageCommand;
import com.darkniightz.core.commands.NearCommand;
import com.darkniightz.core.commands.NotesCommand;
import com.darkniightz.core.commands.NickCommand;
import com.darkniightz.core.commands.PayCommand;
import com.darkniightz.core.commands.PreviewCommand;
import com.darkniightz.core.commands.RankCommand;
import com.darkniightz.core.commands.ReplyCommand;
import com.darkniightz.core.commands.RtpCommand;
import com.darkniightz.core.commands.RulesCommand;
import com.darkniightz.core.commands.SetHomeCommand;
import com.darkniightz.core.commands.SetDonorCommand;
import com.darkniightz.core.commands.SetRankCommand;
import com.darkniightz.core.commands.SetSpawnCommand;
import com.darkniightz.core.commands.SettingsCommand;
import com.darkniightz.core.commands.SmpCommand;
import com.darkniightz.core.commands.SpawnCommand;
import com.darkniightz.core.commands.StatsCommand;
import com.darkniightz.core.commands.WarpCommand;
import com.darkniightz.core.commands.WarpsCommand;
import com.darkniightz.core.commands.WorldStatusCommand;
import com.darkniightz.core.commands.WhoisCommand;
import com.darkniightz.core.commands.mod.*;
import com.darkniightz.core.cosmetics.CosmeticPreviewService;
import com.darkniightz.core.cosmetics.CosmeticsEngine;
import com.darkniightz.core.cosmetics.CosmeticsManager;
import com.darkniightz.core.cosmetics.OutfitManager;
import com.darkniightz.core.cosmetics.ToyboxListener;
import com.darkniightz.core.cosmetics.ToyboxManager;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.gui.MenuListener;
import com.darkniightz.core.hub.HotbarNavigatorListener;
import com.darkniightz.core.hub.HubProtectionListener;
import com.darkniightz.core.moderation.ModerationListener;
import com.darkniightz.core.moderation.ModerationManager;
import com.darkniightz.core.playerjoin.JoinListener;
import com.darkniightz.core.playerjoin.ServerListMotdListener;
import com.darkniightz.core.playerjoin.WorldChangeListener;
import com.darkniightz.core.players.PlayerSettingsListener;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.system.CombatTagListener;
import com.darkniightz.core.system.CombatTagManager;
import com.darkniightz.core.system.CommandSecurityListener;
import com.darkniightz.core.system.BroadcasterManager;
import com.darkniightz.core.system.BossBarManager;
import com.darkniightz.core.system.EconomyManager;
import com.darkniightz.core.system.EventModeChatListener;
import com.darkniightz.core.system.EventModeCombatListener;
import com.darkniightz.core.system.EventModeManager;
import com.darkniightz.core.system.EventWorldProtectionListener;
import com.darkniightz.core.system.GraveListener;
import com.darkniightz.core.system.GraveManager;
import com.darkniightz.core.system.HomesManager;
import com.darkniightz.core.system.LeaderboardManager;
import com.darkniightz.core.system.AuditLogService;
import com.darkniightz.core.system.MaintenanceManager;
import com.darkniightz.core.system.MessageManager;
import com.darkniightz.core.system.MinecraftVersionMonitor;
import com.darkniightz.core.system.NicknameManager;
import com.darkniightz.core.system.OpsAlertService;
import com.darkniightz.core.system.OverallStatsManager;
import com.darkniightz.core.system.ServerScoreboardManager;
import com.darkniightz.core.system.TagCustomizationManager;
import com.darkniightz.core.system.WarpsManager;
import com.darkniightz.core.world.PreviewPedestalManager;
import com.darkniightz.core.world.PreviewPedestalListener;
import com.darkniightz.core.world.SpawnManager;
import com.darkniightz.core.world.WorldConfigManager;
import com.darkniightz.core.world.WorldManager;
import com.darkniightz.core.tracking.CommandTrackingListener;
import com.darkniightz.core.tracking.StatsTrackingListener;
import com.darkniightz.main.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;

/**
 * Supported Paper range: 1.21.9-1.21.11.
 */
public final class JebaitedCore extends JavaPlugin {

    private static JebaitedCore instance;

    private RankManager rankManager;
    private ProfileStore profileStore;
    private DevModeManager devModeManager;
    private DebugStateManager debugStateManager;
    private DebugFeedManager debugFeedManager;
    private DeployStatusManager deployStatusManager;
    private ModerationManager moderationManager;
    private CosmeticsManager cosmeticsManager;
    private CosmeticsEngine cosmeticsEngine;
    private CosmeticPreviewService cosmeticPreviewService;
    private OutfitManager outfitManager;
    private ToyboxManager toyboxManager;
    private BroadcasterManager broadcasterManager;
    private BossBarManager bossBarManager;
    private EventModeManager eventModeManager;
    private ServerScoreboardManager scoreboardManager;
    private EconomyManager economyManager;
    private HomesManager homesManager;
    private MessageManager messageManager;
    private NicknameManager nicknameManager;
    private TagCustomizationManager tagCustomizationManager;
    private LeaderboardManager leaderboardManager;
    private CombatTagManager combatTagManager;
    private com.darkniightz.core.system.JoinPriorityManager joinPriorityManager;
    private GraveManager graveManager;
    private OverallStatsManager overallStatsManager;
    private WarpsManager warpsManager;
    private WorldConfigManager worldConfigManager;
    private WorldManager worldManager;
    private SpawnManager spawnManager;
    private PreviewPedestalManager previewPedestalManager;
    private DatabaseManager databaseManager;
    private OpsAlertService opsAlertService;
    private MinecraftVersionMonitor minecraftVersionMonitor;
    private com.darkniightz.core.system.PanelConnectorService panelConnectorService;
    private MaintenanceManager maintenanceManager;
    private AuditLogService auditLogService;
    private PlayerProfileDAO playerProfileDAO;
    private HotbarNavigatorListener hotbarNavigatorListener;
    private StatsTrackingListener statsTrackingListener;
    private com.darkniightz.core.system.RestartManager restartManager;
    private int dirtyFlushTaskId = -1;
    private int dbRetryTaskId = -1;
    private boolean dbDependentServicesStarted = false;
    private boolean dbAlertActive = false;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        this.panelConnectorService = new com.darkniightz.core.system.PanelConnectorService(this);
        this.opsAlertService = new OpsAlertService(this);
        this.minecraftVersionMonitor = new MinecraftVersionMonitor(this, this.opsAlertService);

        // Initialize and connect to the database
        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.connect();

        // Initialize DAOs
        this.playerProfileDAO = new PlayerProfileDAO(databaseManager, getLogger());

        // Initialize file-backed stores/managers
        this.rankManager = new RankManager(this);
        this.profileStore = new ProfileStore(this);
        this.maintenanceManager = new MaintenanceManager(this, databaseManager, playerProfileDAO, profileStore, rankManager);
        this.auditLogService = new AuditLogService(this, databaseManager);
        this.devModeManager = new DevModeManager(this);
        this.debugStateManager = new DebugStateManager();
        this.debugFeedManager = new DebugFeedManager(this);
        this.deployStatusManager = new DeployStatusManager(this);
        this.moderationManager = new ModerationManager(this, playerProfileDAO);
        this.worldConfigManager = new WorldConfigManager(this);
        this.worldManager = new WorldManager(this, worldConfigManager);
        this.worldManager.ensureSmpWorldLoaded();
        this.cosmeticsManager = new CosmeticsManager(this);
        this.outfitManager = new OutfitManager(this);
        this.toyboxManager = new ToyboxManager(this, profileStore, cosmeticsManager);
        this.cosmeticsEngine = new CosmeticsEngine(this, profileStore, moderationManager, rankManager);
        this.cosmeticPreviewService = new CosmeticPreviewService(this, profileStore, cosmeticsManager, cosmeticsEngine, toyboxManager);
        this.broadcasterManager = new BroadcasterManager(this);
        this.bossBarManager = new BossBarManager(this);
        this.eventModeManager = new EventModeManager(this, broadcasterManager, bossBarManager);
        this.spawnManager = new SpawnManager(this, worldManager, worldConfigManager);
        this.economyManager = new EconomyManager(this, profileStore, rankManager);
        this.homesManager = new HomesManager(this);
        this.messageManager = new MessageManager();
        this.nicknameManager = new NicknameManager(this);
        this.tagCustomizationManager = new TagCustomizationManager(this);
        this.leaderboardManager = new LeaderboardManager(this);
        this.combatTagManager = new CombatTagManager();
        this.joinPriorityManager = new com.darkniightz.core.system.JoinPriorityManager(this, profileStore, rankManager);
        this.graveManager = new GraveManager(this);
        this.overallStatsManager = new OverallStatsManager(databaseManager, getLogger());
        this.warpsManager = new WarpsManager(this);
        this.previewPedestalManager = new PreviewPedestalManager(this);
        this.scoreboardManager = new ServerScoreboardManager(this, profileStore, rankManager, worldManager);

        if (databaseManager.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    initializeDatabaseTables();
                } catch (Exception e) {
                    getLogger().log(Level.SEVERE, "Could not create database tables!", e);
                } finally {
                    Bukkit.getScheduler().runTask(this, this::finishEnable);
                }
            });
        } else {
            finishEnable();
        }

    }

    @Override
    public void onDisable() {
        if (dirtyFlushTaskId != -1) {
            Bukkit.getScheduler().cancelTask(dirtyFlushTaskId);
            dirtyFlushTaskId = -1;
        }
        if (dbRetryTaskId != -1) {
            Bukkit.getScheduler().cancelTask(dbRetryTaskId);
            dbRetryTaskId = -1;
        }
        stopRuntimeServices();
        if (profileStore != null) profileStore.flushAll();
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
    public DebugStateManager getDebugStateManager() { return debugStateManager; }
    public DebugFeedManager getDebugFeedManager() { return debugFeedManager; }
    public DeployStatusManager getDeployStatusManager() { return deployStatusManager; }
    public ModerationManager getModerationManager() { return moderationManager; }
    public CosmeticsManager getCosmeticsManager() { return cosmeticsManager; }
    public CosmeticsEngine getCosmeticsEngine() { return cosmeticsEngine; }
    public CosmeticPreviewService getCosmeticPreviewService() { return cosmeticPreviewService; }
    public OutfitManager getOutfitManager() { return outfitManager; }
    public ToyboxManager getToyboxManager() { return toyboxManager; }
    public BroadcasterManager getBroadcasterManager() { return broadcasterManager; }
    public BossBarManager getBossBarManager() { return bossBarManager; }
    public EventModeManager getEventModeManager() { return eventModeManager; }
    public ServerScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public HomesManager getHomesManager() { return homesManager; }
    public MessageManager getMessageManager() { return messageManager; }
    public NicknameManager getNicknameManager() { return nicknameManager; }
    public TagCustomizationManager getTagCustomizationManager() { return tagCustomizationManager; }
    public LeaderboardManager getLeaderboardManager() { return leaderboardManager; }
    public CombatTagManager getCombatTagManager() { return combatTagManager; }
    public GraveManager getGraveManager() { return graveManager; }
    public OverallStatsManager getOverallStatsManager() { return overallStatsManager; }
    public WarpsManager getWarpsManager() { return warpsManager; }
    public WorldConfigManager getWorldConfigManager() { return worldConfigManager; }
    public WorldManager getWorldManager() { return worldManager; }
    public SpawnManager getSpawnManager() { return spawnManager; }
    public PreviewPedestalManager getPreviewPedestalManager() { return previewPedestalManager; }
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    public MaintenanceManager getMaintenanceManager() { return maintenanceManager; }
    public AuditLogService getAuditLogService() { return auditLogService; }
    public PlayerProfileDAO getPlayerProfileDAO() {
        return playerProfileDAO;
    }
    public OpsAlertService getOpsAlertService() { return opsAlertService; }
    public MinecraftVersionMonitor getMinecraftVersionMonitor() { return minecraftVersionMonitor; }
    public com.darkniightz.core.system.PanelConnectorService getPanelConnectorService() { return panelConnectorService; }
    public com.darkniightz.core.system.RestartManager getRestartManager() { return restartManager; }

    private void initializeDatabaseTables() {
        new com.darkniightz.main.database.SchemaManager(databaseManager, getLogger()).runMigrations();
    }

    private void finishEnable() {
        registerListeners();
        startRuntimeServices();
        registerCommands();
        startBackgroundFlushTask();
        logStartupSummary();
        getLogger().info("JebaitedCore enabled.");
    }

    // ----- Internal helpers for registration -----
    private void registerListeners() {
        // Chat renderer + join handling
        Bukkit.getPluginManager().registerEvents(new ChatListener(this, rankManager, profileStore, moderationManager, devModeManager, nicknameManager), this);
        Bukkit.getPluginManager().registerEvents(new JoinListener(this, rankManager, profileStore, playerProfileDAO, debugFeedManager), this);
        if (auditLogService != null) Bukkit.getPluginManager().registerEvents(auditLogService, this);
        if (maintenanceManager != null) Bukkit.getPluginManager().registerEvents(maintenanceManager, this);
        Bukkit.getPluginManager().registerEvents(new CommandTrackingListener(profileStore, rankManager, debugFeedManager, overallStatsManager), this);
        this.statsTrackingListener = new StatsTrackingListener(this, profileStore, rankManager);
        Bukkit.getPluginManager().registerEvents(statsTrackingListener, this);
        Bukkit.getPluginManager().registerEvents(new ModerationListener(profileStore, rankManager, moderationManager, this, debugFeedManager), this);
        // GUI + Hub Hotbar
        Bukkit.getPluginManager().registerEvents(new MenuListener(), this);
        this.hotbarNavigatorListener = new HotbarNavigatorListener(this);
        Bukkit.getPluginManager().registerEvents(hotbarNavigatorListener, this);
        Bukkit.getPluginManager().registerEvents(new ToyboxListener(this, profileStore, toyboxManager, debugFeedManager), this);
        Bukkit.getPluginManager().registerEvents(new HubProtectionListener(this, profileStore, rankManager, spawnManager), this);
        Bukkit.getPluginManager().registerEvents(new ServerListMotdListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PreviewPedestalListener(this, previewPedestalManager, cosmeticPreviewService, cosmeticsManager, profileStore, toyboxManager), this);
        Bukkit.getPluginManager().registerEvents(new WorldChangeListener(this, worldManager, spawnManager, cosmeticsEngine, toyboxManager, hotbarNavigatorListener), this);
        Bukkit.getPluginManager().registerEvents(new EventModeChatListener(this, eventModeManager), this);
        Bukkit.getPluginManager().registerEvents(new EventModeCombatListener(eventModeManager), this);
        Bukkit.getPluginManager().registerEvents(new EventWorldProtectionListener(this, eventModeManager), this);
        Bukkit.getPluginManager().registerEvents(new CommandSecurityListener(this, profileStore, rankManager, devModeManager), this);
        Bukkit.getPluginManager().registerEvents(new CombatTagListener(this, combatTagManager), this);
        Bukkit.getPluginManager().registerEvents(new com.darkniightz.core.playerjoin.PriorityJoinListener(this, joinPriorityManager), this);
        Bukkit.getPluginManager().registerEvents(new GraveListener(this, graveManager), this);
        Bukkit.getPluginManager().registerEvents(new PlayerSettingsListener(this), this);
    }

    private void startRuntimeServices() {
        if (outfitManager != null) outfitManager.start();
        if (cosmeticsEngine != null) cosmeticsEngine.start();
        if (broadcasterManager != null) broadcasterManager.start();
        if (bossBarManager != null) bossBarManager.start();
        if (eventModeManager != null) eventModeManager.start();
        if (scoreboardManager != null) scoreboardManager.start();
        if (maintenanceManager != null) maintenanceManager.start();
        if (auditLogService != null) auditLogService.start();
        if (minecraftVersionMonitor != null) minecraftVersionMonitor.start();
        this.restartManager = new com.darkniightz.core.system.RestartManager(this);
        restartManager.startScheduled();
        if (!startDbDependentServicesIfReady()) {
            scheduleDbDependentRetry();
        }
        if (graveManager != null) graveManager.start();
        if (toyboxManager != null) refreshToyboxesForOnline();
        applyStyledTabForOnline();
    }

    private void stopRuntimeServices() {
        if (eventModeManager != null) eventModeManager.stop();
        if (statsTrackingListener != null) statsTrackingListener.flushOnlinePlaytime();
        if (outfitManager != null) outfitManager.stop();
        if (cosmeticsEngine != null) cosmeticsEngine.stop();
        if (toyboxManager != null) toyboxManager.stop();
        if (broadcasterManager != null) broadcasterManager.stop();
        if (bossBarManager != null) bossBarManager.stop();
        if (scoreboardManager != null) scoreboardManager.stop();
        if (leaderboardManager != null) leaderboardManager.stop();
        if (auditLogService != null) auditLogService.stop();
        if (minecraftVersionMonitor != null) minecraftVersionMonitor.stop();
        if (graveManager != null) graveManager.stop();
        if (restartManager != null) restartManager.shutdown();
        if (dbRetryTaskId != -1) {
            Bukkit.getScheduler().cancelTask(dbRetryTaskId);
            dbRetryTaskId = -1;
        }
        dbDependentServicesStarted = false;
        dbAlertActive = false;
    }

    private void registerCommands() {
        // Core/help
        bindCommand("rank", new RankCommand(profileStore, rankManager, devModeManager));
        bindCommand("setrank", new SetRankCommand(profileStore, rankManager, devModeManager));
        bindCommand("setdonor", new SetDonorCommand(profileStore, rankManager, devModeManager));
        bindCommand("togglerank", new com.darkniightz.core.commands.ToggleRankCommand(profileStore, rankManager));
        bindCommand("coins", new CoinsCommand(profileStore, rankManager, devModeManager));
        BalanceCommand balanceCommand = new BalanceCommand(economyManager, profileStore, rankManager, devModeManager);
        bindCommand("balance", balanceCommand);
        bindCommand("pay", new PayCommand(economyManager));
        bindCommand("eco", new EcoCommand(economyManager, profileStore, rankManager, devModeManager));
        BalanceTopCommand balanceTopCommand = new BalanceTopCommand(profileStore, economyManager);
        bindCommand("balancetop", balanceTopCommand);
        bindCommand("sethome", new SetHomeCommand(this, homesManager, profileStore, rankManager));
        bindCommand("home", new HomeCommand(this, homesManager));
        bindCommand("delhome", new DelHomeCommand(this, homesManager));
        bindCommand("homes", new HomesCommand(this, homesManager));
        bindCommand("nick", new NickCommand(this, profileStore, rankManager, devModeManager, nicknameManager));
        bindCommand("tag", new com.darkniightz.core.commands.TagCommand(this, profileStore, rankManager, tagCustomizationManager));
        bindCommand("whois", new WhoisCommand(profileStore, rankManager, devModeManager, economyManager, nicknameManager));
        bindCommand("leaderboard", new com.darkniightz.core.commands.LeaderboardCommand(leaderboardManager, profileStore, rankManager, devModeManager));
        bindCommand("trade", new com.darkniightz.core.commands.TradeCommand(this));
        bindCommand("grave", new com.darkniightz.core.commands.GraveCommand(graveManager));
        bindCommand("graves", new com.darkniightz.core.commands.GraveCommand(graveManager));
        bindCommand("near", new NearCommand(this));
        bindCommand("rules", new RulesCommand(this));
        bindCommand("rtp", new RtpCommand(this, worldManager));
        MessageCommand messageCommand = new MessageCommand(profileStore, rankManager, messageManager);
        bindCommand("message", messageCommand);
        ReplyCommand replyCommand = new ReplyCommand(profileStore, rankManager, messageManager);
        bindCommand("reply", replyCommand);
        bindCommand("maintenance", new MaintenanceCommand(maintenanceManager, profileStore, rankManager, devModeManager));
        bindCommand("notes", new NotesCommand(playerProfileDAO, profileStore, rankManager, devModeManager));
        bindCommand("warp", new WarpCommand(this, warpsManager, economyManager));
        bindCommand("warps", new WarpsCommand(warpsManager, economyManager));
        bindCommand("setwarp", new SetWarpCommand(this, warpsManager, profileStore, rankManager, devModeManager));
        bindCommand("delwarp", new DelWarpCommand(this, warpsManager, profileStore, rankManager, devModeManager));
        bindCommand("stats", new StatsCommand(this, profileStore, rankManager, devModeManager));
        bindCommand("settings", new SettingsCommand(this));
        // Backwards-compatible alias: coin -> coins
        bindCommand("coin", new CoinsCommand(profileStore, rankManager, devModeManager));
        bindCommand("devmode", new DevModeCommand(devModeManager));
        JebaitedCommand helpCmd = new JebaitedCommand(profileStore, rankManager, devModeManager, worldManager);
        bindCommand("jebaited", helpCmd);
        bindCommand("help", helpCmd);
        bindCommand("spawn", new SpawnCommand(this, spawnManager, profileStore, rankManager, devModeManager));
        bindCommand("hub", new HubCommand(this, worldManager, spawnManager));
        bindCommand("smp", new SmpCommand(this, worldManager, spawnManager));
        bindCommand("worldstatus", new WorldStatusCommand(worldManager, profileStore, rankManager, devModeManager));
        bindCommand("compat", new com.darkniightz.core.commands.CompatReportCommand(this, worldManager, spawnManager, profileStore, rankManager, devModeManager));
        bindCommand("setspawn", new SetSpawnCommand(this, spawnManager, profileStore, rankManager, devModeManager));
        bindCommand("loadout", new com.darkniightz.core.commands.LoadoutCommand(profileStore, rankManager, toyboxManager, devModeManager, worldManager));
        bindCommand("previewpedestal", new com.darkniightz.core.commands.PreviewPedestalCommand(this, previewPedestalManager, profileStore, rankManager, devModeManager, worldManager));
        bindCommand("preview", new PreviewCommand(this, cosmeticsManager, profileStore, cosmeticPreviewService, toyboxManager, worldManager));
        com.darkniightz.core.commands.EventModeCommand eventCommand =
            new com.darkniightz.core.commands.EventModeCommand(this, eventModeManager, profileStore, rankManager, devModeManager);
        bindCommand("event", eventCommand);
        bindCommand("eventmode", eventCommand);
        // Reload
        bindCommand("jreload", new com.darkniightz.core.commands.ReloadCommand(this, profileStore, rankManager, devModeManager));
        // Hub menu commands (aliases share executor)
        MenuCommand menuCmd = new MenuCommand(this);
        bindCommand("menu", menuCmd);
        bindCommand("servers", menuCmd);
        bindCommand("navigator", menuCmd);
        // Wardrobe/cosmetics
        CosmeticsCommand cosCmd = new CosmeticsCommand(this, cosmeticsManager, profileStore, toyboxManager, cosmeticPreviewService, devModeManager, worldManager);
        bindCommand("cosmetics", cosCmd);
        bindCommand("wardrobe", cosCmd);
        bindCommand("debug", new com.darkniightz.core.commands.DebugCommand(this));
        bindCommand("devdebug", new com.darkniightz.core.commands.DebugCommand(this));
        // Moderation
        bindCommand("generatepassword", new GeneratePasswordCommand(profileStore, rankManager, devModeManager));
        bindCommand("restart", new com.darkniightz.core.commands.mod.RestartCommand(restartManager, profileStore, rankManager, devModeManager));
        bindCommand("kick", new KickCommand(profileStore, rankManager, devModeManager));
        bindCommand("warn", new WarnCommand(this, profileStore, rankManager, devModeManager));
        bindCommand("mute", new MuteCommand(this, profileStore, rankManager, devModeManager, true));
        bindCommand("tempmute", new MuteCommand(this, profileStore, rankManager, devModeManager, false));
        bindCommand("unmute", new UnmuteCommand(profileStore, rankManager, devModeManager));
        bindCommand("ban", new BanCommand(this, profileStore, rankManager, devModeManager, true));
        bindCommand("tempban", new BanCommand(this, profileStore, rankManager, devModeManager, false));
        bindCommand("unban", new UnbanCommand(profileStore, rankManager, devModeManager));
        bindCommand("freeze", new FreezeCommand(profileStore, rankManager, devModeManager, moderationManager));
        bindCommand("vanish", new VanishCommand(profileStore, rankManager, devModeManager, moderationManager));
        bindCommand("staffchat", new StaffChatCommand(profileStore, rankManager, devModeManager, moderationManager));
        bindCommand("clearchat", new ClearChatCommand(profileStore, rankManager, devModeManager));
        bindCommand("slowmode", new SlowmodeCommand(profileStore, rankManager, devModeManager, moderationManager));
        bindCommand("history", new HistoryCommand(profileStore, rankManager, devModeManager));
    }

    private void bindCommand(String name, CommandExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().severe("Command '" + name + "' is not declared in plugin.yml; executor not bound.");
            return;
        }
        cmd.setExecutor(executor);
        if (executor instanceof TabCompleter tc) cmd.setTabCompleter(tc);
    }

    private void startBackgroundFlushTask() {
        if (profileStore == null || !databaseManager.isEnabled()) return;
        if (dirtyFlushTaskId != -1) Bukkit.getScheduler().cancelTask(dirtyFlushTaskId);

        long periodTicks = Math.max(20L, getConfig().getLong("database.flush_dirty_every_ticks", 100L));
        dirtyFlushTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(this,
                () -> {
                    try {
                        profileStore.flushDirty();
                    } catch (Exception e) {
                        getLogger().log(Level.WARNING, "Failed to flush dirty profiles.", e);
                    }
                },
                periodTicks,
                periodTicks).getTaskId();
    }

    /**
     * Reloads config-driven components and refreshes cached player profiles from the database.
     * Restricted by command side, but callable from API too.
     */
    public void reloadCore() {
        // Persist current cache before reloading
        if (profileStore != null) profileStore.flushAll();

        // Reload config from disk
        reloadConfig();

        // Re-create config-driven managers
        this.rankManager = new RankManager(this);
        this.devModeManager = new DevModeManager(this);
        this.worldConfigManager = new WorldConfigManager(this);
        this.worldManager = new WorldManager(this, worldConfigManager);
        this.worldManager.ensureSmpWorldLoaded();
        // Warn if worlds named in config are not loaded — NPE source for cosmetics/spawns
        String _hubName = worldConfigManager.getHubWorldName();
        if (org.bukkit.Bukkit.getWorld(_hubName) == null)
            getLogger().warning("[Reload] Hub world '" + _hubName + "' is not loaded — cosmetics and spawns may fail. Check worlds.hub in config.yml");
        String _smpName = worldConfigManager.getSmpWorldName();
        if (org.bukkit.Bukkit.getWorld(_smpName) == null)
            getLogger().warning("[Reload] SMP world '" + _smpName + "' is not loaded — SMP features may fail. Check worlds.smp in config.yml");
        this.spawnManager = new SpawnManager(this, worldManager, worldConfigManager);
        this.economyManager = new EconomyManager(this, profileStore, rankManager);
        this.homesManager = new HomesManager(this);
        this.messageManager = new MessageManager();
        this.nicknameManager = new NicknameManager(this);
        this.tagCustomizationManager = new TagCustomizationManager(this);
        this.leaderboardManager = new LeaderboardManager(this);
        this.combatTagManager = new CombatTagManager();
        this.joinPriorityManager = new com.darkniightz.core.system.JoinPriorityManager(this, profileStore, rankManager);
        this.graveManager = new GraveManager(this);
        this.overallStatsManager = new OverallStatsManager(databaseManager, getLogger());
        this.warpsManager = new WarpsManager(this);
        this.scoreboardManager = new ServerScoreboardManager(this, profileStore, rankManager, worldManager);
        this.dbDependentServicesStarted = false;
        // Keep existing moderation state; no need to recreate moderationManager
        stopRuntimeServices();
        if (dirtyFlushTaskId != -1) {
            Bukkit.getScheduler().cancelTask(dirtyFlushTaskId);
            dirtyFlushTaskId = -1;
        }
        if (dbRetryTaskId != -1) {
            Bukkit.getScheduler().cancelTask(dbRetryTaskId);
            dbRetryTaskId = -1;
        }

        if (debugFeedManager != null) {
            debugFeedManager.recordSystem("Core reloaded", java.util.List.of("§7Config, listeners, commands, and caches have been refreshed."));
        }

        // Refresh cache from DB for all online players using the new default group
        if (profileStore != null) {
            profileStore.reloadOnlineFromDatabase(rankManager.getDefaultGroup());
        }

        // Re-register listeners with updated manager instances
        org.bukkit.event.HandlerList.unregisterAll(this);
        registerListeners();
        startRuntimeServices();
        // Re-register commands so executors carry updated manager references
        registerCommands();
        startBackgroundFlushTask();

        getLogger().info("JebaitedCore reloaded: config, listeners, commands, and caches have been refreshed.");
    }

    private void applyStyledTabForOnline() {
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            refreshPlayerPresentation(p);
        }
    }

    public void refreshPlayerPresentation(org.bukkit.entity.Player player) {
        if (player == null) return;
        try {
            final var legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();
            var prof = profileStore.getOrCreate(player, rankManager.getDefaultGroup());
            String tab = safeLegacyDisplay(buildTabDisplay(player, prof), 64);
            player.playerListName(legacy.deserialize(tab));
            player.setPlayerListName(tab);
            if (scoreboardManager != null) {
                scoreboardManager.refreshPlayer(player);
            }
        } catch (RuntimeException ex) {
            getLogger().warning("Failed to refresh player presentation for " + player.getName() + ": " + ex.getMessage());
        }
    }

    public void refreshAllPlayerPresentations() {
        for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            refreshPlayerPresentation(player);
        }
    }

    public String buildChatPrefixWithTag(com.darkniightz.core.players.PlayerProfile profile, String rankPrefix) {
        return renderTag(profile, false, com.darkniightz.core.cosmetics.CosmeticsManager.TagPosition.PREFIX)
                + (rankPrefix == null ? "" : rankPrefix);
    }

    public String decorateStyledNameWithTag(com.darkniightz.core.players.PlayerProfile profile, String styledName, boolean tabContext) {
        String base = styledName == null ? "" : styledName;
        String prefix = renderTag(profile, tabContext, com.darkniightz.core.cosmetics.CosmeticsManager.TagPosition.PREFIX);
        String suffix = renderTag(profile, tabContext, com.darkniightz.core.cosmetics.CosmeticsManager.TagPosition.SUFFIX);
        return prefix + base + suffix;
    }

    private String buildTabDisplay(org.bukkit.entity.Player player, com.darkniightz.core.players.PlayerProfile profile) {
        String rank = profile == null || profile.getDisplayRank() == null ? rankManager.getDefaultGroup() : profile.getDisplayRank();
        var style = rankManager.getStyle(rank);
        String baseName = nicknameManager == null ? player.getName() : nicknameManager.displayName(player.getName(), player.getUniqueId());
        String styledName = com.darkniightz.core.chat.ChatUtil.buildStyledName(baseName, style);
        styledName = decorateStyledNameWithTag(profile, styledName, true);
        String prefix = (style.prefix == null || style.prefix.isEmpty()) ? "" : style.prefix + " ";
        return safeLegacyDisplay(prefix + styledName, 64);
    }

    private String safeLegacyDisplay(String raw, int maxLength) {
        if (raw == null) {
            return "";
        }
        if (raw.length() <= maxLength) {
            return raw;
        }
        return raw.substring(0, Math.max(0, maxLength));
    }

    private String renderTag(com.darkniightz.core.players.PlayerProfile profile, boolean tabContext, com.darkniightz.core.cosmetics.CosmeticsManager.TagPosition position) {
        if (!getConfig().getBoolean("tags.enabled", true)) {
            return "";
        }
        String tag = resolveActiveTagLabel(profile);
        if (tag == null || tag.isBlank()) {
            return "";
        }
        var resolvedPosition = resolveActiveTagPosition(profile);
        if (resolvedPosition != position) {
            return "";
        }

        String key = tabContext
                ? (position == com.darkniightz.core.cosmetics.CosmeticsManager.TagPosition.PREFIX ? "tags.prefix.tab_format" : "tags.suffix.tab_format")
                : (position == com.darkniightz.core.cosmetics.CosmeticsManager.TagPosition.PREFIX ? "tags.prefix.chat_format" : "tags.suffix.chat_format");

        String fallback = position == com.darkniightz.core.cosmetics.CosmeticsManager.TagPosition.PREFIX
                ? (tabContext ? "§8[§d{tag}§8] " : "§8[§d{tag}§8] ")
                : (tabContext ? " §8[§d{tag}§8]" : " §8[§d{tag}§8]");
        String format = getConfig().getString(key, fallback);
        return format == null ? "" : format.replace("{tag}", tag);
    }

    private String resolveActiveTagLabel(com.darkniightz.core.players.PlayerProfile profile) {
        if (profile == null || profile.getActiveTag() == null || profile.getActiveTag().isBlank()) {
            return null;
        }
        String key = profile.getActiveTag();
        if (cosmeticsManager != null) {
            var cosmetic = cosmeticsManager.get(key);
            if (cosmetic != null) {
                if (com.darkniightz.core.system.TagCustomizationManager.CUSTOM_TAG_KEY.equalsIgnoreCase(cosmetic.key)
                        && tagCustomizationManager != null
                        && profile.getUuid() != null) {
                    String custom = tagCustomizationManager.getCustomTag(profile.getUuid());
                    if (custom != null && !custom.isBlank()) {
                        return custom;
                    }
                    return null;
                }
                return org.bukkit.ChatColor.stripColor(cosmetic.name);
            }
        }
        return key;
    }

    private com.darkniightz.core.cosmetics.CosmeticsManager.TagPosition resolveActiveTagPosition(com.darkniightz.core.players.PlayerProfile profile) {
        if (profile == null || profile.getActiveTag() == null || profile.getActiveTag().isBlank()) {
            return com.darkniightz.core.cosmetics.CosmeticsManager.TagPosition.PREFIX;
        }
        if (cosmeticsManager != null) {
            var cosmetic = cosmeticsManager.get(profile.getActiveTag());
            if (cosmetic != null && cosmetic.category == com.darkniightz.core.cosmetics.CosmeticsManager.Category.TAGS) {
                return cosmetic.tagPosition;
            }
        }
        String fallback = getConfig().getString("tags.default_position", "prefix");
        return "suffix".equalsIgnoreCase(fallback)
                ? com.darkniightz.core.cosmetics.CosmeticsManager.TagPosition.SUFFIX
                : com.darkniightz.core.cosmetics.CosmeticsManager.TagPosition.PREFIX;
    }

    private void refreshToyboxesForOnline() {
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (worldManager != null && worldManager.isHub(p)) {
                toyboxManager.refresh(p);
            } else {
                toyboxManager.clear(p);
            }
        }
    }

    private boolean startDbDependentServicesIfReady() {
        if (dbDependentServicesStarted) {
            return true;
        }
        if (databaseManager == null || !databaseManager.isEnabled()) {
            return false;
        }
        if (!databaseManager.canAcquireConnection()) {
            return false;
        }
        try {
            moderationManager.loadPersistentState();
        } catch (Exception ex) {
            getLogger().log(Level.WARNING, "Could not load moderation state; will retry.", ex);
            return false;
        }
        if (leaderboardManager != null) {
            leaderboardManager.start();
        }
        dbDependentServicesStarted = true;
        if (dbAlertActive && opsAlertService != null) {
            opsAlertService.sendAsync("Database recovered", "DB-dependent services are now active.");
        }
        dbAlertActive = false;
        getLogger().info("Database-dependent services are active.");
        return true;
    }

    private void scheduleDbDependentRetry() {
        if (dbRetryTaskId != -1 || databaseManager == null || !databaseManager.isEnabled()) {
            return;
        }
        getLogger().warning("Database not ready yet; DB-dependent services will retry every 10s.");
        if (!dbAlertActive && opsAlertService != null) {
            dbAlertActive = true;
            String detail = databaseManager.getLastConnectError();
            opsAlertService.sendAsync("Database not ready", detail == null || detail.isBlank() ? "Retry loop started." : detail);
        }
        dbRetryTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (databaseManager != null && databaseManager.canAcquireConnection()) {
                Bukkit.getScheduler().runTask(this, () -> {
                    if (startDbDependentServicesIfReady() && dbRetryTaskId != -1) {
                        Bukkit.getScheduler().cancelTask(dbRetryTaskId);
                        dbRetryTaskId = -1;
                    }
                });
            }
        }, 200L, 200L).getTaskId();
    }

    private void logStartupSummary() {
        String hubName = worldManager == null ? "world" : worldManager.getHubWorldName();
        String smpName = worldManager == null ? "smp" : worldManager.getSmpWorldName();
        boolean hubLoaded = Bukkit.getWorld(hubName) != null;
        boolean smpLoaded = Bukkit.getWorld(smpName) != null;
        boolean dbEnabled = databaseManager != null && databaseManager.isEnabled();
        boolean dbReady = databaseManager != null && databaseManager.canAcquireConnection();
        String currentMc = Bukkit.getMinecraftVersion();
        String latestKnownMc = minecraftVersionMonitor == null ? currentMc : minecraftVersionMonitor.getLatestKnownVersion();
        String versionState = minecraftVersionMonitor != null && minecraftVersionMonitor.isOutdated() ? "update-available" : "ok";
        getLogger().info("Startup health | db=" + (dbEnabled ? (dbReady ? "ready" : "deferred") : "disabled")
                + " | hub=" + hubName + ":" + (hubLoaded ? "loaded" : "missing")
                + " | smp=" + smpName + ":" + (smpLoaded ? "loaded" : "missing")
                + " | db-services=" + (dbDependentServicesStarted ? "active" : "pending")
                + " | mc=" + currentMc + "->" + latestKnownMc + ":" + versionState);
    }
}
