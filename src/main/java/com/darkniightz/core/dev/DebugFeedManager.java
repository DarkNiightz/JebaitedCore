package com.darkniightz.core.dev;

import com.darkniightz.core.system.MaterialCompat;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class DebugFeedManager {
    public enum Category {
        SYSTEM,
        COMMAND,
        LISTENER,
        JOIN,
        MODERATION,
        COSMETIC,
        GADGET,
        PREVIEW,
        EVENT
    }

    public static final class DebugEvent {
        public final long timestamp;
        public final Category category;
        public final Material icon;
        public final String title;
        public final List<String> details;

        private DebugEvent(long timestamp, Category category, Material icon, String title, List<String> details) {
            this.timestamp = timestamp;
            this.category = category;
            this.icon = icon;
            this.title = title;
            this.details = details;
        }
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Plugin plugin;
    private final Deque<DebugEvent> events = new ArrayDeque<>();
    private final int maxEvents;

    public DebugFeedManager(Plugin plugin) {
        this.plugin = plugin;
        this.maxEvents = Math.max(25, plugin.getConfig().getInt("debug.feed.max_events", 96));
    }

    public void record(Category category, Material icon, String title, List<String> details) {
        List<String> safeDetails = details == null ? List.of() : List.copyOf(details);
        synchronized (events) {
            events.addFirst(new DebugEvent(System.currentTimeMillis(), category, icon, title, safeDetails));
            while (events.size() > maxEvents) {
                events.removeLast();
            }
        }
    }

    public void recordPlayer(Category category, Material icon, Player player, String title, List<String> details) {
        List<String> enriched = new ArrayList<>();
        if (player != null) {
            enriched.add("§7Actor: §f" + player.getName());
            enriched.add("§7UUID: §f" + player.getUniqueId());
        }
        if (details != null) {
            enriched.addAll(details);
        }
        record(category, icon, title, enriched);
    }

    public void recordSystem(String title, List<String> details) {
        record(Category.SYSTEM, Material.COMMAND_BLOCK, title, details);
    }

    public void recordCommand(Player player, String commandLine) {
        recordPlayer(Category.COMMAND, Material.WRITABLE_BOOK, player, "Command: /" + commandLine, List.of("§7Executed from chat/command pipeline."));
    }

    public void recordListener(String name, List<String> details) {
        record(Category.LISTENER, Material.LEVER, "Listener: " + name, details);
    }

    public void recordJoin(Player player, String title, List<String> details) {
        recordPlayer(Category.JOIN, Material.NAME_TAG, player, title, details);
    }

    public void recordModeration(Player player, String title, List<String> details) {
        recordPlayer(Category.MODERATION, Material.IRON_SWORD, player, title, details);
    }

    public void recordCosmetic(Player player, String title, List<String> details) {
        recordPlayer(Category.COSMETIC, Material.BLAZE_POWDER, player, title, details);
    }

    public void recordGadget(Player player, String title, List<String> details) {
        recordPlayer(Category.GADGET, MaterialCompat.resolve(Material.PAPER, "FIREWORK_ROCKET", "FIREWORK", "PAPER"), player, title, details);
    }

    public void recordPreview(Player player, String title, List<String> details) {
        recordPlayer(Category.PREVIEW, MaterialCompat.resolve(Material.COMPASS, "SPYGLASS", "COMPASS"), player, title, details);
    }

    public List<DebugEvent> snapshot() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    public List<DebugEvent> snapshot(Category category) {
        synchronized (events) {
            return events.stream()
                    .filter(event -> event.category == category)
                    .toList();
        }
    }

    public String formatTime(long timestamp) {
        return TIME.format(Instant.ofEpochMilli(timestamp));
    }

    public void clear() {
        synchronized (events) {
            events.clear();
        }
    }
}
