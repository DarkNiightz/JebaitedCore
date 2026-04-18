package com.darkniightz.core.shop;

import com.darkniightz.core.system.MaterialCompat;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default seed rows for {@code server_shop_prices} (ROADMAP §17 pricing reference).
 */
public final class ShopCatalog {

    private ShopCatalog() {
    }

    public static List<ShopPriceRow> buildDefaultRows() {
        List<ShopPriceRow> list = new ArrayList<>();
        AtomicInteger sort = new AtomicInteger(0);

        blocks(list, sort);
        farming(list, sort);
        mobs(list, sort);
        ores(list, sort);
        dyes(list, sort);
        music(list, sort);
        food(list, sort);
        decoration(list, sort);
        redstone(list, sort);

        return list;
    }

    private static String pretty(Material m) {
        if (m == null) return "?";
        String[] parts = m.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            String p = parts[i];
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            }
        }
        return sb.toString();
    }

    private static void add(List<ShopPriceRow> out, Material mat, String category, double buy, double sell, AtomicInteger sort) {
        if (mat == null || mat.isAir()) return;
        int ord = sort.getAndIncrement();
        int maxStack = new ItemStack(mat, 1).getMaxStackSize();
        out.add(new ShopPriceRow(
                mat.getKey().toString(),
                category,
                pretty(mat),
                buy,
                sell,
                Math.max(1, maxStack),
                ord,
                true
        ));
    }

    private static void blocks(List<ShopPriceRow> out, AtomicInteger sort) {
        String c = "blocks";
        Material[] logs = {
                Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
                Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG
        };
        for (Material m : logs) add(out, m, c, 8.0, 3.50, sort);
        add(out, Material.BAMBOO_BLOCK, c, 7.0, 3.0, sort);
        add(out, Material.STONE, c, 4.0, 1.80, sort);
        add(out, Material.COBBLESTONE, c, 3.0, 1.40, sort);
        add(out, Material.DEEPSLATE, c, 6.0, 2.70, sort);
        add(out, Material.ANDESITE, c, 5.0, 2.20, sort);
        add(out, Material.DIORITE, c, 5.0, 2.20, sort);
        add(out, Material.GRANITE, c, 5.0, 2.20, sort);
        add(out, Material.SAND, c, 4.0, 1.80, sort);
        add(out, Material.GRAVEL, c, 4.0, 1.80, sort);
        add(out, Material.DIRT, c, 3.0, 1.40, sort);
        add(out, Material.GRASS_BLOCK, c, 3.0, 1.40, sort);
        add(out, Material.MYCELIUM, c, 12.0, 5.50, sort);
        add(out, Material.PODZOL, c, 12.0, 5.50, sort);
        add(out, Material.CLAY, c, 6.0, 2.70, sort);
        for (DyeColor dc : DyeColor.values()) {
            String name = dc.name() + "_TERRACOTTA";
            Material m = Material.matchMaterial(name);
            if (m != null) add(out, m, c, 9.0, 4.0, sort);
        }
        for (DyeColor dc : DyeColor.values()) {
            Material m = Material.matchMaterial(dc.name() + "_CONCRETE");
            if (m != null) add(out, m, c, 14.0, 6.50, sort);
        }
        for (DyeColor dc : DyeColor.values()) {
            Material m = Material.matchMaterial(dc.name() + "_WOOL");
            if (m != null) add(out, m, c, 8.0, 3.50, sort);
        }
        add(out, Material.GLASS, c, 7.0, 3.0, sort);
        for (DyeColor dc : DyeColor.values()) {
            Material m = Material.matchMaterial(dc.name() + "_STAINED_GLASS");
            if (m != null) add(out, m, c, 11.0, 5.0, sort);
        }
    }

    private static void farming(List<ShopPriceRow> out, AtomicInteger sort) {
        String c = "farming";
        add(out, Material.WHEAT_SEEDS, c, 2.0, 0.90, sort);
        add(out, Material.WHEAT, c, 4.0, 1.80, sort);
        add(out, Material.CARROT, c, 5.0, 2.20, sort);
        add(out, Material.POTATO, c, 5.0, 2.20, sort);
        add(out, Material.BEETROOT_SEEDS, c, 4.0, 1.80, sort);
        add(out, Material.BEETROOT, c, 4.0, 1.80, sort);
        add(out, Material.PUMPKIN_SEEDS, c, 6.0, 2.70, sort);
        add(out, Material.MELON_SEEDS, c, 6.0, 2.70, sort);
        add(out, Material.PUMPKIN, c, 8.0, 3.50, sort);
        add(out, Material.MELON, c, 8.0, 3.50, sort);
        add(out, Material.SUGAR_CANE, c, 5.0, 2.20, sort);
        add(out, Material.CACTUS, c, 7.0, 3.0, sort);
        add(out, Material.BAMBOO, c, 4.0, 1.80, sort);
        add(out, Material.COCOA_BEANS, c, 9.0, 4.0, sort);
        add(out, Material.SWEET_BERRIES, c, 8.0, 3.50, sort);
        add(out, Material.GLOW_BERRIES, c, 8.0, 3.50, sort);
        add(out, Material.NETHER_WART, c, 12.0, 5.50, sort);
        add(out, Material.BONE_MEAL, c, 10.0, 4.50, sort);
        add(out, Material.HAY_BLOCK, c, 18.0, 8.0, sort);
        add(out, Material.DRIED_KELP_BLOCK, c, 15.0, 7.0, sort);
    }

    private static void mobs(List<ShopPriceRow> out, AtomicInteger sort) {
        String c = "mobs";
        add(out, Material.ROTTEN_FLESH, c, 3.0, 1.40, sort);
        add(out, Material.BONE, c, 5.0, 2.20, sort);
        add(out, Material.STRING, c, 6.0, 2.70, sort);
        add(out, Material.SPIDER_EYE, c, 8.0, 3.50, sort);
        add(out, Material.GUNPOWDER, c, 8.0, 3.50, sort);
        add(out, Material.ENDER_PEARL, c, 35.0, 16.0, sort);
        add(out, Material.BLAZE_ROD, c, 25.0, 11.0, sort);
        add(out, Material.GHAST_TEAR, c, 60.0, 27.0, sort);
        add(out, Material.PHANTOM_MEMBRANE, c, 60.0, 27.0, sort);
        add(out, Material.MAGMA_CREAM, c, 18.0, 8.0, sort);
        add(out, Material.SHULKER_SHELL, c, 120.0, 55.0, sort);
        add(out, Material.TOTEM_OF_UNDYING, c, 450.0, 95.0, sort);
        addEgg(out, EntityType.COW, c, 40.0, 18.0, sort);
        addEgg(out, EntityType.PIG, c, 40.0, 18.0, sort);
        addEgg(out, EntityType.CHICKEN, c, 40.0, 18.0, sort);
        addEgg(out, EntityType.SHEEP, c, 40.0, 18.0, sort);
        addEgg(out, EntityType.ZOMBIE, c, 55.0, 25.0, sort);
        addEgg(out, EntityType.SKELETON, c, 55.0, 25.0, sort);
    }

    private static void addEgg(List<ShopPriceRow> out, EntityType type, String category, double buy, double sell, AtomicInteger sort) {
        Material m = Material.matchMaterial(type.name() + "_SPAWN_EGG");
        if (m != null) add(out, m, category, buy, sell, sort);
    }

    private static void ores(List<ShopPriceRow> out, AtomicInteger sort) {
        String c = "ores";
        add(out, Material.COAL, c, 6.0, 2.70, sort);
        add(out, Material.RAW_IRON, c, 9.0, 4.0, sort);
        add(out, Material.IRON_INGOT, c, 12.0, 5.50, sort);
        add(out, Material.RAW_GOLD, c, 18.0, 8.0, sort);
        add(out, Material.GOLD_INGOT, c, 25.0, 11.0, sort);
        add(out, Material.RAW_COPPER, c, 7.0, 3.0, sort);
        add(out, Material.COPPER_INGOT, c, 10.0, 4.50, sort);
        add(out, Material.LAPIS_LAZULI, c, 15.0, 7.0, sort);
        add(out, Material.REDSTONE, c, 14.0, 6.50, sort);
        add(out, Material.DIAMOND, c, 120.0, 55.0, sort);
        add(out, Material.EMERALD, c, 90.0, 40.0, sort);
        add(out, MaterialCompat.resolve(Material.QUARTZ, "QUARTZ", "NETHER_QUARTZ"), c, 22.0, 10.0, sort);
        add(out, Material.NETHERITE_SCRAP, c, 80.0, 36.0, sort);
        add(out, Material.ANCIENT_DEBRIS, c, 95.0, 43.0, sort);
        add(out, Material.NETHERITE_INGOT, c, 450.0, 210.0, sort);
        add(out, Material.IRON_BLOCK, c, 108.0, 49.50, sort);
        add(out, Material.GOLD_BLOCK, c, 225.0, 99.0, sort);
        add(out, Material.DIAMOND_BLOCK, c, 1080.0, 495.0, sort);
        add(out, Material.EMERALD_BLOCK, c, 810.0, 360.0, sort);
    }

    private static void dyes(List<ShopPriceRow> out, AtomicInteger sort) {
        String c = "dyes";
        Material[] dyes = {
                Material.WHITE_DYE, Material.ORANGE_DYE, Material.MAGENTA_DYE, Material.LIGHT_BLUE_DYE,
                Material.YELLOW_DYE, Material.LIME_DYE, Material.PINK_DYE, Material.GRAY_DYE,
                Material.LIGHT_GRAY_DYE, Material.CYAN_DYE, Material.PURPLE_DYE, Material.BLUE_DYE,
                Material.BROWN_DYE, Material.GREEN_DYE, Material.RED_DYE, Material.BLACK_DYE
        };
        for (Material m : dyes) add(out, m, c, 5.0, 2.20, sort);
    }

    private static void music(List<ShopPriceRow> out, AtomicInteger sort) {
        String c = "music";
        for (Material m : Material.values()) {
            if (m.name().startsWith("MUSIC_DISC_")) {
                add(out, m, c, 85.0, 18.0, sort);
            }
        }
        add(out, Material.GOAT_HORN, c, 45.0, 20.0, sort);
    }

    private static void food(List<ShopPriceRow> out, AtomicInteger sort) {
        String c = "food";
        add(out, Material.BREAD, c, 6.0, 2.70, sort);
        add(out, Material.COOKIE, c, 6.0, 2.70, sort);
        add(out, Material.PUMPKIN_PIE, c, 18.0, 8.0, sort);
        add(out, Material.CAKE, c, 45.0, 20.0, sort);
        add(out, Material.APPLE, c, 4.0, 1.80, sort);
        add(out, Material.BAKED_POTATO, c, 5.0, 2.20, sort);
        add(out, Material.COOKED_CHICKEN, c, 12.0, 5.50, sort);
        add(out, Material.COOKED_BEEF, c, 12.0, 5.50, sort);
        add(out, Material.COOKED_PORKCHOP, c, 12.0, 5.50, sort);
        add(out, Material.COOKED_MUTTON, c, 12.0, 5.50, sort);
        add(out, Material.COOKED_SALMON, c, 12.0, 5.50, sort);
        add(out, Material.GOLDEN_APPLE, c, 85.0, 38.0, sort);
        add(out, Material.ENCHANTED_GOLDEN_APPLE, c, 2500.0, 1100.0, sort);
        add(out, Material.RABBIT_STEW, c, 22.0, 10.0, sort);
        add(out, Material.SUSPICIOUS_STEW, c, 22.0, 10.0, sort);
        add(out, Material.HONEY_BOTTLE, c, 15.0, 7.0, sort);
    }

    private static void decoration(List<ShopPriceRow> out, AtomicInteger sort) {
        String c = "decoration";
        Material[] flowers = {
                Material.DANDELION, Material.POPPY, Material.BLUE_ORCHID, Material.ALLIUM, Material.AZURE_BLUET,
                Material.RED_TULIP, Material.ORANGE_TULIP, Material.WHITE_TULIP, Material.PINK_TULIP,
                Material.OXEYE_DAISY, Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY, Material.SUNFLOWER,
                Material.LILAC, Material.ROSE_BUSH, Material.PEONY, Material.LARGE_FERN, Material.TALL_GRASS,
                Material.PINK_PETALS, Material.TORCHFLOWER
        };
        for (Material m : flowers) add(out, m, c, 4.0, 1.80, sort);
        add(out, Material.LANTERN, c, 18.0, 8.0, sort);
        add(out, Material.SOUL_LANTERN, c, 18.0, 8.0, sort);
        add(out, Material.CAMPFIRE, c, 15.0, 7.0, sort);
        add(out, Material.SOUL_CAMPFIRE, c, 15.0, 7.0, sort);
        add(out, Material.ITEM_FRAME, c, 10.0, 4.50, sort);
        add(out, Material.GLOW_ITEM_FRAME, c, 10.0, 4.50, sort);
        add(out, Material.PAINTING, c, 25.0, 11.0, sort);
        add(out, Material.ARMOR_STAND, c, 35.0, 16.0, sort);
        add(out, Material.BOOKSHELF, c, 22.0, 10.0, sort);
        add(out, Material.LECTERN, c, 22.0, 10.0, sort);
        add(out, Material.FLOWER_POT, c, 8.0, 3.50, sort);
        add(out, Material.SCAFFOLDING, c, 12.0, 5.50, sort);
        add(out, MaterialCompat.resolve(Material.IRON_BARS, "CHAIN"), c, 14.0, 6.50, sort);
        add(out, Material.BELL, c, 14.0, 6.50, sort);
        for (DyeColor dc : DyeColor.values()) {
            Material m = Material.matchMaterial(dc.name() + "_CARPET");
            if (m != null) add(out, m, c, 7.0, 3.0, sort);
        }
        add(out, Material.WHITE_BANNER, c, 20.0, 9.0, sort);
    }

    private static void redstone(List<ShopPriceRow> out, AtomicInteger sort) {
        String c = "redstone";
        add(out, Material.REDSTONE, c, 14.0, 6.50, sort);
        add(out, Material.REDSTONE_TORCH, c, 16.0, 7.0, sort);
        add(out, Material.REPEATER, c, 16.0, 7.0, sort);
        add(out, Material.COMPARATOR, c, 22.0, 10.0, sort);
        add(out, Material.PISTON, c, 28.0, 12.50, sort);
        add(out, Material.STICKY_PISTON, c, 28.0, 12.50, sort);
        add(out, Material.OBSERVER, c, 35.0, 16.0, sort);
        add(out, Material.HOPPER, c, 35.0, 16.0, sort);
        add(out, Material.DROPPER, c, 32.0, 14.50, sort);
        add(out, Material.DISPENSER, c, 32.0, 14.50, sort);
        add(out, Material.RAIL, c, 12.0, 5.50, sort);
        add(out, Material.POWERED_RAIL, c, 18.0, 8.0, sort);
        add(out, Material.DETECTOR_RAIL, c, 16.0, 7.0, sort);
        add(out, Material.ACTIVATOR_RAIL, c, 14.0, 6.50, sort);
        add(out, Material.LEVER, c, 10.0, 4.50, sort);
        add(out, Material.STONE_BUTTON, c, 10.0, 4.50, sort);
        add(out, Material.OAK_BUTTON, c, 10.0, 4.50, sort);
        add(out, Material.STONE_PRESSURE_PLATE, c, 12.0, 5.50, sort);
        add(out, Material.OAK_PRESSURE_PLATE, c, 12.0, 5.50, sort);
        add(out, Material.LIGHT_WEIGHTED_PRESSURE_PLATE, c, 14.0, 6.50, sort);
        add(out, Material.HEAVY_WEIGHTED_PRESSURE_PLATE, c, 14.0, 6.50, sort);
        add(out, Material.TARGET, c, 40.0, 18.0, sort);
        add(out, Material.SCULK_SENSOR, c, 85.0, 38.0, sort);
        add(out, Material.CALIBRATED_SCULK_SENSOR, c, 120.0, 55.0, sort);
        add(out, Material.DAYLIGHT_DETECTOR, c, 28.0, 12.50, sort);
    }
}
