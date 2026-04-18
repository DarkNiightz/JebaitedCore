package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.cosmetics.CollectionBookMenu;
import com.darkniightz.core.cosmetics.CosmeticsManager;
import com.darkniightz.core.cosmetics.CosmeticPreviewService;
import com.darkniightz.core.cosmetics.ToyboxManager;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.world.WorldManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;

public class CosmeticsCommand implements CommandExecutor {
    private final Plugin plugin;
    private final CosmeticsManager cosmetics;
    private final ProfileStore profiles;
    private final ToyboxManager toyboxManager;
    private final CosmeticPreviewService previewService;
    private final DevModeManager devMode;
    private final WorldManager worldManager;
    private final RankManager rankManager;

    public CosmeticsCommand(Plugin plugin, CosmeticsManager cosmetics, ProfileStore profiles, ToyboxManager toyboxManager) {
        this(plugin, cosmetics, profiles, toyboxManager, null, null, null);
    }

    public CosmeticsCommand(Plugin plugin, CosmeticsManager cosmetics, ProfileStore profiles, ToyboxManager toyboxManager, CosmeticPreviewService previewService) {
        this(plugin, cosmetics, profiles, toyboxManager, previewService, null, null);
    }

    public CosmeticsCommand(Plugin plugin, CosmeticsManager cosmetics, ProfileStore profiles, ToyboxManager toyboxManager, CosmeticPreviewService previewService, DevModeManager devMode, WorldManager worldManager) {
        this.plugin = plugin;
        this.cosmetics = cosmetics;
        this.profiles = profiles;
        this.toyboxManager = toyboxManager;
        this.previewService = previewService;
        this.devMode = devMode;
        this.rankManager = plugin instanceof JebaitedCore core ? core.getRankManager() : null;
        if (worldManager != null) {
            this.worldManager = worldManager;
        } else if (plugin instanceof JebaitedCore core) {
            this.worldManager = core.getWorldManager();
        } else {
            this.worldManager = new WorldManager(plugin);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && handleAdminSubcommand(sender, args)) {
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can open the cosmetics menu."));
            return true;
        }
        if (!worldManager.requireHub(p, devMode)) {
            return true;
        }
        PlayerProfile prof = profiles.getOrCreate(p, plugin.getConfig().getString("ranks.default", "pleb"));
        prof.incWardrobeOpens();
        profiles.save(p.getUniqueId());
        new CollectionBookMenu(plugin, cosmetics, profiles, previewService, toyboxManager).open(p);
        return true;
    }

    private boolean handleAdminSubcommand(CommandSender sender, String[] args) {
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!sub.equals("give") && !sub.equals("take") && !sub.equals("wipe")) {
            return false;
        }
        if (!hasAdminAccess(sender)) {
            sender.sendMessage(Messages.noPerm());
            return true;
        }
        if (!(plugin instanceof JebaitedCore core)) {
            sender.sendMessage(Messages.prefixed("§cCore context unavailable."));
            return true;
        }
        if (sub.equals("wipe")) {
            if (args.length < 2) {
                sender.sendMessage("USAGE: /cosmetics wipe <player>");
                return true;
            }
            return runWipe(sender, core, args[1]);
        }
        if (args.length < 3) {
            sender.sendMessage("USAGE: /cosmetics " + sub + " <id> <player>");
            return true;
        }
        String cosmeticId = args[1];
        String playerName = args[2];
        CosmeticsManager.Cosmetic cosmetic = cosmetics.get(cosmeticId);
        if (cosmetic == null) {
            sender.sendMessage("UNKNOWN_COSMETIC " + cosmeticId);
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        UUID targetUuid = target.getUniqueId();
        if (targetUuid == null) {
            sender.sendMessage("PLAYER_NOT_FOUND " + playerName);
            return true;
        }
        if (sub.equals("give")) {
            String cosmeticType = cosmetic.category.name().toLowerCase(Locale.ROOT);
            core.getPlayerProfileDAO().unlockCosmetic(targetUuid, cosmeticId, cosmeticType);
            sender.sendMessage("GAVE " + cosmeticId + " " + (target.getName() != null ? target.getName() : targetUuid));
            return true;
        }
        return runTake(sender, core, target, cosmeticId);
    }

    private boolean runTake(CommandSender sender, JebaitedCore core, OfflinePlayer target, String cosmeticId) {
        UUID targetUuid = target.getUniqueId();
        if (targetUuid == null) {
            sender.sendMessage("PLAYER_NOT_FOUND");
            return true;
        }
        String sql = "DELETE FROM player_cosmetics WHERE player_uuid = ? AND cosmetic_id = ?";
        try (Connection conn = core.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, targetUuid.toString());
            ps.setString(2, cosmeticId);
            int deleted = ps.executeUpdate();
            cleanupEquippedState(targetUuid, cosmeticId);
            sender.sendMessage("TOOK " + cosmeticId + " " + (target.getName() != null ? target.getName() : targetUuid) + " rows=" + deleted);
        } catch (SQLException e) {
            core.getLogger().warning("Cosmetics take failed: " + e.getMessage());
            sender.sendMessage("ERROR_TAKE_FAILED");
        }
        return true;
    }

    private boolean runWipe(CommandSender sender, JebaitedCore core, String playerName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        UUID targetUuid = target.getUniqueId();
        if (targetUuid == null) {
            sender.sendMessage("PLAYER_NOT_FOUND " + playerName);
            return true;
        }
        String sql = "DELETE FROM player_cosmetics WHERE player_uuid = ?";
        try (Connection conn = core.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, targetUuid.toString());
            int deleted = ps.executeUpdate();
            clearEquippedState(targetUuid);
            sender.sendMessage("WIPED " + (target.getName() != null ? target.getName() : targetUuid) + " rows=" + deleted);
        } catch (SQLException e) {
            core.getLogger().warning("Cosmetics wipe failed: " + e.getMessage());
            sender.sendMessage("ERROR_WIPE_FAILED");
        }
        return true;
    }

    private void cleanupEquippedState(UUID targetUuid, String cosmeticId) {
        PlayerProfile profile = profiles.get(targetUuid);
        if (profile == null) {
            return;
        }
        if (cosmeticId.equalsIgnoreCase(profile.getEquippedParticles())) {
            profile.setEquippedParticles(null);
            profile.setParticleActivatedAt(null);
        }
        if (cosmeticId.equalsIgnoreCase(profile.getEquippedTrail())) {
            profile.setEquippedTrail(null);
            profile.setTrailActivatedAt(null);
        }
        if (cosmeticId.equalsIgnoreCase(profile.getEquippedGadget())) {
            profile.setEquippedGadget(null);
        }
        if (cosmeticId.equalsIgnoreCase(profile.getActiveTag())) {
            profile.setActiveTag(null);
        }
        profiles.save(targetUuid);
    }

    private void clearEquippedState(UUID targetUuid) {
        PlayerProfile profile = profiles.get(targetUuid);
        if (profile == null) {
            return;
        }
        profile.setEquippedParticles(null);
        profile.setParticleActivatedAt(null);
        profile.setEquippedTrail(null);
        profile.setTrailActivatedAt(null);
        profile.setEquippedGadget(null);
        profile.setActiveTag(null);
        profiles.save(targetUuid);
    }

    private boolean hasAdminAccess(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (player.isOp() || (devMode != null && devMode.isActive(player.getUniqueId()))) {
            return true;
        }
        if (rankManager == null) {
            return false;
        }
        PlayerProfile actor = profiles.getOrCreate(player, rankManager.getDefaultGroup());
        String actorRank = actor == null || actor.getPrimaryRank() == null ? rankManager.getDefaultGroup() : actor.getPrimaryRank();
        return rankManager.isAtLeast(actorRank, "admin");
    }
}
