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
    private BukkitTask task;

    public CosmeticsEngine(Plugin plugin, ProfileStore profiles, ModerationManager moderation, RankManager ranks) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.moderation = moderation;
        this.ranks = ranks;
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
        boolean hideWhenVanished = plugin.getConfig().getBoolean("cosmetics.toggles.hide_when_vanished", true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerProfile prof = profiles.getOrCreate(p, ranks.getDefaultGroup());
            // Skip if vanished and config says to hide
            if (hideWhenVanished && moderation != null && moderation.isVanished(p.getUniqueId())) continue;

            // Particles
            String part = prof.getEquippedParticles();
            if (part != null) spawnParticleForKey(p, part);

            // Trails (spawn at feet moving)
            String trail = prof.getEquippedTrail();
            if (trail != null) spawnTrailForKey(p, trail);
        }
    }

    private void spawnParticleForKey(Player p, String key) {
        Particle particle = switch (key) {
            case "ember_sparks" -> Particle.FLAME;
            case "leaf_whirl" -> Particle.CLOUD;
            case "aqua_bubbles" -> Particle.CLOUD;
            default -> null;
        };
        if (particle != null) {
            p.getWorld().spawnParticle(particle, p.getLocation().add(0, 1.2, 0), 12, 0.3, 0.6, 0.3, 0.01);
        }
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
