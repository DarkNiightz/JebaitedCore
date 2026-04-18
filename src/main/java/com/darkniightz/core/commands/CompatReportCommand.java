package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.system.McMMOIntegration;
import com.darkniightz.core.world.SpawnManager;
import com.darkniightz.core.world.WorldManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class CompatReportCommand implements CommandExecutor {
    private final JebaitedCore core;
    private final Plugin plugin;
    private final WorldManager worldManager;
    private final SpawnManager spawnManager;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;

    public CompatReportCommand(Plugin plugin,
                               WorldManager worldManager,
                               SpawnManager spawnManager,
                               ProfileStore profiles,
                               RankManager ranks,
                               DevModeManager devMode) {
        this.core = plugin instanceof JebaitedCore jc ? jc : null;
        this.plugin = plugin;
        this.worldManager = worldManager;
        this.spawnManager = spawnManager;
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (sender instanceof Player player) {
            var profile = profiles.getOrCreate(player, ranks.getDefaultGroup());
            boolean bypass = devMode != null && devMode.isActive(player.getUniqueId());
            if (!bypass && !ranks.isAtLeast(profile.getPrimaryRank(), "admin")) {
                sender.sendMessage(Messages.noPerm());
                return true;
            }
        }

        String apiTarget = plugin.getDescription().getAPIVersion();
        if (apiTarget == null || apiTarget.isBlank()) {
            apiTarget = "unspecified";
        }

        World hub = Bukkit.getWorld(worldManager.getHubWorldName());
        World smp = Bukkit.getWorld(worldManager.getSmpWorldName());
        boolean smpAutoCreate = plugin.getConfig().getBoolean("worlds.smp_settings.create_if_missing", true);
        boolean hasHubSpawn = spawnManager.getSpawnForWorld(worldManager.getHubWorldName()) != null;
        boolean hasSmpSpawn = spawnManager.getSpawnForWorld(worldManager.getSmpWorldName()) != null;
        boolean dbEnabled = plugin.getConfig().getBoolean("database.enabled", true);
        boolean dbReady = core != null && core.getDatabaseManager() != null && core.getDatabaseManager().canAcquireConnection();
        String dbError = core != null && core.getDatabaseManager() != null ? core.getDatabaseManager().getLastConnectError() : "";

        java.io.File worldContainer = Bukkit.getWorldContainer();
        java.io.File hubFolder = new java.io.File(worldContainer, worldManager.getHubWorldName());
        java.io.File smpFolder = new java.io.File(worldContainer, worldManager.getSmpWorldName());
        java.io.File legacyPlayers = new java.io.File(hubFolder, "players");
        java.io.File unknownPlayers = new java.io.File(hubFolder, "unknownplayers");
        java.io.File backupDir = new java.io.File(worldContainer, "backups");
        java.io.File latestWorldBackup = latestBackup(backupDir, "worlds-");
        java.io.File latestDbBackup = latestBackup(backupDir, "db-");

        sender.sendMessage(Messages.prefixed("§6§lCompatibility Report"));
        sender.sendMessage(Messages.prefixed("§7Plugin: §f" + plugin.getDescription().getVersion() + " §8| §7API target: §f" + apiTarget));
        sender.sendMessage(Messages.prefixed("§7Server: §f" + Bukkit.getName() + " §8| §7Bukkit: §f" + Bukkit.getBukkitVersion()));
        sender.sendMessage(Messages.prefixed("§7Minecraft: §f" + Bukkit.getMinecraftVersion() + " §8| §7Java: §f" + System.getProperty("java.version")));
        sender.sendMessage(Messages.prefixed("§7Target family: §f" + plugin.getConfig().getString("minecraft_support.target_family", "1.21.x")
                + " §8| §7minimum runtime: §f" + plugin.getConfig().getString("minecraft_support.minimum_runtime_family", "1.21.x")));
        if (core != null && core.getMinecraftVersionMonitor() != null) {
            String status = core.getMinecraftVersionMonitor().isOutdated() ? "§eUPDATE AVAILABLE" : "§aUP TO DATE";
            sender.sendMessage(Messages.prefixed("§7Latest known MC: §f" + core.getMinecraftVersionMonitor().getLatestKnownVersion() + " §8| §7status: " + status));
        }

        sender.sendMessage(Messages.prefixed("§8- §bWorld readiness"));
        sender.sendMessage(Messages.prefixed("§7Hub world §8(" + worldManager.getHubWorldName() + "): " + mark(hub != null)
                + " §8| §7spawn: " + mark(hasHubSpawn)));
        sender.sendMessage(Messages.prefixed("§7SMP world §8(" + worldManager.getSmpWorldName() + "): " + mark(smp != null)
                + " §8| §7spawn: " + mark(hasSmpSpawn)
                + " §8| §7auto-create: " + mark(smpAutoCreate)));
        sender.sendMessage(Messages.prefixed("§7Hub folder: " + mark(hubFolder.exists()) + " §8| §7SMP folder: " + mark(smpFolder.exists())));
        sender.sendMessage(Messages.prefixed("§7Legacy world/players: " + mark(!legacyPlayers.exists())
            + " §8| §7world/unknownplayers dir: " + mark(!unknownPlayers.exists() || unknownPlayers.isDirectory())));

        sender.sendMessage(Messages.prefixed("§8- §bDatabase"));
        sender.sendMessage(Messages.prefixed("§7enabled: " + mark(dbEnabled) + " §8| §7ready: " + mark(dbReady)));
        if (dbEnabled && !dbReady && dbError != null && !dbError.isBlank()) {
            sender.sendMessage(Messages.prefixed("§7last error: §c" + dbError));
        }

        sender.sendMessage(Messages.prefixed("§8- §bBackups"));
        sender.sendMessage(Messages.prefixed("§7World backup: " + backupStatus(latestWorldBackup)));
        sender.sendMessage(Messages.prefixed("§7DB backup: " + backupStatus(latestDbBackup)));

        sender.sendMessage(Messages.prefixed("§8- §bOptional integrations"));
        sender.sendMessage(Messages.prefixed("§7Vault: " + pluginMark("Vault")
                + " §8| §7PlaceholderAPI: " + pluginMark("PlaceholderAPI")
                + " §8| §7LuckPerms: " + pluginMark("LuckPerms")));
        sender.sendMessage(Messages.prefixed("§7ViaVersion: " + pluginMark("ViaVersion")
            + " §8| §7ProtocolLib: " + pluginMark("ProtocolLib")
            + " §8| §7mcMMO: " + mcMmoStatus()));
        sender.sendMessage(Messages.prefixed("§7mcMMO bridge (getPowerLevel): " + McMMOIntegration.compatPowerLevelBridgeSummary()));
        boolean selfTest = plugin.getConfig().getBoolean("integrations.mcmmo.bridge_self_test", false);
        sender.sendMessage(Messages.prefixed("§7integrations.mcmmo.bridge_self_test: " + (selfTest ? "§aON §8(logs at enable)" : "§7off")));
        sender.sendMessage(Messages.prefixed("§7EconomyShopGUI: " + pluginMark("EconomyShopGUI")
                + " §8| §7AuctionHouse: " + pluginMark("AuctionHouse")
                + " §8| §7Essentials: " + pluginMark("Essentials")));

        sender.sendMessage(Messages.prefixed("§8Tip: Use this after startup or reload to verify SMP readiness quickly."));
        sender.sendMessage(Messages.prefixed("§8mcMMO: Confirm hologram/profile §7mcmmo_level§8, §7/party§8 + §7/p§8 tabs, §7/mcstats§8, §7/mctop§8, §7/inspect§8, §7/mcrank§8 on staging."));
        return true;
    }

    private String pluginMark(String pluginName) {
        return mark(Bukkit.getPluginManager().isPluginEnabled(pluginName));
    }

    private String mark(boolean ok) {
        return ok ? "§aOK" : "§cMISSING";
    }

    private String mcMmoStatus() {
        if (!McMMOIntegration.isEnabled()) {
            return "§cMISSING";
        }
        String version = McMMOIntegration.getVersion();
        return version == null || version.isBlank() ? "§aOK" : "§aOK §8(v" + version + ")";
    }

    private java.io.File latestBackup(java.io.File dir, String prefix) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return null;
        }
        java.io.File[] files = dir.listFiles((d, name) -> name.startsWith(prefix));
        if (files == null || files.length == 0) {
            return null;
        }
        java.util.Arrays.sort(files, java.util.Comparator.comparingLong(java.io.File::lastModified).reversed());
        return files[0];
    }

    private String backupStatus(java.io.File file) {
        if (file == null) {
            return "§cMISSING";
        }
        long ageMs = Math.max(0L, System.currentTimeMillis() - file.lastModified());
        long minutes = ageMs / 60000L;
        if (minutes < 60L) {
            return "§a" + minutes + "m ago";
        }
        long hours = minutes / 60L;
        if (hours < 48L) {
            return "§e" + hours + "h ago";
        }
        long days = hours / 24L;
        return "§c" + days + "d ago";
    }
}