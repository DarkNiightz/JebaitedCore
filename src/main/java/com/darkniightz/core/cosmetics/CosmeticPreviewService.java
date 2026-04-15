package com.darkniightz.core.cosmetics;

import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Objects;

public class CosmeticPreviewService {
    private final Plugin plugin;
    private final ProfileStore profiles;
    private final CosmeticsManager cosmetics;
    private final CosmeticsEngine cosmeticsEngine;
    private final ToyboxManager toyboxManager;

    public CosmeticPreviewService(Plugin plugin, ProfileStore profiles, CosmeticsManager cosmetics, CosmeticsEngine cosmeticsEngine, ToyboxManager toyboxManager) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.cosmetics = cosmetics;
        this.cosmeticsEngine = cosmeticsEngine;
        this.toyboxManager = toyboxManager;
    }

    public void preview(Player player, CosmeticsManager.Cosmetic cosmetic) {
        if (player == null || cosmetic == null || !cosmetic.enabled) {
            return;
        }
        PlayerProfile profile = profiles.getOrCreate(player, plugin.getConfig().getString("ranks.default", "pleb"));
        if (profile != null) {
            profile.markPreviewedCosmetic(cosmetic.key);
            profiles.saveDeferred(player.getUniqueId());
        }
        previewEffect(player, cosmetic);
        recordFeed(player, "Cosmetic preview", List.of(
                "§7Item: §f" + cosmetic.name,
                "§7Key: §f" + cosmetic.key,
                "§7Category: §f" + cosmetic.category.name().toLowerCase()
        ));
    }

    public void previewByKey(Player player, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        preview(player, cosmetics.get(key));
    }

    public void showcaseFeatured(Player player) {
        if (player == null) {
            return;
        }
        CosmeticsManager.Cosmetic featured = cosmetics.getFeaturedCosmetic();
        if (featured == null) {
            player.sendMessage(com.darkniightz.core.Messages.prefixed("§7There isn't a featured cosmetic right now."));
            return;
        }
        PlayerProfile profile = profiles.getOrCreate(player, plugin.getConfig().getString("ranks.default", "pleb"));
        if (profile != null) {
            profile.markPreviewedCosmetic(featured.key);
            profiles.saveDeferred(player.getUniqueId());
        }

        player.sendMessage(com.darkniightz.core.Messages.prefixed("§d§lSpotlight Preview §7- §f" + featured.name + " §7(" + cosmetics.rarityLabel(featured) + "§7)"));
        if (featured.category == CosmeticsManager.Category.GADGETS) {
            runGadgetShowcase(player, featured);
        } else if (featured.category == CosmeticsManager.Category.TAGS) {
            player.sendMessage(com.darkniightz.core.Messages.prefixed("§7Tags are text cosmetics and do not have a visual preview pulse."));
        } else {
            runTimedPreview(player, featured, 3, 20L);
        }
        recordFeed(player, "Spotlight preview", List.of(
                "§7Item: §f" + featured.name,
                "§7Key: §f" + featured.key,
                "§7Category: §f" + featured.category.name().toLowerCase()
        ));
    }

    private void previewEffect(Player player, CosmeticsManager.Cosmetic cosmetic) {
        switch (cosmetic.category) {
            case PARTICLES -> {
                if (cosmeticsEngine != null) cosmeticsEngine.previewParticle(player, cosmetic.key);
            }
            case TRAILS -> {
                if (cosmeticsEngine != null) cosmeticsEngine.previewTrail(player, cosmetic.key);
            }
            case GADGETS -> {
                if (toyboxManager != null) toyboxManager.preview(player, cosmetic.key);
            }
            case TAGS -> {
                // Tags are display-only cosmetics and do not spawn particles or gadgets.
            }
        }
    }

    private void runTimedPreview(Player player, CosmeticsManager.Cosmetic cosmetic, int pulses, long periodTicks) {
        int total = Math.max(1, pulses);
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!player.isOnline() || tick >= total) {
                    cancel();
                    return;
                }
                previewEffect(player, cosmetic);
                if (tick == total - 1) {
                    if (soundEnabled(player)) {
                        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
                    }
                    player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1.2, 0), 18, 0.25, 0.35, 0.25, 0.02);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, Math.max(1L, periodTicks));
    }

    private void runGadgetShowcase(Player player, CosmeticsManager.Cosmetic cosmetic) {
        previewEffect(player, cosmetic);
        new BukkitRunnable() {
            int pulse = 0;

            @Override
            public void run() {
                if (!player.isOnline() || pulse >= 3) {
                    cancel();
                    return;
                }
                var loc = player.getLocation().add(0, 1.0 + (pulse * 0.1), 0);
                player.getWorld().spawnParticle(Particle.END_ROD, loc, 8, 0.18, 0.2, 0.18, 0.02);
                player.getWorld().spawnParticle(Particle.CLOUD, loc, 10, 0.22, 0.22, 0.22, 0.03);
                if (soundEnabled(player)) {
                    player.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.4f);
                }
                pulse++;
            }
        }.runTaskTimer(plugin, 20L, 12L);
    }

    private boolean soundEnabled(Player player) {
        PlayerProfile profile = profiles.getOrCreate(player, plugin.getConfig().getString("ranks.default", "pleb"));
        return profile == null || profile.isSoundCuesEnabled();
    }

    private void recordFeed(Player player, String title, List<String> details) {
        if (plugin instanceof com.darkniightz.main.JebaitedCore core && core.getDebugFeedManager() != null) {
            core.getDebugFeedManager().recordGadget(player, title, details);
        }
    }
}
