package com.darkniightz.core.cosmetics;

import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.system.MaterialCompat;
import com.darkniightz.core.system.SoundCompat;
import org.bukkit.Color;
import org.bukkit.ChatColor;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class ToyboxManager {
    private final Plugin plugin;
    private final ProfileStore profiles;
    private final CosmeticsManager cosmetics;
    private final NamespacedKey toyboxKey;
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    public ToyboxManager(Plugin plugin, ProfileStore profiles, CosmeticsManager cosmetics) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.cosmetics = cosmetics;
        this.toyboxKey = new NamespacedKey(plugin, "toybox_key");
    }

    public void refresh(Player player) {
        if (player == null) return;
        PlayerProfile profile = profiles.getOrCreate(player, plugin.getConfig().getString("ranks.default", "pleb"));
        if (profile == null) return;

        int slot = getSlot();
        ItemStack current = player.getInventory().getItem(slot);
        if (profile.getEquippedGadget() == null) {
            if (isToyboxItem(current)) {
                player.getInventory().setItem(slot, null);
            }
            return;
        }

        CosmeticsManager.Cosmetic cosmetic = cosmetics.get(profile.getEquippedGadget());
        if (cosmetic == null || cosmetic.category != CosmeticsManager.Category.GADGETS) {
            if (isToyboxItem(current)) {
                player.getInventory().setItem(slot, null);
            }
            return;
        }
        player.getInventory().setItem(slot, buildToyboxItem(cosmetic));
    }

    public boolean trigger(Player player) {
        PlayerProfile profile = profiles.getOrCreate(player, plugin.getConfig().getString("ranks.default", "pleb"));
        if (profile == null) return false;

        String active = profile.getEquippedGadget();
        if (active == null) {
            player.sendMessage(com.darkniightz.core.Messages.prefixed("§7Choose a gadget in the wardrobe first."));
            return true;
        }

        CosmeticsManager.Cosmetic cosmetic = cosmetics.get(active);
        if (cosmetic == null || cosmetic.category != CosmeticsManager.Category.GADGETS || !cosmetic.enabled) {
            player.sendMessage(com.darkniightz.core.Messages.prefixed("§cThat toybox item is not available."));
            return true;
        }

        int cooldownSeconds = cosmetic.cooldownSeconds > 0 ? cosmetic.cooldownSeconds : plugin.getConfig().getInt("cosmetics.rules.gadget_cooldown_default", 3);
        String cooldownKey = player.getUniqueId() + ":" + cosmetic.key;
        long now = System.currentTimeMillis();
        Long lastUse = cooldowns.get(cooldownKey);
        long cooldownMs = Math.max(0, cooldownSeconds) * 1000L;
        if (lastUse != null && now - lastUse < cooldownMs) {
            long remaining = Math.max(1, (cooldownMs - (now - lastUse) + 999) / 1000L);
            player.sendMessage(com.darkniightz.core.Messages.prefixed("§7Toybox cooling down for §e" + remaining + "s§7."));
            return true;
        }
        cooldowns.put(cooldownKey, now);

        runEffect(player, cosmetic);
        recordFeed(player, "Toybox used", List.of("§7Gadget: §f" + cosmetic.name, "§7Key: §f" + cosmetic.key));
        return true;
    }

    public void preview(Player player, String gadgetKey) {
        CosmeticsManager.Cosmetic cosmetic = cosmetics.get(gadgetKey);
        if (cosmetic == null || cosmetic.category != CosmeticsManager.Category.GADGETS) {
            player.sendMessage(com.darkniightz.core.Messages.prefixed("§cThat gadget does not exist."));
            return;
        }
        runEffect(player, cosmetic);
        recordFeed(player, "Toybox preview", List.of("§7Gadget: §f" + cosmetic.name, "§7Key: §f" + cosmetic.key));
    }

    public void stop() {
        cooldowns.clear();
    }

    public void clear(Player player) {
        if (player == null) return;
        int slot = getSlot();
        ItemStack current = player.getInventory().getItem(slot);
        if (isToyboxItem(current)) {
            player.getInventory().setItem(slot, null);
        }
    }

    public boolean isToyboxItem(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return false;
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(toyboxKey, PersistentDataType.STRING);
    }

    private ItemStack buildToyboxItem(CosmeticsManager.Cosmetic cosmetic) {
        Material material = cosmetic.icon == null ? MaterialCompat.resolve(Material.PAPER, "FIREWORK_ROCKET", "FIREWORK", "PAPER") : cosmetic.icon;
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d§lToybox: §f" + ChatColor.stripColor(cosmetic.name));
            List<String> lore = new ArrayList<>();
            lore.add("§7Active gadget: §f" + cosmetic.name);
            lore.add("§8Right-click to use it.");
            lore.add("§7Cooldown: §e" + Math.max(1, cosmetic.cooldownSeconds > 0 ? cosmetic.cooldownSeconds : plugin.getConfig().getInt("cosmetics.rules.gadget_cooldown_default", 3)) + "s");
            lore.add("§8Switch gadgets in /cosmetics.");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(toyboxKey, PersistentDataType.STRING, cosmetic.key);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void runEffect(Player player, CosmeticsManager.Cosmetic cosmetic) {
        String action = cosmetic.actionKey == null ? cosmetic.key : cosmetic.actionKey.toLowerCase(Locale.ROOT);
        switch (action) {
            case "firework_popper" -> runFireworkPopper(player);
            case "slime_launcher" -> runSlimeLauncher(player);
            case "bubble_wand" -> runBubbleWand(player);
            case "prism_ping" -> runPrismPing(player);
            case "confetti_cannon" -> runConfettiCannon(player);
            case "note_burst" -> runNoteBurst(player);
            case "sparkler" -> runSparkler(player);
            case "paint_splatter" -> runPaintSplatter(player);
            case "mini_firework" -> runMiniFirework(player);
            case "ghost_lantern" -> runGhostLantern(player);
            case "lightning_rod" -> runLightningRod(player);
            case "flower_bomb" -> runFlowerBomb(player);
            case "disco_orb" -> runDiscoOrb(player);
            case "time_bubble" -> runTimeBubble(player);
            case "sparkler_trail" -> runSparklerTrail(player);
            default -> runDefaultToy(player);
        }
    }

    private void runFireworkPopper(Player player) {
        var loc = player.getLocation().add(0, 1.0, 0);
        player.getWorld().spawnParticle(resolveParticleByName("FIREWORK", Particle.CLOUD), loc, 45, 0.35, 0.45, 0.35, 0.05);
        SoundCompat.play(player.getWorld(), loc, 1.0f, 1.2f, Sound.ENTITY_GENERIC_EXPLODE, "ENTITY_FIREWORK_ROCKET_BLAST", "ENTITY_FIREWORK_BLAST", "ENTITY_GENERIC_EXPLODE");
        player.sendMessage(com.darkniightz.core.Messages.prefixed("§dPop! §7A burst of sparkles follows you."));
    }

    private void runSlimeLauncher(Player player) {
        var loc = player.getLocation().add(0, 0.2, 0);
        Vector velocity = new Vector(0, 1.15, 0);
        player.setFallDistance(0f);
        player.setVelocity(velocity);
        player.getWorld().spawnParticle(Particle.CLOUD, loc, 30, 0.3, 0.2, 0.3, 0.08);
        player.getWorld().playSound(loc, Sound.ENTITY_SLIME_JUMP, 1.0f, 1.0f);
        player.sendMessage(com.darkniightz.core.Messages.prefixed("§aBoing! §7Up you go."));
    }

    private void runBubbleWand(Player player) {
        var loc = player.getLocation().add(0, 1.0, 0);
        player.getWorld().spawnParticle(Particle.BUBBLE_POP, loc, 40, 0.4, 0.45, 0.4, 0.03);
        player.getWorld().spawnParticle(Particle.SPLASH, loc, 16, 0.25, 0.2, 0.25, 0.05);
        player.getWorld().playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.5f);
        player.sendMessage(com.darkniightz.core.Messages.prefixed("§bPop-pop! §7Bubbles everywhere."));
    }

    private void runPrismPing(Player player) {
        var loc = player.getLocation().add(0, 1.0, 0);
        var dust = resolveParticleByName("DUST", Particle.CLOUD);
        player.getWorld().spawnParticle(dust, loc, 18, 0.25, 0.35, 0.25, 0.05, new Particle.DustOptions(Color.fromRGB(123, 186, 255), 1.2f));
        player.getWorld().spawnParticle(dust, loc, 12, 0.25, 0.35, 0.25, 0.05, new Particle.DustOptions(Color.fromRGB(255, 123, 230), 1.2f));
        player.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.85f, 1.3f);
        player.sendMessage(com.darkniightz.core.Messages.prefixed("§dPing! §7A prism ripple flashes out."));
    }

    private void runConfettiCannon(Player player) {
        var loc = player.getLocation().add(0, 1.0, 0);
        Color[] colors = {
                Color.fromRGB(255, 85, 85),
                Color.fromRGB(255, 191, 85),
                Color.fromRGB(90, 220, 255),
                Color.fromRGB(190, 110, 255)
        };
        for (Color color : colors) {
            player.getWorld().spawnParticle(resolveParticleByName("DUST", Particle.CLOUD), loc, 8, 0.35, 0.4, 0.35, 0.04, new Particle.DustOptions(color, 1.0f));
        }
        player.getWorld().spawnParticle(resolveParticleByName("FIREWORK", Particle.CLOUD), loc, 24, 0.35, 0.4, 0.35, 0.05);
        SoundCompat.play(player.getWorld(), loc, 1.0f, 1.35f, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, "ENTITY_FIREWORK_ROCKET_TWINKLE", "ENTITY_FIREWORK_TWINKLE", "ENTITY_EXPERIENCE_ORB_PICKUP");
        player.sendMessage(com.darkniightz.core.Messages.prefixed("§6Confetti everywhere. §7You have, regrettably, started a party."));
    }

    private void runNoteBurst(Player player) {
        player.sendMessage(com.darkniightz.core.Messages.prefixed("§e♪ §7Piano sequence armed."));
        var notes = new Sound[] {
                Sound.BLOCK_NOTE_BLOCK_HARP,
                Sound.BLOCK_NOTE_BLOCK_BASS,
                Sound.BLOCK_NOTE_BLOCK_HARP,
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Sound.BLOCK_NOTE_BLOCK_HARP,
                Sound.BLOCK_NOTE_BLOCK_PLING,
                Sound.BLOCK_NOTE_BLOCK_HARP,
                Sound.BLOCK_NOTE_BLOCK_XYLOPHONE
        };
        var pitches = new float[] {0.9f, 0.6f, 1.0f, 1.25f, 1.05f, 1.35f, 1.1f, 1.55f};
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!player.isOnline() || tick >= notes.length) {
                    cancel();
                    return;
                }
                var loc = player.getLocation().add(0, 1.0, 0);
                player.getWorld().spawnParticle(Particle.NOTE, loc, 4, 0.18, 0.25, 0.18, 0.05);
                player.getWorld().playSound(loc, notes[tick], 1.0f, pitches[tick]);
                if (tick == notes.length - 1) {
                    player.getWorld().spawnParticle(Particle.END_ROD, loc, 18, 0.25, 0.3, 0.25, 0.02);
                    SoundCompat.play(player.getWorld(), loc, 0.8f, 1.4f, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, "ENTITY_FIREWORK_ROCKET_TWINKLE", "ENTITY_FIREWORK_TWINKLE", "ENTITY_EXPERIENCE_ORB_PICKUP");
                    player.sendMessage(com.darkniightz.core.Messages.prefixed("§e♪ §7Piano sequence complete."));
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    private void runSparkler(Player player) {
        var loc = player.getLocation().add(0, 1.0, 0);
        player.getWorld().spawnParticle(Particle.FLAME, loc, 14, 0.2, 0.25, 0.2, 0.03);
        player.getWorld().spawnParticle(resolveParticleByName("DUST", Particle.CLOUD), loc, 16, 0.2, 0.25, 0.2, 0.03,
                new Particle.DustOptions(Color.fromRGB(255, 220, 90), 1.0f));
        player.getWorld().spawnParticle(resolveParticleByName("FIREWORK", Particle.CLOUD), loc, 8, 0.2, 0.25, 0.2, 0.02);
        player.getWorld().playSound(loc, Sound.BLOCK_FIRE_AMBIENT, 0.8f, 1.5f);
        SoundCompat.play(player.getWorld(), loc, 0.75f, 1.7f, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, "ENTITY_FIREWORK_ROCKET_LAUNCH", "ENTITY_FIREWORK_LAUNCH", "ENTITY_EXPERIENCE_ORB_PICKUP");
        Vector launch = player.getLocation().getDirection().normalize().multiply(0.55).setY(1.35);
        player.setFallDistance(0f);
        player.setVelocity(launch);
        new BukkitRunnable() {
            int pulse = 0;

            @Override
            public void run() {
                if (!player.isOnline() || pulse >= 4) {
                    cancel();
                    return;
                }
                var pulseLoc = player.getLocation().add(0, 0.75, 0);
                player.getWorld().spawnParticle(Particle.CLOUD, pulseLoc, 10, 0.15, 0.2, 0.15, 0.02);
                player.getWorld().spawnParticle(Particle.END_ROD, pulseLoc, 4, 0.1, 0.15, 0.1, 0.01);
                pulse++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
        player.sendMessage(com.darkniightz.core.Messages.prefixed("§6Launch! §7Sparkler boost engaged."));
    }

    private void runPaintSplatter(Player player) {
        Location base = getTargetLocation(player, 30);
        List<BlockState> restore = new ArrayList<>();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                Block block = base.clone().add(x, 0, z).getBlock();
                if (!block.getType().isSolid()) continue;
                restore.add(block.getState());
                block.setType(randomConcreteColor());
            }
        }
        player.getWorld().spawnParticle(Particle.EXPLOSION, base.clone().add(0, 1.0, 0), 10, 0.4, 0.4, 0.4, 0.02);
        player.getWorld().playSound(base, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.4f);
        new BukkitRunnable() {
            @Override
            public void run() {
                for (BlockState state : restore) {
                    state.update(true, false);
                }
            }
        }.runTaskLater(plugin, 100L);
        player.sendMessage(com.darkniightz.core.Messages.prefixed("§cSplash! §7The floor got a temporary makeover."));
    }

    private void runMiniFirework(Player player) {
        Location loc = player.getLocation().add(0, 1.0, 0);
        Firework firework = player.getWorld().spawn(loc, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .withColor(Color.fromRGB(255, 126, 126), Color.fromRGB(126, 219, 255))
                .withFade(Color.fromRGB(255, 248, 180))
                .trail(true)
                .flicker(true)
                .with(FireworkEffect.Type.BALL_LARGE)
                .build());
        meta.setPower(0);
        firework.setFireworkMeta(meta);
        SoundCompat.play(player.getWorld(), loc, 0.9f, 1.2f, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, "ENTITY_FIREWORK_ROCKET_LAUNCH", "ENTITY_FIREWORK_LAUNCH", "ENTITY_EXPERIENCE_ORB_PICKUP");
        player.sendMessage(com.darkniightz.core.Messages.prefixed("§dPop! §7A tiny firework bursts above you."));
    }

    private void runGhostLantern(Player player) {
        Location loc = player.getLocation().add(0, 1.0, 0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 120, 0, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 0, false, false, true));
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!player.isOnline() || tick++ >= 20) {
                    cancel();
                    return;
                }
                player.getWorld().spawnParticle(Particle.SOUL, loc, 8, 0.35, 0.45, 0.35, 0.02);
                player.getWorld().spawnParticle(Particle.END_ROD, loc, 2, 0.15, 0.2, 0.15, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 3L);
        player.getWorld().playSound(loc, Sound.BLOCK_SOUL_SAND_STEP, 0.7f, 1.8f);
        player.sendMessage(com.darkniightz.core.Messages.prefixed("§7Ghost lantern lit. §fYou feel briefly untouchable."));
    }

    private void runLightningRod(Player player) {
        Location target = getTargetLocation(player, 40);
        player.getWorld().strikeLightningEffect(target);
        player.getWorld().playSound(target, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        player.sendMessage(com.darkniightz.core.Messages.prefixed("§bCrack! §7A harmless lightning arc zaps the target."));
    }

    private void runFlowerBomb(Player player) {
        Location base = getTargetLocation(player, 30);
        List<BlockState> restore = new ArrayList<>();
        Material[] flowers = {
                Material.POPPY,
                Material.DANDELION,
                Material.BLUE_ORCHID,
                Material.ALLIUM,
                Material.OXEYE_DAISY,
                Material.CORNFLOWER,
                Material.LILY_OF_THE_VALLEY
        };
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                if (ThreadLocalRandom.current().nextDouble() > 0.45) continue;
                Block block = base.clone().add(x, 0, z).getBlock();
                Block below = block.getRelative(0, -1, 0);
                if (block.getType() != Material.AIR) continue;
                if (!below.getType().isSolid()) continue;
                restore.add(block.getState());
                block.setType(flowers[ThreadLocalRandom.current().nextInt(flowers.length)]);
            }
        }
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, base.clone().add(0, 1.0, 0), 22, 0.7, 0.45, 0.7, 0.08);
        player.getWorld().playSound(base, Sound.BLOCK_GRASS_PLACE, 0.9f, 1.2f);
        new BukkitRunnable() {
            @Override
            public void run() {
                for (BlockState state : restore) {
                    state.update(true, false);
                }
            }
        }.runTaskLater(plugin, 120L);
        player.sendMessage(com.darkniightz.core.Messages.prefixed("§aBloom! §7A little flower storm blossoms nearby."));
    }

    private void runDiscoOrb(Player player) {
        Location loc = player.getLocation().add(0, 1.8, 0);
        new BukkitRunnable() {
            int tick = 0;
            final Sound[] notes = {
                    Sound.BLOCK_NOTE_BLOCK_HARP,
                    Sound.BLOCK_NOTE_BLOCK_BELL,
                    Sound.BLOCK_NOTE_BLOCK_BASS,
                    Sound.BLOCK_NOTE_BLOCK_PLING
            };

            @Override
            public void run() {
                if (!player.isOnline() || tick >= 24) {
                    cancel();
                    return;
                }
                player.getWorld().spawnParticle(Particle.NOTE, loc, 6, 0.45, 0.45, 0.45, 1.0);
                player.getWorld().spawnParticle(resolveParticleByName("DUST", Particle.CLOUD), loc, 12, 0.35, 0.35, 0.35, 0.03,
                        new Particle.DustOptions(Color.fromRGB(255, 82, 174), 1.2f));
                player.getWorld().playSound(loc, notes[tick % notes.length], 0.8f, 1.1f + (tick % 4) * 0.12f);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
        player.sendMessage(com.darkniightz.core.Messages.prefixed("§dDisco orb online. §7The room starts vibing."));
    }

    private void runTimeBubble(Player player) {
        Location loc = player.getLocation().add(0, 1.0, 0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 80, 0, false, false, true));
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!player.isOnline() || tick++ >= 30) {
                    cancel();
                    return;
                }
                player.getWorld().spawnParticle(Particle.PORTAL, loc, 18, 0.6, 0.65, 0.6, 0.08);
                player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc, 8, 0.45, 0.45, 0.45, 0.03);
            }
        }.runTaskTimer(plugin, 0L, 3L);
        player.getWorld().playSound(loc, Sound.BLOCK_PORTAL_AMBIENT, 0.85f, 1.25f);
        player.sendMessage(com.darkniightz.core.Messages.prefixed("§bTime bubble sealed. §7Everything gets a little slower."));
    }

    private void runSparklerTrail(Player player) {
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!player.isOnline() || tick++ >= 160) {
                    cancel();
                    return;
                }
                Location loc = player.getLocation().add(0, 0.15, 0);
                player.getWorld().spawnParticle(Particle.FIREWORK, loc, 6, 0.25, 0.08, 0.25, 0.02);
                player.getWorld().spawnParticle(Particle.END_ROD, loc, 2, 0.12, 0.05, 0.12, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 1L);
        player.sendMessage(com.darkniightz.core.Messages.prefixed("§6Sparkler trail armed. §7You leave a bright little wake."));
    }

    private Material randomConcreteColor() {
        Material[] colors = {
                Material.RED_CONCRETE,
                Material.BLUE_CONCRETE,
                Material.GREEN_CONCRETE,
                Material.YELLOW_CONCRETE,
                Material.PURPLE_CONCRETE,
                Material.PINK_CONCRETE,
                Material.ORANGE_CONCRETE,
                Material.LIGHT_BLUE_CONCRETE
        };
        return colors[ThreadLocalRandom.current().nextInt(colors.length)];
    }

    private Location getTargetLocation(Player player, int distance) {
        try {
            Block block = player.getTargetBlockExact(distance);
            if (block != null) {
                return block.getLocation();
            }
        } catch (NoSuchMethodError ignored) {
            // Fallback below.
        }
        Location eye = player.getEyeLocation();
        return eye.add(eye.getDirection().normalize().multiply(Math.max(1, distance / 2.0)));
    }

    private void runDefaultToy(Player player) {
        var loc = player.getLocation().add(0, 1.0, 0);
        player.getWorld().spawnParticle(resolveParticleByName("ENCHANTMENT_TABLE", Particle.CLOUD), loc, 20, 0.25, 0.35, 0.25, 0.3);
        player.getWorld().playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.75f, 1.1f);
        player.sendMessage(com.darkniightz.core.Messages.prefixed("§7The toybox glows softly, but nothing else happens."));
    }

    private int getSlot() {
        return Math.max(0, Math.min(8, plugin.getConfig().getInt("hotbar.toybox.slot", 8)));
    }

    private Particle resolveParticleByName(String name, Particle fallback) {
        try {
            return Particle.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private void recordFeed(Player player, String title, List<String> details) {
        if (plugin instanceof com.darkniightz.main.JebaitedCore core && core.getDebugFeedManager() != null) {
            core.getDebugFeedManager().recordGadget(player, title, details);
        }
    }
}
