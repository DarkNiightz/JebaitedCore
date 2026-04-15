package com.darkniightz.core.dev;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

public class DeployStatusManager {
    public record DeployStatus(
            String lastDeployAt,
            boolean containerRunning,
            boolean restartPerformed,
            String jarName,
            String composeFile,
            String lastResult
    ) {}

    private static final DateTimeFormatter READABLE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Plugin plugin;
    private final File statusFile;

    public DeployStatusManager(Plugin plugin) {
        this.plugin = plugin;
        this.statusFile = new File(plugin.getDataFolder(), "deploy-status.properties");
    }

    public DeployStatus snapshot() {
        Properties props = new Properties();
        if (statusFile.isFile()) {
            try (FileInputStream in = new FileInputStream(statusFile)) {
                props.load(in);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not read deploy status: " + e.getMessage());
            }
        }

        String lastDeployAt = readable(props.getProperty("lastDeployAt", "unknown"));
        boolean containerRunning = Boolean.parseBoolean(props.getProperty("minecraftContainerRunning", "false"));
        boolean restartPerformed = Boolean.parseBoolean(props.getProperty("restartPerformed", "false"));
        String jarName = props.getProperty("jarName", "unknown");
        String composeFile = props.getProperty("composeFile", "docker-compose.yml");
        String lastResult = props.getProperty("lastResult", "unknown");
        return new DeployStatus(lastDeployAt, containerRunning, restartPerformed, jarName, composeFile, lastResult);
    }

    public String summaryLine() {
        DeployStatus status = snapshot();
        return "Deploy: " + status.lastDeployAt() + " | Container: " + (status.containerRunning() ? "running" : "stopped") + " | Result: " + status.lastResult();
    }

    public File getStatusFile() {
        return statusFile;
    }

    private String readable(String raw) {
        if (raw == null || raw.isBlank() || "unknown".equalsIgnoreCase(raw)) {
            return "unknown";
        }
        try {
            return READABLE.format(Instant.parse(raw));
        } catch (Exception ignored) {
            return raw;
        }
    }
}
