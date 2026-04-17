package com.darkniightz.core.system;

import com.darkniightz.main.JebaitedCore;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerLoadEvent;

import java.util.Locale;

/**
 * mcMMO may register overlapping commands after JebaitedCore enables, overwriting our
 * {@link org.bukkit.command.PluginCommand} entries. Re-evict and re-register via
 * {@link JebaitedCore#reassertMcMMOCommandOwnership()} when mcMMO loads, after full server load
 * ({@link ServerLoadEvent}), and on delayed ticks so we run after late command registration.
 */
public final class PartyCommandOwnershipListener implements Listener {

    private final JebaitedCore plugin;

    public PartyCommandOwnershipListener(JebaitedCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPluginEnable(PluginEnableEvent event) {
        String n = event.getPlugin().getName().toLowerCase(Locale.ROOT);
        if (n.contains("mcmmo")) {
            Bukkit.getScheduler().runTask(plugin, plugin::reassertMcMMOCommandOwnership);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerLoad(ServerLoadEvent event) {
        Bukkit.getScheduler().runTask(plugin, plugin::reassertMcMMOCommandOwnership);
    }
}
