package com.darkniightz.core.system;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Optional mcMMO bridge that uses reflection so the core plugin has no hard dependency.
 */
public final class McMMOIntegration {

    private static final AtomicBoolean LOGGED_POWER_LEVEL_MISS = new AtomicBoolean(false);

    private McMMOIntegration() {
    }

    public record LeaderboardRow(String playerName, int value) {
    }

    public record SkillLevel(String skillName, int level) {
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

    /**
     * One-shot startup probe: logs whether {@link #getPowerLevel(OfflinePlayer)} resolves for an online player.
     * Controlled by {@code integrations.mcmmo.bridge_self_test} in config.
     */
    public static void runBridgeSelfTest(JavaPlugin plugin) {
        if (!plugin.getConfig().getBoolean("integrations.mcmmo.bridge_self_test", false)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isEnabled()) {
                plugin.getLogger().warning("[mcMMO bridge] self-test: mcMMO not enabled.");
                return;
            }
            Player probe = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
            if (probe == null) {
                plugin.getLogger().info("[mcMMO bridge] self-test: no online players; skipped getPowerLevel probe.");
                return;
            }
            Integer pl = getPowerLevel(probe);
            if (pl == null) {
                plugin.getLogger().warning("[mcMMO bridge] self-test: getPowerLevel returned null for "
                        + probe.getName() + " — check ExperienceAPI compatibility (e.g. mcMMO 2.2.x).");
            } else {
                plugin.getLogger().info("[mcMMO bridge] self-test: OK getPowerLevel=" + pl + " for " + probe.getName() + ".");
            }
        }, 1L);
    }

    @Nullable
    public static Integer getPowerLevel(OfflinePlayer player) {
        if (player == null || player.getUniqueId() == null || !isEnabled()) {
            return null;
        }

        try {
            Class<?> expApi = Class.forName("com.gmail.nossr50.api.ExperienceAPI");
            for (Method m : expApi.getMethods()) {
                if (!"getPowerLevel".equals(m.getName()) || m.getParameterCount() != 1) {
                    continue;
                }
                if (!Modifier.isStatic(m.getModifiers())) {
                    continue;
                }

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

        if (LOGGED_POWER_LEVEL_MISS.compareAndSet(false, true) && isEnabled()) {
            Bukkit.getLogger().warning("[JebaitedCore] mcMMO ExperienceAPI.getPowerLevel could not be resolved; "
                    + "power level may show as N/A. Confirm mcMMO version (tested against 2.2.x API).");
        }

        return null;
    }

    /**
     * Non-child primary skills (enum names), for tab completion and displays.
     */
    public static List<String> primarySkillEnumNames() {
        if (!isEnabled()) {
            return List.of();
        }
        try {
            Class<?> skillTools = Class.forName("com.gmail.nossr50.util.skills.SkillTools");
            Field f = skillTools.getField("NON_CHILD_SKILLS");
            Object list = f.get(null);
            List<String> out = new ArrayList<>();
            if (list instanceof Iterable<?> it) {
                for (Object o : it) {
                    if (o != null) {
                        Method name = o.getClass().getMethod("name");
                        out.add(String.valueOf(name.invoke(o)));
                    }
                }
            }
            Collections.sort(out);
            return out;
        } catch (ReflectiveOperationException e) {
            return List.of();
        }
    }

    public static List<SkillLevel> collectSkillLevels(OfflinePlayer target) {
        if (target == null || !isEnabled()) {
            return List.of();
        }
        List<String> skills = primarySkillEnumNames();
        if (skills.isEmpty()) {
            return List.of();
        }
        List<SkillLevel> rows = new ArrayList<>();
        for (String skill : skills) {
            try {
                int lvl;
                if (target.isOnline() && target.getPlayer() != null) {
                    lvl = invokeGetLevelOnline(target.getPlayer(), skill);
                } else {
                    String name = target.getName();
                    if (name == null || name.isBlank()) {
                        continue;
                    }
                    lvl = invokeGetLevelOffline(name, skill);
                }
                rows.add(new SkillLevel(skill, lvl));
            } catch (Exception ignored) {
                // Skip skills the current mcMMO build rejects (offline DB miss, invalid skill, etc.)
            }
        }
        return rows;
    }

    private static int invokeGetLevelOnline(Player player, String skillEnumName) throws ReflectiveOperationException {
        Class<?> expApi = Class.forName("com.gmail.nossr50.api.ExperienceAPI");
        Method m = expApi.getMethod("getLevel", Player.class, String.class);
        Object out = m.invoke(null, player, skillEnumName);
        return out instanceof Number n ? n.intValue() : 0;
    }

    private static int invokeGetLevelOffline(String playerName, String skillEnumName) throws ReflectiveOperationException {
        Class<?> expApi = Class.forName("com.gmail.nossr50.api.ExperienceAPI");
        Method m = expApi.getMethod("getLevelOffline", String.class, String.class);
        Object out = m.invoke(null, playerName, skillEnumName);
        return out instanceof Number n ? n.intValue() : 0;
    }

    /**
     * Reads one page of leaderboard data via mcMMO's database manager (same query as /mctop).
     *
     * @param skillToken {@code null} or {@code "all"} for power level; otherwise a skill token matched by mcMMO's SkillTools
     */
    public static List<LeaderboardRow> readLeaderboardPage(@Nullable String skillToken, int page, int perPage) {
        if (!isEnabled() || page < 1 || perPage < 1) {
            return List.of();
        }
        try {
            Class<?> mcClazz = Class.forName("com.gmail.nossr50.mcMMO");
            Object dbm = mcClazz.getMethod("getDatabaseManager").invoke(null);
            Class<?> pstClass = Class.forName("com.gmail.nossr50.datatypes.skills.PrimarySkillType");
            Object skillArg = null;
            if (skillToken != null && !skillToken.isBlank() && !"all".equalsIgnoreCase(skillToken)) {
                skillArg = matchPrimarySkill(skillToken);
                if (skillArg == null) {
                    return List.of();
                }
            }
            Method read = dbm.getClass().getMethod("readLeaderboard", pstClass, int.class, int.class);
            @SuppressWarnings("unchecked")
            List<Object> raw = (List<Object>) read.invoke(dbm, skillArg, page, perPage);
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            List<LeaderboardRow> out = new ArrayList<>();
            for (Object row : raw) {
                String name = extractRecordField(row, "playerName");
                Integer value = extractRecordInt(row, "value");
                if (name != null && value != null) {
                    out.add(new LeaderboardRow(name, value));
                }
            }
            return out;
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c != null) {
                Bukkit.getLogger().log(Level.FINE, "[JebaitedCore] mcMMO readLeaderboard failed: " + c.getMessage(), c);
            }
            return List.of();
        } catch (ReflectiveOperationException e) {
            Bukkit.getLogger().log(Level.FINE, "[JebaitedCore] mcMMO readLeaderboard reflection failed.", e);
            return List.of();
        }
    }

    @Nullable
    public static Object matchPrimarySkill(String token) {
        if (token == null || token.isBlank() || !isEnabled()) {
            return null;
        }
        try {
            Class<?> mcClazz = Class.forName("com.gmail.nossr50.mcMMO");
            Object pluginInstance = mcClazz.getField("p").get(null);
            Object skillTools = pluginInstance.getClass().getMethod("getSkillTools").invoke(pluginInstance);
            Method match = skillTools.getClass().getMethod("matchSkill", String.class);
            return match.invoke(skillTools, token);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static @Nullable String extractRecordField(Object recordRow, String accessor) {
        if (recordRow == null) {
            return null;
        }
        try {
            Method m = recordRow.getClass().getMethod(accessor);
            Object v = m.invoke(recordRow);
            return v == null ? null : String.valueOf(v);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static @Nullable Integer extractRecordInt(Object recordRow, String accessor) {
        if (recordRow == null) {
            return null;
        }
        try {
            Method m = recordRow.getClass().getMethod(accessor);
            Object v = m.invoke(recordRow);
            if (v instanceof Number n) {
                return n.intValue();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    /**
     * One-line status for {@code /compat}: probes {@link #getPowerLevel} on the first online player when mcMMO is on.
     */
    public static String compatPowerLevelBridgeSummary() {
        if (!isEnabled()) {
            return "§cMISSING";
        }
        Player probe = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (probe == null) {
            return "§eSKIP §8(no online players)";
        }
        Integer pl = getPowerLevel(probe);
        if (pl == null) {
            return "§cFAIL §8(getPowerLevel null for " + probe.getName() + ")";
        }
        return "§aOK §8(" + probe.getName() + " PL=" + pl + ")";
    }

    /**
     * Overall power-level rank from mcMMO DB (same source as /mcrank total line).
     */
    @Nullable
    public static Integer getOverallRank(java.util.UUID uuid) {
        if (uuid == null || !isEnabled()) {
            return null;
        }
        try {
            Class<?> expApi = Class.forName("com.gmail.nossr50.api.ExperienceAPI");
            Method m = expApi.getMethod("getPlayerRankOverall", java.util.UUID.class);
            Object out = m.invoke(null, uuid);
            if (out instanceof Number n) {
                return n.intValue();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return null;
    }

    /**
     * Per-skill leaderboard rank (non-child skills only).
     */
    @Nullable
    public static Integer getSkillRank(java.util.UUID uuid, String skillEnumName) {
        if (uuid == null || skillEnumName == null || skillEnumName.isBlank() || !isEnabled()) {
            return null;
        }
        try {
            Class<?> expApi = Class.forName("com.gmail.nossr50.api.ExperienceAPI");
            Method m = expApi.getMethod("getPlayerRankSkill", java.util.UUID.class, String.class);
            Object out = m.invoke(null, uuid, skillEnumName);
            if (out instanceof Number n) {
                return n.intValue();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return null;
    }
}
