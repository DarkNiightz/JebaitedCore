package com.darkniightz.core.system;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;

import java.util.Locale;

/**
 * Resolves sound names safely across server versions.
 */
public final class SoundCompat {
    private SoundCompat() {
    }

    public static Sound resolve(Sound fallback, String... candidateNames) {
        if (candidateNames != null) {
            for (String candidate : candidateNames) {
                Sound sound = match(candidate);
                if (sound != null) {
                    return sound;
                }
            }
        }
        return fallback;
    }

    public static void play(World world, Location location, float volume, float pitch, Sound fallback, String... candidateNames) {
        if (world == null || location == null) {
            return;
        }
        Sound sound = resolve(fallback, candidateNames);
        if (sound != null) {
            world.playSound(location, sound, volume, pitch);
        }
    }

    private static Sound match(String soundName) {
        if (soundName == null || soundName.isBlank()) {
            return null;
        }
        try {
            return Sound.valueOf(soundName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
