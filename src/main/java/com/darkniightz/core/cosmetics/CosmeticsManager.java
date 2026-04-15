package com.darkniightz.core.cosmetics;

import com.darkniightz.core.system.MaterialCompat;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.time.LocalDate;

public class CosmeticsManager {
    public enum Category { PARTICLES, TRAILS, GADGETS, TAGS }
    public enum TagPosition { PREFIX, SUFFIX }

    public static final class Cosmetic {
        public final String key;
        public final Category category;
        public final Material icon;
        public final String name;
        public final List<String> lore;
        public final int price;
        public final boolean enabled;
        public final String actionKey;
        public final int cooldownSeconds;
        public final TagPosition tagPosition;

        public Cosmetic(String key, Category category, Material icon, String name, List<String> lore, int price, boolean enabled) {
            this(key, category, icon, name, lore, price, enabled, null, 0, TagPosition.PREFIX);
        }

        public Cosmetic(String key, Category category, Material icon, String name, List<String> lore, int price, boolean enabled, String actionKey, int cooldownSeconds) {
            this(key, category, icon, name, lore, price, enabled, actionKey, cooldownSeconds, TagPosition.PREFIX);
        }

        public Cosmetic(String key, Category category, Material icon, String name, List<String> lore, int price, boolean enabled, String actionKey, int cooldownSeconds, TagPosition tagPosition) {
            this.key = key;
            this.category = category;
            this.icon = icon;
            this.name = name;
            this.lore = lore;
            this.price = price;
            this.enabled = enabled;
            this.actionKey = actionKey;
            this.cooldownSeconds = Math.max(0, cooldownSeconds);
            this.tagPosition = tagPosition == null ? TagPosition.PREFIX : tagPosition;
        }
    }

    private final Plugin plugin;
    private final Map<Category, List<Cosmetic>> byCategory = new EnumMap<>(Category.class);
    private final Map<String, Cosmetic> byKey = new HashMap<>();

    public CosmeticsManager(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        byCategory.clear(); byKey.clear();
        for (Category c : Category.values()) byCategory.put(c, new ArrayList<>());
        // Preferred path (new structure)
        ConfigurationSection catalog = plugin.getConfig().getConfigurationSection("cosmetics.catalog");
        if (catalog != null) {
            loadCategory(catalog.getConfigurationSection("particles"), Category.PARTICLES);
            loadCategory(catalog.getConfigurationSection("trails"), Category.TRAILS);
            // Gadgets optional in catalog; still support
            loadCategory(catalog.getConfigurationSection("gadgets"), Category.GADGETS);
            loadCategory(catalog.getConfigurationSection("tags"), Category.TAGS);
        } else {
            // Fallback minimal defaults
            int def = plugin.getConfig().getInt("cosmetics.rules.price_default", 100);
            add(new Cosmetic("ember_sparks", Category.PARTICLES, Material.BLAZE_POWDER, "§6Ember Sparks", List.of("§7Warm ember particles"), def, true, "ember_sparks", 0));
            add(new Cosmetic("leaf_whirl", Category.PARTICLES, MaterialCompat.resolve(Material.VINE, "OAK_LEAVES", "LEAVES", "VINE"), "§2Leaf Whirl", List.of("§7Forest breeze"), def, true, "leaf_whirl", 0));
            add(new Cosmetic("aqua_bubbles", Category.PARTICLES, MaterialCompat.resolve(Material.WATER_BUCKET, "HEART_OF_THE_SEA", "WATER_BUCKET"), "§bAqua Bubbles", List.of("§7Bubbly vibe"), def, true, "aqua_bubbles", 0));
            add(new Cosmetic("prism_glow", Category.PARTICLES, MaterialCompat.resolve(Material.QUARTZ, "AMETHYST_SHARD", "PRISMARINE_CRYSTALS", "QUARTZ"), "§dPrism Glow", List.of("§7A soft crystal bloom"), def, true, "prism_glow", 0));
            add(new Cosmetic("rune_trail", Category.TRAILS, Material.ENCHANTED_BOOK, "§5Rune Trail", List.of("§7Magical ink behind your steps"), def, true, "rune_trail", 0));
            add(new Cosmetic("feather_trail", Category.TRAILS, Material.FEATHER, "§fFeather Trail", List.of("§7A light drifting wake"), def, true, "feather_trail", 0));
            add(new Cosmetic("star_trail", Category.TRAILS, Material.NETHER_STAR, "§eStar Trail", List.of("§7Tiny stars everywhere"), def, true, "star_trail", 0));
            add(new Cosmetic("firework_popper", Category.GADGETS, MaterialCompat.resolve(Material.PAPER, "FIREWORK_ROCKET", "FIREWORK", "PAPER"), "§dFirework Popper", List.of("§7A cheerful burst of sparkles"), def, true, "firework_popper", 3));
            add(new Cosmetic("slime_launcher", Category.GADGETS, Material.SLIME_BALL, "§aSlime Launcher", List.of("§7A silly launch with bounce"), def, true, "slime_launcher", 4));
            add(new Cosmetic("bubble_wand", Category.GADGETS, Material.BLAZE_ROD, "§bBubble Wand", List.of("§7A wand that spits out bubbles"), def, true, "bubble_wand", 2));
            add(new Cosmetic("prism_ping", Category.GADGETS, MaterialCompat.resolve(Material.QUARTZ, "AMETHYST_SHARD", "PRISMARINE_CRYSTALS", "QUARTZ"), "§dPrism Ping", List.of("§7Tap the air and scatter color"), def, true, "prism_ping", 3));
            add(new Cosmetic("confetti_cannon", Category.GADGETS, Material.CAKE, "§6Confetti Cannon", List.of("§7A tiny party in your hand"), def, true, "confetti_cannon", 5));
            add(new Cosmetic("tag_legend", Category.TAGS, Material.NAME_TAG, "§6Legend", List.of("§7Server tag for standout players"), def, true, "tag_legend", 0));
            add(new Cosmetic("tag_creator", Category.TAGS, Material.NAME_TAG, "§dCreator", List.of("§7Tag for builders and creators"), def, true, "tag_creator", 0));
        }

        // Always keep a few curated extras available, even if the config is still catching up.
        ensure(new Cosmetic("nebula_orbit", Category.PARTICLES, Material.NETHER_STAR, "§5Nebula Orbit",
                List.of("§7A drifting little galaxy halo."), 175, true, "nebula_orbit", 0));
        ensure(new Cosmetic("prism_trail", Category.TRAILS, MaterialCompat.resolve(Material.NETHER_STAR, "AMETHYST_CLUSTER", "AMETHYST_SHARD", "END_CRYSTAL", "NETHER_STAR"), "§dPrism Trail",
                List.of("§7A pastel streak that keeps changing."), 140, true, "prism_trail", 0));
        ensure(new Cosmetic("note_burst", Category.GADGETS, MaterialCompat.resolve(Material.NOTE_BLOCK, "MUSIC_DISC_5", "MUSIC_DISC_11", "NOTE_BLOCK"), "§eNote Burst",
                List.of("§7Pop a musical little sound blast."), 90, true, "note_burst", 2));
        ensure(new Cosmetic("sparkler", Category.GADGETS, Material.GLOWSTONE_DUST, "§6Sparkler",
                List.of("§7A handheld sparkle wand."), 120, true, "sparkler", 2));
        ensure(new Cosmetic("paint_splatter", Category.GADGETS, MaterialCompat.resolve(Material.SLIME_BALL, "LIME_DYE", "CACTUS", "SLIME_BALL"), "§aPaint Splatter",
                List.of("§7Explode a patch of playful color."), 140, true, "paint_splatter", 5));
        ensure(new Cosmetic("mini_firework", Category.GADGETS, MaterialCompat.resolve(Material.GLOWSTONE_DUST, "FIREWORK_STAR", "FIREWORK_CHARGE", "GLOWSTONE_DUST"), "§dMini Firework",
                List.of("§7A tiny burst with a clean pop."), 110, true, "mini_firework", 3));
        ensure(new Cosmetic("ghost_lantern", Category.GADGETS, MaterialCompat.resolve(Material.TORCH, "SOUL_LANTERN", "LANTERN", "TORCH"), "§bGhost Lantern",
                List.of("§7A little spooky spotlight with souls."), 150, true, "ghost_lantern", 6));
        ensure(new Cosmetic("lightning_rod", Category.GADGETS, Material.BLAZE_ROD, "§eLightning Rod",
                List.of("§7Call a dramatic spark from above."), 160, true, "lightning_rod", 6));
        ensure(new Cosmetic("flower_bomb", Category.GADGETS, Material.POPPY, "§dFlower Bomb",
                List.of("§7Turn the ground into a tiny garden."), 130, true, "flower_bomb", 4));
        ensure(new Cosmetic("disco_orb", Category.GADGETS, MaterialCompat.resolve(Material.NETHER_STAR, "AMETHYST_CLUSTER", "AMETHYST_SHARD", "END_CRYSTAL", "NETHER_STAR"), "§dDisco Orb",
                List.of("§7A pulsing party orb with notes."), 165, true, "disco_orb", 5));
        ensure(new Cosmetic("time_bubble", Category.GADGETS, Material.CLOCK, "§bTime Bubble",
                List.of("§7A wobbly slow-motion bubble."), 155, true, "time_bubble", 5));
        ensure(new Cosmetic("sparkler_trail", Category.GADGETS, Material.GLOWSTONE_DUST, "§6Sparkler Trail",
                List.of("§7A trailing ribbon of sparkles."), 125, true, "sparkler_trail", 2));
        ensure(new Cosmetic("animal_lover", Category.TAGS, Material.NAME_TAG, "§aAnimal Lover",
            List.of("§7For players with a soft spot for every creature."), 140, true, "animal_lover", 0, TagPosition.PREFIX));
        ensure(new Cosmetic("event_champion", Category.TAGS, Material.NAME_TAG, "§6Event Champion",
            List.of("§7A flex tag for top event grinders."), 170, true, "event_champion", 0, TagPosition.PREFIX));
        ensure(new Cosmetic("holiday_spirit", Category.TAGS, Material.NAME_TAG, "§cHoliday Spirit",
            List.of("§7Seasonal tag slot for annual events."), 150, true, "holiday_spirit", 0, TagPosition.PREFIX));
        ensure(new Cosmetic("legacy_member", Category.TAGS, Material.NAME_TAG, "§bLegacy Member",
            List.of("§7A clean suffix for long-time players."), 180, true, "legacy_member", 0, TagPosition.SUFFIX));
        ensure(new Cosmetic("chaos_goblin", Category.TAGS, Material.NAME_TAG, "§dChaos Goblin",
            List.of("§7For players who bring the fun and mayhem."), 160, true, "chaos_goblin", 0, TagPosition.SUFFIX));
        ensure(new Cosmetic("tag_custom", Category.TAGS, Material.NAME_TAG, "§5Custom Tag",
            List.of("§7Unlocks /tag to set your own colored text.", "§8Use & color codes and activate anytime."), 300, true, "tag_custom", 0, TagPosition.PREFIX));
    }

    private void loadCategory(ConfigurationSection sec, Category cat) {
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection c = sec.getConfigurationSection(key);
            if (c == null) continue;
            boolean enabled = c.getBoolean("enabled", true);
            int price = c.getInt("price", plugin.getConfig().getInt("cosmetics.rules.price_default", 100));
            Material icon = defaultIcon(cat);
            String iconName = c.getString("icon");
            if (iconName != null) {
                icon = MaterialCompat.resolveConfigured(iconName, defaultIcon(cat), "BARRIER", "PAPER");
            }
            String pretty = c.getString("display_name", c.getString("name", prettyName(key, cat)));
            List<String> lore = new ArrayList<>();
            List<String> customLore = c.getStringList("lore");
            if (customLore != null && !customLore.isEmpty()) lore.addAll(customLore);
            if (lore.isEmpty()) {
                lore.add("§7Price: §6" + price + " coins");
            }
            String action = c.getString("action", key);
            int cooldownSeconds = c.getInt("cooldown_seconds", 0);
            TagPosition tagPosition = TagPosition.PREFIX;
            if (cat == Category.TAGS) {
                String raw = c.getString("position", "prefix");
                if (raw != null && raw.equalsIgnoreCase("suffix")) {
                    tagPosition = TagPosition.SUFFIX;
                }
            }
            add(new Cosmetic(key, cat, icon, pretty, lore, price, enabled, action, cooldownSeconds, tagPosition));
        }
    }

    private void add(Cosmetic cosmetic) {
        byCategory.get(cosmetic.category).add(cosmetic);
        byKey.put(cosmetic.key, cosmetic);
    }

    private void ensure(Cosmetic cosmetic) {
        if (byKey.containsKey(cosmetic.key)) return;
        add(cosmetic);
    }

    public List<Cosmetic> getByCategory(Category c) { return Collections.unmodifiableList(byCategory.getOrDefault(c, List.of())); }
    public Cosmetic get(String key) { return byKey.get(key); }
    public List<Cosmetic> getAll() {
        List<Cosmetic> all = new ArrayList<>();
        all.addAll(getByCategory(Category.PARTICLES));
        all.addAll(getByCategory(Category.TRAILS));
        all.addAll(getByCategory(Category.GADGETS));
        all.addAll(getByCategory(Category.TAGS));
        return Collections.unmodifiableList(all);
    }

    public Cosmetic getFeaturedCosmetic() {
        List<Cosmetic> pool = new ArrayList<>();
        for (Cosmetic cosmetic : getAll()) {
            if (cosmetic != null && cosmetic.enabled) {
                pool.add(cosmetic);
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        int idx = Math.floorMod(LocalDate.now().getDayOfYear(), pool.size());
        return pool.get(idx);
    }

    public String rarityLabel(Cosmetic cosmetic) {
        if (cosmetic == null) return "§7Common";
        int price = Math.max(0, cosmetic.price);
        if (price >= 175) return "§6Mythic";
        if (price >= 150) return "§dEpic";
        if (price >= 125) return "§bRare";
        if (price >= 100) return "§aUncommon";
        return "§7Common";
    }

    private static Material defaultIcon(Category cat) {
        return switch (cat) {
            case PARTICLES -> Material.BLAZE_POWDER;
            case TRAILS -> Material.FEATHER;
            case GADGETS -> Material.STICK;
            case TAGS -> Material.NAME_TAG;
        };
    }

    private static String prettyName(String key, Category cat) {
        String base = Arrays.stream(key.split("[_-]"))
                .filter(s -> !s.isEmpty())
                .map(s -> s.substring(0,1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT))
                .reduce((a,b) -> a + " " + b).orElse(key);
        String color = switch (cat) {
            case PARTICLES -> "§6";
            case TRAILS -> "§b";
            case GADGETS -> "§d";
            case TAGS -> "§5";
        };
        return color + base;
    }
}
