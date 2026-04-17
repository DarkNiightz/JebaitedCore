package com.darkniightz.core.eventmode;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Loads structured arena definitions from {@code event_mode.arena_registry} in {@code config.yml}.
 * When empty or invalid, {@link EventEngine} falls back to DB spawns and legacy {@code event_mode.koth} corners.
 */
public final class EventArenaRegistry {

    /** Normalised event kind bucket → arena key → config */
    private final Map<String, Map<String, ArenaConfig>> byKind = new HashMap<>();

    public void reload(FileConfiguration cfg, Logger log) {
        byKind.clear();
        ConfigurationSection root = cfg.getConfigurationSection("event_mode.arena_registry");
        if (root == null) {
            return;
        }
        for (String kindKey : root.getKeys(false)) {
            if ("enabled".equalsIgnoreCase(kindKey)) {
                continue;
            }
            String kind = normalizeKindKey(kindKey);
            List<?> rawList = root.getList(kindKey);
            if (rawList == null || rawList.isEmpty()) {
                continue;
            }
            Map<String, ArenaConfig> arenas = new HashMap<>();
            for (Object o : rawList) {
                if (!(o instanceof Map<?, ?> map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) map;
                ArenaConfig ac = parseArena(kind, m, log);
                if (ac != null) {
                    arenas.put(ac.key().toLowerCase(Locale.ROOT), ac);
                }
            }
            if (!arenas.isEmpty()) {
                byKind.put(kind, arenas);
            }
        }
        int total = byKind.values().stream().mapToInt(Map::size).sum();
        if (total > 0) {
            log.info("[EventArenaRegistry] Loaded " + total + " arena definition(s) from config.");
        }
    }

    private static String normalizeKindKey(String raw) {
        if (raw == null) return "";
        String k = raw.toLowerCase(Locale.ROOT).trim();
        return switch (k) {
            case "hardcore", "hardcore_ffa", "hardcoreffa" -> "ffa";
            case "hardcore_duels", "hardcoreduels" -> "duels";
            case "hardcore_koth", "hardcorekoth" -> "hardcore_koth";
            default -> k;
        };
    }

    public List<String> listArenaKeysForKind(String eventSpecKey) {
        String kind = normalizeKindKey(eventSpecKey == null ? "" : eventSpecKey.toLowerCase(Locale.ROOT));
        Map<String, ArenaConfig> m = byKind.get(kind);
        if (m == null || m.isEmpty()) return List.of();
        List<String> keys = new ArrayList<>(m.keySet());
        Collections.sort(keys);
        return keys;
    }

    public List<String> describeAll() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Map<String, ArenaConfig>> e : byKind.entrySet()) {
            for (ArenaConfig ac : e.getValue().values()) {
                lines.add("§7" + e.getKey() + " §8/ §f" + ac.key() + " §8— §7" + ac.displayName());
            }
        }
        return lines;
    }

    /** First arena for the kind, or null. */
    public ArenaConfig defaultArena(String eventSpecKey) {
        String kind = normalizeKindKey(eventSpecKey == null ? "" : eventSpecKey.toLowerCase(Locale.ROOT));
        Map<String, ArenaConfig> m = byKind.get(kind);
        if (m == null || m.isEmpty()) return null;
        return m.values().iterator().next();
    }

    public ArenaConfig get(String eventSpecKey, String arenaKey) {
        if (arenaKey == null || arenaKey.isBlank()) return null;
        String kind = normalizeKindKey(eventSpecKey == null ? "" : eventSpecKey.toLowerCase(Locale.ROOT));
        Map<String, ArenaConfig> m = byKind.get(kind);
        if (m == null) return null;
        return m.get(arenaKey.toLowerCase(Locale.ROOT));
    }

    private ArenaConfig parseArena(String kindBucket, Map<String, Object> m, Logger log) {
        String key = String.valueOf(m.getOrDefault("key", "default"));
        String display = m.get("display") != null ? String.valueOf(m.get("display")) : key;
        List<Location> spawns = parseSpawns(m.get("spawns"), log);

        ArenaConfig.KothHill hill = null;
        Integer kothDur = null;
        Object hillObj = m.get("hill");
        if (hillObj instanceof Map<?, ?> hm) {
            @SuppressWarnings("unchecked")
            Map<String, Object> hillMap = (Map<String, Object>) hm;
            hill = parseHill(hillMap, log);
        }
        Object durObj = m.get("duration_seconds");
        if (durObj instanceof Number n) {
            kothDur = Math.max(10, n.intValue());
        }

        ArenaConfig.CtfLayout ctf = ArenaConfig.CtfLayout.empty();
        Object ctfObj = m.get("ctf");
        if (ctfObj instanceof Map<?, ?> cm) {
            @SuppressWarnings("unchecked")
            Map<String, Object> ctfMap = (Map<String, Object>) cm;
            ctf = parseCtf(ctfMap, log);
        }

        return new ArenaConfig(key, display, spawns, hill, kothDur, ctf);
    }

    private List<Location> parseSpawns(Object spawnsObj, Logger log) {
        if (!(spawnsObj instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Location> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> sm)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> spawn = (Map<String, Object>) sm;
            Location loc = parseLocation(spawn, log);
            if (loc != null) out.add(loc);
        }
        return out;
    }

    private Location parseLocation(Map<String, Object> m, Logger log) {
        String worldName = m.get("world") != null ? String.valueOf(m.get("world")) : null;
        if (worldName == null || worldName.isBlank()) {
            log.warning("[EventArenaRegistry] Spawn missing world name — skipped.");
            return null;
        }
        World w = Bukkit.getWorld(worldName);
        if (w == null) {
            log.warning("[EventArenaRegistry] World not loaded '" + worldName + "' — spawn skipped.");
            return null;
        }
        double x = doubleVal(m.get("x"));
        double y = doubleVal(m.get("y"));
        double z = doubleVal(m.get("z"));
        float yaw = (float) doubleVal(m.get("yaw"));
        float pitch = (float) doubleVal(m.get("pitch"));
        return new Location(w, x, y, z, yaw, pitch);
    }

    private ArenaConfig.KothHill parseHill(Map<String, Object> hillMap, Logger log) {
        String world = hillMap.get("world") != null ? String.valueOf(hillMap.get("world")) : null;
        if (world == null || world.isBlank()) {
            log.warning("[EventArenaRegistry] Hill missing world — skipped.");
            return null;
        }
        int ax, ay, az, bx, by, bz;
        if (hillMap.get("min") instanceof List<?> minL && hillMap.get("max") instanceof List<?> maxL
                && minL.size() >= 3 && maxL.size() >= 3) {
            ax = intVal(minL.get(0));
            ay = intVal(minL.get(1));
            az = intVal(minL.get(2));
            bx = intVal(maxL.get(0));
            by = intVal(maxL.get(1));
            bz = intVal(maxL.get(2));
        } else {
            ax = intVal(hillMap.get("ax"));
            ay = intVal(hillMap.get("ay"));
            az = intVal(hillMap.get("az"));
            bx = intVal(hillMap.get("bx"));
            by = intVal(hillMap.get("by"));
            bz = intVal(hillMap.get("bz"));
        }
        return new ArenaConfig.KothHill(world, ax, ay, az, bx, by, bz);
    }

    private ArenaConfig.CtfLayout parseCtf(Map<String, Object> ctfMap, Logger log) {
        Location redSpawn = ctfMap.get("red_spawn") instanceof Map<?, ?> rm
                ? parseLocation(castMap(rm), log) : null;
        Location blueSpawn = ctfMap.get("blue_spawn") instanceof Map<?, ?> bm
                ? parseLocation(castMap(bm), log) : null;
        Location redFlag = ctfMap.get("red_flag") instanceof Map<?, ?> rf
                ? parseLocation(castMap(rf), log) : null;
        Location blueFlag = ctfMap.get("blue_flag") instanceof Map<?, ?> bf
                ? parseLocation(castMap(bf), log) : null;
        int win = ctfMap.get("win_score") instanceof Number n ? Math.max(1, n.intValue()) : 3;
        int ret = ctfMap.get("flag_return_seconds") instanceof Number n2 ? Math.max(5, n2.intValue()) : 30;
        return new ArenaConfig.CtfLayout(redSpawn, blueSpawn, redFlag, blueFlag, win, ret);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    private static double doubleVal(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }

    private static int intVal(Object o) {
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }
}
