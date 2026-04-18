package com.darkniightz.core.system;

import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CommandSecurityListener implements Listener {

    private static final String UNKNOWN = "Unknown command.";
    private static final String THROTTLED = "§eSlow down.";

    private final JebaitedCore plugin;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;
    private final Map<String, Long> commandCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> feedbackCooldowns = new ConcurrentHashMap<>();

    private static final Set<String> BLOCKED_PROBES = Set.of(
            "?", "plugins", "pl", "version", "ver", "about", "icanhasbukkit",
            "bukkit:plugins", "bukkit:pl", "bukkit:version", "bukkit:ver",
            "paper", "paper:paper", "permissions", "perms", "perm", "lp", "luckperms"
    );

    public CommandSecurityListener(JebaitedCore plugin, ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPreprocess(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage();
        if (raw == null || raw.isBlank() || raw.charAt(0) != '/') {
            return;
        }

        Player player = event.getPlayer();
        String label = raw.substring(1).split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (label.isBlank()) {
            return;
        }

        String base = baseLabel(label);
        boolean bypass = isStaffBypass(player);

        if ("?".equals(base) || "help".equals(base)) {
            event.setCancelled(true);
            String remainder = raw.contains(" ") ? raw.substring(raw.indexOf(' ') + 1).trim() : "";
            String rewritten = remainder.isBlank() ? "jebaited" : "jebaited " + remainder;
            Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(rewritten));
            return;
        }

        if (!bypass && (BLOCKED_PROBES.contains(label) || BLOCKED_PROBES.contains(base))) {
            event.setCancelled(true);
            sendUnknown(player);
            return;
        }

        String resolved = resolveKnownPluginLabel(base);
        if (resolved == null) {
            if (!bypass) {
                event.setCancelled(true);
                sendUnknown(player);
            }
            return;
        }

        if (!canAccess(player, resolved)) {
            event.setCancelled(true);
            sendUnknown(player);
            return;
        }

        // HC event confirmation bypass: player already saw the warning, allow the confirm click
        // even if it arrives within the normal 600 ms anti-spam window.
        boolean isHcConfirm = "event".equals(resolved)
                && raw.toLowerCase(Locale.ROOT).matches("(?i)^/event\\s+join\\s+confirm\\b.*");
        if (!bypass && !isHcConfirm && shouldThrottle(player, resolved)) {
            event.setCancelled(true);
            sendThrottle(player);
        }
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        if (isStaffBypass(player)) {
            return;
        }

        Set<String> toRemove = new HashSet<>();
        for (String cmd : Set.copyOf(event.getCommands())) {
            String lower = cmd.toLowerCase(Locale.ROOT);
            String base = baseLabel(lower);
            if (BLOCKED_PROBES.contains(lower) || BLOCKED_PROBES.contains(base)) {
                toRemove.add(cmd);
                continue;
            }
            String resolved = resolveKnownPluginLabel(base);
            if (resolved != null && !canAccess(player, resolved)) {
                toRemove.add(cmd);
            }
        }
        event.getCommands().removeAll(toRemove);
    }

    private String resolveKnownPluginLabel(String label) {
        Map<String, Map<String, Object>> commands = plugin.getDescription().getCommands();
        if (commands == null || label == null || label.isBlank()) {
            return null;
        }
        if (commands.containsKey(label)) {
            return label;
        }
        for (Map.Entry<String, Map<String, Object>> entry : commands.entrySet()) {
            Object aliases = entry.getValue().get("aliases");
            if (aliases instanceof String alias && label.equalsIgnoreCase(alias)) {
                return entry.getKey();
            }
            if (aliases instanceof List<?> list) {
                for (Object item : list) {
                    if (item != null && label.equalsIgnoreCase(String.valueOf(item))) {
                        return entry.getKey();
                    }
                }
            }
        }
        return null;
    }

    private boolean canAccess(Player player, String label) {
        if (player == null) {
            return false;
        }
        if (isStaffBypass(player)) {
            return true;
        }

        PlayerProfile profile = profiles.getOrCreate(player, ranks.getDefaultGroup());
        String rank = profile == null || profile.getPrimaryRank() == null ? ranks.getDefaultGroup() : profile.getPrimaryRank();
        String donorRank = profile == null ? null : profile.getDonorRank();

        return switch (label) {
            case "near" -> {
                String minRank = plugin.getConfig().getString("near.min_rank", "diamond");
                yield ranks.isAtLeast(rank, minRank)
                        || (donorRank != null && ranks.isAtLeast(donorRank, minRank));
            }
            case "back" -> ranks.isAtLeast(rank, "grandmaster")
                    || (donorRank != null && ranks.isAtLeast(donorRank, "grandmaster"));
            case "deathtp", "dtp" -> ranks.isAtLeast(rank, "legend")
                    || (donorRank != null && ranks.isAtLeast(donorRank, "legend"));
            case "repair" -> ranks.isAtLeast(rank, "grandmaster")
                    || (donorRank != null && ranks.isAtLeast(donorRank, "grandmaster"));
            case "feed" -> ranks.isAtLeast(rank, "diamond")
                    || (donorRank != null && ranks.isAtLeast(donorRank, "diamond"));
            case "enderchest", "ec", "craft", "anvil" -> ranks.isAtLeast(rank, "gold")
                    || (donorRank != null && ranks.isAtLeast(donorRank, "gold"));
            case "kit" -> donorRank != null;
            case "kick", "warn", "staffchat", "history", "vanish", "notes", "whois", "generatepassword" -> ranks.isAtLeast(rank, "helper");
            case "tempmute", "tempban" -> ranks.isAtLeast(rank, "helper");
            case "mute", "ban", "freeze", "slowmode" -> ranks.isAtLeast(rank, "moderator");
            case "unmute", "unban" -> ranks.isAtLeast(rank, "srmod");
            case "setrank" -> ranks.isAtLeast(rank, "srmod");
            case "setspawn", "eco", "setwarp", "delwarp", "setdonor", "worldstatus", "compat", "leaderboard", "previewpedestal", "maintenance" -> ranks.isAtLeast(rank, "admin");
            case "jreload", "debug", "devdebug", "devmode" -> ranks.isAtLeast(rank, "developer") || ranks.isAtLeast(rank, "admin");
            default -> true;
        };
    }

    private boolean isStaffBypass(Player player) {
        if (player == null) {
            return false;
        }
        if (player.isOp() || (devMode != null && devMode.isActive(player.getUniqueId()))) {
            return true;
        }
        PlayerProfile profile = profiles.getOrCreate(player, ranks.getDefaultGroup());
        String rank = profile == null || profile.getPrimaryRank() == null ? ranks.getDefaultGroup() : profile.getPrimaryRank();
        return ranks.isAtLeast(rank, "admin");
    }

    private boolean shouldThrottle(Player player, String label) {
        if (player == null || !plugin.getConfig().getBoolean("protection.command_spam.enabled", true)) {
            return false;
        }
        long cooldownMs = commandCooldownMs(label);
        if (cooldownMs <= 0L) {
            return false;
        }
        long now = System.currentTimeMillis();
        String key = player.getUniqueId() + ":" + label.toLowerCase(Locale.ROOT);
        Long previous = commandCooldowns.put(key, now);
        return previous != null && now - previous < cooldownMs;
    }

    private long commandCooldownMs(String label) {
        long defaultMs = Math.max(0L, plugin.getConfig().getLong("protection.command_spam.default_cooldown_ms", 250L));
        long heavyMs = Math.max(defaultMs, plugin.getConfig().getLong("protection.command_spam.heavy_cooldown_ms", 1500L));
        long externalMs = Math.max(heavyMs, plugin.getConfig().getLong("protection.command_spam.external_cooldown_ms", 5000L));
        if (label == null || label.isBlank()) {
            return defaultMs;
        }
        return switch (label.toLowerCase(Locale.ROOT)) {
            case "worldstatus", "compat", "history", "whois", "balancetop", "leaderboard", "mctop", "mcrank", "debug", "devdebug", "jreload" -> heavyMs;
            case "generatepassword" -> externalMs;
            case "trade", "coins", "balance", "stats", "mcstats", "inspect", "mcinspect", "mmoinspect", "near", "home", "homes", "warp", "warps", "spawn", "hub", "smp", "menu", "servers", "navigator", "cosmetics", "preview", "help", "jebaited", "message", "msg", "reply", "r", "pay", "party", "pa", "p", "event", "chatgame", "cg", "achievements", "ach", "achieve", "back", "feed", "repair", "deathtp", "dtp", "enderchest", "ec", "craft", "anvil", "kit", "combatlogs", "combatlog", "ctag", "shop", "market", "donate", "store", "link" -> Math.max(defaultMs, 600L);
            default -> defaultMs;
        };
    }

    private void sendUnknown(Player player) {
        if (canSendFeedback(player)) {
            player.sendMessage(UNKNOWN);
        }
    }

    private void sendThrottle(Player player) {
        if (canSendFeedback(player)) {
            player.sendMessage(THROTTLED);
        }
    }

    private boolean canSendFeedback(Player player) {
        if (player == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        long feedbackMs = Math.max(250L, plugin.getConfig().getLong("protection.command_spam.feedback_cooldown_ms", 1200L));
        Long previous = feedbackCooldowns.put(player.getUniqueId(), now);
        return previous == null || now - previous >= feedbackMs;
    }

    private String baseLabel(String label) {
        int idx = label.indexOf(':');
        return idx >= 0 ? label.substring(idx + 1) : label;
    }
}
