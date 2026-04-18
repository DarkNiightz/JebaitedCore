package com.darkniightz.core.system;

import com.darkniightz.main.JebaitedCore;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * Forwards server log lines to Discord (rate-limited). Attached to {@link org.bukkit.Bukkit#getLogger()}.
 */
public final class DiscordConsoleLogHandler extends Handler {
    private final JavaPlugin plugin;
    private long windowStartMs;
    private int linesInWindow;

    public DiscordConsoleLogHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        setLevel(java.util.logging.Level.INFO);
    }

    @Override
    public void publish(LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }
        if (!(plugin instanceof JebaitedCore core)) {
            return;
        }
        var svc = core.getDiscordIntegrationService();
        if (svc == null || !svc.isConsoleMirrorEnabled()) {
            return;
        }
        String msg = record.getMessage();
        if (msg == null) {
            return;
        }
        if (record.getParameters() != null && record.getParameters().length > 0) {
            try {
                msg = java.text.MessageFormat.format(msg, record.getParameters());
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (msg.isBlank()) {
            return;
        }
        Throwable t = record.getThrown();
        if (t != null) {
            msg = msg + " — " + t.getClass().getSimpleName();
        }
        long now = System.currentTimeMillis();
        if (now - windowStartMs > 1000L) {
            windowStartMs = now;
            linesInWindow = 0;
        }
        if (linesInWindow++ > 10) {
            return;
        }
        String threadName = Thread.currentThread().getName();
        String line = "[" + record.getLevel().getName() + "] [" + threadName + "] " + msg;
        if (line.length() > 1800) {
            line = line.substring(0, 1800) + "…";
        }
        String finalLine = line;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> svc.notifyConsoleMirror(finalLine));
    }

    @Override
    public void flush() {}

    @Override
    public void close() throws SecurityException {}
}
