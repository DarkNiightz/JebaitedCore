package com.darkniightz.core.eventmode.handler;

import com.darkniightz.core.eventmode.ArenaConfig;
import com.darkniightz.core.eventmode.CtfKitUtil;
import com.darkniightz.core.eventmode.EventSession;
import com.darkniightz.core.eventmode.team.Team;
import com.darkniightz.core.eventmode.team.TeamEngine;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Capture the Flag — wool flags at configured blocks, interact pickup, score on delivery.
 */
public final class CtfHandler implements EventHandler {

    private static final double CAPTURE_RADIUS = 5.0;

    private final Plugin plugin;
    private final NamespacedKey groundFlagKey;

    public CtfHandler(Plugin plugin) {
        this.plugin = plugin;
        this.groundFlagKey = new NamespacedKey(plugin, "ctf_ground_flag");
    }

    /** @return true if the pickup event should be cancelled (our ground-flag item). */
    public boolean handleGroundFlagPickup(Player player, Item item, EventSession session) {
        if (session == null || item == null) return false;
        if (!item.getPersistentDataContainer().has(groundFlagKey, PersistentDataType.STRING)) {
            return false;
        }
        String tag = item.getPersistentDataContainer().get(groundFlagKey, PersistentDataType.STRING);
        Team team = TeamEngine.teamOf(session, player.getUniqueId());
        if (team == null) {
            return true;
        }
        if ("red".equals(tag)) {
            if (team != Team.BLUE) return true;
            if (session.ctfRedFlagCarrier != null || session.ctfRedFlagAtBase) {
                return true;
            }
            removeGroundFlagItem(session, true);
            session.ctfRedFlagCarrier = player.getUniqueId();
            session.ctfRedFlagDropLocation = null;
            session.ctfRedFlagReturnAtMs = 0L;
            return true;
        }
        if ("blue".equals(tag)) {
            if (team != Team.RED) return true;
            if (session.ctfBlueFlagCarrier != null || session.ctfBlueFlagAtBase) {
                return true;
            }
            removeGroundFlagItem(session, false);
            session.ctfBlueFlagCarrier = player.getUniqueId();
            session.ctfBlueFlagDropLocation = null;
            session.ctfBlueFlagReturnAtMs = 0L;
            return true;
        }
        return true;
    }

    private void removeGroundFlagItem(EventSession session, boolean redFlag) {
        UUID id = redFlag ? session.ctfRedGroundItemEntity : session.ctfBlueGroundItemEntity;
        if (id != null) {
            org.bukkit.entity.Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
            if (redFlag) session.ctfRedGroundItemEntity = null;
            else session.ctfBlueGroundItemEntity = null;
        }
    }

    private void spawnGroundFlagItem(EventSession session, boolean redFlag, Location drop) {
        removeGroundFlagItem(session, redFlag);
        if (drop == null || drop.getWorld() == null) return;
        World w = drop.getWorld();
        Location at = drop.clone();
        at.setX(at.getBlockX() + 0.5);
        at.setZ(at.getBlockZ() + 0.5);
        at.setY(at.getY() + 0.2);
        Material mat = redFlag ? Material.RED_WOOL : Material.BLUE_WOOL;
        String tag = redFlag ? "red" : "blue";
        Item ent = w.spawn(at, Item.class);
        ent.setItemStack(new ItemStack(mat, 1));
        ent.setVelocity(new Vector(0, 0.08, 0));
        ent.getPersistentDataContainer().set(groundFlagKey, PersistentDataType.STRING, tag);
        ent.setPickupDelay(0);
        ent.setPersistent(true);
        if (redFlag) session.ctfRedGroundItemEntity = ent.getUniqueId();
        else session.ctfBlueGroundItemEntity = ent.getUniqueId();
    }

    @Override
    public void onStart(EventSession session) {
        ArenaConfig.CtfLayout layout = layout(session);
        if (!layout.isComplete()) {
            return;
        }
        session.ctfRedScore.set(0);
        session.ctfBlueScore.set(0);
        session.ctfRedFlagCarrier = null;
        session.ctfBlueFlagCarrier = null;
        session.ctfRedFlagAtBase = true;
        session.ctfBlueFlagAtBase = true;
        session.ctfRedFlagDropLocation = null;
        session.ctfBlueFlagDropLocation = null;
        session.ctfRedFlagReturnAtMs = 0L;
        session.ctfBlueFlagReturnAtMs = 0L;
        session.ctfRedGroundItemEntity = null;
        session.ctfBlueGroundItemEntity = null;
        placeWool(layout.redFlagBlock(), Material.RED_WOOL);
        placeWool(layout.blueFlagBlock(), Material.BLUE_WOOL);
    }

    private static void placeWool(Location loc, Material wool) {
        if (loc == null || loc.getWorld() == null) return;
        loc.getBlock().setType(wool, false);
    }

    private static UUID firstActive(Set<UUID> team, EventSession session) {
        for (UUID id : team) {
            if (session.active.contains(id)) return id;
        }
        return null;
    }

    private ArenaConfig.CtfLayout layout(EventSession session) {
        if (session.resolvedArenaConfig == null) return ArenaConfig.CtfLayout.empty();
        ArenaConfig.CtfLayout l = session.resolvedArenaConfig.ctf();
        return l != null ? l : ArenaConfig.CtfLayout.empty();
    }

    /** Called from {@code EventEngine} instead of elimination when a CTF participant would die. */
    public void onParticipantDowned(EventSession session, Player player) {
        UUID id = player.getUniqueId();
        ArenaConfig.CtfLayout lay = layout(session);
        int retSec = lay.flagReturnSeconds();

        if (id.equals(session.ctfRedFlagCarrier)) {
            session.ctfRedFlagCarrier = null;
            session.ctfRedFlagAtBase = false;
            session.ctfRedFlagDropLocation = player.getLocation().clone();
            session.ctfRedFlagReturnAtMs = System.currentTimeMillis() + retSec * 1000L;
            spawnGroundFlagItem(session, true, session.ctfRedFlagDropLocation);
        }
        if (id.equals(session.ctfBlueFlagCarrier)) {
            session.ctfBlueFlagCarrier = null;
            session.ctfBlueFlagAtBase = false;
            session.ctfBlueFlagDropLocation = player.getLocation().clone();
            session.ctfBlueFlagReturnAtMs = System.currentTimeMillis() + retSec * 1000L;
            spawnGroundFlagItem(session, false, session.ctfBlueFlagDropLocation);
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            Location spawn = teamSpawn(session, TeamEngine.teamOf(session, id));
            if (spawn != null) player.teleport(spawn);
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.getInventory().setItemInOffHand(null);
            boolean red = session.ctfTeamRed.contains(id);
            CtfKitUtil.apply(player, red ? lay.redKit() : lay.blueKit());
        });
    }

    private Location teamSpawn(EventSession session, Team team) {
        ArenaConfig.CtfLayout lay = layout(session);
        if (team == Team.RED) return lay.redSpawn() != null ? lay.redSpawn().clone() : null;
        if (team == Team.BLUE) return lay.blueSpawn() != null ? lay.blueSpawn().clone() : null;
        return null;
    }

    @Override
    public void onDeath(EventSession session, Player player) {
        // CTF uses onParticipantDowned from EventEngine — no-op here.
    }

    @Override
    public void onRespawn(EventSession session, Player player) {
    }

    @Override
    public void onTick(EventSession session) {
        ArenaConfig.CtfLayout lay = layout(session);
        long now = System.currentTimeMillis();
        if (session.endsAtMs > 0 && now >= session.endsAtMs && session.ctfPendingWinnerUuid == null) {
            int r = session.ctfRedScore.get();
            int b = session.ctfBlueScore.get();
            UUID w = null;
            if (r > b) w = firstActive(session.ctfTeamRed, session);
            else if (b > r) w = firstActive(session.ctfTeamBlue, session);
            else if (!session.active.isEmpty()) w = session.active.iterator().next();
            if (w != null) {
                session.ctfPendingWinnerUuid = w;
            }
            session.endsAtMs = 0L;
        }
        if (!lay.isComplete()) return;

        if (!session.ctfRedFlagAtBase && session.ctfRedFlagCarrier == null
                && session.ctfRedFlagReturnAtMs > 0 && now >= session.ctfRedFlagReturnAtMs) {
            returnFlag(session, true, lay);
        }
        if (!session.ctfBlueFlagAtBase && session.ctfBlueFlagCarrier == null
                && session.ctfBlueFlagReturnAtMs > 0 && now >= session.ctfBlueFlagReturnAtMs) {
            returnFlag(session, false, lay);
        }

        for (UUID id : session.active) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            tryScore(session, p, lay);
        }
    }

    private void returnFlag(EventSession session, boolean redFlag, ArenaConfig.CtfLayout lay) {
        if (redFlag) {
            removeGroundFlagItem(session, true);
            session.ctfRedFlagReturnAtMs = 0L;
            session.ctfRedFlagAtBase = true;
            session.ctfRedFlagDropLocation = null;
            placeWool(lay.redFlagBlock(), Material.RED_WOOL);
        } else {
            removeGroundFlagItem(session, false);
            session.ctfBlueFlagReturnAtMs = 0L;
            session.ctfBlueFlagAtBase = true;
            session.ctfBlueFlagDropLocation = null;
            placeWool(lay.blueFlagBlock(), Material.BLUE_WOOL);
        }
    }

    private void tryScore(EventSession session, Player carrier, ArenaConfig.CtfLayout lay) {
        Team team = TeamEngine.teamOf(session, carrier.getUniqueId());
        if (team == null) return;

        // Red player delivers enemy (blue) flag to red base
        if (team == Team.RED && carrier.getUniqueId().equals(session.ctfBlueFlagCarrier)) {
            if (lay.redSpawn() != null && carrier.getLocation().distance(lay.redSpawn()) <= CAPTURE_RADIUS) {
                session.ctfRedScore.incrementAndGet();
                session.ctfBlueFlagCarrier = null;
                session.ctfBlueFlagAtBase = true;
                placeWool(lay.blueFlagBlock(), Material.BLUE_WOOL);
                maybeWin(session, Team.RED, lay.winScore());
            }
        }
        // Blue player delivers enemy (red) flag to blue base
        if (team == Team.BLUE && carrier.getUniqueId().equals(session.ctfRedFlagCarrier)) {
            if (lay.blueSpawn() != null && carrier.getLocation().distance(lay.blueSpawn()) <= CAPTURE_RADIUS) {
                session.ctfBlueScore.incrementAndGet();
                session.ctfRedFlagCarrier = null;
                session.ctfRedFlagAtBase = true;
                placeWool(lay.redFlagBlock(), Material.RED_WOOL);
                maybeWin(session, Team.BLUE, lay.winScore());
            }
        }
    }

    private void maybeWin(EventSession session, Team side, int winScore) {
        int score = side == Team.RED ? session.ctfRedScore.get() : session.ctfBlueScore.get();
        if (score < winScore) return;
        java.util.Set<UUID> roster = side == Team.RED ? session.ctfTeamRed : session.ctfTeamBlue;
        UUID winner = roster.stream().filter(session.active::contains).findFirst().orElse(null);
        if (winner != null) {
            session.ctfPendingWinnerUuid = winner;
        }
    }

    @Override
    public void onEnd(EventSession session) {
        removeGroundFlagItem(session, true);
        removeGroundFlagItem(session, false);
        ArenaConfig.CtfLayout lay = layout(session);
        if (lay.redFlagBlock() != null && lay.redFlagBlock().getWorld() != null) {
            lay.redFlagBlock().getBlock().setType(Material.AIR, false);
        }
        if (lay.blueFlagBlock() != null && lay.blueFlagBlock().getWorld() != null) {
            lay.blueFlagBlock().getBlock().setType(Material.AIR, false);
        }
    }

    @Override
    public List<String> getScoreboardLines(EventSession session) {
        List<String> lines = new ArrayList<>();
        lines.add("§cRed §7" + session.ctfRedScore.get() + "  §8|  §9Blue §7" + session.ctfBlueScore.get());
        long remain = session.endsAtMs > 0
                ? Math.max(0, (session.endsAtMs - System.currentTimeMillis()) / 1000L) : 0L;
        lines.add("§7Time: §f" + remain + "s");
        String r = session.ctfRedFlagCarrier != null ? "§cCarried" : (session.ctfRedFlagAtBase ? "§aBase" : "§eGround");
        String b = session.ctfBlueFlagCarrier != null ? "§9Carried" : (session.ctfBlueFlagAtBase ? "§aBase" : "§eGround");
        lines.add("§7Red flag: " + r + "  §8|  §7Blue: " + b);
        return lines;
    }

    /** Right-click wool at pole or dropped carrier pickup. */
    public void handleInteract(Player player, org.bukkit.block.Block block, EventSession session) {
        if (session == null || block == null) return;
        ArenaConfig.CtfLayout lay = layout(session);
        if (!lay.isComplete()) return;
        Team team = TeamEngine.teamOf(session, player.getUniqueId());
        if (team == null) return;

        Material type = block.getType();
        Location bl = block.getLocation();

        // Pick up enemy flag from base (wool block)
        if (type == Material.RED_WOOL && bl.distanceSquared(lay.redFlagBlock()) < 1.5 && session.ctfRedFlagAtBase) {
            if (team == Team.BLUE) {
                session.ctfRedFlagAtBase = false;
                session.ctfRedFlagCarrier = player.getUniqueId();
                block.setType(Material.AIR, false);
            }
            return;
        }
        if (type == Material.BLUE_WOOL && bl.distanceSquared(lay.blueFlagBlock()) < 1.5 && session.ctfBlueFlagAtBase) {
            if (team == Team.RED) {
                session.ctfBlueFlagAtBase = false;
                session.ctfBlueFlagCarrier = player.getUniqueId();
                block.setType(Material.AIR, false);
            }
        }
    }
}
