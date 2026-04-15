package com.darkniightz.core.cosmetics;

import com.darkniightz.core.moderation.ModerationManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.world.WorldManager;
import org.bukkit.Color;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class CosmeticsEngine {
    private final Plugin plugin;
    private final ProfileStore profiles;
    private final ModerationManager moderation;
    private final RankManager ranks;
    private final com.darkniightz.core.cosmetics.CosmeticsManager cosmeticsManager;
    private final WorldManager worldManager;
    private BukkitTask task;

    public CosmeticsEngine(Plugin plugin, ProfileStore profiles, ModerationManager moderation, RankManager ranks) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.moderation = moderation;
        this.ranks = ranks;
        this.cosmeticsManager = (plugin instanceof com.darkniightz.main.JebaitedCore jc) ? jc.getCosmeticsManager() : null;
        if (plugin instanceof com.darkniightz.main.JebaitedCore jc && jc.getWorldManager() != null) {
            this.worldManager = jc.getWorldManager();
        } else {
            this.worldManager = new WorldManager(plugin);
        }
    }

    public void start() {
        stop();
        long periodTicks = 10L; // ~0.5s
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, periodTicks, periodTicks);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    private void tick() {
        boolean hideWhenVanished = plugin.getConfig().getBoolean("cosmetics.rules.disable_when_vanished", true);
        int autoOffMin = plugin.getConfig().getInt("cosmetics.rules.particles.auto_off_minutes", 5);
        long autoOffMs = autoOffMin > 0 ? autoOffMin * 60_000L : -1L;
        int perTickCap = Math.max(1, plugin.getConfig().getInt("cosmetics.rules.particles.per_tick_cap", 100));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!worldManager.isHub(p)) {
                continue;
            }
            PlayerProfile prof = profiles.getOrCreate(p, ranks.getDefaultGroup());
            // Skip if vanished and config says to hide
            if (hideWhenVanished && moderation != null && moderation.isVanished(p.getUniqueId())) continue;

            long now = System.currentTimeMillis();

            // Particles
            String part = prof.getEquippedParticles();
            if (part != null) {
                // Auto-off timer
                Long at = prof.getParticleActivatedAt();
                if (autoOffMs > 0 && at != null && now - at > autoOffMs) {
                    prof.setEquippedParticles(null);
                    prof.setParticleActivatedAt(null);
                    profiles.save(p.getUniqueId());
                    p.sendMessage(com.darkniightz.core.Messages.prefixed("§eYour particle effect has been turned off to prevent lag."));
                } else {
                    spawnParticleAdvanced(p, part, perTickCap);
                }
            }

            // Trails (spawn at feet moving)
            String trail = prof.getEquippedTrail();
            if (trail != null) {
                Long at = prof.getTrailActivatedAt();
                if (autoOffMs > 0 && at != null && now - at > autoOffMs) {
                    prof.setEquippedTrail(null);
                    prof.setTrailActivatedAt(null);
                    profiles.save(p.getUniqueId());
                    p.sendMessage(com.darkniightz.core.Messages.prefixed("§eYour trail has been turned off to prevent lag."));
                } else {
                    spawnTrailForKey(p, trail);
                }
            }
        }
    }

    private void spawnParticleAdvanced(Player p, String key, int perTickCap) {
        String base = "cosmetics.catalog.particles." + key;
        org.bukkit.configuration.ConfigurationSection sec = plugin.getConfig().getConfigurationSection(base);
        String particleName = sec != null ? sec.getString("particle", null) : null;
        Particle particle = resolveParticle(particleName, key);
        Color dustColor = parseColor(sec != null ? sec.getString("color") : null);
        Color dustColorTo = parseColor(sec != null ? sec.getString("color_to") : null);
        String pattern = sec != null ? sec.getString("pattern", "spiral") : "spiral";
        double radius = sec != null ? sec.getDouble("radius", 0.6) : 0.6;
        double speed = sec != null ? sec.getDouble("speed", 0.2) : 0.2;
        int density = sec != null ? sec.getInt("density", 12) : 12;
        density = Math.max(1, Math.min(density, perTickCap));

        var loc = p.getLocation().add(0, 1.2, 0);
        double t = (System.currentTimeMillis() % 10_000L) / 1000.0; // seconds rolling
        double baseAngle = t * (2 * Math.PI) * speed; // revolutions per second scaled by speed

        switch (pattern.toLowerCase(java.util.Locale.ROOT)) {
            case "ring" -> {
                for (int i = 0; i < density; i++) {
                    double ang = baseAngle + (2 * Math.PI) * (i / (double) density);
                    double x = radius * Math.cos(ang);
                    double z = radius * Math.sin(ang);
                    spawnParticle(p, particle, loc.getX() + x, loc.getY(), loc.getZ() + z, dustColor, dustColorTo);
                }
            }
            case "helix" -> {
                double height = 1.0;
                for (int i = 0; i < density; i++) {
                    double prog = i / (double) density;
                    double ang = baseAngle + prog * 4 * Math.PI;
                    double y = loc.getY() + prog * height;
                    double x = radius * Math.cos(ang);
                    double z = radius * Math.sin(ang);
                    spawnParticle(p, particle, loc.getX() + x, y, loc.getZ() + z, dustColor, dustColorTo);
                }
            }
            case "double_helix", "double-helix" -> {
                double height = 1.0;
                for (int i = 0; i < density; i++) {
                    double prog = i / (double) density;
                    double ang = baseAngle + prog * 4 * Math.PI;
                    double y = loc.getY() + prog * height;
                    double x1 = radius * Math.cos(ang);
                    double z1 = radius * Math.sin(ang);
                    double x2 = radius * Math.cos(ang + Math.PI);
                    double z2 = radius * Math.sin(ang + Math.PI);
                    spawnParticle(p, particle, loc.getX() + x1, y, loc.getZ() + z1, dustColor, dustColorTo);
                    spawnParticle(p, particle, loc.getX() + x2, y, loc.getZ() + z2, dustColor, dustColorTo);
                }
            }
            case "pulse" -> {
                double pulse = 0.3 + (Math.sin(baseAngle * 2) + 1.0) * 0.2;
                for (int i = 0; i < density; i++) {
                    double ang = baseAngle + (2 * Math.PI) * (i / (double) density);
                    double y = loc.getY() + Math.sin(baseAngle + i * 0.25) * pulse;
                    double x = radius * Math.cos(ang);
                    double z = radius * Math.sin(ang);
                    spawnParticle(p, particle, loc.getX() + x, y, loc.getZ() + z, dustColor, dustColorTo);
                }
            }
            default -> {
                // spiral
                for (int i = 0; i < density; i++) {
                    double ang = baseAngle + (2 * Math.PI) * (i / (double) density);
                    double y = loc.getY() + (i % 4) * 0.05; // slight vertical variation
                    double x = radius * Math.cos(ang);
                    double z = radius * Math.sin(ang);
                    spawnParticle(p, particle, loc.getX() + x, y, loc.getZ() + z, dustColor, dustColorTo);
                }
            }
        }
    }

    public void previewParticle(Player player, String key) {
        if (!worldManager.isHub(player)) return;
        spawnParticleAdvanced(player, key, Math.max(1, plugin.getConfig().getInt("cosmetics.rules.particles.per_tick_cap", 100)));
    }

    public void previewTrail(Player player, String key) {
        if (!worldManager.isHub(player)) return;
        spawnTrailForKey(player, key);
    }

    public void clearActiveEffects(Player player) {
        if (player == null) return;
        PlayerProfile profile = profiles.getOrCreate(player, ranks.getDefaultGroup());
        if (profile == null) return;
        boolean changed = false;
        if (profile.getEquippedParticles() != null) {
            profile.setEquippedParticles(null);
            profile.setParticleActivatedAt(null);
            changed = true;
        }
        if (profile.getEquippedTrail() != null) {
            profile.setEquippedTrail(null);
            profile.setTrailActivatedAt(null);
            changed = true;
        }
        if (changed) {
            profiles.save(player.getUniqueId());
        }
    }

    private void spawnTrailForKey(Player p, String key) {
        Particle particle = resolveTrailParticle(key);
        if (particle == null) return;

        var loc = p.getLocation().add(0, 0.1, 0);
        for (Player viewer : viewersFor(p)) {
            switch (key) {
                case "prism_trail" -> {
                    Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(120, 170, 255), 1.2f);
                    viewer.spawnParticle(resolveParticleByName("DUST", Particle.CLOUD), loc, 10, 0.16, 0.05, 0.16, 0.0, dust);
                }
                case "ember_tail" -> {
                    Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 123, 0), 1.1f);
                    viewer.spawnParticle(resolveParticleByName("DUST", Particle.CLOUD), loc, 8, 0.12, 0.03, 0.12, 0.0, dust);
                    viewer.spawnParticle(Particle.FLAME, loc, 2, 0.08, 0.02, 0.08, 0.0);
                }
                case "bubble_stream" -> {
                    viewer.spawnParticle(Particle.BUBBLE_POP, loc, 12, 0.18, 0.08, 0.18, 0.0);
                    viewer.spawnParticle(Particle.SPLASH, loc, 6, 0.15, 0.02, 0.15, 0.0);
                }
                default -> viewer.spawnParticle(particle, loc, 8, 0.2, 0.0, 0.2, 0.0);
            }
        }
    }

    private Particle resolveParticle(String particleName, String key) {
        if (particleName != null) {
            try {
                return Particle.valueOf(particleName.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Fall back to curated defaults below.
            }
        }
        return switch (key) {
            case "ember_sparks" -> Particle.FLAME;
            case "leaf_whirl" -> resolveParticleByName("VILLAGER_HAPPY", Particle.CLOUD);
            case "aqua_bubbles" -> Particle.BUBBLE_POP;
            case "prism_glow" -> resolveParticleByName("DUST", Particle.CLOUD);
            case "aurora_ribbon" -> resolveParticleByName("DUST", Particle.CLOUD);
            case "cherry_drift" -> resolveParticleByName("VILLAGER_HAPPY", Particle.CLOUD);
            case "nebula_orbit" -> resolveParticleByName("DUST", Particle.CLOUD);
            default -> Particle.CLOUD;
        };
    }

    private Particle resolveTrailParticle(String key) {
        return switch (key) {
            case "rune_trail" -> Particle.END_ROD;
            case "feather_trail" -> Particle.CLOUD;
            case "star_trail" -> Particle.CRIT;
            case "prism_trail" -> resolveParticleByName("DUST", Particle.CLOUD);
            case "ember_tail" -> Particle.FLAME;
            case "bubble_stream" -> Particle.BUBBLE_POP;
            default -> null;
        };
    }

    private void spawnParticle(Player p, Particle particle, double x, double y, double z, Color dustColor, Color dustColorTo) {
        for (Player viewer : viewersFor(p)) {
            if (isDustParticle(particle)) {
                if (dustColorTo != null) {
                    viewer.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0, new Particle.DustTransition(dustColor != null ? dustColor : Color.fromRGB(255, 255, 255), dustColorTo, 1.3f));
                } else {
                    viewer.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0, new Particle.DustOptions(dustColor != null ? dustColor : Color.fromRGB(255, 255, 255), 1.3f));
                }
                continue;
            }
            viewer.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    private java.util.List<Player> viewersFor(Player owner) {
        java.util.List<Player> viewers = new java.util.ArrayList<>();
        if (owner == null || owner.getWorld() == null) {
            return viewers;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getWorld().equals(owner.getWorld())) continue;
            if (viewer.getLocation().distanceSquared(owner.getLocation()) > (128D * 128D)) continue;
            PlayerProfile viewerProfile = profiles.getOrCreate(viewer, ranks.getDefaultGroup());
            if (viewerProfile != null && !viewerProfile.isCosmeticVisibilityEnabled()) continue;
            viewers.add(viewer);
        }
        return viewers;
    }

    private Color parseColor(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String cleaned = raw.trim().replace("#", "");
        try {
            if (cleaned.contains(",")) {
                String[] parts = cleaned.split(",");
                if (parts.length >= 3) {
                    int r = Integer.parseInt(parts[0].trim());
                    int g = Integer.parseInt(parts[1].trim());
                    int b = Integer.parseInt(parts[2].trim());
                    return Color.fromRGB(clamp255(r), clamp255(g), clamp255(b));
                }
            }
            if (cleaned.length() == 6) {
                int rgb = Integer.parseInt(cleaned, 16);
                return Color.fromRGB(rgb);
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private Particle resolveParticleByName(String name, Particle fallback) {
        try {
            return Particle.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private boolean isDustParticle(Particle particle) {
        String name = particle.name();
        return "DUST".equals(name) || "REDSTONE".equals(name);
    }
}
