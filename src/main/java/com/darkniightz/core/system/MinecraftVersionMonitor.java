package com.darkniightz.core.system;

import com.darkniightz.core.Messages;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks the currently running Minecraft server version and notifies owners /
 * developers when Mojang has shipped a newer release than the one in use.
 */
public class MinecraftVersionMonitor {
    private static final Pattern LATEST_RELEASE_PATTERN = Pattern.compile("\\\"release\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private final JebaitedCore plugin;
    private final OpsAlertService opsAlertService;
    private final HttpClient httpClient;

    private volatile int scheduledTaskId = -1;
    private volatile String latestKnownVersion;
    private volatile Instant lastCheckedAt;
    private volatile boolean outdated;

    public MinecraftVersionMonitor(JebaitedCore plugin, OpsAlertService opsAlertService) {
        this.plugin = plugin;
        this.opsAlertService = opsAlertService;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.latestKnownVersion = cleanVersion(plugin.getConfig().getString("minecraft_support.version_alerts.latest_known_version", Bukkit.getMinecraftVersion()));
        this.lastCheckedAt = null;
        this.outdated = false;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("minecraft_support.version_alerts.enabled", true)) {
            return;
        }

        checkAsync();

        long hours = Math.max(1L, plugin.getConfig().getLong("minecraft_support.version_alerts.check_interval_hours", 12L));
        long ticks = hours * 60L * 60L * 20L;
        scheduledTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkNowSafely, ticks, ticks).getTaskId();
    }

    public void stop() {
        if (scheduledTaskId != -1) {
            Bukkit.getScheduler().cancelTask(scheduledTaskId);
            scheduledTaskId = -1;
        }
    }

    public boolean isOutdated() {
        return outdated;
    }

    public String getLatestKnownVersion() {
        return latestKnownVersion;
    }

    public String getCurrentServerVersion() {
        return cleanVersion(Bukkit.getMinecraftVersion());
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void checkAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::checkNowSafely);
    }

    private void checkNowSafely() {
        try {
            checkNow();
        } catch (Exception ex) {
            plugin.getLogger().warning("Minecraft version check failed: " + ex.getMessage());
        }
    }

    private void checkNow() {
        String currentVersion = getCurrentServerVersion();
        String latestVersion = latestKnownVersion;

        if (plugin.getConfig().getBoolean("minecraft_support.version_alerts.fetch_latest_release", true)) {
            String fetchedVersion = fetchLatestReleaseVersion();
            if (fetchedVersion != null && !fetchedVersion.isBlank()) {
                latestVersion = fetchedVersion;
                latestKnownVersion = fetchedVersion;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getConfig().set("minecraft_support.version_alerts.latest_known_version", fetchedVersion);
                    plugin.saveConfig();
                });
            }
        }

        lastCheckedAt = Instant.now();
        outdated = compareVersions(currentVersion, latestVersion) < 0;

        if (outdated) {
            String detail = "Running " + currentVersion + " while latest release is " + latestVersion;
            plugin.getLogger().warning("Minecraft update available | " + detail);
            if (opsAlertService != null) {
                opsAlertService.sendAsync("Minecraft update available", detail);
            }
            notifyOnlineStaff(currentVersion, latestVersion);
        } else {
            plugin.getLogger().info("Minecraft version check OK | current=" + currentVersion + " | latest=" + latestVersion);
        }
    }

    private void notifyOnlineStaff(String currentVersion, String latestVersion) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            String message = Messages.prefixed("§eMinecraft §f" + latestVersion + " §eis available. This server is still on §f" + currentVersion + "§e.");
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (shouldNotify(player)) {
                    player.sendMessage(message);
                }
            }
        });
    }

    private boolean shouldNotify(Player player) {
        if (player == null) {
            return false;
        }
        if (player.isOp()) {
            return true;
        }
        if (plugin.getRankManager() == null || plugin.getProfileStore() == null) {
            return false;
        }

        String minimumRank = plugin.getConfig().getString("minecraft_support.version_alerts.notify_min_rank", "developer");
        String primaryRank = plugin.getProfileStore().getOrCreate(player, plugin.getRankManager().getDefaultGroup()).getPrimaryRank();
        return plugin.getRankManager().isAtLeast(primaryRank, minimumRank);
    }

    private String fetchLatestReleaseVersion() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }

            Matcher matcher = LATEST_RELEASE_PATTERN.matcher(response.body());
            if (matcher.find()) {
                return cleanVersion(matcher.group(1));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private int compareVersions(String left, String right) {
        if (left == null || left.isBlank() || right == null || right.isBlank()) {
            return 0;
        }

        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int max = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < max; i++) {
            int a = i < leftParts.length ? parseInt(leftParts[i]) : 0;
            int b = i < rightParts.length ? parseInt(rightParts[i]) : 0;
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        return 0;
    }

    private int parseInt(String value) {
        if (value == null) {
            return 0;
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String cleanVersion(String version) {
        if (version == null) {
            return "unknown";
        }
        String cleaned = version.trim();
        int dashIndex = cleaned.indexOf('-');
        if (dashIndex > 0) {
            cleaned = cleaned.substring(0, dashIndex);
        }
        return cleaned;
    }
}
