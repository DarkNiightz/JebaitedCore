package com.darkniightz.core.cosmetics;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class CosmeticsManager {
    public enum Category { PARTICLES, TRAILS, GADGETS }

    public static final class Cosmetic {
        public final String key;
        public final Category category;
        public final Material icon;
        public final String name;
        public final List<String> lore;
        public final int price;
        public final boolean enabled;

        public Cosmetic(String key, Category category, Material icon, String name, List<String> lore, int price, boolean enabled) {
            this.key = key; this.category = category; this.icon = icon; this.name = name; this.lore = lore; this.price = price; this.enabled = enabled;
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
        } else {
            // Fallback minimal defaults
            int def = plugin.getConfig().getInt("cosmetics.rules.price_default", 100);
            add(new Cosmetic("ember_sparks", Category.PARTICLES, Material.BLAZE_POWDER, "§6Ember Sparks", List.of("§7Warm ember particles"), def, true));
            add(new Cosmetic("leaf_whirl", Category.PARTICLES, Material.OAK_LEAVES, "§2Leaf Whirl", List.of("§7Forest breeze"), def, true));
            add(new Cosmetic("aqua_bubbles", Category.PARTICLES, Material.HEART_OF_THE_SEA, "§bAqua Bubbles", List.of("§7Bubbly vibe"), def, true));
            add(new Cosmetic("rune_trail", Category.TRAILS, Material.ENCHANTED_BOOK, "§5Rune Trail", List.of(), def, true));
            add(new Cosmetic("feather_trail", Category.TRAILS, Material.FEATHER, "§fFeather Trail", List.of(), def, true));
            add(new Cosmetic("star_trail", Category.TRAILS, Material.NETHER_STAR, "§eStar Trail", List.of(), def, true));
            add(new Cosmetic("firework_popper", Category.GADGETS, Material.FIREWORK_ROCKET, "§dFirework Popper", List.of(), def, true));
            add(new Cosmetic("slime_launcher", Category.GADGETS, Material.SLIME_BALL, "§aSlime Launcher", List.of(), def, true));
        }
    }

    private void loadCategory(ConfigurationSection sec, Category cat) {
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection c = sec.getConfigurationSection(key);
            if (c == null) continue;
            boolean enabled = c.getBoolean("enabled", true);
            int price = c.getInt("price", plugin.getConfig().getInt("cosmetics.rules.price_default", 100));
            // Derive basic display
            Material icon = defaultIcon(cat);
            String pretty = prettyName(key, cat);
            List<String> lore = new ArrayList<>();
            lore.add("§7Price: §6" + price + " coins");
            add(new Cosmetic(key, cat, icon, pretty, lore, price, enabled));
        }
    }

    private void add(Cosmetic cosmetic) {
        byCategory.get(cosmetic.category).add(cosmetic);
        byKey.put(cosmetic.key, cosmetic);
    }

    public List<Cosmetic> getByCategory(Category c) { return Collections.unmodifiableList(byCategory.getOrDefault(c, List.of())); }
    public Cosmetic get(String key) { return byKey.get(key); }

    private static Material defaultIcon(Category cat) {
        return switch (cat) {
            case PARTICLES -> Material.BLAZE_POWDER;
            case TRAILS -> Material.FEATHER;
            case GADGETS -> Material.STICK;
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
        };
        return color + base;
    }
}
