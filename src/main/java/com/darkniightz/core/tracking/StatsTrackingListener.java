package com.darkniightz.core.tracking;

import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.system.FriendManager;
import com.darkniightz.core.system.OverallStatsManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.Tag;
import org.bukkit.Material;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class StatsTrackingListener implements Listener {
    private static final Set<EntityType> BOSS_TYPES = Set.of(
            EntityType.ENDER_DRAGON,
            EntityType.WITHER,
            EntityType.WARDEN,
            EntityType.ELDER_GUARDIAN,
            EntityType.RAVAGER
    );

    private final ProfileStore profiles;
    private final RankManager ranks;
    private final OverallStatsManager overallStats;
    private final FriendManager friendManager;
    private final Plugin plugin;
    private final Map<UUID, Long> sessionStart = new ConcurrentHashMap<>();
    private final BukkitTask periodicFlushTask;

    public StatsTrackingListener(Plugin plugin, ProfileStore profiles, RankManager ranks, FriendManager friendManager) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.ranks = ranks;
        this.friendManager = friendManager;
        this.overallStats = plugin instanceof JebaitedCore core ? core.getOverallStatsManager() : null;
        this.periodicFlushTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flushOnlinePlaytime, 20L * 60L, 20L * 60L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        sessionStart.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        if (overallStats != null) {
            overallStats.increment(OverallStatsManager.TOTAL_JOINS, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        flushForPlayer(player, true);
    }

    public void flushOnlinePlaytime() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            flushForPlayer(online, false);
        }
    }

    private void flushForPlayer(Player player, boolean removeSession) {
        if (player == null) return;
        Long startedAt = sessionStart.get(player.getUniqueId());
        if (startedAt == null) return;
        long now = System.currentTimeMillis();
        long delta = now - startedAt;
        if (delta <= 0L) return;

        PlayerProfile profile = profiles.getOrCreate(player, ranks.getDefaultGroup());
        if (profile != null) {
            profile.addPlaytimeMs(delta);
            profiles.saveDeferred(player.getUniqueId());
            if (overallStats != null) {
                overallStats.increment(OverallStatsManager.TOTAL_PLAYTIME_MS, delta);
            }
        }

        if (removeSession) {
            sessionStart.remove(player.getUniqueId());
        } else {
            sessionStart.put(player.getUniqueId(), now);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        // Do not count event-mode deaths toward persistent stats
        if (plugin instanceof JebaitedCore core
                && core.getEventModeManager() != null
                && core.getEventModeManager().isParticipant(victim)) {
            return;
        }

        PlayerProfile victimProfile = profiles.getOrCreate(victim, ranks.getDefaultGroup());
        if (victimProfile != null) {
            victimProfile.incDeaths();
            profiles.saveDeferred(victim.getUniqueId());
            if (overallStats != null) {
                overallStats.increment(OverallStatsManager.TOTAL_DEATHS, 1);
            }
        }

        Player killer = victim.getKiller();
        if (killer != null) {
            PlayerProfile killerProfile = profiles.getOrCreate(killer, ranks.getDefaultGroup());
            if (killerProfile != null) {
                killerProfile.incKills();
                profiles.saveDeferred(killer.getUniqueId());
                if (overallStats != null) {
                    overallStats.increment(OverallStatsManager.TOTAL_KILLS, 1);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        if (event.getEntity() instanceof Player) return;

        PlayerProfile killerProfile = profiles.getOrCreate(killer, ranks.getDefaultGroup());
        if (killerProfile == null) return;

        killerProfile.incMobsKilled();
        if (overallStats != null) {
            overallStats.increment(OverallStatsManager.TOTAL_MOBS, 1);
        }
        if (BOSS_TYPES.contains(event.getEntityType())) {
            killerProfile.incBossesKilled();
            if (overallStats != null) {
                overallStats.increment(OverallStatsManager.TOTAL_BOSSES, 1);
            }
        }
        profiles.saveDeferred(killer.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        PlayerProfile profile = profiles.getOrCreate(player, ranks.getDefaultGroup());
        if (profile == null) return;
        profile.incBlocksBroken();
        if (overallStats != null) {
            overallStats.increment(OverallStatsManager.TOTAL_BLOCKS, 1);
        }
        if (isCrop(event.getBlock().getType())) {
            profile.incCropsBroken();
            if (overallStats != null) {
                overallStats.increment(OverallStatsManager.TOTAL_CROPS, 1);
            }
        }
        profiles.saveDeferred(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFishCaught(PlayerFishEvent event) {
        if (event.getPlayer() == null) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player player = event.getPlayer();
        PlayerProfile profile = profiles.getOrCreate(player, ranks.getDefaultGroup());
        if (profile == null) return;
        profile.incFishCaught();
        profiles.saveDeferred(player.getUniqueId());
        if (overallStats != null) {
            overallStats.increment(OverallStatsManager.TOTAL_FISH, 1);
        }
    }

    private boolean isCrop(Material material) {
        return Tag.CROPS.isTagged(material)
                || material == Material.SUGAR_CANE
                || material == Material.CACTUS
                || material == Material.PUMPKIN
                || material == Material.MELON;
    }

    // ── Friends — shared stat tracking ───────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExpChange(PlayerExpChangeEvent event) {
        if (friendManager == null || event.getAmount() <= 0) return;
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        long xp = event.getAmount();
        // Track XP together with every online friend
        Set<UUID> friends = friendManager.getFriends(uuid);
        if (friends.isEmpty()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (UUID friendUuid : friends) {
                if (Bukkit.getPlayer(friendUuid) != null) {
                    friendManager.addXpTogether(uuid, friendUuid, xp);
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFriendPvpKill(PlayerDeathEvent event) {
        if (friendManager == null) return;
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;
        if (!friendManager.isFriend(killer.getUniqueId(), victim.getUniqueId())) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                friendManager.addKillTogether(killer.getUniqueId(), victim.getUniqueId()));
    }
}