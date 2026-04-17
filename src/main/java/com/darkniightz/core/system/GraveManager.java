package com.darkniightz.core.system;

import com.darkniightz.main.JebaitedCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GraveManager {
    /** Sentinel expiresAt value meaning this grave never expires (Legend/GM insurance). */
    public static final long TTL_INSURED = Long.MAX_VALUE;

    private final JebaitedCore plugin;
    private final Map<UUID, Grave> gravesById = new ConcurrentHashMap<>();
    private final Map<String, UUID> graveIdByBlock = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> trackerTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> trackerBars = new ConcurrentHashMap<>();

    private File dataFile;
    private YamlConfiguration data;
    private BukkitTask expiryTask;

    public GraveManager(JebaitedCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        loadData();
        restoreGraves();
        if (expiryTask != null) expiryTask.cancel();
        expiryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickExpiry, 20L, 20L * 15L);
    }

    public void stop() {
        if (expiryTask != null) {
            expiryTask.cancel();
            expiryTask = null;
        }
        for (BukkitTask task : trackerTasks.values()) {
            task.cancel();
        }
        for (BossBar bar : trackerBars.values()) {
            bar.removeAll();
            bar.setVisible(false);
        }
        trackerTasks.clear();
        trackerBars.clear();
        saveAll();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("graves.enabled", true);
    }

    public boolean shouldCreateGraveForPlayerDeath(Player player, org.bukkit.event.entity.EntityDamageEvent.DamageCause cause) {
        if (!isEnabled() || player == null) return false;
        if (!isSmp(player.getWorld())) return false;
        if (isEventWorld(player.getWorld())) return false;
        if (cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.VOID) return false;
        return true;
    }

    public Grave createNormalGrave(Player owner, Location deathLoc, List<ItemStack> contents) {
        if (owner == null || deathLoc == null || contents == null || contents.isEmpty()) return null;
        // Never create graves for event participants — their inventory is managed by EventModeManager.
        if (plugin.getEventModeManager() != null && plugin.getEventModeManager().isParticipant(owner)) return null;
        // Belt-and-suspenders: never create graves when death occurs in the event world.
        if (isEventWorld(deathLoc.getWorld())) return null;
        Location placed = findPlacement(deathLoc);
        if (placed == null || placed.getWorld() == null) return null;

        long now = System.currentTimeMillis();

        // Determine TTL — Legend/GM donors get insured graves that never expire.
        boolean insured = isInsuredRank(owner);
        long expiresAt = insured
                ? TTL_INSURED
                : now + Math.max(60_000L, plugin.getConfig().getLong("graves.ttl_seconds", 600L) * 1000L);

        List<ItemStack> sanitized = sanitize(contents);
        Grave grave = new Grave(
                UUID.randomUUID(),
                owner.getUniqueId(),
                null,
                placed,
                now,
                expiresAt,
                false,
                false,
                sanitized
        );
        placeMarker(grave.location());
        register(grave);
        if (plugin.getOverallStatsManager() != null) {
            plugin.getOverallStatsManager().increment(OverallStatsManager.TOTAL_GRAVES, 1);
        }

        sendOwnerInfo(owner, grave, true);
        saveAll();

        if (insured) {
            triggerAutoLoot(owner, grave, sanitized);
        }
        return grave;
    }

    /** Returns true if the player has Legend or higher as primary or donor rank. */
    public boolean isInsuredRank(Player player) {
        if (player == null) return false;
        com.darkniightz.core.players.PlayerProfile profile = plugin.getProfileStore().get(player.getUniqueId());
        if (profile == null) return false;
        com.darkniightz.core.ranks.RankManager ranks = plugin.getRankManager();
        String primary = profile.getPrimaryRank();
        String donor = profile.getDonorRank();
        return (primary != null && ranks.isAtLeast(primary, "legend"))
                || (donor != null && ranks.isAtLeast(donor, "legend"));
    }

    /** Returns the donor/effective rank string used for vault page limits. */
    private String effectiveVaultRank(Player player) {
        com.darkniightz.core.players.PlayerProfile profile = plugin.getProfileStore().get(player.getUniqueId());
        if (profile == null) return null;
        String donor = profile.getDonorRank();
        if (donor != null) return donor;
        // Fall back to primary rank if it is a donor-tier rank
        String primary = profile.getPrimaryRank();
        if (primary != null && (primary.equalsIgnoreCase("legend") || primary.equalsIgnoreCase("grandmaster"))) {
            return primary;
        }
        return null;
    }

    /**
     * Asynchronously moves the items from an insured grave into the owner's vault.
     * Overflow items remain in the grave (still TTL_INSURED). Notifies the owner on completion.
     */
    private void triggerAutoLoot(Player owner, Grave grave, List<ItemStack> items) {
        com.darkniightz.core.system.PrivateVaultManager pvManager = plugin.getPrivateVaultManager();
        if (pvManager == null) return;

        String vaultRank = effectiveVaultRank(owner);
        if (vaultRank == null) return; // No vault access — leave items in grave

        // Warn if vault fill is already known and ≥ 90%
        int fillPercent = pvManager.getVaultFillPercent(owner.getUniqueId(), vaultRank);
        if (fillPercent >= 90) {
            owner.sendMessage(com.darkniightz.core.Messages.prefixed(
                    "§6⚠ §eYour vault is §c" + fillPercent + "% full §e— §fitems will stay in your insured grave if it fills up."));
        }

        pvManager.autoLootToVault(owner.getUniqueId(), vaultRank, items, overflow -> {
            // Called back on the main thread
            if (overflow == null || overflow.isEmpty()) {
                // All items safely in vault — remove grave
                saveInventory(grave.id(), java.util.List.of());
                if (owner.isOnline()) {
                    owner.sendMessage(com.darkniightz.core.Messages.prefixed(
                            "§d✦ Grave Insurance §8— §aAll items moved to your vault."));
                }
            } else {
                // Partial overflow — update grave to overflow contents only
                saveInventory(grave.id(), overflow);
                if (owner.isOnline()) {
                    Location l = grave.location();
                    owner.sendMessage(com.darkniightz.core.Messages.prefixed(
                            "§d✦ Grave Insurance §8— §e" + overflow.size() + " item stack(s) couldn't fit in your vault."));
                    owner.sendMessage(com.darkniightz.core.Messages.prefixed(
                            "§7They remain in your insured grave at §f"
                            + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()
                            + " §7(never expires)."));
                }
            }
        });
    }

    public Grave createCombatLogGrave(Player quitter, UUID killer) {
        if (quitter == null || quitter.getWorld() == null) return null;
        if (!isEnabled() || !isSmp(quitter.getWorld()) || isEventWorld(quitter.getWorld())) return null;

        List<ItemStack> contents = capturePlayerItems(quitter);
        if (contents.isEmpty()) return null;

        Location placed = findPlacement(quitter.getLocation());
        if (placed == null || placed.getWorld() == null) return null;

        long now = System.currentTimeMillis();
        long ttlMs = Math.max(60_000L, plugin.getConfig().getLong("graves.ttl_seconds", 600L) * 1000L);
        Grave grave = new Grave(
                UUID.randomUUID(),
                quitter.getUniqueId(),
                killer,
                placed,
                now,
                now + ttlMs,
            false,
                true,
                sanitize(contents)
        );

        wipeInventory(quitter);
        placeMarker(grave.location());
        register(grave);
        if (plugin.getOverallStatsManager() != null) {
            plugin.getOverallStatsManager().increment(OverallStatsManager.TOTAL_GRAVES, 1);
            plugin.getOverallStatsManager().increment(OverallStatsManager.TOTAL_COMBAT_LOG_GRAVES, 1);
        }
        saveAll();

        Player hunter = killer == null ? null : Bukkit.getPlayer(killer);
        if (hunter != null && hunter.isOnline()) {
            Location l = grave.location();
            hunter.sendMessage(com.darkniightz.core.Messages.prefixed("§cCombat-log grave spawned at §f" + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + "§c."));
        }
        return grave;
    }

    public Grave getByBlock(Location location) {
        if (location == null || location.getWorld() == null) return null;
        UUID id = graveIdByBlock.get(key(location));
        if (id == null) return null;
        return gravesById.get(id);
    }

    public Grave getById(UUID graveId) {
        if (graveId == null) return null;
        return gravesById.get(graveId);
    }

    public Optional<Grave> getLatestForOwner(UUID owner) {
        if (owner == null) return Optional.empty();
        return gravesById.values().stream()
                .filter(g -> owner.equals(g.owner()))
                .max(Comparator.comparingLong(Grave::createdAt));
    }

    public List<Grave> getAllForOwner(UUID owner) {
        if (owner == null) return List.of();
        return gravesById.values().stream()
                .filter(g -> owner.equals(g.owner()))
                .sorted(Comparator.comparingLong(Grave::createdAt).reversed())
                .toList();
    }

    public boolean isGraveBlock(Block block) {
        return block != null && block.getWorld() != null && graveIdByBlock.containsKey(key(block.getLocation()));
    }

    public void saveInventory(UUID graveId, List<ItemStack> newContents) {
        Grave grave = gravesById.get(graveId);
        if (grave == null) return;
        List<ItemStack> clean = sanitize(newContents);
        Grave updated = grave.withContents(clean);
        gravesById.put(graveId, updated);
        if (clean.isEmpty()) {
            removeGrave(graveId, false, false);
        } else {
            saveAll();
        }
    }

    public void trackOwnerToLatestGrave(Player player) {
        if (player == null) return;
        stopTracker(player.getUniqueId());
        Optional<Grave> latest = getLatestForOwner(player.getUniqueId());
        // Don't track combat log graves — the owner cannot retrieve them, so a bossbar
        // pointing at a locked chest would only confuse/spam.
        if (latest.isEmpty() || latest.get().combatLog()) return;
        startTracker(player, latest.get());
    }

    public void stopTracking(UUID playerId) {
        stopTracker(playerId);
    }

    public String debugSummary() {
        return "graves=" + gravesById.size();
    }

    public void dropAndExpire(UUID graveId) {
        removeGrave(graveId, true, false);
    }

    private void restoreGraves() {
        if (data == null) return;
        ConfigurationSection root = data.getConfigurationSection("graves");
        if (root == null) return;

        long now = System.currentTimeMillis();
        for (String idRaw : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(idRaw);
                ConfigurationSection sec = root.getConfigurationSection(idRaw);
                if (sec == null) continue;
                World world = Bukkit.getWorld(sec.getString("world", ""));
                if (world == null) continue;
                Location loc = new Location(world, sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"));
                long createdAt = sec.getLong("createdAt");
                long expiresAt = sec.getLong("expiresAt");
                if (expiresAt <= now) {
                    continue;
                }

                UUID owner = UUID.fromString(sec.getString("owner"));
                UUID killer = null;
                String killerRaw = sec.getString("killer");
                if (killerRaw != null && !killerRaw.isBlank()) {
                    try { killer = UUID.fromString(killerRaw); } catch (IllegalArgumentException ignored) {}
                }
                boolean locked = sec.getBoolean("locked", false);
                boolean combatLog = sec.getBoolean("combatLog", false);
                List<ItemStack> contents = new ArrayList<>();
                for (Object o : sec.getList("items", List.of())) {
                    if (o instanceof ItemStack item && item.getType() != Material.AIR && item.getAmount() > 0) {
                        contents.add(item);
                    }
                }

                Grave grave = new Grave(id, owner, killer, loc, createdAt, expiresAt, locked, combatLog, contents);
                placeMarker(grave.location());
                register(grave);
            } catch (Exception ignored) {
            }
        }
        saveAll();
    }

    private void tickExpiry() {
        long now = System.currentTimeMillis();
        List<UUID> expired = gravesById.values().stream()
                .filter(g -> g.expiresAt() <= now)
                .map(Grave::id)
                .toList();
        for (UUID id : expired) {
            Grave g = gravesById.get(id);
            if (g == null) continue;
            removeGrave(id, true, true);
        }
    }

    private void removeGrave(UUID graveId, boolean dropItems, boolean expired) {
        Grave grave = gravesById.remove(graveId);
        if (grave == null) return;
        graveIdByBlock.remove(key(grave.location()));

        Block block = grave.location().getBlock();
        if (block.getType() == Material.CHEST) {
            block.setType(Material.AIR, false);
        }

        if (dropItems && grave.location().getWorld() != null) {
            for (ItemStack item : grave.contents()) {
                if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) continue;
                Item dropped = grave.location().getWorld().dropItemNaturally(grave.location().clone().add(0.5, 0.5, 0.5), item.clone());
                dropped.setUnlimitedLifetime(false);
            }
        }

        if (expired) {
            Player owner = Bukkit.getPlayer(grave.owner());
            if (owner != null && owner.isOnline()) {
                owner.sendMessage(com.darkniightz.core.Messages.prefixed("§7Your grave at §f" + grave.location().getBlockX() + " " + grave.location().getBlockY() + " " + grave.location().getBlockZ() + " §7expired."));
            }
        }

        stopTracker(grave.owner());
        Player owner = Bukkit.getPlayer(grave.owner());
        if (owner != null && owner.isOnline()) {
            trackOwnerToLatestGrave(owner);
        }
        saveAll();
    }

    private void sendOwnerInfo(Player owner, Grave grave, boolean giveCompass) {
        Location l = grave.location();
        owner.sendMessage(com.darkniightz.core.Messages.prefixed("§aGrave created at §f" + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + "§a."));
        if (grave.expiresAt() == TTL_INSURED) {
            owner.sendMessage(com.darkniightz.core.Messages.prefixed("§d✦ Grave Insurance §8— §7moving items to your vault now..."));
        } else {
            long mins = Math.max(1L, (grave.expiresAt() - System.currentTimeMillis()) / 60_000L);
            owner.sendMessage(com.darkniightz.core.Messages.prefixed("§7Expires in §f" + mins + "m§7. Anyone can loot this grave."));
        }
        if (giveCompass) {
            giveCompass(owner, grave);
        }
        trackOwnerToLatestGrave(owner);
    }

    private void startTracker(Player owner, Grave grave) {
        stopTracker(owner.getUniqueId());

        BossBar bar = Bukkit.createBossBar("§dGrave Tracker", BarColor.PURPLE, BarStyle.SOLID);
        bar.addPlayer(owner);
        bar.setVisible(true);
        trackerBars.put(owner.getUniqueId(), bar);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!owner.isOnline()) {
                stopTracker(owner.getUniqueId());
                return;
            }
            Grave current = gravesById.get(grave.id());
            if (current == null) {
                stopTracker(owner.getUniqueId());
                return;
            }

            BossBar activeBar = trackerBars.get(owner.getUniqueId());
            String coords = current.location().getBlockX() + " " + current.location().getBlockY() + " " + current.location().getBlockZ();
            if (current.expiresAt() == TTL_INSURED) {
                if (activeBar != null) {
                    activeBar.setProgress(1.0);
                    activeBar.setTitle("§d✦ Insured Grave §8— §f" + coords);
                }
                owner.setCompassTarget(current.location());
                owner.sendActionBar("§d✦ §7Insured grave: §f" + coords);
            } else {
                long remainMs = Math.max(0L, current.expiresAt() - System.currentTimeMillis());
                long totalMs = Math.max(1L, current.expiresAt() - current.createdAt());
                double progress = Math.max(0d, Math.min(1d, (double) remainMs / (double) totalMs));
                int seconds = (int) Math.ceil(remainMs / 1000.0);
                if (activeBar != null) {
                    activeBar.setProgress(progress);
                    activeBar.setTitle("§dGrave: §f" + coords + " §8(" + seconds + "s)");
                }
                owner.setCompassTarget(current.location());
                owner.sendActionBar("§7Grave tracker: §f" + coords + " §8(" + seconds + "s)");
            }
        }, 10L, 20L);
        trackerTasks.put(owner.getUniqueId(), task);
    }

    private void stopTracker(UUID ownerId) {
        BukkitTask old = trackerTasks.remove(ownerId);
        if (old != null) old.cancel();
        BossBar bar = trackerBars.remove(ownerId);
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
        }
    }

    private void register(Grave grave) {
        gravesById.put(grave.id(), grave);
        graveIdByBlock.put(key(grave.location()), grave.id());
    }

    private List<ItemStack> capturePlayerItems(Player player) {
        List<ItemStack> out = new ArrayList<>();
        if (player == null) return out;
        addAll(out, player.getInventory().getContents());
        return out;
    }

    private void wipeInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.updateInventory();
    }

    private List<ItemStack> sanitize(List<ItemStack> raw) {
        List<ItemStack> out = new ArrayList<>();
        if (raw == null) return out;
        for (ItemStack item : raw) {
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) continue;
            out.add(item.clone());
        }
        return out;
    }

    private void addAll(List<ItemStack> target, ItemStack[] source) {
        if (source == null) return;
        for (ItemStack item : source) {
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) continue;
            target.add(item.clone());
        }
    }

    private Location findPlacement(Location base) {
        if (base == null || base.getWorld() == null) return null;
        World world = base.getWorld();
        int bx = base.getBlockX();
        int by = Math.max(world.getMinHeight() + 1, base.getBlockY());
        int bz = base.getBlockZ();

        for (int y = by; y <= by + 3; y++) {
            Location loc = new Location(world, bx, y, bz);
            Block block = loc.getBlock();
            Block below = world.getBlockAt(bx, y - 1, bz);
            if (block.getType().isAir() && below.getType().isSolid()) {
                return block.getLocation();
            }
        }

        return new Location(world, bx, by, bz);
    }

    private void placeMarker(Location location) {
        Block block = location.getBlock();
        block.setType(Material.CHEST, false);
    }

    private boolean isSmp(World world) {
        if (world == null || plugin.getWorldManager() == null) return false;
        return plugin.getWorldManager().isSmp(world);
    }

    private boolean isEventWorld(World world) {
        if (world == null) return false;
        String eventWorld = plugin.getConfig().getString("event_mode.world", "events");
        return eventWorld != null && eventWorld.equalsIgnoreCase(world.getName());
    }

    private String key(Location location) {
        return location.getWorld().getName().toLowerCase(Locale.ROOT) + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private void giveCompass(Player player, Grave grave) {
        ItemStack compass = new ItemStack(Material.COMPASS, 1);
        CompassMeta meta = (CompassMeta) compass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§dGrave Compass");
            meta.setLodestone(grave.location());
            meta.setLodestoneTracked(false);
            meta.setLore(List.of("§7Tracks your grave location", "§f" + grave.location().getBlockX() + " " + grave.location().getBlockY() + " " + grave.location().getBlockZ()));
            compass.setItemMeta(meta);
        }
        Map<Integer, ItemStack> left = player.getInventory().addItem(compass);
        if (!left.isEmpty()) {
            for (ItemStack leftover : left.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        player.updateInventory();
    }

    private void loadData() {
        dataFile = new File(plugin.getDataFolder(), "graves.yml");
        if (!dataFile.exists()) {
            try {
                if (dataFile.getParentFile() != null) {
                    dataFile.getParentFile().mkdirs();
                }
                dataFile.createNewFile();
            } catch (IOException ignored) {
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveAll() {
        if (data == null) return;
        data.set("graves", null);
        for (Grave grave : gravesById.values()) {
            String path = "graves." + grave.id();
            data.set(path + ".owner", grave.owner().toString());
            data.set(path + ".killer", grave.killer() == null ? null : grave.killer().toString());
            data.set(path + ".world", grave.location().getWorld() == null ? null : grave.location().getWorld().getName());
            data.set(path + ".x", grave.location().getX());
            data.set(path + ".y", grave.location().getY());
            data.set(path + ".z", grave.location().getZ());
            data.set(path + ".createdAt", grave.createdAt());
            data.set(path + ".expiresAt", grave.expiresAt());
            data.set(path + ".locked", grave.locked());
            data.set(path + ".combatLog", grave.combatLog());
            data.set(path + ".items", grave.contents());
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save graves.yml: " + e.getMessage());
        }
    }

    public record Grave(
            UUID id,
            UUID owner,
            UUID killer,
            Location location,
            long createdAt,
            long expiresAt,
            boolean locked,
            boolean combatLog,
            List<ItemStack> contents
    ) {
        public Grave withContents(List<ItemStack> newContents) {
            return new Grave(id, owner, killer, location, createdAt, expiresAt, locked, combatLog, newContents);
        }
    }
}
