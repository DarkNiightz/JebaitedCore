package com.darkniightz.core.world;

import com.darkniightz.core.cosmetics.CollectionBookMenu;
import com.darkniightz.core.cosmetics.CosmeticPreviewService;
import com.darkniightz.core.cosmetics.CosmeticsManager;
import com.darkniightz.core.cosmetics.ToyboxManager;
import com.darkniightz.core.players.ProfileStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PreviewPedestalListener implements Listener {
    private final Plugin plugin;
    private final PreviewPedestalManager pedestalManager;
    private final CosmeticPreviewService previewService;
    private final CosmeticsManager cosmetics;
    private final ProfileStore profiles;
    private final ToyboxManager toyboxManager;
    private final WorldManager worldManager;
    private final Map<UUID, Long> lastTrigger = new ConcurrentHashMap<>();

    public PreviewPedestalListener(Plugin plugin, PreviewPedestalManager pedestalManager, CosmeticPreviewService previewService, CosmeticsManager cosmetics, ProfileStore profiles, ToyboxManager toyboxManager) {
        this.plugin = plugin;
        this.pedestalManager = pedestalManager;
        this.previewService = previewService;
        this.cosmetics = cosmetics;
        this.profiles = profiles;
        this.toyboxManager = toyboxManager;
        this.worldManager = plugin instanceof com.darkniightz.main.JebaitedCore core ? core.getWorldManager() : new WorldManager(plugin);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom() == null || event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Location pedestal = pedestalManager.getConfiguredPedestal();
        if (pedestal == null) return;
        var pedestalWorld = pedestal.getWorld();
        if (pedestalWorld == null) return;
        Player player = event.getPlayer();
        if (!worldManager.isHub(player)) return;
        if (!pedestalWorld.equals(player.getWorld())) return;
        if (!isStandingOnPedestal(player.getLocation(), pedestal)) return;

        long cooldownMs = Math.max(1000L, plugin.getConfig().getLong("hub.preview_pedestal.cooldown_ms", 8000L));
        long now = System.currentTimeMillis();
        Long last = lastTrigger.get(player.getUniqueId());
        if (last != null && now - last < cooldownMs) return;
        lastTrigger.put(player.getUniqueId(), now);

        var profile = profiles.getOrCreate(player, plugin.getConfig().getString("ranks.default", "pleb"));
        if (profile == null || profile.isSoundCuesEnabled()) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        }
        player.sendMessage(com.darkniightz.core.Messages.prefixed("§d§lPreview Pedestal §7- the spotlight is yours."));

        int openDelay = Math.max(1, plugin.getConfig().getInt("hub.preview_pedestal.open_delay_ticks", 2));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !worldManager.isHub(player)) {
                return;
            }
            new CollectionBookMenu(plugin, cosmetics, profiles, previewService, toyboxManager).open(player);
            if (previewService != null) {
                previewService.showcaseFeatured(player);
            }
        }, openDelay);
    }

    private boolean isStandingOnPedestal(Location current, Location pedestal) {
        return current.getBlockX() == pedestal.getBlockX()
                && current.getBlockY() == pedestal.getBlockY()
                && current.getBlockZ() == pedestal.getBlockZ();
    }
}
