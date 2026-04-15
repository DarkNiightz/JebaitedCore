package com.darkniightz.core.system;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Small compatibility helper for resolving item materials safely across
 * multiple Minecraft version families.
 */
public final class MaterialCompat {
    private MaterialCompat() {
    }

    public static Material resolve(Material fallback, String... candidateNames) {
        if (candidateNames != null) {
            for (String candidate : candidateNames) {
                Material resolved = match(candidate);
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        return fallback == null ? Material.STONE : fallback;
    }

    public static Material resolveConfigured(String configuredName, Material fallback, String... fallbackNames) {
        Material configured = match(configuredName);
        if (configured != null) {
            return configured;
        }
        return resolve(fallback, fallbackNames);
    }

    public static boolean isSupported(String materialName) {
        return match(materialName) != null;
    }

    private static Material match(String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return null;
        }

        Material material = Material.matchMaterial(materialName);
        if (material != null) {
            return material;
        }

        try {
            return Material.valueOf(materialName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
