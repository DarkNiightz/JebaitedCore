package com.darkniightz.core.system;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Optional mcMMO bridge that uses reflection so the core plugin has no hard dependency.
 */
public final class McMMOIntegration {

    private McMMOIntegration() {
    }

    public static boolean isEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("mcMMO")
                || Bukkit.getPluginManager().isPluginEnabled("McMMO");
    }

    @Nullable
    public static String getVersion() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("mcMMO");
        if (plugin == null) {
            plugin = Bukkit.getPluginManager().getPlugin("McMMO");
        }
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }
        return plugin.getDescription().getVersion();
    }

    @Nullable
    public static Integer getPowerLevel(OfflinePlayer player) {
        if (player == null || player.getUniqueId() == null || !isEnabled()) {
            return null;
        }

        try {
            Class<?> expApi = Class.forName("com.gmail.nossr50.api.ExperienceAPI");
            for (Method m : expApi.getMethods()) {
                if (!"getPowerLevel".equals(m.getName()) || m.getParameterCount() != 1) continue;
                if (!Modifier.isStatic(m.getModifiers())) continue;

                Class<?> p = m.getParameterTypes()[0];

                Object argument = null;
                if (p.isInstance(player)) {
                    argument = player;
                } else if (player.isOnline() && player.getPlayer() != null && p.isInstance(player.getPlayer())) {
                    argument = player.getPlayer();
                } else if (String.class.equals(p)) {
                    String name = player.getName();
                    if (name != null && !name.isBlank()) {
                        argument = name;
                    }
                } else if (java.util.UUID.class.equals(p)) {
                    argument = player.getUniqueId();
                }

                if (argument == null) {
                    continue;
                }

                try {
                    Object out = m.invoke(null, argument);
                    if (out instanceof Number n) {
                        return n.intValue();
                    }
                } catch (IllegalArgumentException ignored) {
                    // Try next overload if this one doesn't accept the resolved argument at runtime.
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Different mcMMO builds can move APIs; keep this optional and silent.
        }

        return null;
    }
}