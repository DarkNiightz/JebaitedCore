package com.darkniightz.core.world;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

public class PreviewPedestalManager {
    private final Plugin plugin;

    public PreviewPedestalManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public Location getConfiguredPedestal() {
        FileConfiguration cfg = plugin.getConfig();
        String raw = cfg.getString("hub.preview_pedestal.location", null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(":");
        if (parts.length < 6) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = Float.parseFloat(parts[4]);
            float pitch = Float.parseFloat(parts[5]);
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException ex) {
            plugin.getLogger().warning("Invalid preview pedestal location in config: " + raw);
            return null;
        }
    }

    public void setConfiguredPedestal(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        String encoded = String.format(Locale.ROOT, "%s:%.3f:%.3f:%.3f:%.2f:%.2f",
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch());
        plugin.getConfig().set("hub.preview_pedestal.location", encoded);
        plugin.saveConfig();
    }

    public void clearConfiguredPedestal() {
        plugin.getConfig().set("hub.preview_pedestal.location", null);
        plugin.saveConfig();
    }
}
