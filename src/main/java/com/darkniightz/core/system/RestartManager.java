package com.darkniightz.core.system;

import com.darkniightz.main.JebaitedCore;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Manages scheduled and manual server restarts with countdown announcements,
 * pre-restart data flush, and webpanel notification.
 *
 * Announcement milestones (seconds remaining):
 *   1800, 900, 600, 300, 180, 120, 60, 30, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1
 */
public class RestartManager {

    /** Ordered set of remaining-seconds thresholds that trigger an announcement. */
    private static final Set<Long> MILESTONES;

    static {
        MILESTONES = new LinkedHashSet<>();
        for (long s : new long[]{1800, 900, 600, 300, 180, 120, 60, 30, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1}) {
            MILESTONES.add(s);
        }
    }

    private final JebaitedCore plugin;

    /** Task that fires every second during the countdown. */
    private BukkitTask countdownTask;
    /** Task for the automatic scheduled restart. */
    private BukkitTask scheduleTask;

    private volatile boolean restartPending = false;
    /** Epoch-ms when the actual restart will fire. */
    private volatile long restartAtMs = -1;
    private volatile String restartReason = "Scheduled restart";
    private volatile String restartInitiator = "CONSOLE";

    public RestartManager(JebaitedCore plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public boolean isRestartPending() { return restartPending; }

    public long getSecondsUntilRestart() {
        if (!restartPending) return -1;
        long diff = restartAtMs - System.currentTimeMillis();
        return Math.max(0, diff / 1000);
    }

    /** Begin the startup scheduled-restart loop (call from onEnable). */
    public void startScheduled() {
        cancelScheduledTask();
        if (!plugin.getConfig().getBoolean("restart.schedule.enabled", false)) return;

        long intervalHours = plugin.getConfig().getLong("restart.schedule.interval-hours", 12);
        boolean useTime = plugin.getConfig().getBoolean("restart.schedule.use-time", false);

        long delaySeconds;
        if (useTime) {
            String timeStr = plugin.getConfig().getString("restart.schedule.time", "04:00");
            delaySeconds = secondsUntilNextTime(timeStr);
        } else {
            delaySeconds = intervalHours * 3600L;
        }

        String reason = plugin.getConfig().getString("restart.schedule.reason", "Scheduled restart");
        long countdownSeconds = plugin.getConfig().getLong("restart.schedule.countdown-seconds", 300L);

        // Fire once after delay; CountdownTask handles recurring shutdown
        long triggerDelay = Math.max(1, delaySeconds - countdownSeconds);
        scheduleTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!restartPending) {
                scheduleRestart(countdownSeconds, reason, null);
            }
        }, triggerDelay * 20L);

        plugin.getLogger().info("[Restart] Next scheduled restart in " + formatSeconds(delaySeconds)
                + " (countdown starts " + formatSeconds(countdownSeconds) + " before).");
    }

    /**
     * Schedule a restart with a custom countdown.
     *
     * @param countdownSeconds seconds until restart
     * @param reason           broadcast reason (can be null)
     * @param initiator        who triggered it (null = CONSOLE)
     */
    public void scheduleRestart(long countdownSeconds, String reason, CommandSender initiator) {
        if (restartPending) return; // already scheduled

        restartPending = true;
        restartAtMs = System.currentTimeMillis() + countdownSeconds * 1000L;
        restartReason = reason != null ? reason : "Server restart";
        restartInitiator = initiator != null ? initiator.getName() : "CONSOLE";

        String prefix = announcePrefix();
        String reasonDisplay = restartReason.isEmpty() ? "" : " §7(" + restartReason + "§7)";
        Bukkit.broadcastMessage(prefix + "§cThe server will restart in §e" + formatSeconds(countdownSeconds) + reasonDisplay + "§c!");

        if (initiator != null && !(initiator instanceof Player)) {
            // Console or RCON – no extra message needed (already broadcast)
        }

        notifyPanel("restart_scheduled", Map.of(
                "countdown_seconds", String.valueOf(countdownSeconds),
                "reason", restartReason,
                "initiator", restartInitiator
        ));

        startCountdownTask();
    }

    /** Cancel a pending restart. */
    public boolean cancelRestart(CommandSender canceller) {
        if (!restartPending) return false;
        restartPending = false;
        restartAtMs = -1;
        stopCountdownTask();

        String name = canceller != null ? canceller.getName() : "CONSOLE";
        String prefix = announcePrefix();
        Bukkit.broadcastMessage(prefix + "§aThe pending restart has been §2cancelled §aby §e" + name + "§a.");

        notifyPanel("restart_cancelled", Map.of("canceller", name));
        return true;
    }

    /** Clean shutdown of all tasks (call from onDisable). */
    public void shutdown() {
        stopCountdownTask();
        cancelScheduledTask();
    }

    // -------------------------------------------------------------------------
    // Countdown loop
    // -------------------------------------------------------------------------

    private void startCountdownTask() {
        stopCountdownTask();
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickCountdown, 20L, 20L);
    }

    private void stopCountdownTask() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    private void cancelScheduledTask() {
        if (scheduleTask != null) {
            scheduleTask.cancel();
            scheduleTask = null;
        }
    }

    private void tickCountdown() {
        if (!restartPending) {
            stopCountdownTask();
            return;
        }

        long remaining = getSecondsUntilRestart();

        if (remaining <= 0) {
            stopCountdownTask();
            executeRestart();
            return;
        }

        // Check if this second is a milestone
        if (MILESTONES.contains(remaining)) {
            announceCountdown(remaining);
        }
    }

    private void announceCountdown(long seconds) {
        String prefix = announcePrefix();
        if (seconds > 60) {
            long minutes = seconds / 60;
            Bukkit.broadcastMessage(prefix + "§cServer restarting in §e" + minutes + " minute" + (minutes == 1 ? "" : "s") + "§c!");
        } else if (seconds == 60) {
            Bukkit.broadcastMessage(prefix + "§cServer restarting in §e1 minute§c!");
        } else if (seconds == 30) {
            Bukkit.broadcastMessage(prefix + "§cServer restarting in §e30 seconds§c!");
        } else if (seconds <= 10) {
            Bukkit.broadcastMessage(prefix + "§c§l" + seconds + "...");
        } else {
            Bukkit.broadcastMessage(prefix + "§cServer restarting in §e" + seconds + " seconds§c!");
        }
    }

    // -------------------------------------------------------------------------
    // Restart execution
    // -------------------------------------------------------------------------

    private void executeRestart() {
        restartPending = false;

        String prefix = announcePrefix();
        Bukkit.broadcastMessage(prefix + "§c§lRESTARTING!");
        Bukkit.broadcastMessage(prefix + "§7See you on the other side. §b✦");

        // 1. Flush all dirty player profiles (synchronous on main thread)
        try {
            if (plugin.getProfileStore() != null) {
                plugin.getProfileStore().flushAll();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Restart] Profile flush error: " + e.getMessage(), e);
        }

        // 2. Save vanilla player data
        Bukkit.savePlayers();

        // 3. Notify panel before kicking (async fire-and-forget)
        notifyPanel("restart_executing", Map.of(
                "reason", restartReason,
                "initiator", restartInitiator,
                "timestamp", String.valueOf(System.currentTimeMillis())
        ));

        // 4. Kick all players after 1 tick (allows broadcast packet to flush)
        String kickMsg = plugin.getConfig().getString("restart.kick-message",
                "§cThe server is restarting. It will be back shortly!");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.kick(net.kyori.adventure.text.Component.text(kickMsg));
            }
            // 5. Actual server restart (respects spigot.yml restart-script or falls back to shutdown)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    Bukkit.spigot().restart();
                } catch (Exception e) {
                    plugin.getLogger().warning("[Restart] spigot().restart() failed — falling back to shutdown: " + e.getMessage());
                    Bukkit.shutdown();
                }
            }, 10L);
        }, 2L);
    }

    // -------------------------------------------------------------------------
    // Panel notification (fire-and-forget, no crash on failure)
    // -------------------------------------------------------------------------

    private void notifyPanel(String event, Map<String, String> extra) {
        try {
            String url = panelUrl() + "/api/server/restart-event";
            String secret = plugin.getConfig().getString("webpanel.provision_secret", "");
            StringBuilder json = new StringBuilder("{\"event\":\"").append(event).append("\"");
            if (extra != null) {
                extra.forEach((k, v) -> json.append(",\"").append(k).append("\":\"")
                        .append(v.replace("\"", "\\\"")).append("\""));
            }
            json.append("}");

            plugin.getPanelConnectorService().postJsonAsync(
                    url, json.toString(),
                    Map.of("X-Provision-Secret", secret),
                    result -> {
                        if (!result.sent() || result.statusCode() < 200 || result.statusCode() >= 300) {
                            plugin.getLogger().fine("[Restart] Panel notify '" + event + "' -> " + result.statusCode());
                        }
                    }
            );
        } catch (Exception e) {
            plugin.getLogger().fine("[Restart] Panel notify failed: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String panelUrl() {
        String url = plugin.getConfig().getString("webpanel.internal_url", "");
        if (url == null || url.isBlank()) {
            url = plugin.getConfig().getString("webpanel.url", "http://localhost:3001");
        }
        return url != null ? url.replaceAll("/+$", "") : "http://localhost:3001";
    }

    private String announcePrefix() {
        return plugin.getConfig().getString("restart.announce-prefix", "§c§l[SERVER] §r");
    }

    private static String formatSeconds(long seconds) {
        if (seconds >= 3600) {
            long h = seconds / 3600;
            long m = (seconds % 3600) / 60;
            return h + " hour" + (h == 1 ? "" : "s") + (m > 0 ? " " + m + " min" : "");
        } else if (seconds >= 60) {
            long m = seconds / 60;
            long s = seconds % 60;
            return m + " minute" + (m == 1 ? "" : "s") + (s > 0 ? " " + s + "s" : "");
        } else {
            return seconds + " second" + (seconds == 1 ? "" : "s");
        }
    }

    /**
     * Calculates seconds until the next occurrence of a wall-clock time string like "04:00"
     * using the server's default timezone.
     */
    private static long secondsUntilNextTime(String timeStr) {
        try {
            String[] parts = timeStr.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;

            ZoneId zone = ZoneId.systemDefault();
            ZonedDateTime now = ZonedDateTime.now(zone);
            ZonedDateTime target = now.with(LocalTime.of(hour, minute, 0));

            if (!target.isAfter(now)) {
                target = target.plusDays(1);
            }
            return TimeUnit.MILLISECONDS.toSeconds(
                    target.toInstant().toEpochMilli() - Instant.now().toEpochMilli());
        } catch (Exception e) {
            return 12 * 3600L; // fallback: 12 hours
        }
    }
}
