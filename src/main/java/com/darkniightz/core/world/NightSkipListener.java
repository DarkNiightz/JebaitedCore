package com.darkniightz.core.world;

import com.darkniightz.main.JebaitedCore;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Smart night skip system for SMP worlds only.
 * Respects sleep_percentage, excludes AFK players, hub, and events world.
 */
public class NightSkipListener implements Listener {

    private final JebaitedCore plugin;
    private final Map<UUID, Long> lastMovement = new HashMap<>();
    private BukkitTask nightCheckTask;

    public NightSkipListener(JebaitedCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (nightCheckTask != null) nightCheckTask.cancel();
        nightCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, this::attemptNightSkip, 20L, 20L);
    }

    @EventHandler
    public void onBedEnter(PlayerBedEnterEvent e) {
        if (e.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;
        if (!isEligibleWorld(e.getPlayer().getWorld())) return;

        lastMovement.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
        broadcastSleeping(e.getPlayer());
    }

    @EventHandler
    public void onBedLeave(PlayerBedLeaveEvent e) {
        if (isEligibleWorld(e.getPlayer().getWorld())) {
            attemptNightSkip();
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.hasChangedBlock()) {
            lastMovement.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastMovement.remove(e.getPlayer().getUniqueId());
        if (isEligibleWorld(e.getPlayer().getWorld())) {
            attemptNightSkip();
        }
    }

    private void broadcastSleeping(Player player) {
        if (!plugin.getConfig().getBoolean("sleep.announce_sleep", true)) return;

        World world = player.getWorld();
        int sleeping = countSleeping(world);
        int needed = getNeededPlayers(world);

        String msg = plugin.getConfig().getString("sleep.sleeping_message",
                "&7[&bZZz&7] &f{player} &7is sleeping &8(&f{sleeping}&8/&f{needed} needed&8)")
                .replace("{player}", player.getName())
                .replace("{sleeping}", String.valueOf(sleeping))
                .replace("{needed}", String.valueOf(needed));

        world.getPlayers().forEach(p -> p.sendMessage(msg.replace('&', '§')));
    }

    private void attemptNightSkip() {
        for (World world : Bukkit.getWorlds()) {
            if (!isEligibleWorld(world)) continue;

            int sleeping = countSleeping(world);
            int needed = getNeededPlayers(world);

            if (needed > 0 && sleeping >= needed) {
                skipNight(world);
            }
        }
    }

    private void skipNight(World world) {
        world.setTime(0);
        world.setStorm(false);
        world.setThundering(false);

        world.getPlayers().forEach(p -> p.setStatistic(org.bukkit.Statistic.TIME_SINCE_REST, 0));

        String msg = plugin.getConfig().getString("sleep.skip_message",
                "&7[&bZZz&7] &fNight skipped — sweet dreams.")
                .replace('&', '§');

        world.getPlayers().forEach(p -> p.sendMessage(msg));
    }

    private int countSleeping(World world) {
        return (int) world.getPlayers().stream()
                .filter(Player::isSleeping)
                .filter(p -> !isAfk(p))
                .count();
    }

    private int getNeededPlayers(World world) {
        double percent = plugin.getConfig().getDouble("sleep.sleep_percentage", 0.30);
        long eligible = world.getPlayers().stream().filter(p -> !isAfk(p)).count();
        return (int) Math.ceil(eligible * percent);
    }

    private boolean isAfk(Player player) {
        long last = lastMovement.getOrDefault(player.getUniqueId(), 0L);
        long timeoutMs = plugin.getConfig().getLong("sleep.afk_timeout_seconds", 300) * 1000L;
        return System.currentTimeMillis() - last > timeoutMs;
    }

    private boolean isEligibleWorld(World world) {
        if (world.getEnvironment() != World.Environment.NORMAL) return false;

        String name = world.getName().toLowerCase();
        String hub = plugin.getConfig().getString("worlds.hub", "world").toLowerCase();
        String events = plugin.getConfig().getString("event_mode.world", "events").toLowerCase();

        return !name.equals(hub) && !name.equals(events);
    }

    public void shutdown() {
        if (nightCheckTask != null) {
            nightCheckTask.cancel();
        }
        lastMovement.clear();
    }
}
