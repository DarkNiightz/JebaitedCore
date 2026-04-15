package com.darkniightz.core.playerjoin;

import com.darkniightz.core.cosmetics.CosmeticsEngine;
import com.darkniightz.core.cosmetics.ToyboxManager;
import com.darkniightz.core.hub.HotbarNavigatorListener;
import com.darkniightz.core.world.SmpReturnManager;
import com.darkniightz.core.world.SpawnManager;
import com.darkniightz.core.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Supported Paper range: 1.21.9-1.21.11.
 */
public class WorldChangeListener implements Listener {
    private final Plugin plugin;
    private final WorldManager worldManager;
    private final SpawnManager spawnManager;
    private final CosmeticsEngine cosmeticsEngine;
    private final ToyboxManager toyboxManager;
    private final HotbarNavigatorListener hotbarNavigator;
    private final Map<UUID, String> lastDeathWorld = new ConcurrentHashMap<>();
    private final Map<UUID, InventorySnapshot> hubSnapshots = new ConcurrentHashMap<>();
    private final Map<UUID, InventorySnapshot> smpSnapshots = new ConcurrentHashMap<>();
    private final Map<UUID, VitalSnapshot> hubVitals = new ConcurrentHashMap<>();
    private final Map<UUID, VitalSnapshot> smpVitals = new ConcurrentHashMap<>();

    public WorldChangeListener(Plugin plugin, WorldManager worldManager, SpawnManager spawnManager, CosmeticsEngine cosmeticsEngine, ToyboxManager toyboxManager, HotbarNavigatorListener hotbarNavigator) {
        this.plugin = plugin;
        this.worldManager = worldManager;
        this.spawnManager = spawnManager;
        this.cosmeticsEngine = cosmeticsEngine;
        this.toyboxManager = toyboxManager;
        this.hotbarNavigator = hotbarNavigator;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        applyGamemodeForWorld(player);

        if (worldManager.isSmp(player)) {
            SmpReturnManager.remember(player);
            smpSnapshots.put(player.getUniqueId(), captureInventory(player));
            smpVitals.put(player.getUniqueId(), captureVitals(player));
        }

        // Always route relogs to Hub spawn when enabled; /smp returns players to their remembered SMP location.
        boolean routeToHub = !(plugin instanceof com.darkniightz.main.JebaitedCore core)
                || core.getWorldConfigManager() == null
                || core.getWorldConfigManager().shouldRouteJoinToHub();
        if (!routeToHub) {
            if (worldManager.isHub(player)) {
                applyHubLoadout(player);
            }
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            World hub = Bukkit.getWorld(worldManager.getHubWorldName());
            if (hub == null) {
                return;
            }
            Location target = spawnManager == null ? null : spawnManager.getSpawnForWorld(hub.getName());
            if (target == null) {
                target = hub.getSpawnLocation();
            }
            if (target != null) {
                player.teleport(target);
            }

            applyHubLoadout(player);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (worldManager.isSmp(player)) {
            SmpReturnManager.remember(player);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (player.getWorld() != null) {
            lastDeathWorld.put(player.getUniqueId(), player.getWorld().getName());
        }
        // Auto-respawn — skip the death screen entirely; respawn location set in onRespawn below
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.isDead()) {
                player.spigot().respawn();
            }
        }, 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!event.isBedSpawn() && !event.isAnchorSpawn()) {
            String worldName = lastDeathWorld.remove(player.getUniqueId());
            World preferred = worldName == null ? null : Bukkit.getWorld(worldName);
            if (preferred == null && event.getRespawnLocation() != null) {
                preferred = event.getRespawnLocation().getWorld();
            }
            if (preferred == null) {
                preferred = player.getWorld();
            }
            if (preferred != null) {
                Location target = spawnManager == null ? null : spawnManager.getSpawnForWorld(preferred.getName());
                if (target == null) {
                    target = preferred.getSpawnLocation();
                }
                if (target != null) {
                    event.setRespawnLocation(target);
                }
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> applyGamemodeForWorld(player));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        boolean fromHub = worldManager.isHub(event.getFrom());
        boolean toHub = worldManager.isHub(player);
        boolean toSmp = worldManager.isSmp(player);

        if (toSmp) {
            if (fromHub) {
                hubSnapshots.put(player.getUniqueId(), captureInventory(player));
                hubVitals.put(player.getUniqueId(), captureVitals(player));
            }
            InventorySnapshot smp = smpSnapshots.get(player.getUniqueId());
            if (smp != null) {
                applyInventory(player, smp);
            }
            VitalSnapshot smpVital = smpVitals.get(player.getUniqueId());
            if (smpVital != null) {
                applyVitals(player, smpVital);
            }
            player.setGameMode(GameMode.SURVIVAL);
            if (cosmeticsEngine != null) {
                cosmeticsEngine.clearActiveEffects(player);
            }
            if (hotbarNavigator != null) {
                hotbarNavigator.clearHubHotbar(player);
            }
            if (toyboxManager != null) {
                toyboxManager.clear(player);
            }
            return;
        }
        if (toHub) {
            if (!fromHub) {
                smpSnapshots.put(player.getUniqueId(), captureInventory(player));
                smpVitals.put(player.getUniqueId(), captureVitals(player));
            }
            if (event.getFrom() != null && !worldManager.getHubWorldName().equalsIgnoreCase(event.getFrom().getName())) {
                Location hubSpawn = spawnManager == null ? null : spawnManager.getSpawnForWorld(worldManager.getHubWorldName());
                if (hubSpawn == null && player.getWorld() != null) {
                    hubSpawn = player.getWorld().getSpawnLocation();
                }
                Location finalHubSpawn = hubSpawn;
                if (finalHubSpawn != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> player.teleport(finalHubSpawn));
                }
            }
            applyHubLoadout(player);
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getFrom() == null || event.getFrom().getWorld() == null) {
            return;
        }
        if (event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }
        String from = event.getFrom().getWorld().getName();
        String to = event.getTo().getWorld().getName();
        if (worldManager.getSmpWorldName().equalsIgnoreCase(from) && !worldManager.getSmpWorldName().equalsIgnoreCase(to)) {
            SmpReturnManager.remember(event.getPlayer().getUniqueId(), event.getFrom());
        }
    }

    private void applyGamemodeForWorld(Player player) {
        if (player == null) {
            return;
        }
        if (worldManager.isHub(player)) {
            player.setGameMode(GameMode.ADVENTURE);
        } else if (worldManager.isSmp(player)) {
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    private void applyHubLoadout(Player player) {
        if (player == null) return;

        InventorySnapshot snapshot = hubSnapshots.get(player.getUniqueId());
        if (snapshot != null) {
            applyInventory(player, snapshot);
        } else {
            clearInventory(player);
        }

        VitalSnapshot vital = hubVitals.get(player.getUniqueId());
        if (vital != null) {
            applyVitals(player, vital);
        } else {
            // Hub has its own stable bars and does not grant SMP healing when players return.
            applyVitals(player, VitalSnapshot.defaultHub());
        }

        player.setGameMode(GameMode.ADVENTURE);
        if (hotbarNavigator != null) {
            hotbarNavigator.ensureHubHotbar(player);
        }
        if (toyboxManager != null) {
            toyboxManager.refresh(player);
        }
    }

    private InventorySnapshot captureInventory(Player player) {
        return new InventorySnapshot(
                player.getInventory().getStorageContents().clone(),
                player.getInventory().getArmorContents().clone(),
                player.getInventory().getItemInOffHand() == null ? null : player.getInventory().getItemInOffHand().clone(),
                player.getInventory().getHeldItemSlot(),
                player.getExp(),
                player.getLevel()
        );
    }

    private VitalSnapshot captureVitals(Player player) {
        return new VitalSnapshot(
                player.getHealth(),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExhaustion(),
                player.getFireTicks()
        );
    }

    private void applyInventory(Player player, InventorySnapshot snapshot) {
        if (player == null || snapshot == null) return;
        player.getInventory().setStorageContents(snapshot.storage == null ? null : snapshot.storage.clone());
        player.getInventory().setArmorContents(snapshot.armor == null ? null : snapshot.armor.clone());
        player.getInventory().setItemInOffHand(snapshot.offHand == null ? null : snapshot.offHand.clone());
        player.getInventory().setHeldItemSlot(Math.max(0, Math.min(8, snapshot.heldSlot)));
        player.setExp(snapshot.exp);
        player.setLevel(snapshot.level);
        player.updateInventory();
    }

    private void clearInventory(Player player) {
        if (player == null) return;
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.getInventory().setHeldItemSlot(0);
        player.setExp(0f);
        player.setLevel(0);
        player.updateInventory();
    }

    private void applyVitals(Player player, VitalSnapshot vitals) {
        if (player == null || vitals == null) return;
        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null
                ? 20.0
            : player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        player.setHealth(Math.max(0.5D, Math.min(maxHealth, vitals.health)));
        player.setFoodLevel(Math.max(0, Math.min(20, vitals.food)));
        player.setSaturation(Math.max(0f, Math.min(20f, vitals.saturation)));
        player.setExhaustion(Math.max(0f, vitals.exhaustion));
        player.setFireTicks(Math.max(0, vitals.fireTicks));
    }

    private record InventorySnapshot(
            org.bukkit.inventory.ItemStack[] storage,
            org.bukkit.inventory.ItemStack[] armor,
            org.bukkit.inventory.ItemStack offHand,
            int heldSlot,
            float exp,
            int level
    ) {}

    private record VitalSnapshot(
            double health,
            int food,
            float saturation,
            float exhaustion,
            int fireTicks
    ) {
        private static VitalSnapshot defaultHub() {
            return new VitalSnapshot(20.0D, 20, 20.0f, 0f, 0);
        }
    }
}
