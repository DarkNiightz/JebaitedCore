package com.darkniightz.core.system;

import com.darkniightz.core.Messages;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.main.JebaitedCore;
import com.darkniightz.main.PlayerProfileDAO;
import com.darkniightz.main.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class MaintenanceManager implements Listener {

    private final JebaitedCore plugin;
    private final DatabaseManager databaseManager;
    private final PlayerProfileDAO dao;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final Set<String> whitelist = ConcurrentHashMap.newKeySet();
    private volatile boolean enabled;

    public MaintenanceManager(JebaitedCore plugin, DatabaseManager databaseManager, PlayerProfileDAO dao, ProfileStore profiles, RankManager ranks) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.dao = dao;
        this.profiles = profiles;
        this.ranks = ranks;
    }

    public void start() {
        if (databaseManager == null || !databaseManager.isEnabled()) {
            enabled = false;
            whitelist.clear();
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::loadStateFromDatabase);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean setEnabled(boolean newState, String updatedBy) {
        this.enabled = newState;
        persistEnabledState(newState, updatedBy);
        if (newState) {
            kickNonExemptPlayers();
        } else {
            Bukkit.broadcastMessage(Messages.prefixed("§aMaintenance mode disabled. The server is open again."));
        }
        return this.enabled;
    }

    public boolean addWhitelisted(String playerName, String addedBy) {
        String normalized = normalize(playerName);
        if (normalized.isBlank()) {
            return false;
        }
        whitelist.add(normalized);
        persistWhitelistUpsert(normalized, addedBy);
        return true;
    }

    public boolean removeWhitelisted(String playerName) {
        String normalized = normalize(playerName);
        if (normalized.isBlank()) {
            return false;
        }
        whitelist.remove(normalized);
        persistWhitelistDelete(normalized);
        return true;
    }

    public List<String> listWhitelisted() {
        List<String> out = new ArrayList<>(whitelist);
        Collections.sort(out);
        return out;
    }

    public boolean canJoin(UUID uuid, String playerName) {
        if (!enabled) {
            return true;
        }
        if (whitelist.contains(normalize(playerName))) {
            return true;
        }
        String rank = dao == null ? null : dao.loadStoredRank(uuid, playerName);
        return rank != null && ranks.isAtLeast(rank, "helper");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!enabled) {
            return;
        }
        if (canJoin(event.getUniqueId(), event.getName())) {
            return;
        }
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                ChatColor.RED + "Server is currently in maintenance mode. Please try again soon.");
    }

    private void kickNonExemptPlayers() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!isExemptOnline(online)) {
                online.kickPlayer(ChatColor.RED + "Server is currently in maintenance mode. Please try again soon.");
            }
        }
        Bukkit.broadcastMessage(Messages.prefixed("§cMaintenance mode enabled. Staff and whitelisted users only."));
    }

    private boolean isExemptOnline(Player player) {
        if (player == null) {
            return false;
        }
        if (player.isOp() || whitelist.contains(normalize(player.getName()))) {
            return true;
        }
        PlayerProfile profile = profiles.getOrCreate(player, ranks.getDefaultGroup());
        String rank = profile == null || profile.getPrimaryRank() == null ? ranks.getDefaultGroup() : profile.getPrimaryRank();
        return ranks.isAtLeast(rank, "helper");
    }

    private void loadStateFromDatabase() {
        whitelist.clear();
        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT enabled FROM server_maintenance ORDER BY updated_at DESC, id DESC LIMIT 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    enabled = rs.next() && rs.getBoolean("enabled");
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT player_name FROM maintenance_whitelist")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        whitelist.add(normalize(rs.getString("player_name")));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load maintenance state", e);
            enabled = false;
        }
        if (enabled) {
            Bukkit.getScheduler().runTask(plugin, this::kickNonExemptPlayers);
        }
    }

    private void persistEnabledState(boolean state, String updatedBy) {
        if (databaseManager == null || !databaseManager.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT INTO server_maintenance (enabled, updated_by) VALUES (?, ?);")) {
                ps.setBoolean(1, state);
                ps.setString(2, updatedBy == null ? "system" : updatedBy);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to persist maintenance state", e);
            }
        });
    }

    private void persistWhitelistUpsert(String playerName, String addedBy) {
        if (databaseManager == null || !databaseManager.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT INTO maintenance_whitelist (player_name, added_by) VALUES (?, ?) ON CONFLICT (player_name) DO UPDATE SET added_by = EXCLUDED.added_by, created_at = NOW();")) {
                ps.setString(1, playerName);
                ps.setString(2, addedBy == null ? "system" : addedBy);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to add maintenance whitelist entry", e);
            }
        });
    }

    private void persistWhitelistDelete(String playerName) {
        if (databaseManager == null || !databaseManager.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM maintenance_whitelist WHERE player_name = ?;")) {
                ps.setString(1, playerName);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to remove maintenance whitelist entry", e);
            }
        });
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
