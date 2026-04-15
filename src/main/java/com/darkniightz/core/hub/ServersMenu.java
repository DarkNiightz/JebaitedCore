package com.darkniightz.core.hub;

import com.darkniightz.core.gui.BaseMenu;
import com.darkniightz.core.gui.ItemBuilder;
import com.darkniightz.core.world.SmpReturnManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public class ServersMenu extends BaseMenu {

    public ServersMenu(Plugin plugin) {
        super(plugin, titleFromConfig(plugin), 27);
    }

    private static String titleFromConfig(Plugin plugin) {
        return plugin.getConfig().getString("menus.servers.title", "§b§lServer Navigator");
    }

    @Override
    protected void populate(Player viewer) {
        Inventory inv = getInventory();
        // Read items from config; if not present, provide sensible defaults
        ConfigurationSection base = plugin.getConfig().getConfigurationSection("menus.servers.items");
        // Positions in menu
        place(inv, 11, base, "hub", Material.NETHER_STAR, "§aHub", List.of("§7You are here"));
        place(inv, 13, base, "pvp", Material.IRON_SWORD, "§cPvP", List.of("§7Coming soon"));
        place(inv, 15, base, "survival", Material.OAK_SAPLING, "§2Survival", List.of("§7Coming soon"));
        place(inv, 22, base, "creative", Material.BRICKS, "§dCreative", List.of("§7Coming soon"));
    }

    private void place(Inventory inv, int slot, ConfigurationSection base, String key, Material defMat, String defName, List<String> defLore) {
        boolean enabled = true;
        Material mat = defMat;
        String name = defName;
        List<String> lore = new ArrayList<>(defLore);
        int effectiveSlot = slot;
        if (base != null) {
            ConfigurationSection sec = base.getConfigurationSection(key);
            if (sec != null) {
                enabled = sec.getBoolean("enabled", true);
                String icon = sec.getString("icon");
                if (icon != null) {
                    try { mat = Material.valueOf(icon.toUpperCase()); } catch (IllegalArgumentException ignored) {}
                }
                name = sec.getString("name", name);
                List<String> l = sec.getStringList("lore");
                if (l != null && !l.isEmpty()) lore = l;
                // Optional slot override from config
                if (sec.isInt("slot")) {
                    int s = sec.getInt("slot", slot);
                    // clamp within inventory size
                    effectiveSlot = Math.max(0, Math.min(inv.getSize() - 1, s));
                }
            }
        }
        if (!enabled) return;
        inv.setItem(effectiveSlot, new ItemBuilder(mat).name(name).lore(lore).build());
    }

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
        if (!(plugin instanceof JebaitedCore core)) {
            return true;
        }
        switch (slot) {
            case 11 -> teleportToWorldSpawn(who, core.getWorldManager() == null ? null : org.bukkit.Bukkit.getWorld(core.getWorldManager().getHubWorldName()), "§aTeleported to Hub.");
            case 13 -> who.sendMessage(com.darkniightz.core.Messages.prefixed("§cPvP §7is coming soon."));
            case 15 -> teleportToSmp(who, core);
            case 22 -> who.sendMessage(com.darkniightz.core.Messages.prefixed("§dCreative §7is coming soon."));
            default -> { return true; }
        }
        who.closeInventory();
        return true;
    }

    private void teleportToSmp(Player who, JebaitedCore core) {
        if (core.getWorldManager() == null) {
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§cSMP world is unavailable right now."));
            return;
        }
        World smp = core.getWorldManager().ensureSmpWorldLoaded();
        if (smp == null) {
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§cSMP world is unavailable right now."));
            return;
        }

        Location target = SmpReturnManager.get(who);
        if (target != null && target.getWorld() != null && !smp.getName().equalsIgnoreCase(target.getWorld().getName())) {
            target = null;
        }
        if (target == null) {
            target = core.getSpawnManager() == null ? null : core.getSpawnManager().getSpawnForWorld(smp.getName());
        }
        if (target == null) {
            target = smp.getSpawnLocation();
        }

        scheduleWorldTeleport(core, who, target, "§2Teleported to Survival.");
    }

    private void teleportToWorldSpawn(Player who, World world, String successMessage) {
        if (world == null) {
            who.sendMessage(com.darkniightz.core.Messages.prefixed("§cThat world is unavailable right now."));
            return;
        }
        Location target = world.getSpawnLocation();
        if (plugin instanceof JebaitedCore core && core.getSpawnManager() != null) {
            Location configured = core.getSpawnManager().getSpawnForWorld(world.getName());
            if (configured != null) {
                target = configured;
            }
        }
        scheduleWorldTeleport((JebaitedCore) plugin, who, target, successMessage);
    }

    private void scheduleWorldTeleport(JebaitedCore core, Player who, Location target, String successMessage) {
        if (core == null || who == null || target == null) {
            if (who != null) who.sendMessage(com.darkniightz.core.Messages.prefixed("§cTeleport failed. Try again in a moment."));
            return;
        }
        if (isStaff(core, who)) {
            if (who.teleport(target)) {
                who.sendMessage(com.darkniightz.core.Messages.prefixed(successMessage));
            } else {
                who.sendMessage(com.darkniightz.core.Messages.prefixed("§cTeleport failed. Try again in a moment."));
            }
            return;
        }

        if (who.getWorld() != null && target.getWorld() != null && who.getWorld().equals(target.getWorld())) {
            if (who.teleport(target)) {
                who.sendMessage(com.darkniightz.core.Messages.prefixed(successMessage));
            } else {
                who.sendMessage(com.darkniightz.core.Messages.prefixed("§cTeleport failed. Try again in a moment."));
            }
            return;
        }

        Location start = who.getLocation().clone();
        Location finalTarget = target.clone();
        who.sendMessage(com.darkniightz.core.Messages.prefixed("§7Teleporting in §f3s§7. Movement cancels."));
        org.bukkit.Bukkit.getScheduler().runTaskLater(core, () -> {
            if (!who.isOnline()) return;
            if (moved(start, who.getLocation())) {
                who.sendMessage(com.darkniightz.core.Messages.prefixed("§cTeleport cancelled because you moved."));
                return;
            }
            if (who.teleport(finalTarget)) {
                who.sendMessage(com.darkniightz.core.Messages.prefixed(successMessage));
            } else {
                who.sendMessage(com.darkniightz.core.Messages.prefixed("§cTeleport failed. Try again in a moment."));
            }
        }, 60L);
    }

    private boolean isStaff(JebaitedCore core, Player player) {
        if (core.getDevModeManager() != null && core.getDevModeManager().isActive(player.getUniqueId())) {
            return true;
        }
        var profile = core.getProfileStore().getOrCreate(player, core.getRankManager().getDefaultGroup());
        return core.getRankManager().isAtLeast(profile.getPrimaryRank(), "helper");
    }

    private boolean moved(Location from, Location now) {
        if (from == null || now == null || from.getWorld() == null || now.getWorld() == null) return true;
        if (!from.getWorld().equals(now.getWorld())) return true;
        return from.getBlockX() != now.getBlockX()
                || from.getBlockY() != now.getBlockY()
                || from.getBlockZ() != now.getBlockZ();
    }
}
