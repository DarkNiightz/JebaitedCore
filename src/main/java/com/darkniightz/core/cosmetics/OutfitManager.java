package com.darkniightz.core.cosmetics;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class OutfitManager {
    public static final class OutfitDefinition {
        private final String key;
        private final String name;
        private final Material icon;
        private final List<String> lore;
        private final ItemStack helmet;
        private final ItemStack chestplate;
        private final ItemStack leggings;
        private final ItemStack boots;
        private final String effectSummary;

        private OutfitDefinition(String key, String name, Material icon, List<String> lore,
                                 ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots,
                                 String effectSummary) {
            this.key = key;
            this.name = name;
            this.icon = icon;
            this.lore = lore;
            this.helmet = helmet;
            this.chestplate = chestplate;
            this.leggings = leggings;
            this.boots = boots;
            this.effectSummary = effectSummary;
        }

        public String getKey() { return key; }
        public String getName() { return name; }
        public Material getIcon() { return icon; }
        public List<String> getLore() { return lore; }
        public String getEffectSummary() { return effectSummary; }
        public ItemStack getHelmet() { return helmet.clone(); }
        public ItemStack getChestplate() { return chestplate.clone(); }
        public ItemStack getLeggings() { return leggings.clone(); }
        public ItemStack getBoots() { return boots.clone(); }
    }

    private static final class ArmorSnapshot {
        private final ItemStack helmet;
        private final ItemStack chestplate;
        private final ItemStack leggings;
        private final ItemStack boots;

        private ArmorSnapshot(ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots) {
            this.helmet = helmet;
            this.chestplate = chestplate;
            this.leggings = leggings;
            this.boots = boots;
        }
    }

    private final Plugin plugin;
    private final NamespacedKey outfitKeyTag;
    private final NamespacedKey outfitPieceTag;
    private final Map<String, OutfitDefinition> outfits = new LinkedHashMap<>();
    private final Map<UUID, ArmorSnapshot> backups = new LinkedHashMap<>();
    private final Map<UUID, BukkitTask> previewTasks = new LinkedHashMap<>();
    private BukkitTask tickTask;

    public OutfitManager(Plugin plugin) {
        this.plugin = plugin;
        this.outfitKeyTag = new NamespacedKey(plugin, "cosmetic_outfit_key");
        this.outfitPieceTag = new NamespacedKey(plugin, "cosmetic_outfit_piece");
        loadDefaults();
    }

    public void start() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        for (BukkitTask task : previewTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        previewTasks.clear();
        restoreAllBackups();
    }

    public List<OutfitDefinition> getOutfits() {
        return List.copyOf(outfits.values());
    }

    public OutfitDefinition getOutfit(String key) {
        if (key == null || key.isBlank()) return null;
        return outfits.get(key.toLowerCase());
    }

    public OutfitDefinition getFeaturedOutfit() {
        List<OutfitDefinition> list = getOutfits();
        if (list.isEmpty()) return null;
        int idx = Math.floorMod(LocalDate.now().getDayOfYear(), list.size());
        return list.get(idx);
    }

    public OutfitDefinition getEquippedOutfit(Player player) {
        if (player == null) return null;
        PlayerInventory inv = player.getInventory();
        ItemStack[] armor = {inv.getHelmet(), inv.getChestplate(), inv.getLeggings(), inv.getBoots()};
        for (OutfitDefinition outfit : outfits.values()) {
            if (matches(outfit, armor)) {
                return outfit;
            }
        }
        return null;
    }

    public boolean equip(Player player, OutfitDefinition outfit) {
        return apply(player, outfit, false);
    }

    public boolean preview(Player player, OutfitDefinition outfit) {
        return apply(player, outfit, true);
    }

    public boolean clear(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        BukkitTask task = previewTasks.remove(uuid);
        if (task != null) task.cancel();

        ArmorSnapshot snapshot = backups.remove(uuid);
        if (snapshot != null) {
            restore(player, snapshot);
            player.sendMessage(com.darkniightz.core.Messages.prefixed("§7Your outfit has been restored to your previous gear."));
            return true;
        }

        boolean removed = clearTaggedOutfitPieces(player);
        if (removed) {
            player.sendMessage(com.darkniightz.core.Messages.prefixed("§7Your outfit has been cleared."));
        }
        return removed;
    }

    private boolean apply(Player player, OutfitDefinition outfit, boolean preview) {
        if (player == null || outfit == null) return false;
        UUID uuid = player.getUniqueId();

        BukkitTask oldPreview = previewTasks.remove(uuid);
        if (oldPreview != null) {
            oldPreview.cancel();
        }

        if (!backups.containsKey(uuid)) {
            backups.put(uuid, snapshot(player));
        }

        PlayerInventory inv = player.getInventory();
        inv.setHelmet(outfit.getHelmet());
        inv.setChestplate(outfit.getChestplate());
        inv.setLeggings(outfit.getLeggings());
        inv.setBoots(outfit.getBoots());
        player.updateInventory();

        if (preview) {
            int previewSeconds = Math.max(5, plugin.getConfig().getInt("menus.wardrobe.preview_seconds", 12));
            player.sendMessage(com.darkniightz.core.Messages.prefixed("§dPreviewing §f" + outfit.getName() + "§d for §f" + previewSeconds + "§d seconds."));
            previewTasks.put(uuid, new BukkitRunnable() {
                @Override
                public void run() {
                    previewTasks.remove(uuid);
                    ArmorSnapshot snapshot = backups.get(uuid);
                    if (snapshot != null && player.isOnline()) {
                        backups.remove(uuid);
                        restore(player, snapshot);
                        player.sendMessage(com.darkniightz.core.Messages.prefixed("§7Preview ended. Your previous gear is back."));
                    }
                }
            }.runTaskLater(plugin, previewSeconds * 20L));
        } else {
            player.sendMessage(com.darkniightz.core.Messages.prefixed("§dEquipped §f" + outfit.getName() + "§d."));
        }

        return true;
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            OutfitDefinition outfit = getEquippedOutfit(player);
            if (outfit == null) continue;
            applyPassiveEffects(player, outfit);
        }
    }

    private void applyPassiveEffects(Player player, OutfitDefinition outfit) {
        String key = outfit.getKey();
        if ("disco_armor".equals(key)) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 45, 0, false, false, true));
            if (ThreadLocalRandom.current().nextInt(4) == 0) {
                player.getWorld().spawnParticle(Particle.NOTE, player.getLocation().add(0, 2.0, 0), 6, 0.35, 0.45, 0.35, 0.0);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.35f, 1.2f);
            }
            return;
        }
        if ("frog_armor".equals(key)) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST, 45, 0, false, false, true));
            if (ThreadLocalRandom.current().nextInt(4) == 0) {
                player.getWorld().spawnParticle(Particle.BLOCK, player.getLocation().add(0, 1.0, 0), 8, 0.25, 0.35, 0.25, 0.0, Material.SLIME_BLOCK.createBlockData());
                player.playSound(player.getLocation(), Sound.ENTITY_SLIME_JUMP, 0.35f, 1.0f);
            }
            return;
        }
        if ("ghost_armor".equals(key)) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING, 45, 0, false, false, true));
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.NIGHT_VISION, 220, 0, false, false, true));
            if (ThreadLocalRandom.current().nextInt(4) == 0) {
                player.getWorld().spawnParticle(Particle.SOUL, player.getLocation().add(0, 1.3, 0), 10, 0.35, 0.45, 0.35, 0.0);
                player.playSound(player.getLocation(), Sound.BLOCK_SOUL_SAND_STEP, 0.25f, 0.8f);
            }
        }
    }

    private void loadDefaults() {
        outfits.clear();
        register(outfit(
                "disco_armor",
                "§dDisco Armor",
                Material.LEATHER_CHESTPLATE,
                List.of("§7A glittery floor-filler with a dancey pulse.", "§8Full set bonus: speed + musical sparks."),
                Color.fromRGB(232, 76, 255),
                Color.fromRGB(73, 213, 255),
                Color.fromRGB(255, 228, 79),
                Color.fromRGB(255, 255, 255),
                "§7Full set bonus: §fSpeed and note bursts."
        ));
        register(outfit(
                "frog_armor",
                "§aFrog Armor",
                Material.LEATHER_CHESTPLATE,
                List.of("§7Bright, bouncy, and a little bit swampy.", "§8Full set bonus: jump boost + slime hops."),
                Color.fromRGB(139, 229, 79),
                Color.fromRGB(77, 154, 59),
                Color.fromRGB(54, 99, 41),
                Color.fromRGB(164, 212, 78),
                "§7Full set bonus: §fJump boost and slime particles."
        ));
        register(outfit(
                "ghost_armor",
                "§fGhost Armor",
                Material.LEATHER_CHESTPLATE,
                List.of("§7Soft, eerie, and a little floaty.", "§8Full set bonus: slow-fall + soul wisps."),
                Color.fromRGB(245, 245, 245),
                Color.fromRGB(207, 207, 207),
                Color.fromRGB(171, 171, 171),
                Color.fromRGB(227, 227, 227),
                "§7Full set bonus: §fSlow falling and soul particles."
        ));
    }

    private OutfitDefinition outfit(String key, String name, Material icon, List<String> lore,
                                    Color helmetColor, Color chestColor, Color legColor, Color bootColor,
                                    String effectSummary) {
        return new OutfitDefinition(
                key.toLowerCase(),
                name,
                icon,
                lore,
                taggedPiece(Material.LEATHER_HELMET, helmetColor, key, "helmet", name + " Helmet"),
                taggedPiece(Material.LEATHER_CHESTPLATE, chestColor, key, "chestplate", name + " Chestplate"),
                taggedPiece(Material.LEATHER_LEGGINGS, legColor, key, "leggings", name + " Leggings"),
                taggedPiece(Material.LEATHER_BOOTS, bootColor, key, "boots", name + " Boots"),
                effectSummary
        );
    }

    private ItemStack taggedPiece(Material material, Color color, String outfitKey, String piece, String displayName) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof LeatherArmorMeta leatherArmorMeta && color != null) {
            leatherArmorMeta.setColor(color);
            meta = leatherArmorMeta;
        }
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(List.of("§7Part of §f" + prettify(outfitKey) + "§7.", "§8Wear the full set for its bonus."));
            meta.getPersistentDataContainer().set(outfitKeyTag, PersistentDataType.STRING, outfitKey.toLowerCase());
            meta.getPersistentDataContainer().set(outfitPieceTag, PersistentDataType.STRING, piece.toLowerCase());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void register(OutfitDefinition outfit) {
        outfits.put(outfit.getKey(), outfit);
    }

    private ArmorSnapshot snapshot(Player player) {
        PlayerInventory inv = player.getInventory();
        return new ArmorSnapshot(
                cloneOrNull(inv.getHelmet()),
                cloneOrNull(inv.getChestplate()),
                cloneOrNull(inv.getLeggings()),
                cloneOrNull(inv.getBoots())
        );
    }

    private void restore(Player player, ArmorSnapshot snapshot) {
        if (player == null || snapshot == null) return;
        PlayerInventory inv = player.getInventory();
        inv.setHelmet(snapshot.helmet == null ? null : snapshot.helmet.clone());
        inv.setChestplate(snapshot.chestplate == null ? null : snapshot.chestplate.clone());
        inv.setLeggings(snapshot.leggings == null ? null : snapshot.leggings.clone());
        inv.setBoots(snapshot.boots == null ? null : snapshot.boots.clone());
        player.updateInventory();
    }

    private void restoreAllBackups() {
        for (Map.Entry<UUID, ArmorSnapshot> entry : new ArrayList<>(backups.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                restore(player, entry.getValue());
            }
        }
        backups.clear();
    }

    private boolean clearTaggedOutfitPieces(Player player) {
        boolean changed = false;
        PlayerInventory inv = player.getInventory();
        changed |= clearIfTagged(inv.getHelmet(), inv::setHelmet);
        changed |= clearIfTagged(inv.getChestplate(), inv::setChestplate);
        changed |= clearIfTagged(inv.getLeggings(), inv::setLeggings);
        changed |= clearIfTagged(inv.getBoots(), inv::setBoots);
        if (changed) {
            player.updateInventory();
        }
        return changed;
    }

    private boolean clearIfTagged(ItemStack item, java.util.function.Consumer<ItemStack> setter) {
        if (!isTaggedOutfitPiece(item)) return false;
        setter.accept(null);
        return true;
    }

    private boolean matches(OutfitDefinition outfit, ItemStack[] armor) {
        if (outfit == null || armor == null || armor.length < 4) return false;
        return sameOutfitPiece(armor[0], outfit.getKey(), "helmet")
                && sameOutfitPiece(armor[1], outfit.getKey(), "chestplate")
                && sameOutfitPiece(armor[2], outfit.getKey(), "leggings")
                && sameOutfitPiece(armor[3], outfit.getKey(), "boots");
    }

    private boolean sameOutfitPiece(ItemStack item, String key, String piece) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        String storedKey = meta.getPersistentDataContainer().get(outfitKeyTag, PersistentDataType.STRING);
        String storedPiece = meta.getPersistentDataContainer().get(outfitPieceTag, PersistentDataType.STRING);
        return key.equalsIgnoreCase(storedKey) && piece.equalsIgnoreCase(storedPiece);
    }

    private boolean isTaggedOutfitPiece(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        String storedKey = meta.getPersistentDataContainer().get(outfitKeyTag, PersistentDataType.STRING);
        return storedKey != null && !storedKey.isBlank();
    }

    private ItemStack cloneOrNull(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }

    private String prettify(String key) {
        return Arrays.stream(key.split("[_-]"))
                .filter(s -> !s.isBlank())
                .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase())
                .reduce((a, b) -> a + " " + b)
                .orElse(key);
    }
}
