package com.darkniightz.core.cosmetics;

import com.darkniightz.core.moderation.ModerationManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
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
    private BukkitTask task;

    // Config values cached at construction time (engine is recreated on reload)
    private final boolean hideWhenVanished;
    private final long autoOffMs;
    private final int perTickCap;

    // Parsed particle render configs, populated lazily and reused each tick
    private static final class CachedParticleConfig {
        final Particle particle;
        final String pattern;
        final double radius;
        final double speed;
        final int density;

        CachedParticleConfig(Particle particle, String pattern, double radius, double speed, int density) {
            this.particle = particle;
            this.pattern = pattern;
            this.radius = radius;
            this.speed = speed;
            this.density = density;
        }
    }

    private final java.util.Map<String, CachedParticleConfig> particleConfigCache = new java.util.concurrent.ConcurrentHashMap<>();

    public CosmeticsEngine(Plugin plugin, ProfileStore profiles, ModerationManager moderation, RankManager ranks) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.moderation = moderation;
        this.ranks = ranks;
        this.cosmeticsManager = (plugin instanceof com.darkniightz.main.JebaitedCore jc) ? jc.getCosmeticsManager() : null;
        // Cache tick-level config values once; engine is recreated on /jreload
        this.hideWhenVanished = plugin.getConfig().getBoolean("cosmetics.rules.disable_when_vanished", true);
        int autoOffMin = plugin.getConfig().getInt("cosmetics.rules.particles.auto_off_minutes", 5);
        this.autoOffMs = autoOffMin > 0 ? autoOffMin * 60_000L : -1L;
        this.perTickCap = Math.max(1, plugin.getConfig().getInt("cosmetics.rules.particles.per_tick_cap", 100));
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
        for (Player p : Bukkit.getOnlinePlayers()) {
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
                    p.sendMessage("§eYour particle effect has been turned off to prevent lag.");
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
                    p.sendMessage("§eYour trail has been turned off to prevent lag.");
                } else {
                    spawnTrailForKey(p, trail);
                }
            }
        }
    }

    private void spawnParticleAdvanced(Player p, String key, int perTickCap) {
        CachedParticleConfig cfg = particleConfigCache.computeIfAbsent(key, this::parseParticleConfig);
        int density = Math.max(1, Math.min(cfg.density, perTickCap));

        var loc = p.getLocation().add(0, 1.2, 0);
        double t = (System.currentTimeMillis() % 10_000L) / 1000.0; // seconds rolling
        double baseAngle = t * (2 * Math.PI) * cfg.speed; // revolutions per second scaled by speed

        switch (cfg.pattern.toLowerCase(java.util.Locale.ROOT)) {
            case "ring" -> {
                for (int i = 0; i < density; i++) {
                    double ang = baseAngle + (2 * Math.PI) * (i / (double) density);
                    double x = cfg.radius * Math.cos(ang);
                    double z = cfg.radius * Math.sin(ang);
                    p.getWorld().spawnParticle(cfg.particle, loc.getX() + x, loc.getY(), loc.getZ() + z, 1, 0, 0, 0, 0);
                }
            }
            case "helix" -> {
                double height = 1.0;
                for (int i = 0; i < density; i++) {
                    double prog = i / (double) density;
                    double ang = baseAngle + prog * 4 * Math.PI;
                    double y = loc.getY() + prog * height;
                    double x = cfg.radius * Math.cos(ang);
                    double z = cfg.radius * Math.sin(ang);
                    p.getWorld().spawnParticle(cfg.particle, loc.getX() + x, y, loc.getZ() + z, 1, 0, 0, 0, 0);
                }
            }
            case "double_helix", "double-helix" -> {
                double height = 1.0;
                for (int i = 0; i < density; i++) {
                    double prog = i / (double) density;
                    double ang = baseAngle + prog * 4 * Math.PI;
                    double y = loc.getY() + prog * height;
                    double x1 = cfg.radius * Math.cos(ang);
                    double z1 = cfg.radius * Math.sin(ang);
                    double x2 = cfg.radius * Math.cos(ang + Math.PI);
                    double z2 = cfg.radius * Math.sin(ang + Math.PI);
                    p.getWorld().spawnParticle(cfg.particle, loc.getX() + x1, y, loc.getZ() + z1, 1, 0, 0, 0, 0);
                    p.getWorld().spawnParticle(cfg.particle, loc.getX() + x2, y, loc.getZ() + z2, 1, 0, 0, 0, 0);
                }
            }
            default -> {
                // spiral
                for (int i = 0; i < density; i++) {
                    double ang = baseAngle + (2 * Math.PI) * (i / (double) density);
                    double y = loc.getY() + (i % 4) * 0.05; // slight vertical variation
                    double x = cfg.radius * Math.cos(ang);
                    double z = cfg.radius * Math.sin(ang);
                    p.getWorld().spawnParticle(cfg.particle, loc.getX() + x, y, loc.getZ() + z, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    private CachedParticleConfig parseParticleConfig(String key) {
        String base = "cosmetics.catalog.particles." + key;
        org.bukkit.configuration.ConfigurationSection sec = plugin.getConfig().getConfigurationSection(base);
        String particleName = sec != null ? sec.getString("particle", null) : null;
        Particle particle;
        if (particleName != null) {
            try { particle = Particle.valueOf(particleName.toUpperCase(java.util.Locale.ROOT)); }
            catch (IllegalArgumentException ex) { particle = Particle.CLOUD; }
        } else {
            particle = switch (key) { case "ember_sparks" -> Particle.FLAME; default -> Particle.CLOUD; };
        }
        String pattern = sec != null ? sec.getString("pattern", "spiral") : "spiral";
        double radius = sec != null ? sec.getDouble("radius", 0.6) : 0.6;
        double speed = sec != null ? sec.getDouble("speed", 0.2) : 0.2;
        int density = sec != null ? sec.getInt("density", 12) : 12;
        return new CachedParticleConfig(particle, pattern, radius, speed, density);
    }

    private void spawnTrailForKey(Player p, String key) {
        Particle particle = switch (key) {
            case "rune_trail" -> Particle.END_ROD;
            case "feather_trail" -> Particle.CLOUD;
            case "star_trail" -> Particle.CRIT;
            default -> null;
        };
        if (particle != null) {
            p.getWorld().spawnParticle(particle, p.getLocation().add(0, 0.1, 0), 8, 0.2, 0.0, 0.2, 0.0);
        }
    }
}
