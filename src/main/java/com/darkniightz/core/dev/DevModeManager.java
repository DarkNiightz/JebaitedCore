package com.darkniightz.core.dev;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple developer mode manager, gated by allowed UUIDs from config.
 * When enabled for a player, optionally grants OP and marks them as bypass for rank checks.
 */
public class DevModeManager {
    private final Plugin plugin;
    private final Set<UUID> allowed;
    private final boolean grantOp;
    private final Map<UUID, Boolean> active = new ConcurrentHashMap<>();
    private final Set<UUID> opGranted = new HashSet<>();

    public DevModeManager(Plugin plugin) {
        this.plugin = plugin;
        this.allowed = new HashSet<>();
        this.grantOp = plugin.getConfig().getBoolean("devmode.grant_op", true);
        List<String> list = plugin.getConfig().getStringList("devmode.allowed_uuids");
        if (list != null) {
            for (String s : list) {
                try { allowed.add(UUID.fromString(s)); } catch (Exception ignored) {}
            }
        }
    }

    public boolean isAllowed(UUID uuid) {
        return allowed.contains(uuid);
    }

    public boolean isActive(UUID uuid) {
        return active.getOrDefault(uuid, false);
    }

    public void setActive(UUID uuid, boolean enable) {
        boolean now = enable;
        active.put(uuid, now);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && grantOp) {
            if (now) {
                if (!p.isOp()) {
                    p.setOp(true);
                    opGranted.add(uuid);
                }
            } else {
                if (opGranted.remove(uuid)) {
                    // Only revoke op if we previously granted it
                    p.setOp(false);
                }
            }
        }
    }

    public void toggle(UUID uuid) {
        setActive(uuid, !isActive(uuid));
    }
}
