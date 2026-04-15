package com.darkniightz.core.cosmetics;

import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.dev.DebugFeedManager;
import com.darkniightz.core.world.WorldManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class ToyboxListener implements Listener {
    private final Plugin plugin;
    private final ProfileStore profiles;
    private final ToyboxManager toyboxManager;
    private final DebugFeedManager feed;
    private final WorldManager worldManager;

    public ToyboxListener(Plugin plugin, ProfileStore profiles, ToyboxManager toyboxManager, DebugFeedManager feed) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.toyboxManager = toyboxManager;
        this.feed = feed;
        this.worldManager = plugin instanceof JebaitedCore core ? core.getWorldManager() : new WorldManager(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("feature_flags.hub.cosmetics", true)) return;
        Player player = event.getPlayer();
        profiles.getOrCreate(player, plugin.getConfig().getString("ranks.default", "pleb"));
        if (!worldManager.isHub(player)) {
            toyboxManager.clear(player);
            return;
        }
        toyboxManager.refresh(player);
        if (feed != null) {
            feed.recordGadget(player, "Toybox refreshed on join", java.util.List.of("§7The active gadget item was placed in the hotbar."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("feature_flags.hub.cosmetics", true)) return;
        if (!worldManager.isHub(event.getPlayer())) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!isRightClick(event.getAction())) return;
        ItemStack item = event.getItem();
        if (!toyboxManager.isToyboxItem(item)) return;
        event.setCancelled(true);
        if (feed != null) {
            feed.recordGadget(event.getPlayer(), "Toybox used", java.util.List.of("§7Right-click gadget item was triggered."));
        }
        toyboxManager.trigger(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!plugin.getConfig().getBoolean("feature_flags.hub.cosmetics", true)) return;
        if (!worldManager.isHub(event.getPlayer())) return;
        if (toyboxManager.isToyboxItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            if (feed != null) {
                feed.recordGadget(event.getPlayer(), "Toybox drop blocked", java.util.List.of("§7A gadget item cannot be dropped."));
            }
        }
    }

    private boolean isRightClick(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }
}
