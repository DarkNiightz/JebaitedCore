package com.darkniightz.core.cosmetics;

import com.darkniightz.core.dev.DebugStateManager;
import com.darkniightz.core.dev.DebugFeedManager;
import com.darkniightz.core.dev.DeployStatusManager;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.gui.BaseMenu;
import com.darkniightz.core.gui.ItemBuilder;
import com.darkniightz.core.moderation.ModerationManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.system.BossBarManager;
import com.darkniightz.core.system.BroadcasterManager;
import com.darkniightz.core.system.MaterialCompat;
import com.darkniightz.core.system.McMMOIntegration;
import com.darkniightz.core.system.OverallStatsManager;
import com.darkniightz.core.world.SpawnManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

public class DebugMenu extends BaseMenu {
    private final JebaitedCore plugin;
    private final DevModeManager devMode;
    private final DebugStateManager debugState;
    private final DebugFeedManager feed;
    private final DeployStatusManager deployStatus;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final CosmeticsManager cosmetics;
    private final CosmeticsEngine cosmeticsEngine;
    private final ToyboxManager toyboxManager;
    private final BroadcasterManager broadcasterManager;
    private final BossBarManager bossBarManager;
    private final SpawnManager spawnManager;
    private final ModerationManager moderationManager;

    public DebugMenu(JebaitedCore plugin,
                     DevModeManager devMode,
                     DebugStateManager debugState,
                     DebugFeedManager feed,
                     DeployStatusManager deployStatus,
                     ProfileStore profiles,
                     RankManager ranks,
                     CosmeticsManager cosmetics,
                     CosmeticsEngine cosmeticsEngine,
                     ToyboxManager toyboxManager,
                     BroadcasterManager broadcasterManager,
                     BossBarManager bossBarManager,
                     SpawnManager spawnManager,
                     ModerationManager moderationManager) {
        super(plugin, "§5§lDev Debug Cockpit", 54);
        this.plugin = plugin;
        this.devMode = devMode;
        this.debugState = debugState;
        this.feed = feed;
        this.deployStatus = deployStatus;
        this.profiles = profiles;
        this.ranks = ranks;
        this.cosmetics = cosmetics;
        this.cosmeticsEngine = cosmeticsEngine;
        this.toyboxManager = toyboxManager;
        this.broadcasterManager = broadcasterManager;
        this.bossBarManager = bossBarManager;
        this.spawnManager = spawnManager;
        this.moderationManager = moderationManager;
    }

    @Override
    protected void populate(Player viewer) {
        Inventory inv = getInventory();
        fill(inv, Material.BLACK_STAINED_GLASS_PANE, " ");

        boolean dbUp = plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isEnabled();
        var em = plugin.getEventModeManager();
        int online = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        int feedCount = feed == null ? 0 : feed.snapshot().size();

        inv.setItem(4, new ItemBuilder(Material.NETHER_STAR)
                .name("§d§lDev Cockpit")
                .lore(List.of(
                        "§7DB: " + (dbUp ? "§aOnline" : "§cOffline"),
                        "§7Players: §f" + online + "§8/§f" + maxPlayers,
                        "§7Event: §f" + (em != null && em.isActive() ? "§aRunning" : "§7None"),
                        "§7Feed: §f" + feedCount + " events"
                ))
                .glow(true)
                .build());

        inv.setItem(10, button(Material.COMPASS, "§bServer Status",
                List.of("§7System tools + health panel.", "§8Live actions: toybox, flush, broadcast, bossbar.", "§8Status: DB, deploy, stats, mcMMO.")));
        inv.setItem(12, button(Material.BLAZE_POWDER, "§dCosmetics",
                List.of("§7Preview and equip particles, trails, and gadgets.", "§8Left click previews, shift-left equips.")));
        inv.setItem(14, button(Material.CLOCK, "§aLive Feed",
                List.of("§7Browse runtime events in real time.", "§8Filter by category, click an entry for details.")));
        inv.setItem(16, button(Material.GRASS_BLOCK, "§aWorld Settings",
                List.of("§7Inspect and tune each loaded world.", "§8Weather, time, mobs, PVP, gamerules.")));

        inv.setItem(28, button(Material.ANVIL, "§cReload Core",
                List.of("§7Reload config, listeners, and caches.", "§8Same as /jreload.")));
        inv.setItem(30, button(Material.CHEST, "§6Backup & Tools",
                List.of("§7Flush profiles, refresh leaderboards.", "§8DB check, schedule restart, clear feed.")));
        inv.setItem(32, button(Material.ENDER_EYE, "§9Spawn Tools",
                List.of("§7Teleport to spawn, set spawn, reset it.", "§8Quick navigation checks.")));
        inv.setItem(34, button(Material.COMPARATOR, "§bDatabase Controls",
                List.of("§7Table counts and wipe operations.", "§c§lShift-click§r§8 required for destructive ops.")));

        inv.setItem(49, button(Material.BARRIER, "§cClose", List.of("§7Close the debug cockpit.")));
    }

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
        if (!isAllowed(who)) {
            who.sendMessage("§cEnable devmode first with /devmode on.");
            return true;
        }
        switch (slot) {
            case 10 -> openSystemHealth(who);
            case 12 -> openCosmetics(who);
            case 14 -> openEvents(who);
            case 16 -> openWorldSettings(who);
            case 28 -> {
                who.sendMessage("§7Reloading core...");
                plugin.reloadCore();
                Bukkit.getScheduler().runTask(plugin, () ->
                    new DebugMenu(plugin, devMode, debugState, feed, deployStatus, profiles, ranks,
                        cosmetics, cosmeticsEngine, toyboxManager, broadcasterManager, bossBarManager,
                        spawnManager, moderationManager).open(who));
            }
            case 30 -> openBackupTools(who);
            case 32 -> openSpawnTools(who);
            case 34 -> openDatabase(who);
            case 49 -> who.closeInventory();
        }
        return true;
    }

    public void openDatabase(Player player) {
        openListMenu(player, "§b§lDatabase Controls", databaseEntries());
    }

    private List<DebugEntry> databaseEntries() {
        List<DebugEntry> list = new ArrayList<>();

        // â”€â”€ Table counts â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        list.add(entry(
            Material.BOOK,
            "§e§lTable Counts",
            List.of("§7Query row counts for all major tables.", "§8Results sent to your chat."),
            player -> {
                player.sendMessage("§b[DB] §7Querying table counts...");
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    String[] tables = {
                        "players", "player_stats", "player_event_stats",
                        "moderation_history", "moderation_state", "player_cosmetics",
                        "overall_stats", "rank_change_requests", "chat_logs",
                        "player_command_log", "server_maintenance", "maintenance_whitelist",
                        "player_notes", "watchlist_entries", "player_vaults",
                        "server_messages", "chat_game_data", "moderation_presets",
                        "friendships", "friend_requests", "friendship_stats",
                        "player_party_stats", "player_achievements", "achievement_vouchers",
                        "server_settings", "schema_migrations"
                    };
                    java.util.List<String> results = new java.util.ArrayList<>();
                    try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection()) {
                        for (String table : tables) {
                            try (java.sql.PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + table);
                                 java.sql.ResultSet rs = ps.executeQuery()) {
                                rs.next();
                                results.add("§8" + table + " §7\u00bb §f" + rs.getLong(1));
                            } catch (java.sql.SQLException ignored) {
                                results.add("§8" + table + " §c(error)");
                            }
                        }
                    } catch (java.sql.SQLException e) {
                        Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage("§c[DB] Failed to connect: " + e.getMessage()));
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§b[DB] §7Row counts:");
                        results.forEach(player::sendMessage);
                    });
                });
            },
            null
        ));

        // â”€â”€ Wipe audit logs â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        list.add(entry(
            Material.PAPER,
            "§6Wipe Audit Logs",
            List.of("§7Truncates §echat_logs §7and §eplayer_command_log§7.",
                    "§8Left-click: preview row counts.",
                    "§c§lShift-click: execute truncate."),
            player -> {
                player.sendMessage("§6[DB] §7Left-click a dangerous button shows this reminder.");
                player.sendMessage("§7Shift-click to truncate chat_logs and player_command_log.");
            },
            player -> {
                player.sendMessage("§6[DB] §7Wiping audit logs...");
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                         java.sql.Statement st = conn.createStatement()) {
                        st.execute("TRUNCATE TABLE chat_logs");
                        st.execute("TRUNCATE TABLE player_command_log");
                        Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage("§6[DB] §aAudit logs wiped."));
                    } catch (java.sql.SQLException e) {
                        Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage("§c[DB] Error: " + e.getMessage()));
                    }
                });
            }
        ));

        // â”€â”€ Wipe moderation history â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        list.add(entry(
            Material.BARRIER,
            "§cWipe Moderation History",
            List.of("§7Deletes all rows from §emoderation_history§7.",
                    "§8Active ban/mute state in moderation_state is §lNOT§r§8 affected.",
                    "§c§lShift-click: execute delete."),
            player -> player.sendMessage("§c[DB] §7Shift-click to wipe moderation_history."),
            player -> {
                player.sendMessage("§c[DB] §7Wiping moderation history...");
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                         java.sql.Statement st = conn.createStatement()) {
                        int rows = st.executeUpdate("DELETE FROM moderation_history");
                        Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage("§c[DB] §a" + rows + " moderation history rows deleted."));
                    } catch (java.sql.SQLException e) {
                        Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage("§c[DB] Error: " + e.getMessage()));
                    }
                });
            }
        ));

        // â”€â”€ Reset achievements â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        list.add(entry(
            Material.GOLD_INGOT,
            "§eReset All Achievements",
            List.of("§7Truncates §eplayer_achievements §7and §eachievement_vouchers§7.",
                    "§c§lShift-click: execute truncate."),
            player -> player.sendMessage("§e[DB] §7Shift-click to reset all achievement progress."),
            player -> {
                player.sendMessage("§e[DB] §7Resetting achievements...");
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                         java.sql.Statement st = conn.createStatement()) {
                        st.execute("TRUNCATE TABLE achievement_vouchers");
                        st.execute("TRUNCATE TABLE player_achievements");
                        Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage("§e[DB] §aAchievements reset."));
                    } catch (java.sql.SQLException e) {
                        Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage("§c[DB] Error: " + e.getMessage()));
                    }
                });
            }
        ));

        // â”€â”€ Reset player stats â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        list.add(entry(
            Material.TNT,
            "§cReset All Player Stats",
            List.of("§7Truncates §eplayer_stats §7and §eplayer_event_stats§7.",
                    "§7Player accounts (rank, balance, cosmetics) are preserved.",
                    "§c§lShift-click: execute truncate."),
            player -> player.sendMessage("§c[DB] §7Shift-click to wipe all player stats."),
            player -> {
                player.sendMessage("§c[DB] §7Wiping player stats...");
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                         java.sql.Statement st = conn.createStatement()) {
                        st.execute("TRUNCATE TABLE player_event_stats");
                        // Reset stats columns to 0 rather than deleting rows (FK chain)
                        st.execute("UPDATE player_stats SET kills=0,deaths=0,mobs_killed=0,bosses_killed=0," +
                            "blocks_broken=0,crops_broken=0,fish_caught=0,playtime_ms=0,playtime_seconds=0," +
                            "messages_sent=0,commands_sent=0,cosmetic_coins=0,balance=0,mcmmo_level=0," +
                            "event_wins_combat=0,event_wins_chat=0,event_wins_hardcore=0");
                        Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage("§c[DB] §aPlayer stats reset."));
                    } catch (java.sql.SQLException e) {
                        Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage("§c[DB] Error: " + e.getMessage()));
                    }
                });
            }
        ));

        // â”€â”€ Full DB wipe â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        list.add(entry(
            Material.BEDROCK,
            "§4§lâ˜¢ Full Database Wipe",
            List.of("§7Drops and recreates the public schema.",
                    "§cAll data will be lost permanently.",
                    "§4Only available when you are the sole online player.",
                    "§c§lShift-click: execute if safe."),
            player -> player.sendMessage("§4[DB] §cShift-click to wipe the entire database. §lThis cannot be undone."),
            player -> {
                long otherPlayers = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
                    .count();
                if (otherPlayers > 0) {
                    player.sendMessage("§4[DB] §cAborted â€” §f" + otherPlayers + "§c other player(s) are online. Clear the server first.");
                    return;
                }
                player.sendMessage("§4[DB] §7Wiping entire database. Server will restart after.");
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                         java.sql.Statement st = conn.createStatement()) {
                        st.execute("DROP SCHEMA public CASCADE");
                        st.execute("CREATE SCHEMA public");
                        st.execute("GRANT ALL ON SCHEMA public TO postgres");
                        st.execute("GRANT ALL ON SCHEMA public TO public");
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage("§4[DB] §aSchema wiped. Restarting server...");
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "restart");
                        });
                    } catch (java.sql.SQLException e) {
                        Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage("§c[DB] Error: " + e.getMessage()));
                    }
                });
            }
        ));

        return list;
    }

    public void openSystemHealth(Player player) {
        openListMenu(player, "§5§lServer Status", systemHealthEntries());
    }

    // Legacy / redirect stubs
    public void openSystem(Player player)    { openSystemHealth(player); }
    public void openHealth(Player player)    { openSystemHealth(player); }
    public void openCommands(Player player)  { openSystemHealth(player); }
    public void openListeners(Player player) { openSystemHealth(player); }
    public void openPreview(Player player)   { openCosmetics(player); }
    public void openActions(Player player)   { openBackupTools(player); }

    public void openCosmetics(Player player) {
        openListMenu(player, "§5§lCosmetics", cosmeticEntries(player));
    }

    public void openEvents(Player player) {
        new DebugFeedMenu().open(player);
    }

    public void openWorldSettings(Player player) {
        openListMenu(player, "§5§lWorld Settings", worldEntries());
    }

    private void openBackupTools(Player player) {
        openListMenu(player, "§5§lBackup & Tools", backupToolsEntries());
    }

    private List<DebugEntry> worldEntries() {
        List<DebugEntry> entries = new ArrayList<>();
        var wm = plugin.getWorldManager();
        String hubName = wm == null ? "" : wm.getHubWorldName();
        String smpName = wm == null ? "" : wm.getSmpWorldName();
        for (World world : Bukkit.getWorlds()) {
            String name = world.getName();
            String roleTag = name.equalsIgnoreCase(hubName) ? "§b[Hub] " :
                             name.equalsIgnoreCase(smpName) ? "§a[SMP] " : "§d[Extra] ";
            List<String> lore = new ArrayList<>();
            lore.add("§7Role: §f" + roleTag.trim());
            lore.add("§7Players: §f" + world.getPlayers().size());
            lore.add("§7Environment: §f" + world.getEnvironment().name());
            lore.add("§7Weather: " + weatherState(world));
            lore.add("§7Time: §f" + timePreset(world.getTime()));
            lore.add("§7Mobs: " + (world.getAllowMonsters() ? "§aON" : "§cOFF") + " §8| §7Animals: " + (world.getAllowAnimals() ? "§aON" : "§cOFF"));
            lore.add("§7PVP: " + (world.getPVP() ? "§aON" : "§cOFF"));
            lore.add("§8Click to open the live settings editor.");
            entries.add(entry(worldIcon(world), roleTag + "§b" + name, lore, p -> openWorldSettingsFor(p, world), null));
        }
        if (entries.isEmpty()) {
            entries.add(entry(Material.BARRIER, "§cNo worlds loaded", List.of("§7There are no editable worlds right now."), null, null));
        }
        return entries;
    }

    private void openWorldSettingsFor(Player player, World world) {
        List<DebugEntry> entries = new ArrayList<>();
        entries.add(entry(Material.COMPASS, "§aTeleport to World Spawn", List.of("§7Jump to the current spawn for this world."), p -> p.teleport(world.getSpawnLocation()), null));
        entries.add(entry(Material.GRASS_BLOCK, "§bApply Hub Preset", List.of("§7Daytime, clear weather, no mobs, no PVP.", "§8Great for lobby or spawn worlds."), p -> {
            world.setTime(1000L);
            world.setStorm(false);
            world.setThundering(false);
            world.setClearWeatherDuration(20 * 60 * 20);
            world.setPVP(false);
            world.setSpawnFlags(false, true);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.MOB_GRIEFING, false);
            p.sendMessage("§aUpdated §f" + world.getName() + "§a with the hub preset.");
            openWorldSettingsFor(p, world);
        }, null));
        entries.add(entry(Material.ZOMBIE_HEAD, "§eToggle Mob Spawning", List.of("§7Currently: " + (world.getAllowMonsters() ? "§aON" : "§cOFF")), p -> {
            world.setSpawnFlags(!world.getAllowMonsters(), world.getAllowAnimals());
            p.sendMessage("§aMob spawning in §f" + world.getName() + " §ais now " + (world.getAllowMonsters() ? "§aON" : "§cOFF") + "§a.");
            openWorldSettingsFor(p, world);
        }, null));
        entries.add(entry(Material.SHEEP_SPAWN_EGG, "§eToggle Animal Spawning", List.of("§7Currently: " + (world.getAllowAnimals() ? "§aON" : "§cOFF")), p -> {
            world.setSpawnFlags(world.getAllowMonsters(), !world.getAllowAnimals());
            p.sendMessage("§aAnimal spawning in §f" + world.getName() + " §ais now " + (world.getAllowAnimals() ? "§aON" : "§cOFF") + "§a.");
            openWorldSettingsFor(p, world);
        }, null));
        entries.add(entry(Material.CLOCK, "§6Cycle Time Preset", List.of("§7Current: §f" + timePreset(world.getTime()), "§8Click cycles day â†’ noon â†’ sunset â†’ night."), p -> {
            long current = world.getTime() % 24000L;
            long next = current < 6000L ? 6000L : current < 12000L ? 12000L : current < 13000L ? 13000L : 1000L;
            world.setTime(next);
            p.sendMessage("§aTime in §f" + world.getName() + " §aset to §f" + timePreset(next) + "§a.");
            openWorldSettingsFor(p, world);
        }, null));
        entries.add(entry(Material.SUNFLOWER, "§bSet Clear Weather", List.of("§7Force this world back to clear skies."), p -> {
            world.setStorm(false);
            world.setThundering(false);
            world.setClearWeatherDuration(20 * 60 * 20);
            p.sendMessage("§aWeather in §f" + world.getName() + " §ais now CLEAR.");
            openWorldSettingsFor(p, world);
        }, null));
        entries.add(entry(Material.WATER_BUCKET, "§9Set Rain", List.of("§7Turn on rain for this world."), p -> {
            world.setStorm(true);
            world.setThundering(false);
            p.sendMessage("§aWeather in §f" + world.getName() + " §ais now RAIN.");
            openWorldSettingsFor(p, world);
        }, null));
        entries.add(entry(Material.TRIDENT, "§5Set Thunder", List.of("§7Turn on thunder for this world."), p -> {
            world.setStorm(true);
            world.setThundering(true);
            p.sendMessage("§aWeather in §f" + world.getName() + " §ais now THUNDER.");
            openWorldSettingsFor(p, world);
        }, null));
        entries.add(entry(Material.IRON_SWORD, "§cToggle PVP", List.of("§7Currently: " + (world.getPVP() ? "§aON" : "§cOFF")), p -> {
            world.setPVP(!world.getPVP());
            p.sendMessage("§aPVP in §f" + world.getName() + " §ais now " + (world.getPVP() ? "§aON" : "§cOFF") + "§a.");
            openWorldSettingsFor(p, world);
        }, null));
        entries.add(entry(Material.DAYLIGHT_DETECTOR, "§fToggle Daylight Cycle", List.of("§7Currently: " + (Boolean.TRUE.equals(world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE)) ? "§aON" : "§cOFF")), p -> {
            boolean next = !Boolean.TRUE.equals(world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE));
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, next);
            p.sendMessage("§aDaylight cycle in §f" + world.getName() + " §ais now " + (next ? "§aON" : "§cOFF") + "§a.");
            openWorldSettingsFor(p, world);
        }, null));
        entries.add(entry(Material.CREEPER_HEAD, "§fToggle Mob Griefing", List.of("§7Currently: " + (Boolean.TRUE.equals(world.getGameRuleValue(GameRule.MOB_GRIEFING)) ? "§aON" : "§cOFF")), p -> {
            boolean next = !Boolean.TRUE.equals(world.getGameRuleValue(GameRule.MOB_GRIEFING));
            world.setGameRule(GameRule.MOB_GRIEFING, next);
            p.sendMessage("§aMob griefing in §f" + world.getName() + " §ais now " + (next ? "§aON" : "§cOFF") + "§a.");
            openWorldSettingsFor(p, world);
        }, null));
        openListMenu(player, "§5§lWorld â€¢ " + world.getName(), entries);
    }

    private Material worldIcon(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            default -> Material.GRASS_BLOCK;
        };
    }

    private String weatherState(World world) {
        if (world.isThundering()) return "§5THUNDER";
        if (world.hasStorm()) return "§9RAIN";
        return "§aCLEAR";
    }

    private String timePreset(long ticks) {
        long normalized = ((ticks % 24000L) + 24000L) % 24000L;
        if (normalized < 3000L) return "Sunrise";
        if (normalized < 9000L) return "Day";
        if (normalized < 13000L) return "Sunset";
        return "Night";
    }

    private void openSpawnTools(Player player) {
        openListMenu(player, "§5§lSpawn Tools", spawnEntries());
    }

    private void openListMenu(Player player, String title, List<DebugEntry> entries) {
        new DebugListMenu(title, entries).open(player);
    }

    // â”€â”€ System Health (combined system tools + health panel) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private List<DebugEntry> systemHealthEntries() {
        List<DebugEntry> entries = new ArrayList<>();
        var wm = plugin.getWorldManager();
        var em = plugin.getEventModeManager();

        // System Tools
        entries.add(entry(Material.GRAY_STAINED_GLASS_PANE, "§8§lâ–Œ System Tools",
                List.of("§7Live actions."), null, null));
        entries.add(entry(Material.MAP, "§bWorld Routing",
                List.of("§7Hub: §f" + (wm == null ? "unknown" : wm.getHubWorldName()),
                        "§7SMP: §f" + (wm == null ? "unknown" : wm.getSmpWorldName()),
                        "§7SMP loaded: " + ((wm != null && Bukkit.getWorld(wm.getSmpWorldName()) != null) ? "§aYES" : "§cNO")),
                p -> {
                    if (wm == null) { p.sendMessage("§cWorldManager unavailable."); return; }
                    p.sendMessage("§7Hub: §f" + wm.getHubWorldName());
                    p.sendMessage("§7SMP: §f" + wm.getSmpWorldName() + "  loaded: " + (Bukkit.getWorld(wm.getSmpWorldName()) != null ? "§aYES" : "§cNO"));
                }, null));
        entries.add(entry(Material.NETHER_STAR, "§dEvent Runtime",
                List.of("§7Combat event: " + (em != null && em.isActive() ? "§aYES" : "§cNO"),
                        "§7" + (em == null ? "unavailable" : ChatColor.stripColor(em.getStatusLine())),
                        "§7Chat game: " + (em != null && em.chatGames().isActive() ? "§aYES" : "§cNO"),
                        "§7" + (em == null ? "unavailable" : ChatColor.stripColor(em.getChatGameStatusLine()))),
                p -> {
                    if (em == null) { p.sendMessage("§cEvent manager unavailable."); return; }
                    p.sendMessage(em.getStatusLine());
                    p.sendMessage(em.getChatGameStatusLine());
                    p.sendMessage("§7Combat events: §f" + String.join(", ", em.getConfiguredEventDisplayNames()));
                    p.sendMessage("§7Chat games: §f" + String.join(", ", em.getConfiguredChatGameDisplayNames()));
                }, null));
        entries.add(entry(Material.LIME_DYE, "§aRefresh Toybox",
                List.of("§7Re-issue the active gadget item to your hotbar."),
                p -> {
                    toyboxManager.refresh(p);
                    p.sendMessage("§aToybox refreshed.");
                    if (feed != null) feed.recordGadget(p, "Toybox refreshed", List.of("§7Re-issued."));
                }, null));
        entries.add(entry(Material.PAPER, "§fFlush Profiles",
                List.of("§7Write all dirty cached profiles to the database now."),
                p -> {
                    profiles.flushAll();
                    p.sendMessage("§aProfiles flushed.");
                    if (feed != null) feed.recordSystem("Profiles flushed", List.of("§7By §f" + p.getName()));
                }, null));
        entries.add(entry(Material.COMPASS, "§eSpawn Teleport",
                List.of("§7Teleport to the configured spawn."),
                p -> {
                    boolean moved = spawnManager.teleportToSpawn(p);
                    if (feed != null) feed.recordSystem("Spawn teleport", List.of("§7Player: §f" + p.getName(), "§7Result: §f" + (moved ? "moved" : "failed")));
                }, null));
        entries.add(entry(Material.REDSTONE_BLOCK, "§cBroadcast Now",
                List.of("§7Fire the next scheduled broadcast immediately."),
                p -> {
                    broadcasterManager.broadcastNow();
                    p.sendMessage("§aBroadcast fired.");
                    if (feed != null) feed.recordSystem("Broadcast fired", List.of("§7By §f" + p.getName()));
                }, null));
        entries.add(entry(Material.DRAGON_EGG, "§dBossBar Rotate",
                List.of("§7Advance the bossbar to the next message."),
                p -> {
                    bossBarManager.rotateNow();
                    p.sendMessage("§aBossbar rotated.");
                    if (feed != null) feed.recordSystem("Bossbar rotated", List.of("§7Title: §f" + bossBarManager.getCurrentTitle()));
                }, null));

        // Server Health
        entries.add(entry(Material.GRAY_STAINED_GLASS_PANE, "§8§lâ–Œ Server Health",
                List.of("§7Status and stats."), null, null));
        boolean dbUp = plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isEnabled();
        boolean mcMmoEnabled = McMMOIntegration.isEnabled();
        String mcMmoVersion = McMMOIntegration.getVersion();
        String version = plugin.getDescription().getVersion();
        String deploySummary = deployStatus == null ? "unknown" : deployStatus.summaryLine();
        int cachedProfiles = plugin.getProfileStore() == null ? 0 : plugin.getProfileStore().cachedCount();
        int dirtyProfiles  = plugin.getProfileStore() == null ? 0 : plugin.getProfileStore().dirtyCount();
        int lbCount = plugin.getLeaderboardManager() == null ? 0 : plugin.getLeaderboardManager().definitionCount();
        long lbRefresh = plugin.getLeaderboardManager() == null ? 0L : plugin.getLeaderboardManager().refreshIntervalSeconds();
        int combatTags = plugin.getCombatTagManager() == null ? 0 : plugin.getCombatTagManager().activeCount();
        java.util.Map<String, Long> stats = plugin.getOverallStatsManager() == null ? java.util.Map.of() : plugin.getOverallStatsManager().loadAll();
        long totalUnique = stats.getOrDefault(OverallStatsManager.UNIQUE_LOGINS, 0L);
        long totalJoins  = stats.getOrDefault(OverallStatsManager.TOTAL_JOINS, 0L);
        long totalKills  = stats.getOrDefault(OverallStatsManager.TOTAL_KILLS, 0L);
        long totalDeaths = stats.getOrDefault(OverallStatsManager.TOTAL_DEATHS, 0L);
        long totalPlayMs = stats.getOrDefault(OverallStatsManager.TOTAL_PLAYTIME_MS, 0L);
        entries.add(entry(dbUp ? Material.EMERALD : Material.REDSTONE, "§bDatabase",
                List.of("§7Status: " + (dbUp ? "§aOnline" : "§cOffline"),
                        "§7Error: §f" + (plugin.getDatabaseManager() == null ? "none" : plugin.getDatabaseManager().getLastConnectError())),
                p -> p.sendMessage("§7DB: " + (dbUp ? "§aOnline" : "§cOffline â€” " + plugin.getDatabaseManager().getLastConnectError())), null));
        entries.add(entry(Material.NETHER_STAR, "§fPlugin & Caches",
                List.of("§7Version: §f" + version,
                        "§7Profiles: §f" + cachedProfiles + " cached §8(§f" + dirtyProfiles + " dirty§8)",
                        "§7Leaderboards: §f" + lbCount + " §8(refresh §f" + lbRefresh + "s§8)",
                        "§7Combat tags: §f" + combatTags + " active"),
                p -> {
                    p.sendMessage("§7Version: §f" + version);
                    p.sendMessage("§7Profiles cached: §f" + cachedProfiles + " §8(dirty: " + dirtyProfiles + ")");
                    p.sendMessage("§7Leaderboards: §f" + lbCount + " §8(refresh " + lbRefresh + "s)");
                    p.sendMessage("§7Combat tags active: §f" + combatTags);
                }, null));
        entries.add(entry(Material.BEACON, "§dDeploy Status",
                List.of("§7" + deploySummary),
                p -> {
                    if (deployStatus != null) {
                        var snap = deployStatus.snapshot();
                        p.sendMessage("§7Last deploy: §f" + snap.lastDeployAt());
                        p.sendMessage("§7Container: §f" + snap.containerRunning());
                        p.sendMessage("§7Restarted: §f" + snap.restartPerformed());
                    }
                }, null));
        entries.add(entry(mcMmoEnabled ? Material.ENCHANTED_BOOK : Material.BOOK, "§5mcMMO Bridge",
                List.of("§7Status: " + (mcMmoEnabled ? "§aEnabled" : "§cNot detected"),
                        "§7Version: §f" + (mcMmoVersion == null ? "unknown" : mcMmoVersion)),
                p -> p.sendMessage("§7mcMMO: " + (mcMmoEnabled ? "§aenabled" : "§cnot detected")), null));
        entries.add(entry(Material.WRITABLE_BOOK, "§6Server Stats",
                List.of("§7Unique logins: §f" + totalUnique,
                        "§7K/D: §f" + totalKills + "§7/§f" + totalDeaths,
                        "§7Playtime: §f" + (totalPlayMs / 3600000L) + "h  §8|  §7Joins: §f" + totalJoins),
                p -> {
                    p.sendMessage("§6Server Stats");
                    p.sendMessage("§7Unique logins: §f" + totalUnique + "  §8|  §7Joins: §f" + totalJoins);
                    p.sendMessage("§7Kills: §f" + totalKills + "  §8|  §7Deaths: §f" + totalDeaths);
                    p.sendMessage("§7Playtime: §f" + (totalPlayMs / 3600000L) + "h");
                    p.sendMessage("§7Messages: §f" + stats.getOrDefault(OverallStatsManager.TOTAL_MESSAGES, 0L));
                    p.sendMessage("§7Commands: §f" + stats.getOrDefault(OverallStatsManager.TOTAL_COMMANDS, 0L));
                    p.sendMessage("§7Graves: §f" + stats.getOrDefault(OverallStatsManager.TOTAL_GRAVES, 0L));
                }, null));
        return entries;
    }

    // â”€â”€ Backup & Tools â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private List<DebugEntry> backupToolsEntries() {
        List<DebugEntry> entries = new ArrayList<>();
        var lbm = plugin.getLeaderboardManager();
        var rm  = plugin.getRestartManager();
        boolean dbUp = plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isEnabled();
        entries.add(entry(Material.PAPER, "§fFlush All Profiles",
                List.of("§7Write every dirty cached profile to the database.", "§8Safe to run at any time."),
                p -> {
                    profiles.flushAll();
                    p.sendMessage("§aAll profiles flushed to DB.");
                    if (feed != null) feed.recordSystem("Manual profile flush", List.of("§7By §f" + p.getName()));
                }, null));
        entries.add(entry(Material.BOOK, "§aRefresh Leaderboards",
                List.of("§7Rebuild all leaderboard caches from the database."),
                p -> {
                    if (lbm == null) { p.sendMessage("§cLeaderboard manager unavailable."); return; }
                    int n = lbm.refreshNow();
                    p.sendMessage("§aLeaderboards refreshed â€” §f" + n + " entries.");
                    if (feed != null) feed.recordSystem("Leaderboards refreshed", List.of("§7" + n + " entries by §f" + p.getName()));
                }, null));
        entries.add(entry(dbUp ? Material.EMERALD : Material.REDSTONE, "§bDB Connection Check",
                List.of("§7Test that the pool can still acquire a connection."),
                p -> {
                    boolean ok = plugin.getDatabaseManager() != null && plugin.getDatabaseManager().canAcquireConnection();
                    p.sendMessage(ok ? "§aDB connection: §fOK" : "§cDB connection: §fFAILED â€” " + (plugin.getDatabaseManager() == null ? "null" : plugin.getDatabaseManager().getLastConnectError()));
                    if (feed != null) feed.recordSystem("DB check", List.of("§7Result: §f" + (ok ? "OK" : "FAILED")));
                }, null));
        entries.add(entry(Material.CLOCK, "§eSchedule Restart (60s)",
                List.of("§7Request a graceful restart in 60 seconds.", "§8Sends a countdown warning to all players."),
                p -> {
                    if (rm == null) { p.sendMessage("§cRestartManager unavailable."); return; }
                    if (rm.isRestartPending()) { p.sendMessage("§eA restart is already pending."); return; }
                    rm.scheduleRestart(60, "Dev cockpit restart", p);
                    if (feed != null) feed.recordSystem("Restart scheduled", List.of("§7By §f" + p.getName(), "§7Countdown: §f60s"));
                }, null));
        entries.add(entry(Material.BARRIER, "§cClear Debug Feed",
                List.of("§7Wipe all recorded debug events from memory."),
                p -> {
                    if (feed == null) { p.sendMessage("§cFeed unavailable."); return; }
                    feed.clear();
                    p.sendMessage("§aDebug feed cleared.");
                }, null));
        return entries;
    }

    /** @deprecated Delegates to systemHealthEntries(). */
    private List<DebugEntry> systemEntries() { return systemHealthEntries(); }

    private List<DebugEntry> commandEntries() {
        List<DebugEntry> entries = new ArrayList<>();
        entries.add(entry(Material.PLAYER_HEAD, "/stats", List.of("§7Open the new stats GUI panel.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/stats", "Open the stats GUI panel.", "/stats [player]"), null, runCommand("/stats"), suggestCommand("/stats [player]", "§7Click to paste: /stats [player]")));
        entries.add(entry(Material.BLAZE_POWDER, "/cosmetics", List.of("§7Open the Cosmetics Lounge.", "§8Right click opens it now. Shift-right pastes the command."), commandInfo("/cosmetics", "Open the Cosmetics Lounge.", "/cosmetics"), null, p -> new CosmeticsMenu(plugin, cosmetics, profiles, toyboxManager, ((com.darkniightz.main.JebaitedCore) plugin).getCosmeticPreviewService()).open(p), suggestCommand("/cosmetics", "§7Click to paste: /cosmetics")));
        entries.add(entry(Material.LEATHER_CHESTPLATE, "/wardrobe", List.of("§7Alias for cosmetics.", "§8Right click opens it now. Shift-right pastes the command."), commandInfo("/wardrobe", "Open the wardrobe directly.", "/wardrobe"), null, p -> new WardrobeMenu(plugin, cosmetics, profiles, toyboxManager, CosmeticsManager.Category.PARTICLES, ((com.darkniightz.main.JebaitedCore) plugin).getCosmeticPreviewService()).open(p), suggestCommand("/wardrobe", "§7Click to paste: /wardrobe")));
        entries.add(entry(Material.COMPASS, "/menu", List.of("§7Open the server navigator.", "§8Right click opens it now. Shift-right pastes the command."), commandInfo("/menu", "Open the server navigator.", "/menu"), null, p -> new com.darkniightz.core.hub.ServersMenu(plugin).open(p), suggestCommand("/menu", "§7Click to paste: /menu")));
        entries.add(entry(Material.ENDER_EYE, "/navigator", List.of("§7Alias for menu.", "§8Right click opens it now. Shift-right pastes the command."), commandInfo("/navigator", "Open the server navigator.", "/navigator"), null, p -> new com.darkniightz.core.hub.ServersMenu(plugin).open(p), suggestCommand("/navigator", "§7Click to paste: /navigator")));
        entries.add(entry(Material.GRASS_BLOCK, "/hub", List.of("§7Return to Hub spawn from any world.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/hub", "Return to Hub world spawn.", "/hub"), null, runCommand("/hub"), suggestCommand("/hub", "§7Click to paste: /hub")));
        entries.add(entry(Material.OAK_SAPLING, "/smp", List.of("§7Go to SMP with return-location support.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/smp", "Travel to SMP using remembered return location when present.", "/smp"), null, runCommand("/smp"), suggestCommand("/smp", "§7Click to paste: /smp")));
        entries.add(entry(Material.CARTOGRAPHY_TABLE, "/worldstatus", List.of("§7Check world routing diagnostics.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/worldstatus", "Show hub/smp routing and world load status.", "/worldstatus"), null, runCommand("/worldstatus"), suggestCommand("/worldstatus", "§7Click to paste: /worldstatus")));
        entries.add(entry(Material.NETHER_STAR, "/event", List.of("§7Control and inspect event runtime.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/event", "Manage events and queues.", "/event <status|list|start|stop|complete|setup>"), null, runCommand("/event status"), suggestCommand("/event status", "§7Click to paste: /event status")));
        entries.add(entry(Material.NETHER_STAR, "/jebaited", List.of("§7Adaptive help and command list.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/jebaited", "Show the adaptive help and command list.", "/jebaited"), null, runCommand("/jebaited"), suggestCommand("/jebaited", "§7Click to paste: /jebaited")));
        entries.add(entry(Material.PURPLE_DYE, "/devmode", List.of("§7Toggle devmode for allowed UUIDs.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/devmode", "Toggle devmode for allowed UUIDs.", "/devmode on"), null, runCommand("/devmode"), suggestCommand("/devmode", "§7Click to paste: /devmode")));
        entries.add(entry(Material.ANVIL, "/jreload", List.of("§7Reload config and refresh caches.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/jreload", "Reload config and refresh caches.", "/jreload"), null, p -> { plugin.reloadCore(); if (feed != null) feed.recordSystem("Core reloaded from command list", List.of("§7Triggered by §f" + p.getName() + "§7.")); }, suggestCommand("/jreload", "§7Click to paste: /jreload")));
        entries.add(entry(Material.IRON_SWORD, "/rank", List.of("§7View or edit ranks.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/rank", "View or edit ranks.", "/rank get <player>"), null, runCommand("/rank"), suggestCommand("/rank get <player>", "§7Click to paste: /rank get <player>")));
        entries.add(entry(Material.GOLDEN_SWORD, "/setrank", List.of("§7Set a player's rank.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/setrank", "Set a player's rank.", "/setrank <player> <group>"), null, runCommand("/setrank"), suggestCommand("/setrank <player> <group>", "§7Click to paste: /setrank <player> <group>")));
        entries.add(entry(Material.EMERALD, "/coins", List.of("§7View or manage Cosmetic Coins.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/coins", "View or manage Cosmetic Coins.", "/coins [player]"), null, runCommand("/coins"), suggestCommand("/coins [player]", "§7Click to paste: /coins [player]")));
        entries.add(entry(Material.GOLD_NUGGET, "/balance", List.of("§7View money balance.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/balance", "View your balance or inspect another player.", "/balance [player]"), null, runCommand("/balance"), suggestCommand("/balance [player]", "§7Click to paste: /balance [player]")));
        entries.add(entry(Material.GOLD_INGOT, "/pay", List.of("§7Send money to an online player.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/pay", "Transfer money to another player.", "/pay <player> <amount>"), null, runCommand("/pay"), suggestCommand("/pay <player> <amount>", "§7Click to paste: /pay <player> <amount>")));
        entries.add(entry(Material.CHEST, "/balancetop", List.of("§7Show richest players.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/balancetop", "Show top balances.", "/balancetop [limit]"), null, runCommand("/balancetop"), suggestCommand("/balancetop [limit]", "§7Click to paste: /balancetop [limit]")));
        entries.add(entry(Material.BEACON, "/eco", List.of("§7Admin balance controls.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/eco", "Admin give/take/set money.", "/eco <give|take|set> <player> <amount>"), null, runCommand("/eco"), suggestCommand("/eco <give|take|set> <player> <amount>", "§7Click to paste: /eco <give|take|set> <player> <amount>")));
        entries.add(entry(Material.RED_BED, "/sethome /home /homes", List.of("§7Manage personal homes.", "§8Right click runs /homes. Shift-right pastes /sethome <name>."), commandInfo("/homes", "List and use player homes.", "/sethome [name] | /home [name] | /delhome <name>"), null, runCommand("/homes"), suggestCommand("/sethome <name>", "§7Click to paste: /sethome <name>")));
        entries.add(entry(Material.NAME_TAG, "/nick /whois", List.of("§7Nickname and profile diagnostics.", "§8Right click runs /nick. Shift-right pastes /whois."), commandInfo("/nick", "Set nickname or inspect player diagnostics.", "/nick <name|off> | /whois <player>"), null, runCommand("/nick"), suggestCommand("/whois <player>", "§7Click to paste: /whois <player>")));
        entries.add(entry(MaterialCompat.resolve(Material.COMPASS, "SPYGLASS", "COMPASS"), "/near", List.of("§7List nearby players in your world.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/near", "Scan nearby players by radius.", "/near [radius]"), null, runCommand("/near"), suggestCommand("/near [radius]", "§7Click to paste: /near [radius]")));
        entries.add(entry(Material.BOOK, "/rules", List.of("§7Show current server rules.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/rules", "Show server rules from config.", "/rules"), null, runCommand("/rules"), suggestCommand("/rules", "§7Click to paste: /rules")));
        entries.add(entry(Material.ENDER_PEARL, "/rtp", List.of("§7Random teleport in SMP.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/rtp", "Teleport to random safe coordinates.", "/rtp"), null, runCommand("/rtp"), suggestCommand("/rtp", "§7Click to paste: /rtp")));
        entries.add(entry(Material.PAPER, "/message /reply", List.of("§7Private messaging commands.", "§8Right click runs /message. Shift-right pastes /reply."), commandInfo("/message", "Send and reply to private messages.", "/message <player> <message> | /reply <message>"), null, runCommand("/message"), suggestCommand("/reply <message>", "§7Click to paste: /reply <message>")));
        entries.add(entry(MaterialCompat.resolve(Material.COMPASS, "LODESTONE", "COMPASS"), "/warp /warps", List.of("§7Use public warps.", "§8Right click runs /warps. Shift-right pastes /warp <name>."), commandInfo("/warps", "List and use public warps.", "/warp <name>"), null, runCommand("/warps"), suggestCommand("/warp <name>", "§7Click to paste: /warp <name>")));
        entries.add(entry(Material.STRUCTURE_BLOCK, "/setwarp /delwarp", List.of("§7Admin warp management.", "§8Right click runs /setwarp. Shift-right pastes usage."), commandInfo("/setwarp", "Create or delete public warps.", "/setwarp <name> [cost] | /delwarp <name>"), null, runCommand("/setwarp"), suggestCommand("/setwarp <name> [cost]", "§7Click to paste: /setwarp <name> [cost]")));
        entries.add(entry(Material.SLIME_BALL, "/spawn", List.of("§7Teleport to spawn.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/spawn", "Teleport to spawn.", "/spawn"), null, runCommand("/spawn"), suggestCommand("/spawn", "§7Click to paste: /spawn")));
        entries.add(entry(Material.BEDROCK, "/setspawn", List.of("§7Set the configured spawn.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/setspawn", "Set the configured spawn.", "/setspawn"), null, runCommand("/setspawn"), suggestCommand("/setspawn", "§7Click to paste: /setspawn")));
        entries.add(entry(MaterialCompat.resolve(Material.BOOK, "WRITABLE_BOOK", "BOOK_AND_QUILL", "BOOK"), "/generatepassword", List.of("§7Provision a web panel login.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/generatepassword", "Provision a web panel login.", "/generatepassword"), null, runCommand("/generatepassword"), suggestCommand("/generatepassword", "§7Click to paste: /generatepassword")));
        entries.add(entry(Material.IRON_DOOR, "/kick", List.of("§7Kick a player.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/kick", "Kick a player.", "/kick <player> <reason>"), null, runCommand("/kick"), suggestCommand("/kick <player> <reason>", "§7Click to paste: /kick <player> <reason>")));
        entries.add(entry(Material.PAPER, "/warn", List.of("§7Warn a player.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/warn", "Warn a player.", "/warn <player> <reason>"), null, runCommand("/warn"), suggestCommand("/warn <player> <reason>", "§7Click to paste: /warn <player> <reason>")));
        entries.add(entry(Material.BLAZE_ROD, "/mute /tempmute", List.of("§7Mute a player.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/tempmute", "Mute a player.", "/tempmute <player> <duration> <reason>"), null, runCommand("/tempmute"), suggestCommand("/tempmute <player> <duration> <reason>", "§7Click to paste: /tempmute <player> <duration> <reason>")));
        entries.add(entry(Material.LAVA_BUCKET, "/ban /tempban", List.of("§7Ban a player.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/tempban", "Ban a player.", "/tempban <player> <duration> <reason>"), null, runCommand("/tempban"), suggestCommand("/tempban <player> <duration> <reason>", "§7Click to paste: /tempban <player> <duration> <reason>")));
        entries.add(entry(Material.BARRIER, "/unban /unmute", List.of("§7Undo punishments.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/unban", "Undo punishments.", "/unban <player> or /unmute <player>"), null, runCommand("/unban"), suggestCommand("/unban <player> or /unmute <player>", "§7Click to paste: /unban <player> or /unmute <player>")));
        entries.add(entry(Material.ICE, "/freeze", List.of("§7Freeze a player.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/freeze", "Freeze a player.", "/freeze <player>"), null, runCommand("/freeze"), suggestCommand("/freeze <player>", "§7Click to paste: /freeze <player>")));
        entries.add(entry(Material.ENDER_PEARL, "/vanish", List.of("§7Toggle staff vanish.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/vanish", "Toggle staff vanish.", "/vanish"), null, runCommand("/vanish"), suggestCommand("/vanish", "§7Click to paste: /vanish")));
        entries.add(entry(MaterialCompat.resolve(Material.BOOK, "WRITABLE_BOOK", "BOOK_AND_QUILL", "BOOK"), "/staffchat", List.of("§7Toggle staff chat or send a message.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/staffchat", "Toggle staff chat or send a message.", "/staffchat <message>"), null, runCommand("/staffchat"), suggestCommand("/staffchat <message>", "§7Click to paste: /staffchat <message>")));
        entries.add(entry(Material.GLASS, "/clearchat", List.of("§7Clear public chat.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/clearchat", "Clear public chat.", "/clearchat"), null, runCommand("/clearchat"), suggestCommand("/clearchat", "§7Click to paste: /clearchat")));
        entries.add(entry(Material.CLOCK, "/slowmode", List.of("§7Set chat slowmode.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/slowmode", "Set chat slowmode.", "/slowmode <seconds>"), null, runCommand("/slowmode"), suggestCommand("/slowmode <seconds>", "§7Click to paste: /slowmode <seconds>")));
        entries.add(entry(Material.PAPER, "/history", List.of("§7View moderation history.", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/history", "View moderation history.", "/history <player>"), null, runCommand("/history"), suggestCommand("/history <player>", "§7Click to paste: /history <player>")));
        entries.add(entry(Material.WOODEN_SWORD, "/combatlogs", List.of("§7Show combat tag time remaining (aliases §f/combatlog§7, §f/ctag§7).", "§8Right click runs it. Shift-right pastes the command."), commandInfo("/combatlogs", "Show whether you are combat tagged and for how long.", "/combatlogs"), null, runCommand("/combatlogs"), suggestCommand("/combatlogs", "§7Click to paste: /combatlogs")));
        return entries;
    }

    private List<DebugEntry> listenerEntries() {
        List<DebugEntry> entries = new ArrayList<>();
        entries.add(entry(Material.NOTE_BLOCK, "§bChatListener", List.of("§7Chat formatting and staff chat routing."), infoAction("ChatListener"), null));
        entries.add(entry(Material.NAME_TAG, "§aJoinListener", List.of("§7Join MOTD and offline rank sync."), infoAction("JoinListener"), null));
        entries.add(entry(Material.ENDER_PEARL, "§bWorldChangeListener", List.of("§7Hub/SMP world transitions, respawn rules, and return-location capture."), infoAction("WorldChangeListener"), null));
        entries.add(entry(Material.BOOKSHELF, "§eMenuListener", List.of("§7Inventory routing and menu clicks."), infoAction("MenuListener"), null));
        entries.add(entry(Material.COMMAND_BLOCK, "§dHotbarNavigatorListener", List.of("§7Hotbar compass and cosmetics slot."), infoAction("HotbarNavigatorListener"), null));
        entries.add(entry(Material.SHIELD, "§cHubProtectionListener", List.of("§7Hub damage, hunger, and build protection."), infoAction("HubProtectionListener"), null));
        entries.add(entry(Material.IRON_TRAPDOOR, "§fModerationListener", List.of("§7Freeze, vanish, mute, and slowmode enforcement."), infoAction("ModerationListener"), null));
        entries.add(entry(Material.PAPER, "§6CommandTrackingListener", List.of("§7Counts command usage for stats."), infoAction("CommandTrackingListener"), null));
        entries.add(entry(Material.IRON_SWORD, "§cCombatTagListener", List.of("§7Combat tag on PvP hit; clears on death; gates TP commands."), infoAction("CombatTagListener"), null));
        entries.add(entry(Material.REPEATER, "§aStatsTrackingListener", List.of("§7Tracks kills, deaths, mobs, bosses, and periodic playtime flush."), infoAction("StatsTrackingListener"), null));
        entries.add(entry(Material.CHAIN_COMMAND_BLOCK, "§dEventModeChatListener", List.of("§7Routes chat to §fChatGameManager§7 when a chat round is live."), infoAction("EventModeChatListener"), null));
        entries.add(entry(Material.IRON_AXE, "§dEventModeCombatListener", List.of("§7Elimination event death/respawn and keep-inventory hooks."), infoAction("EventModeCombatListener"), null));
        entries.add(entry(MaterialCompat.resolve(Material.PAPER, "FIREWORK_ROCKET", "FIREWORK", "PAPER"), "§dToyboxListener", List.of("§7Handles right-click gadget use."), infoAction("ToyboxListener"), null));
        entries.add(entry(Material.NETHER_STAR, "§bServerListMotdListener", List.of("§7Server-list MOTD rendering."), infoAction("ServerListMotdListener"), null));
        return entries;
    }

    private List<DebugEntry> cosmeticEntries(Player viewer) {
        List<DebugEntry> entries = new ArrayList<>();
        for (CosmeticsManager.Cosmetic cosmetic : cosmetics.getByCategory(CosmeticsManager.Category.PARTICLES)) {
            entries.add(cosmeticEntry(cosmetic, viewer));
        }
        for (CosmeticsManager.Cosmetic cosmetic : cosmetics.getByCategory(CosmeticsManager.Category.TRAILS)) {
            entries.add(cosmeticEntry(cosmetic, viewer));
        }
        for (CosmeticsManager.Cosmetic cosmetic : cosmetics.getByCategory(CosmeticsManager.Category.GADGETS)) {
            entries.add(cosmeticEntry(cosmetic, viewer));
        }
        return entries;
    }

    private List<DebugEntry> previewEntries() {
        List<DebugEntry> entries = new ArrayList<>();
        entries.add(entry(Material.BLAZE_POWDER, "§6Preview Particles", List.of("§7Preview your current particle effect."), p -> {
            PlayerProfile prof = profiles.getOrCreate(p, ranks.getDefaultGroup());
            if (prof != null && prof.getEquippedParticles() != null) {
                cosmeticsEngine.previewParticle(p, prof.getEquippedParticles());
                if (feed != null) {
                    feed.recordPreview(p, "Particle preview", List.of("§7Particle key: §f" + prof.getEquippedParticles()));
                }
            } else {
                p.sendMessage("§7No particle equipped.");
            }
        }, null));
        entries.add(entry(Material.FEATHER, "§ePreview Trails", List.of("§7Preview your current trail effect."), p -> {
            PlayerProfile prof = profiles.getOrCreate(p, ranks.getDefaultGroup());
            if (prof != null && prof.getEquippedTrail() != null) {
                cosmeticsEngine.previewTrail(p, prof.getEquippedTrail());
                if (feed != null) {
                    feed.recordPreview(p, "Trail preview", List.of("§7Trail key: §f" + prof.getEquippedTrail()));
                }
            } else {
                p.sendMessage("§7No trail equipped.");
            }
        }, null));
        entries.add(entry(MaterialCompat.resolve(Material.PAPER, "FIREWORK_ROCKET", "FIREWORK", "PAPER"), "§dPreview Toybox", List.of("§7Trigger the active gadget without saving."), p -> {
            PlayerProfile prof = profiles.getOrCreate(p, ranks.getDefaultGroup());
            if (prof != null && prof.getEquippedGadget() != null) {
                toyboxManager.preview(p, prof.getEquippedGadget());
                if (feed != null) {
                    feed.recordPreview(p, "Toybox preview", List.of("§7Gadget key: §f" + prof.getEquippedGadget()));
                }
            } else {
                p.sendMessage("§7No gadget equipped.");
            }
        }, null));
        entries.add(entry(MaterialCompat.resolve(Material.COMPASS, "SPYGLASS", "COMPASS"), "§fPreview Mode", List.of("§7Toggle preview-only behavior for cosmetics."), p -> {
            boolean enabled = debugState.togglePreviewMode(p.getUniqueId());
            p.sendMessage("§aPreview mode is now §e" + (enabled ? "ON" : "OFF") + "§a.");
            if (feed != null) {
                feed.recordPreview(p, "Preview mode toggled", List.of("§7Now: §f" + (enabled ? "ON" : "OFF")));
            }
        }, null));
        return entries;
    }

    private List<DebugEntry> eventEntries() {
        List<DebugEntry> entries = new ArrayList<>();
        if (feed == null) {
            entries.add(entry(Material.BARRIER, "§cFeed unavailable", List.of("§7The debug feed manager is not active."), null, null));
            return entries;
        }

        List<DebugFeedManager.DebugEvent> snapshot = feed.snapshot();
        if (snapshot.isEmpty()) {
            entries.add(entry(Material.BOOK, "§7No recent events", List.of("§7Play with commands, toys, or joins to populate this feed."), null, null));
            return entries;
        }

        for (DebugFeedManager.DebugEvent event : snapshot) {
            List<String> lore = new ArrayList<>();
            lore.add("§8" + feed.formatTime(event.timestamp) + "  \u00b7  " + categoryColor(event.category) + event.category.name().toLowerCase(Locale.ROOT));
            lore.add(" ");
            lore.addAll(event.details);
            lore.add("§7Click to view in chat.");
            entries.add(entry(event.icon == null ? Material.PAPER : event.icon, event.title, lore, player -> {
                player.sendMessage("§d§lDebug Event§7: §f" + event.title);
                player.sendMessage("§7Category: §f" + event.category.name().toLowerCase(Locale.ROOT));
                player.sendMessage("§7Time: §f" + feed.formatTime(event.timestamp));
                for (String line : event.details) {
                    player.sendMessage(line);
                }
            }, null));
        }
        return entries;
    }

    private List<DebugEntry> filteredEventEntries(Player viewer) {
        if (feed == null) {
            return List.of(entry(Material.BARRIER, "§cFeed unavailable", List.of("§7The debug feed manager is not active."), null, null));
        }

        DebugFeedManager.Category filter = debugState.getFeedFilter(viewer.getUniqueId());
        List<DebugEntry> entries = new ArrayList<>();
        for (DebugFeedManager.DebugEvent event : feed.snapshot()) {
            if (filter != null && event.category != filter) continue;
            List<String> lore = new ArrayList<>();
            lore.add("§8" + feed.formatTime(event.timestamp) + "  \u00b7  " + categoryColor(event.category) + event.category.name().toLowerCase(Locale.ROOT));
            lore.add(" ");
            lore.addAll(event.details);
            lore.add("§7Click to view in chat.");
            entries.add(entry(event.icon == null ? Material.PAPER : event.icon, event.title, lore, player -> {
                player.sendMessage("§d§lDebug Event§7: §f" + event.title);
                player.sendMessage("§7Category: §f" + event.category.name().toLowerCase(Locale.ROOT));
                player.sendMessage("§7Time: §f" + feed.formatTime(event.timestamp));
                for (String line : event.details) {
                    player.sendMessage(line);
                }
            }, null));
        }

        if (entries.isEmpty()) {
            String filterName = filter == null ? "all" : filter.name().toLowerCase(Locale.ROOT);
            entries.add(entry(Material.BOOK, "§7No matching events", List.of("§7Filter: §f" + filterName, "§7Trigger some commands or actions to populate this feed."), null, null));
        }
        return entries;
    }

    private static String categoryColor(DebugFeedManager.Category cat) {
        return switch (cat) {
            case SYSTEM     -> "§b";
            case COMMAND    -> "§e";
            case LISTENER   -> "§6";
            case JOIN       -> "§a";
            case MODERATION -> "§c";
            case COSMETIC, GADGET -> "§d";
            case PREVIEW    -> "§7";
            case EVENT      -> "§5";
        };
    }

    /** @deprecated Delegates to systemHealthEntries(). */
    @SuppressWarnings("unused")
    private List<DebugEntry> healthEntries() { return systemHealthEntries(); }

    private final class DebugFeedMenu extends BaseMenu {
        private final int[] slots = {19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
        private int page = 0;

        private DebugFeedMenu() {
            super(DebugMenu.this.plugin, "§5§lLive Debug Feed", 54);
        }

        @Override
        protected void populate(Player viewer) {
            Inventory inv = getInventory();
            fill(inv, Material.GRAY_STAINED_GLASS_PANE, " ");

            DebugFeedManager.Category filter = debugState.getFeedFilter(viewer.getUniqueId());
            List<DebugEntry> entries = filteredEventEntries(viewer);
            int start = page * slots.length;
            for (int i = 0; i < slots.length; i++) {
                int index = start + i;
                if (index >= entries.size()) break;
                DebugEntry entry = entries.get(index);
                List<String> lore = new ArrayList<>(entry.lore);
                lore.add(" ");
                if (entry.leftAction != null) lore.add("§7Left click for details.");
                if (entry.rightAction != null) lore.add("§7Right click to run it.");
                if (entry.shiftAction != null) lore.add("§7Shift click for a secondary action.");
                if (entry.shiftRightAction != null) lore.add("§7Shift-right click to paste a template.");
                inv.setItem(slots[i], new ItemBuilder(entry.icon).name(entry.title).lore(lore).build());
            }

            inv.setItem(4, new ItemBuilder(Material.CLOCK)
                    .name("§d§lLive Feed")
                    .lore(List.of(
                            "§7Filter: §f" + (filter == null ? "all" : filter.name().toLowerCase(Locale.ROOT)),
                            "§7Entries: §f" + entries.size(),
                            "§8Use the chips below to refine what you see."
                    ))
                    .glow(true)
                    .build());

            inv.setItem(9, filterButton(Material.BOOK, "§fAll", null, filter));
            inv.setItem(10, filterButton(Material.COMMAND_BLOCK, "§bSystem", DebugFeedManager.Category.SYSTEM, filter));
            inv.setItem(11, filterButton(MaterialCompat.resolve(Material.BOOK, "WRITABLE_BOOK", "BOOK_AND_QUILL", "BOOK"), "§eCommands", DebugFeedManager.Category.COMMAND, filter));
            inv.setItem(12, filterButton(Material.LEVER, "§6Listeners", DebugFeedManager.Category.LISTENER, filter));
            inv.setItem(13, filterButton(Material.NAME_TAG, "§aJoin", DebugFeedManager.Category.JOIN, filter));
            inv.setItem(14, filterButton(Material.IRON_SWORD, "§cModeration", DebugFeedManager.Category.MODERATION, filter));
            inv.setItem(15, filterButton(Material.BLAZE_POWDER, "§dCosmetics", DebugFeedManager.Category.COSMETIC, filter));
            inv.setItem(16, filterButton(MaterialCompat.resolve(Material.PAPER, "FIREWORK_ROCKET", "FIREWORK", "PAPER"), "§dGadgets", DebugFeedManager.Category.GADGET, filter));
            inv.setItem(17, filterButton(MaterialCompat.resolve(Material.COMPASS, "SPYGLASS", "COMPASS"), "§fPreview", DebugFeedManager.Category.PREVIEW, filter));

            if (page > 0) {
                inv.setItem(45, new ItemBuilder(Material.ARROW).name("§ePrevious Page").lore(List.of("§7Go back one page.")).build());
            }
            if (page < pageCount(entries.size()) - 1) {
                inv.setItem(53, new ItemBuilder(Material.ARROW).name("§aNext Page").lore(List.of("§7Go forward one page.")).build());
            }
            inv.setItem(49, new ItemBuilder(Material.BARRIER).name("§cBack").lore(List.of("§7Return to the debug cockpit.")).build());
            inv.setItem(46, new ItemBuilder(Material.TNT).name("§cClear Feed").lore(List.of("§7Remove all recorded events from memory.")).build());
        }

        @Override
        public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
            if (slot == 45 && page > 0) {
                page--;
                populate(who);
                who.updateInventory();
                return true;
            }
            if (slot == 53) {
                int maxPage = pageCount(filteredEventEntries(who).size());
                if (page < maxPage - 1) {
                    page++;
                    populate(who);
                    who.updateInventory();
                }
                return true;
            }
            if (slot == 46) {
                if (DebugMenu.this.feed != null) DebugMenu.this.feed.clear();
                who.sendMessage("§aDebug feed cleared.");
                page = 0;
                populate(who);
                who.updateInventory();
                return true;
            }
            if (slot == 49) {
                DebugMenu.this.open(who);
                return true;
            }

            DebugFeedManager.Category selected = null;
            if (slot == 10) selected = DebugFeedManager.Category.SYSTEM;
            else if (slot == 11) selected = DebugFeedManager.Category.COMMAND;
            else if (slot == 12) selected = DebugFeedManager.Category.LISTENER;
            else if (slot == 13) selected = DebugFeedManager.Category.JOIN;
            else if (slot == 14) selected = DebugFeedManager.Category.MODERATION;
            else if (slot == 15) selected = DebugFeedManager.Category.COSMETIC;
            else if (slot == 16) selected = DebugFeedManager.Category.GADGET;
            else if (slot == 17) selected = DebugFeedManager.Category.PREVIEW;

            if (slot == 9 || (slot >= 10 && slot <= 17)) {
                debugState.setFeedFilter(who.getUniqueId(), slot == 9 ? null : selected);
                page = 0;
                populate(who);
                who.updateInventory();
                return true;
            }

            List<DebugEntry> entries = filteredEventEntries(who);
            int index = -1;
            for (int i = 0; i < slots.length; i++) {
                if (slots[i] == slot) {
                    index = page * slots.length + i;
                    break;
                }
            }
            if (index >= 0 && index < entries.size()) {
                DebugEntry entry = entries.get(index);
                if (rightClick && shiftClick && entry.shiftRightAction != null) entry.shiftRightAction.accept(who);
                else if (rightClick && entry.rightAction != null) entry.rightAction.accept(who);
                else if (shiftClick && entry.shiftAction != null) entry.shiftAction.accept(who);
                else if (entry.leftAction != null) entry.leftAction.accept(who);
                return true;
            }
            return true;
        }

        private ItemStack filterButton(Material material, String name, DebugFeedManager.Category category, DebugFeedManager.Category current) {
            boolean selected = (category == null && current == null) || (category != null && category == current);
            List<String> lore = selected
                    ? List.of("§aSelected", "§7Click to change the live feed filter.")
                    : List.of("§7Click to filter the live feed.");
            return new ItemBuilder(material).name(name).lore(lore).glow(selected).build();
        }

        private int pageCount(int total) {
            return Math.max(1, (int) Math.ceil(total / (double) slots.length));
        }
    }

    private List<DebugEntry> actionEntries() {
        List<DebugEntry> entries = new ArrayList<>();
        entries.add(entry(Material.ANVIL, "§bReload Core", List.of("§7Run the full config/cache reload."), p -> {
            plugin.reloadCore();
            if (feed != null) {
                feed.recordSystem("Core reloaded from action page", List.of("§7Triggered by §f" + p.getName() + "§7."));
            }
        }, null));
        entries.add(entry(Material.REDSTONE_TORCH, "§aRefresh Toybox", List.of("§7Re-give the active gadget item."), p -> {
            toyboxManager.refresh(p);
            if (feed != null) {
                feed.recordGadget(p, "Toybox refreshed", List.of("§7Active gadget item was re-issued."));
            }
        }, null));
        entries.add(entry(Material.COMPASS, "§eTeleport Spawn", List.of("§7Go to the configured spawn."), p -> spawnManager.teleportToSpawn(p), null));
        entries.add(entry(Material.REDSTONE_BLOCK, "§cBroadcast Now", List.of("§7Fire the next broadcaster line."), p -> {
            broadcasterManager.broadcastNow();
            if (feed != null) {
                feed.recordSystem("Broadcast fired", List.of("§7Triggered by §f" + p.getName() + "§7."));
            }
        }, null));
        entries.add(entry(Material.DRAGON_EGG, "§dBossBar Now", List.of("§7Rotate the bossbar immediately."), p -> {
            bossBarManager.rotateNow();
            if (feed != null) {
                feed.recordSystem("Bossbar rotated", List.of("§7Current title: §f" + bossBarManager.getCurrentTitle()));
            }
        }, null));
        entries.add(entry(Material.PAPER, "§fFlush Profiles", List.of("§7Save all cached player profiles."), p -> {
            profiles.flushAll();
            if (feed != null) {
                feed.recordSystem("Profiles flushed", List.of("§7Triggered by §f" + p.getName() + "§7."));
            }
        }, null));
        entries.add(entry(Material.BARRIER, "§cDisable Preview", List.of("§7Turn preview mode off for this player."), p -> {
            if (debugState.isPreviewMode(p.getUniqueId())) {
                debugState.togglePreviewMode(p.getUniqueId());
            }
            if (feed != null) {
                feed.recordPreview(p, "Preview mode cleared", List.of("§7Debug session preview state disabled."));
            }
        }, null));
        return entries;
    }

    private List<DebugEntry> spawnEntries() {
        List<DebugEntry> entries = new ArrayList<>();
        entries.add(entry(Material.COMPASS, "§aTeleport to Spawn", List.of("§7Send yourself to this world's spawn configuration."), p -> spawnManager.teleportToSpawn(p), null));
        entries.add(entry(Material.OAK_DOOR, "§eSet Spawn Here (This World)", List.of("§7Save your current location as spawn for this world."), p -> {
            if (p.getWorld() == null) {
                p.sendMessage("§cWorld unavailable.");
                return;
            }
            spawnManager.setSpawnForWorld(p.getWorld().getName(), p.getLocation());
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("teleport.spawn.set-message", "&aSpawn set at your location!")) + " §7(" + p.getWorld().getName() + ")");
        }, null));
        entries.add(entry(Material.PAPER, "§fShow Spawn (This World)", List.of("§7Print this world's configured spawn to chat."), p -> {
            if (p.getWorld() == null) {
                p.sendMessage("§cWorld unavailable.");
                return;
            }
            var loc = spawnManager.getSpawnForWorld(p.getWorld().getName());
            p.sendMessage("§7Configured spawn: §f" + (loc == null ? "none" : loc.getWorld().getName() + " " + Math.round(loc.getX()) + ", " + Math.round(loc.getY()) + ", " + Math.round(loc.getZ())));
        }, null));
        return entries;
    }

    private DebugEntry cosmeticEntry(CosmeticsManager.Cosmetic cosmetic, Player viewer) {
        String type = switch (cosmetic.category) {
            case PARTICLES -> "Particle";
            case TRAILS -> "Trail";
            case GADGETS -> "Gadget";
            case TAGS -> "Tag";
        };
        List<String> lore = new ArrayList<>();
        if (cosmetic.lore != null) lore.addAll(cosmetic.lore);
        lore.add(" ");
        lore.add("§7Type: §f" + type);
        lore.add("§7Price: §6" + cosmetic.price + " coins");
        lore.add("§7Left click: §fpreview (temporary)");
        lore.add("§7Shift-left: §fequip and save");
        return entry(cosmetic.icon, cosmetic.name, lore, player -> previewOrEquip(player, cosmetic, false), player -> previewOrEquip(player, cosmetic, true));
    }

    private void previewOrEquip(Player player, CosmeticsManager.Cosmetic cosmetic, boolean forceEquip) {
        if (cosmetic.category == CosmeticsManager.Category.PARTICLES) {
            if (forceEquip || !debugState.isPreviewMode(player.getUniqueId())) {
                equipParticle(player, cosmetic);
            } else {
                cosmeticsEngine.previewParticle(player, cosmetic.key);
                if (feed != null) {
                    feed.recordPreview(player, "Particle preview", List.of("§7Particle key: §f" + cosmetic.key));
                }
            }
            return;
        }
        if (cosmetic.category == CosmeticsManager.Category.TRAILS) {
            if (forceEquip || !debugState.isPreviewMode(player.getUniqueId())) {
                equipTrail(player, cosmetic);
            } else {
                cosmeticsEngine.previewTrail(player, cosmetic.key);
                if (feed != null) {
                    feed.recordPreview(player, "Trail preview", List.of("§7Trail key: §f" + cosmetic.key));
                }
            }
            return;
        }
        if (cosmetic.category == CosmeticsManager.Category.TAGS) {
            equipTag(player, cosmetic);
            return;
        }
        if (forceEquip || !debugState.isPreviewMode(player.getUniqueId())) {
            equipGadget(player, cosmetic);
        } else {
            toyboxManager.preview(player, cosmetic.key);
            if (feed != null) {
                feed.recordPreview(player, "Toybox preview", List.of("§7Gadget key: §f" + cosmetic.key));
            }
        }
    }

    private void equipParticle(Player player, CosmeticsManager.Cosmetic cosmetic) {
        PlayerProfile prof = profiles.getOrCreate(player, ranks.getDefaultGroup());
        if (prof == null) return;
        prof.setEquippedParticles(cosmetic.key);
        prof.setParticleActivatedAt(System.currentTimeMillis());
        profiles.save(player.getUniqueId());
        player.sendMessage("§aEquipped preview particle: §e" + cosmetic.name);
        if (feed != null) {
            feed.recordCosmetic(player, "Equipped particle", List.of("§7Key: §f" + cosmetic.key, "§7Name: §f" + cosmetic.name));
        }
    }

    private void equipTrail(Player player, CosmeticsManager.Cosmetic cosmetic) {
        PlayerProfile prof = profiles.getOrCreate(player, ranks.getDefaultGroup());
        if (prof == null) return;
        prof.setEquippedTrail(cosmetic.key);
        prof.setTrailActivatedAt(System.currentTimeMillis());
        profiles.save(player.getUniqueId());
        player.sendMessage("§aEquipped preview trail: §e" + cosmetic.name);
        if (feed != null) {
            feed.recordCosmetic(player, "Equipped trail", List.of("§7Key: §f" + cosmetic.key, "§7Name: §f" + cosmetic.name));
        }
    }

    private void equipGadget(Player player, CosmeticsManager.Cosmetic cosmetic) {
        PlayerProfile prof = profiles.getOrCreate(player, ranks.getDefaultGroup());
        if (prof == null) return;
        prof.setEquippedGadget(cosmetic.key);
        profiles.save(player.getUniqueId());
        toyboxManager.refresh(player);
        player.sendMessage("§aEquipped preview gadget: §e" + cosmetic.name);
        if (feed != null) {
            feed.recordGadget(player, "Equipped gadget", List.of("§7Key: §f" + cosmetic.key, "§7Name: §f" + cosmetic.name));
        }
    }

    private void equipTag(Player player, CosmeticsManager.Cosmetic cosmetic) {
        PlayerProfile prof = profiles.getOrCreate(player, ranks.getDefaultGroup());
        if (prof == null) return;
        prof.setActiveTag(cosmetic.key);
        profiles.save(player.getUniqueId());
        plugin.refreshPlayerPresentation(player);
        player.sendMessage("§aEquipped preview tag: §e" + cosmetic.name);
        if (feed != null) {
            feed.recordCosmetic(player, "Equipped tag", List.of("§7Key: §f" + cosmetic.key, "§7Name: §f" + cosmetic.name));
        }
    }

    private Consumer<Player> infoAction(String name) {
        return player -> player.sendMessage("§7Listener: §f" + name + " §8- registered and active if the plugin is enabled.");
    }

    private Consumer<Player> commandInfo(String command, String whatItDoes, String usage) {
        return player -> {
            player.sendMessage("§d§lCommand§7: §f" + command);
            player.sendMessage("§7What it does: §f" + whatItDoes);
            player.sendMessage("§7Usage: §f" + usage);
            player.sendMessage("§8Left click explains it, right click runs it, shift-right click pastes a template.");
        };
    }

    private Consumer<Player> suggestCommand(String command, String prompt) {
        return player -> player.sendMessage(Component.text(prompt).clickEvent(ClickEvent.suggestCommand(command)));
    }

    private Consumer<Player> runCommand(String command) {
        return player -> {
            String raw = command.startsWith("/") ? command.substring(1) : command;
            player.performCommand(raw);
        };
    }

    private boolean isAllowed(Player player) {
        return devMode != null && devMode.isAllowed(player.getUniqueId()) && devMode.isActive(player.getUniqueId());
    }

    private DebugEntry entry(Material icon, String name, List<String> lore, Consumer<Player> left, Consumer<Player> shift) {
        return new DebugEntry(icon, name, lore, left, shift, null, null);
    }

    private DebugEntry entry(Material icon, String name, List<String> lore, Consumer<Player> left, Consumer<Player> shift, Consumer<Player> right, Consumer<Player> shiftRight) {
        return new DebugEntry(icon, name, lore, left, shift, right, shiftRight);
    }

    private ItemStack button(Material icon, String name, List<String> lore) {
        return new ItemBuilder(icon).name(name).lore(lore).glow(true).build();
    }

    private void fill(Inventory inv, Material material, String name) {
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, new ItemBuilder(material).name(name).build());
        }
    }

    private static final class DebugEntry {
        final Material icon;
        final String title;
        final List<String> lore;
        final Consumer<Player> leftAction;
        final Consumer<Player> shiftAction;
        final Consumer<Player> rightAction;
        final Consumer<Player> shiftRightAction;

        DebugEntry(Material icon, String title, List<String> lore, Consumer<Player> leftAction, Consumer<Player> shiftAction, Consumer<Player> rightAction, Consumer<Player> shiftRightAction) {
            this.icon = icon;
            this.title = title;
            this.lore = lore == null ? List.of() : List.copyOf(lore);
            this.leftAction = leftAction;
            this.shiftAction = shiftAction;
            this.rightAction = rightAction;
            this.shiftRightAction = shiftRightAction;
        }
    }

    private final class DebugListMenu extends BaseMenu {
        private final List<DebugEntry> entries;
        private int page = 0;

        private DebugListMenu(String title, List<DebugEntry> entries) {
            super(DebugMenu.this.plugin, title, 54);
            this.entries = Objects.requireNonNull(entries);
        }

        @Override
        protected void populate(Player viewer) {
            Inventory inv = getInventory();
            fill(inv, Material.GRAY_STAINED_GLASS_PANE, " ");
            int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
            int start = page * slots.length;
            for (int i = 0; i < slots.length; i++) {
                int index = start + i;
                if (index >= entries.size()) break;
                DebugEntry entry = entries.get(index);
                List<String> lore = new ArrayList<>(entry.lore);
                lore.add(" ");
                lore.add("§7Left click for info/preview.");
                lore.add("§7Shift-left for the persistent/equip action.");
                inv.setItem(slots[i], new ItemBuilder(entry.icon).name(entry.title).lore(lore).build());
            }

            inv.setItem(4, new ItemBuilder(Material.NETHER_STAR)
                    .name("§d§lPage " + (page + 1) + "§7/§f" + pageCount())
                    .lore(List.of("§7Browse the debug entries.", "§8Use arrows to page through."))
                    .glow(true)
                    .build());

            if (page > 0) {
                inv.setItem(45, new ItemBuilder(Material.ARROW).name("§ePrevious Page").lore(List.of("§7Go back one page.")).build());
            }
            if (page < pageCount() - 1) {
                inv.setItem(53, new ItemBuilder(Material.ARROW).name("§aNext Page").lore(List.of("§7Go forward one page.")).build());
            }
            inv.setItem(49, new ItemBuilder(Material.BARRIER).name("§cBack").lore(List.of("§7Return to the debug cockpit.")).build());
        }

        @Override
        public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
            if (slot == 45 && page > 0) {
                page--;
                populate(who);
                who.updateInventory();
                return true;
            }
            if (slot == 53 && page < pageCount() - 1) {
                page++;
                populate(who);
                who.updateInventory();
                return true;
            }
            if (slot == 49) {
                DebugMenu.this.open(who);
                return true;
            }
            int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
            int index = -1;
            for (int i = 0; i < slots.length; i++) {
                if (slots[i] == slot) {
                    index = page * slots.length + i;
                    break;
                }
            }
            if (index >= 0 && index < entries.size()) {
                DebugEntry entry = entries.get(index);
                Consumer<Player> action = shiftClick && entry.shiftAction != null ? entry.shiftAction : entry.leftAction;
                if (action != null) {
                    action.accept(who);
                } else {
                    who.sendMessage("§7" + ChatColor.stripColor(entry.title));
                }
                return true;
            }
            return true;
        }

        private int pageCount() {
            return Math.max(1, (int) Math.ceil(entries.size() / 21.0));
        }
    }
}

