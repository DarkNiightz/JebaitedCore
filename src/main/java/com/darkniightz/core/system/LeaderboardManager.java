package com.darkniightz.core.system;

import com.darkniightz.main.JebaitedCore;
import com.darkniightz.main.PlayerProfileDAO;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class LeaderboardManager {
    public enum Category {
        BLOCKS_BROKEN("blocks_broken", "Blocks Broken"),
        CROPS_BROKEN("crops_broken", "Crops Broken"),
        FISH_CAUGHT("fish_caught", "Fish Caught"),
        MCMMO_LEVEL("mcmmo_level", "mcMMO Level"),
        PLAYTIME("playtime_ms", "Playtime"),
        KILLS("kills", "Kills"),
        BOSSES_KILLED("bosses_killed", "Bosses Killed"),
        EVENT_WINS_COMBAT("event_wins_combat", "Combat Event Wins"),
        EVENT_WINS_CHAT("event_wins_chat", "Chat Event Wins"),
        EVENT_WINS_HARDCORE("event_wins_hardcore", "Hardcore Event Wins");

        public final String key;
        public final String title;

        Category(String key, String title) {
            this.key = key;
            this.title = title;
        }

        public static Category fromInput(String raw) {
            if (raw == null) return null;
            String needle = raw.trim().toLowerCase(Locale.ROOT);
            for (Category c : values()) {
                if (c.name().equalsIgnoreCase(needle) || c.key.equalsIgnoreCase(needle)) {
                    return c;
                }
            }
            return null;
        }
    }

    private final JebaitedCore plugin;
    private final Map<String, Definition> definitions = new HashMap<>();
    private BukkitTask refreshTask;
    private static final long REFRESH_PERIOD_TICKS = 20L * 60L;

    public LeaderboardManager(JebaitedCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        loadDefinitions();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::refreshAll);
        if (refreshTask != null) refreshTask.cancel();
        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshAll, REFRESH_PERIOD_TICKS, REFRESH_PERIOD_TICKS);
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    public List<String> listDefinitionIds() {
        return new ArrayList<>(definitions.keySet());
    }

    public List<String> listDefinitionDetails() {
        List<String> out = new ArrayList<>();
        for (Definition def : definitions.values()) {
            String world = def.location.getWorld() == null ? "unknown" : def.location.getWorld().getName();
            out.add(def.id + " [" + def.category.key + "] @ " + world + " ("
                    + def.location.getBlockX() + ", " + def.location.getBlockY() + ", " + def.location.getBlockZ() + ")");
        }
        out.sort(String::compareToIgnoreCase);
        return out;
    }

    public int definitionCount() {
        return definitions.size();
    }

    public long refreshIntervalSeconds() {
        return REFRESH_PERIOD_TICKS / 20L;
    }

    public boolean refreshDefinition(String id) {
        if (id == null || id.isBlank()) return false;
        Definition def = definitions.get(id.toLowerCase(Locale.ROOT));
        if (def == null) return false;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> refresh(def));
        return true;
    }

    public int refreshNow() {
        int count = definitions.size();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::refreshAll);
        return count;
    }

    public String debugDefinition(String id) {
        if (id == null || id.isBlank()) return "missing id";
        Definition def = definitions.get(id.toLowerCase(Locale.ROOT));
        if (def == null) return "definition not found";
        String world = def.location.getWorld() == null ? "unknown" : def.location.getWorld().getName();
        int taggedNearby = 0;
        if (def.location.getWorld() != null) {
            taggedNearby = (int) def.location.getWorld().getNearbyEntities(def.location.clone().add(0.5, 2.4, 0.5), 2.0, 4.0, 2.0,
                    e -> e instanceof ArmorStand && e.getScoreboardTags().contains("jc_lb_" + def.id)).size();
        }
        return "id=" + def.id
                + ", category=" + def.category.key
                + ", world=" + world
                + ", xyz=" + def.location.getBlockX() + "/" + def.location.getBlockY() + "/" + def.location.getBlockZ()
                + ", trackedEntities=" + def.entityIds.size()
                + ", nearbyTagged=" + taggedNearby;
    }

    public boolean setAt(String id, Category category, Location location) {
        if (id == null || id.isBlank() || category == null || location == null || location.getWorld() == null) {
            return false;
        }
        Definition def = new Definition(id.toLowerCase(Locale.ROOT), category, location.clone());
        definitions.put(def.id, def);
        saveDefinition(def);
        refresh(def);
        return true;
    }

    public boolean remove(String id) {
        if (id == null || id.isBlank()) return false;
        Definition def = definitions.remove(id.toLowerCase(Locale.ROOT));
        if (def == null) return false;
        despawn(def);
        plugin.getConfig().set("leaderboards.definitions." + def.id, null);
        plugin.saveConfig();
        return true;
    }

    private void loadDefinitions() {
        definitions.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("leaderboards.definitions");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;
            Category cat = Category.fromInput(sec.getString("category"));
            World world = Bukkit.getWorld(sec.getString("world", ""));
            if (cat == null || world == null) continue;
            Location loc = new Location(world, sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"));
            definitions.put(id.toLowerCase(Locale.ROOT), new Definition(id.toLowerCase(Locale.ROOT), cat, loc));
        }
    }

    private void saveDefinition(Definition def) {
        String path = "leaderboards.definitions." + def.id;
        plugin.getConfig().set(path + ".category", def.category.key);
        plugin.getConfig().set(path + ".world", def.location.getWorld().getName());
        plugin.getConfig().set(path + ".x", def.location.getX());
        plugin.getConfig().set(path + ".y", def.location.getY());
        plugin.getConfig().set(path + ".z", def.location.getZ());
        plugin.saveConfig();
    }

    private void refreshAll() {
        // Called on an async thread — build all line sets before touching the world
        List<Map.Entry<Definition, List<String>>> built = new ArrayList<>();
        for (Definition def : definitions.values()) {
            built.add(Map.entry(def, buildLines(def.category)));
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Map.Entry<Definition, List<String>> e : built) {
                despawn(e.getKey());
                spawn(e.getKey(), e.getValue());
            }
        });
    }

    private void refresh(Definition def) {
        // Called on an async thread — fetch lines, then apply on main thread
        List<String> lines = buildLines(def.category);
        Bukkit.getScheduler().runTask(plugin, () -> {
            despawn(def);
            spawn(def, lines);
        });
    }

    private List<String> buildLines(Category category) {
        List<String> lines = new ArrayList<>();
        lines.add("§d§l" + category.title + " §7Top 10");
        List<Entry> top = getTop(category, 10);
        for (int i = 0; i < 10; i++) {
            if (i < top.size()) {
                Entry e = top.get(i);
                lines.add("§f#" + (i + 1) + " §d" + e.name + " §8- §b" + e.valueText);
            } else {
                lines.add("§f#" + (i + 1) + " §7---");
            }
        }
        return lines;
    }

    private List<Entry> getTop(Category category, int limit) {
        if (category == Category.MCMMO_LEVEL) {
            return getTopMcMMO(limit);
        }
        PlayerProfileDAO dao = plugin.getPlayerProfileDAO();
        if (dao == null) return List.of();
        List<PlayerProfileDAO.StatTopRecord> rows = dao.getTopByStat(category.key, limit);
        List<Entry> out = new ArrayList<>();
        for (PlayerProfileDAO.StatTopRecord row : rows) {
            out.add(new Entry(row.username(), formatValue(category, row.value())));
        }
        return out;
    }

    private List<Entry> getTopMcMMO(int limit) {
        PlayerProfileDAO dao = plugin.getPlayerProfileDAO();
        if (dao == null) return List.of();
        List<EntryWithValue> values = new ArrayList<>();
        for (UUID uuid : dao.listAllPlayerUuids()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            Integer level = McMMOIntegration.getPowerLevel(op);
            if (level == null) continue;
            String name = op.getName() == null ? uuid.toString().substring(0, 8) : op.getName();
            values.add(new EntryWithValue(name, level));
        }
        values.sort(Comparator.comparingLong((EntryWithValue v) -> v.value).reversed().thenComparing(v -> v.name));
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, values.size()); i++) {
            EntryWithValue v = values.get(i);
            out.add(new Entry(v.name, Long.toString(v.value)));
        }
        return out;
    }

    private String formatValue(Category category, long value) {
        if (category == Category.PLAYTIME) {
            long totalMinutes = Math.max(0L, value / 60000L);
            long hours = totalMinutes / 60L;
            long mins = totalMinutes % 60L;
            return hours + "h " + mins + "m";
        }
        return Long.toString(value);
    }

    private void spawn(Definition def, List<String> lines) {
        World world = def.location.getWorld();
        if (world == null) return;
        for (int i = 0; i < lines.size(); i++) {
            Location lineLoc = def.location.clone().add(0.5, 2.4 - (i * 0.26), 0.5);
            ArmorStand as = (ArmorStand) world.spawnEntity(lineLoc, EntityType.ARMOR_STAND);
            as.setInvisible(true);
            as.setMarker(false);
            as.setGravity(false);
            as.setSmall(true);
            as.setBasePlate(false);
            as.setInvulnerable(true);
            as.setPersistent(true);
            as.setCollidable(false);
            as.setCustomNameVisible(true);
            as.setCustomName(lines.get(i));
            as.addScoreboardTag("jc_lb");
            as.addScoreboardTag("jc_lb_" + def.id);
            def.entityIds.add(as.getUniqueId());
        }
    }

    private void despawn(Definition def) {
        World world = def.location.getWorld();
        if (world == null) return;
        for (UUID id : new ArrayList<>(def.entityIds)) {
            var entity = Bukkit.getEntity(id);
            if (entity instanceof LivingEntity living && !living.isDead()) {
                living.remove();
            }
        }
        def.entityIds.clear();

        for (var entity : world.getNearbyEntities(def.location.clone().add(0.5, 2.4, 0.5), 2.0, 4.0, 2.0,
                e -> e instanceof ArmorStand && e.getScoreboardTags().contains("jc_lb_" + def.id))) {
            entity.remove();
        }
    }

    private static final class Definition {
        final String id;
        final Category category;
        final Location location;
        final List<UUID> entityIds = new ArrayList<>();

        private Definition(String id, Category category, Location location) {
            this.id = id;
            this.category = category;
            this.location = location;
        }
    }

    private record Entry(String name, String valueText) {}
    private record EntryWithValue(String name, long value) {}
}
