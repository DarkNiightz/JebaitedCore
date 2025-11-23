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

        public Cosmetic(String key, Category category, Material icon, String name, List<String> lore) {
            this.key = key; this.category = category; this.icon = icon; this.name = name; this.lore = lore;
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
        ConfigurationSection defs = plugin.getConfig().getConfigurationSection("cosmetics.definitions");
        if (defs != null) {
            loadCategory(defs.getConfigurationSection("particles"), Category.PARTICLES);
            loadCategory(defs.getConfigurationSection("trails"), Category.TRAILS);
            loadCategory(defs.getConfigurationSection("gadgets"), Category.GADGETS);
        } else {
            // Fallback minimal defaults
            add(new Cosmetic("ember_sparks", Category.PARTICLES, Material.BLAZE_POWDER, "§6Ember Sparks", List.of("§7Warm ember particles")));
            add(new Cosmetic("leaf_whirl", Category.PARTICLES, Material.OAK_LEAVES, "§2Leaf Whirl", List.of("§7Forest breeze")));
            add(new Cosmetic("aqua_bubbles", Category.PARTICLES, Material.HEART_OF_THE_SEA, "§bAqua Bubbles", List.of("§7Bubbly vibe")));
            add(new Cosmetic("rune_trail", Category.TRAILS, Material.ENCHANTED_BOOK, "§5Rune Trail", List.of()));
            add(new Cosmetic("feather_trail", Category.TRAILS, Material.FEATHER, "§fFeather Trail", List.of()));
            add(new Cosmetic("star_trail", Category.TRAILS, Material.NETHER_STAR, "§eStar Trail", List.of()));
            add(new Cosmetic("firework_popper", Category.GADGETS, Material.FIREWORK_ROCKET, "§dFirework Popper", List.of()));
            add(new Cosmetic("slime_launcher", Category.GADGETS, Material.SLIME_BALL, "§aSlime Launcher", List.of()));
        }
    }

    private void loadCategory(ConfigurationSection sec, Category cat) {
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection c = sec.getConfigurationSection(key);
            if (c == null) continue;
            String iconName = c.getString("icon", "PAPER");
            Material icon;
            try { icon = Material.valueOf(iconName.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { icon = Material.PAPER; }
            String name = c.getString("name", key);
            List<String> lore = c.getStringList("lore");
            add(new Cosmetic(key, cat, icon, name, lore));
        }
    }

    private void add(Cosmetic cosmetic) {
        byCategory.get(cosmetic.category).add(cosmetic);
        byKey.put(cosmetic.key, cosmetic);
    }

    public List<Cosmetic> getByCategory(Category c) { return Collections.unmodifiableList(byCategory.getOrDefault(c, List.of())); }
    public Cosmetic get(String key) { return byKey.get(key); }
}
