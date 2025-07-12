package com.darkniightz.main.managers;

import com.darkniightz.main.Core;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogManager {

    private static LogManager instance;
    private final File logFile;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private LogManager(Plugin plugin) {
        File logsDir = new File(plugin.getDataFolder(), "logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        logFile = new File(logsDir, "actions.log");
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create log file: " + e.getMessage());
            }
        }
    }

    public static LogManager getInstance(Plugin plugin) {
        if (instance == null) {
            instance = new LogManager(plugin);
        }
        return instance;
    }

    public void log(String message) {
        String timestamp = dateFormat.format(new Date());
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            writer.println("[" + timestamp + "] " + message);
        } catch (IOException e) {
            Core.getInstance().getLogger().warning("Failed to write to log: " + e.getMessage());
        }
    }
}