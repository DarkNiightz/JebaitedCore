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
        super(plugin, "Â§5Â§lDev Debug Cockpit", 54);
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
                .name("Â§dÂ§lDev Cockpit")
                .lore(List.of(
                        "Â§7DB: " + (dbUp ? "Â§aOnline" : "Â§cOffline"),
                        "Â§7Players: Â§f" + online + "Â§8/Â§f" + maxPlayers,
                        "Â§7Event: Â§f" + (em != null && em.isActive() ? "Â§aRunning" : "Â§7None"),
                        "Â§7Feed: Â§f" + feedCount + " events"
                ))
                .glow(true)
                .build());

        inv.setItem(10, button(Material.COMPASS, "Â§bServer Status",
                List.of("Â§7System tools + health panel.", "Â§8Live actions: toybox, flush, broadcast, bossbar.", "Â§8Status: DB, deploy, stats, mcMMO.")));
        inv.setItem(12, button(Material.BLAZE_POWDER, "Â§dCosmetics",
                List.of("Â§7Preview and equip particles, trails, and gadgets.", "Â§8Left click previews, shift-left equips.")));
        inv.setItem(14, button(Material.CLOCK, "Â§aLive Feed",
                List.of("Â§7Browse runtime events in real time.", "Â§8Filter by category, click an entry for details.")));
        inv.setItem(16, button(Material.GRASS_BLOCK, "Â§aWorld Settings",
                List.of("Â§7Inspect and tune each loaded world.", "Â§8Weather, time, mobs, PVP, gamerules.")));

        inv.setItem(28, button(Material.ANVIL, "Â§cReload Core",
                List.of("Â§7Reload config, listeners, and caches.", "Â§8Same as /jreload.")));
        inv.setItem(30, button(Material.CHEST, "Â§6Backup & Tools",
                List.of("Â§7Flush profiles, refresh leaderboards.", "Â§8DB check, schedule restart, clear feed.")));
        inv.setItem(32, button(Material.ENDER_EYE, "Â§9Spawn Tools",
                List.of("Â§7Teleport to spawn, set spawn, reset it.", "Â§8Quick navigation checks.")));
        inv.setItem(34, button(Material.COMPARATOR, "Â§bDatabase Controls",
                List.of("Â§7Table counts and wipe operations.", "Â§cÂ§lShift-clickÂ§rÂ§8 required for destructive ops.")));

        inv.setItem(49, button(Material.BARRIER, "Â§cClose", List.of("Â§7Close the debug cockpit.")));
    }

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
        if (!isAllowed(who)) {
            who.sendMessage("Â§cEnable devmode first with /devmode on.");
            return true;
        }
        switch (slot) {
            case 10 -> openSystemHealth(who);
            case 12 -> openCosmetics(who);
            case 14 -> openEvents(who);
            case 16 -> openWorldSettings(who);
            case 28 -> {
                who.sendMessage("Â§7Reloading core...");
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
        openListMenu(player, "Â§bÂ§lDatabase Controls", databaseEntries());
    }

    private List<DebugEntry> databaseEntries() {
        List<DebugEntry> list = new ArrayList<>();

        // â”€â”€ Table counts â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        list.add(entry(
            Material.BOOK,
            "Â§eÂ§lTable Counts",
            List.of("Â§7Query row counts for all major tables.", "Â§8Results sent to your chat."),
            player -> {
                player.sendMessage("Â§b[DB] Â§7Querying table counts...");
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
                                results.add("Â§8" + table + " Â§7Â» Â§f" + rs.getLong(1));
                            } catch (java.sql.SQLException ignored) {
                                results.add("Â§8" + table + " Â§c(error)");
                            }
                        }
                    } catch (java.sql.SQLException e) {
                        Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage("Â§c[DB] Failed to connect: " + e.getMessage()));
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("Â§b[DB] Â§7Row counts:");
                        results.forEach(player::sendMessage);
                    });
                });
            },
            null
        ));

        // â”€â”€ Wipe audit logs â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        list.add(entry(
            Material.PAPER,
            "Â§6Wipe Audit Logs",
            List.of("Â§7Truncates Â§echat_logs Â§7and Â§eplayer_command_logÂ§7.",
                    "Â§8Left-click: preview row counts.",
                    "Â§cÂ§lShift-click: execute truncate."),
            player -> {
                player.sendMessage("Â§6[DB] Â§7Left-click a dangerous button shows this reminder.");
                player.sendMessage("Â§7Shift-click to truncate chat_logs and player_command_log.");
            },
            player -> {
                player.sendMessage("Â§6[DB] Â§7Wiping audit logs...");
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                         java.sql.Statement st = conn.createStatement()) {
                        st.execute("TRUNCATE TABLE chat_logs");
                        st.execute("TRUNCATE TABLE player_command_log");
                        Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage("Â§6[DB] Â§aAudit logs wiped."));
                    } catch (java.sql.SQLException e) {
                        Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage("Â§c[DB] Error: " + e.getMessage()));
                    }
                });
            }
        ));

        // â”€â”€ Wipe moderation history â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        list.add(entry(
            Material.BARRIER,
            "Â§cWipe Moderation History",
            List.of("Â§7Deletes all rows from Â§emoderation_historyÂ§7.",
                    "Â§8Active ban/mute state in moderation_state is Â§lNOTÂ§rÂ§8 affected.",
                    "Â§cÂ§lShift-click: execute delete."),
            player -> player.sendMessage("Â§c[DB] Â§7Shift-click to wipe moderation_history."),
            player -> {
                player.sendMessage("Â§c[DB] Â§7Wiping moderation history...");
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                         java.sql.Statement st = conn.createStatement()) {
                        int rows = st.executeUpdate("DELETE FROM moderation_history");
                        Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage("Â§c[DB] Â§a" + rows + " moderation history rows deleted."));
                    } catch (java.sql.SQLException e) {
                        Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage("Â§c[DB] Error: " + e.getMessage()));
                    }
                });
            }
        ));

        // â”€â”€ Reset achievements â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        list.add(entry(
            Material.GOLD_INGOT,
            "Â§eReset All Achievements",
            List.of("Â§7Truncates Â§eplayer_achievements Â§7and Â§eachievement_vouchersÂ§7.",
                    "Â§cÂ§lShift-click: execute truncate."),
            player -> player.sendMessage("Â§e[DB] Â§7Shift-click to reset all achievement progress."),
            player -> {
                player.sendMessage("Â§e[DB] Â§7Resetting achievements...");
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                         java.sql.Statement st = conn.createStatement()) {
                        st.execute("TRUNCATE TABLE achievement_vouchers");
                        st.execute("TRUNCATE TABLE player_achievements");
                        Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage("Â§e[DB] Â§aAchievements reset."));
                    } catch (java.sql.SQLException e) {
                        Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage("Â§c[DB] Error: " + e.getMessage()));
                    }
                });
            }
        ));

        // â”€â”€ Reset player stats â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        list.add(entry(
            Material.TNT,
            "Â§cReset All Player Stats",
            List.of("Â§7Truncates Â§eplayer_stats Â§7and Â§eplayer_event_statsÂ§7.",
                    "Â§7Player accounts (rank, balance, cosmetics) are preserved.",
                    "Â§cÂ§lShift-click: execute truncate."),
            player -> player.sendMessage("Â§c[DB] Â§7Shift-click to wipe all player stats."),
            player -> {
                player.sendMessage("Â§c[DB] Â§7Wiping player stats...");
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
                            player.sendMessage("Â§c[DB] Â§aPlayer stats reset."));
                    } catch (java.sql.SQLException e) {
                        Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage("Â§c[DB] Error: " + e.getMessage()));
                    }
                });
            }
        ));

        // â”€â”€ Full DB wipe â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        list.add(entry(
            Material.BEDROCK,
            "Â§4Â§lâ˜¢ Full Database Wipe",
            List.of("Â§7Drops and recreates the public schema.",
                    "Â§cAll data will be lost permanently.",
                    "Â§4Only available when you are the sole online player.",
                    "Â§cÂ§lShift-click: execute if safe."),
            player -> player.sendMessage("Â§4[DB] Â§cShift-click to wipe the entire database. Â§lThis cannot be undone."),
            player -> {
                long otherPlayers = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
                    .count();
                if (otherPlayers > 0) {
                    player.sendMessage("Â§4[DB] Â§cAborted â€” Â§f" + otherPlayers + "Â§c other player(s) are online. Clear the server first.");
                    return;
                }
                player.sendMessage("Â§4[DB] Â§7Wiping entire database. Server will restart after.");
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                         java.sql.Statement st = conn.createStatement()) {
                        st.execute("DROP SCHEMA public CASCADE");
                        st.execute("CREATE SCHEMA public");
                        st.execute("GRANT ALL ON SCHEMA public TO postgres");
                        st.execute("GRANT ALL ON SCHEMA public TO public");
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage("Â§4[DB] Â§aSchema wiped. Restarting server...");
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "restart");
                        });
                    } catch (java.sql.SQLException e) {
                        Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage("Â§c[DB] Error: " + e.getMessage()));
                    }
                });
            }
        ));

        return list;
    }

    public void openSystemHealth(Player player) {
        openListMenu(player, "Â§5Â§lServer Status", systemHealthEntries());
    }

    // Legacy / redirect stubs
    public void openSystem(Player player)    { openSystemHealth(player); }
    public void openHealth(Player player)    { openSystemHealth(player); }
    public void openCommands(Player player)  { openSystemHealth(player); }
    public void openListeners(Player player) { openSystemHealth(player); }
    public void openPreview(Player player)   { openCosmetics(player); }
    public void openActions(Player player)   { openBackupTools(player); }

    public void openCosmetics(Player player) {
        openListMenu(player, "Â§5Â§lCosmetics", cosmeticEntries(player));
    }

    public void openEvents(Player player) {
        new DebugFeedMenu().open(player);
    }

    public void openWorldSettings(Player player) {
        openListMenu(player, "Â§5Â§lWorld Settings", worldEntries());
    }

    private void openBackupTools(Player player) {
        openListMenu(player, "Â§5Â§lBackup & Tools", backupToolsEntries());
    }

    private List<DebugEntry> worldEntries() {
        List<DebugEntry> entries = new ArrayList<>();
        var wm = plugin.getWorldManager();
        String hubName = wm == null ? "" : wm.getHubWorldName();
        String smpName = wm == null ? "" : wm.getSmpWorldName();
        for (World world : Bukkit.getWorlds()) {
            String name = world.getName();
            String roleTag = name.equalsIgnoreCase(hubName) ? "Â§b[Hub] " :
                             name.equalsIgnoreCase(smpName) ? "Â§a[SMP] " : "Â§d[Extra] ";
            List<String> lore = new ArrayList<>();
            lore.add("Â§7Role: Â§f" + roleTag.trim());
            lore.add("Â§7Players: Â§f" + world.getPlayers().size());
            lore.add("Â§7Environment: Â§f" + world.getEnvironment().name());
            lore.add("Â§7Weather: " + weatherState(world));
            lore.add("Â§7Time: Â§f" + timePreset(world.getTime()));
            lore.add("Â§7Mobs: " + (world.getAllowMonsters() ? "Â§aON" : "Â§cOFF") + " Â§8| Â§7Animals: " + (world.getAllowAnimals() ? "Â§aON" : "Â§cOFF"));
            lore.add("Â§7PVP: " + (world.getPVP() ? "Â§aON" : "Â§cOFF"));
            lore.add("Â§8Click to open the live settings editor.");
            entries.add(entry(worldIcon(world), roleTag + "Â§b" + name, lore, p -> openWorldSettingsFor(p, world), null));
        }
        if (entries.isEmpty()) {
            entries.add(entry(Material.BARRIER, "Â§cNo worlds loaded", List.of("Â§7There are no editable worlds right now."), null, null));
        }
        return entries;
    }

    private void openWorldSettingsFor(Player player, World world) {
        List<DebugEntry> entries = new ArrayList<>();
        entries.add(entry(Material.COMPASS, "Â§aTeleport to World Spawn", List.of("Â§7Jump to the current spawn for this world."), p -> p.teleport(world.getSpawnLocation()), null));
        entries.add(entry(Material.GRASS_BLOCK, "Â§bApply Hub Preset", List.of("Â§7Daytime, clear weather, no mobs, no PVP.", "Â§8Great for lobby or spawn worlds."), p -> {
            world.setTime(1000L);
            world.setStorm(false);
            world.setThundering(false);
            world.setClearWeatherDuration(20 * 60 * 20);
            world.setPVP(false);
            world.setSpawnFlags(false, true);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.MOB_GRIEFING, false);
            p.sendMessage("Â§aUpdated Â§f" + world.getName() + "Â§a with the hub preset.");
            openWorldSettingsFor(p, world);
        }, null));
        entries.add(entry(Material.ZOMBIE_HEAD, "Â§eToggle Mob Spawning", List.of("Â§7Currently: " + (world.getAllowMonsters() ? "Â§aON" : "Â§cOFF")), p -> {
            world.setSpawnFlags(!world.getAllowMonsters(), world.getAllowAnimals());
            p.sendMessage("Â§aMob spawning in Â§f" + world.getName() + " Â§ais now " + (world.getAllowMonsters() ? "Â§aON" : "Â§cOFF") + "Â§a.");
            openWorldSettingsFor(p, world);
        }, null));
        entries.add(entry(Material.SHEEP_SPAWN_EGG, "Â§eToggle Animal Spawning", List.of("Â§7Currently: " + (world.getAllowAnimals() ? "Â§aON" : "Â§cOFF")), p -> {
            world.setSpawnFlags(world.getAllowMonsters(), !world.getAllowAnimals());
            p.sendMessage("Â§aAnimal spawning in Â§f" + world.getName() + " Â§ais now " + (world.getAllowAnimals() ? "Â§aON" : "Â§cOFF") + "Â§a.");
            openWorldSettingsFor(p, world);
        }, null));
        entries.add(entry(Material.CLOCK, "Â§6Cycle Time Preset", List.of("Â§7Current: Â§f" + timePreset(world.getTime()), "Â§8Click cycles day â†’ noon â†’ sunset â†’ night."), p -> {
            long current = world.getTime() % 24000L;
            long next = current < 6000L ? 6000L : current < 12000L ? 12000L : current < 13000L ? 13000L : 1000L;
            world.setTime(next);
            p.sendMessage("Â§aTime in Â§f" + world.getName() + " Â§aset to Â§f" + timePreset(next) + "Â§a.");
            openWorldSettingsFor(p, world);
        }, null));
        entries.add(entry(Material.SUNFLOWER, "Â§bSet Clear Weather", List.of("Â§7Force this world back to clear skies."), p -> {
            world.setStorm(false);
            world.setThundering(false);
            world.setClearWeatherDuration(20 * 60 * 20);
            p.sendMessage("Â§aWeather in Â§f" + world.getName() + " Â§ais now CLEAR.");
            openWorldSettingsFor(p, world);
        }, null));
        entries.add(entry(Material.WATER_BUCKET, "Â§9Set Rain", List.of("Â§7Turn on rain for this world."), p -> {
            world.setStorm(true);
            world.setThundering(false);
            p.sendMessage("Â§aWeather in Â§f" + world.getName() + " Â§ais now RAIN.");
            openWorldSettingsFor(p, world);
        }, null));
        entries.add(entry(Material.TRIDENT, "Â§5Set Thunder", List.of("Â§7Turn on thunder for this world."), p -> {
            world.setStorm(true);
            world.setThundering(true);
            p.sendMessage("Â§aWeather in Â§f" + world.getName() + " Â§ais now THUNDER.");
            openWorldSettingsFor(p, world);
        }, null));
        entries.add(entry(Material.IRON_SWORD, "Â§cToggle PVP", List.of("Â§7Currently: " + (world.getPVP() ? "Â§aON" : "Â§cOFF")), p -> {
            world.setPVP(!world.getPVP());
            p.sendMessage("Â§aPVP in Â§f" + world.getName() + " Â§ais now " + (world.getPVP() ? "Â§aON" : "Â§cOFF") + "Â§a.");
            openWorldSettingsFor(p, world);
        }, null));
        entries.add(entry(Material.DAYLIGHT_DETECTOR, "Â§fToggle Daylight Cycle", List.of("Â§7Currently: " + (Boolean.TRUE.equals(world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE)) ? "Â§aON" : "Â§cOFF")), p -> {
            boolean next = !Boolean.TRUE.equals(world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE));
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, next);
            p.sendMessage("Â§aDaylight cycle in Â§f" + world.getName() + " Â§ais now " + (next ? "Â§aON" : "Â§cOFF") + "Â§a.");
            openWorldSettingsFor(p, world);
        }, null));
        entries.add(entry(Material.CREEPER_HEAD, "Â§fToggle Mob Griefing", List.of("Â§7Currently: " + (Boolean.TRUE.equals(world.getGameRuleValue(GameRule.MOB_GRIEFING)) ? "Â§aON" : "Â§cOFF")), p -> {
            boolean next = !Boolean.TRUE.equals(world.getGameRuleValue(GameRule.MOB_GRIEFING));
            world.setGameRule(GameRule.MOB_GRIEFING, next);
            p.sendMessage("Â§aMob griefing in Â§f" + world.getName() + " Â§ais now " + (next ? "Â§aON" : "Â§cOFF") + "Â§a.");
            openWorldSettingsFor(p, world);
        }, null));
        openListMenu(player, "Â§5Â§lWorld â€¢ " + world.getName(), entries);
    }

    private Material worldIcon(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            default -> Material.GRASS_BLOCK;
        };
    }

    private String weatherState(World world) {
        if (world.isThundering()) return "Â§5THUNDER";
        if (world.hasStorm()) return "Â§9RAIN";
        return "Â§aCLEAR";
    }

    private String timePreset(long ticks) {
        long normalized = ((ticks % 24000L) + 24000L) % 24000L;
        if (normalized < 3000L) return "Sunrise";
        if (normalized < 9000L) return "Day";
        if (normalized < 13000L) return "Sunset";
        return "Night";
    }

    private void openSpawnTools(Player player) {
        openListMenu(player, "Â§5Â§lSpawn Tools", spawnEntries());
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
        entries.add(entry(Material.GRAY_STAINED_GLASS_PANE, "Â§8Â§lâ–Œ System Tools",
                List.of("Â§7Live actions."), null, null));
        entries.add(entry(Material.MAP, "Â§bWorld Routing",
                List.of("Â§7Hub: Â§f" + (wm == null ? "unknown" : wm.getHubWorldName()),
                        "Â§7SMP: Â§f" + (wm == null ? "unknown" : wm.getSmpWorldName()),
                        "Â§7SMP loaded: " + ((wm != null && Bukkit.getWorld(wm.getSmpWorldName()) != null) ? "Â§aYES" : "Â§cNO")),
                p -> {
                    if (wm == null) { p.sendMessage("Â§cWorldManager unavailable."); return; }
                    p.sendMessage("Â§7Hub: Â§f" + wm.getHubWorldName());
                    p.sendMessage("Â§7SMP: Â§f" + wm.getSmpWorldName() + "  loaded: " + (Bukkit.getWorld(wm.getSmpWorldName()) != null ? "Â§aYES" : "Â§cNO"));
                }, null));
        entries.add(entry(Material.NETHER_STAR, "Â§dEvent Runtime",
                List.of("Â§7Active: " + (em != null && em.isActive() ? "Â§aYES" : "Â§cNO"),
                        "Â§7" + (em == null ? "unavailable" : ChatColor.stripColor(em.getStatusLine()))),
                p -> {
                    if (em == null) { p.sendMessage("Â§cEvent manager unavailable."); return; }
                    p.sendMessage(em.getStatusLine());
                    p.sendMessage("Â§7Events: Â§f" + String.join(", ", em.getConfiguredEventDisplayNames()));
                }, null));
        entries.add(entry(Material.LIME_DYE, "Â§aRefresh Toybox",
                List.of("Â§7Re-issue the active gadget item to your hotbar."),
                p -> {
                    toyboxManager.refresh(p);
                    p.sendMessage("Â§aToybox refreshed.");
                    if (feed != null) feed.recordGadget(p, "Toybox refreshed", List.of("Â§7Re-issued."));
                }, null));
        entries.add(entry(Material.PAPER, "Â§fFlush Profiles",
                List.of("Â§7Write all dirty cached profiles to the database now."),
                p -> {
                    profiles.flushAll();
                    p.sendMessage("Â§aProfiles flushed.");
                    if (feed != null) feed.recordSystem("Profiles flushed", List.of("Â§7By Â§f" + p.getName()));
                }, null));
        entries.add(entry(Material.COMPASS, "Â§eSpawn Teleport",
                List.of("Â§7Teleport to the configured spawn."),
                p -> {
                    boolean moved = spawnManager.teleportToSpawn(p);
                    if (feed != null) feed.recordSystem("Spawn teleport", List.of("Â§7Player: Â§f" + p.getName(), "Â§7Result: Â§f" + (moved ? "moved" : "failed")));
                }, null));
        entries.add(entry(Material.REDSTONE_BLOCK, "Â§cBroadcast Now",
                List.of("Â§7Fire the next scheduled broadcast immediately."),
                p -> {
                    broadcasterManager.broadcastNow();
                    p.sendMessage("Â§aBroadcast fired.");
                    if (feed != null) feed.recordSystem("Broadcast fired", List.of("Â§7By Â§f" + p.getName()));
                }, null));
        entries.add(entry(Material.DRAGON_EGG, "Â§dBossBar Rotate",
                List.of("Â§7Advance the bossbar to the next message."),
                p -> {
                    bossBarManager.rotateNow();
                    p.sendMessage("Â§aBossbar rotated.");
                    if (feed != null) feed.recordSystem("Bossbar rotated", List.of("Â§7Title: Â§f" + bossBarManager.getCurrentTitle()));
                }, null));

        // Server Health
        entries.add(entry(Material.GRAY_STAINED_GLASS_PANE, "Â§8Â§lâ–Œ Server Health",
                List.of("Â§7Status and stats."), null, null));
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
        entries.add(entry(dbUp ? Material.EMERALD : Material.REDSTONE, "Â§bDatabase",
                List.of("Â§7Status: " + (dbUp ? "Â§aOnline" : "Â§cOffline"),
                        "Â§7Error: Â§f" + (plugin.getDatabaseManager() == null ? "none" : plugin.getDatabaseManager().getLastConnectError())),
                p -> p.sendMessage("Â§7DB: " + (dbUp ? "Â§aOnline" : "Â§cOffline â€” " + plugin.getDatabaseManager().getLastConnectError())), null));
        entries.add(entry(Material.NETHER_STAR, "Â§fPlugin & Caches",
                List.of("Â§7Version: Â§f" + version,
                        "Â§7Profiles: Â§f" + cachedProfiles + " cached Â§8(Â§f" + dirtyProfiles + " dirtyÂ§8)",
                        "Â§7Leaderboards: Â§f" + lbCount + " Â§8(refresh Â§f" + lbRefresh + "sÂ§8)",
                        "Â§7Combat tags: Â§f" + combatTags + " active"),
                p -> {
                    p.sendMessage("Â§7Version: Â§f" + version);
                    p.sendMessage("Â§7Profiles cached: Â§f" + cachedProfiles + " Â§8(dirty: " + dirtyProfiles + ")");
                    p.sendMessage("Â§7Leaderboards: Â§f" + lbCount + " Â§8(refresh " + lbRefresh + "s)");
                    p.sendMessage("Â§7Combat tags active: Â§f" + combatTags);
                }, null));
        entries.add(entry(Material.BEACON, "Â§dDeploy Status",
                List.of("Â§7" + deploySummary),
                p -> {
                    if (deployStatus != null) {
                        var snap = deployStatus.snapshot();
                        p.sendMessage("Â§7Last deploy: Â§f" + snap.lastDeployAt());
                        p.sendMessage("Â§7Container: Â§f" + snap.containerRunning());
                        p.sendMessage("Â§7Restarted: Â§f" + snap.restartPerformed());
                    }
                }, null));
        entries.add(entry(mcMmoEnabled ? Material.ENCHANTED_BOOK : Material.BOOK, "Â§5mcMMO Bridge",
                List.of("Â§7Status: " + (mcMmoEnabled ? "Â§aEnabled" : "Â§cNot detected"),
                        "Â§7Version: Â§f" + (mcMmoVersion == null ? "unknown" : mcMmoVersion)),
                p -> p.sendMessage("Â§7mcMMO: " + (mcMmoEnabled ? "Â§aenabled" : "Â§cnot detected")), null));
        entries.add(entry(Material.WRITABLE_BOOK, "Â§6Server Stats",
                List.of("Â§7Unique logins: Â§f" + totalUnique,
                        "Â§7K/D: Â§f" + totalKills + "Â§7/Â§f" + totalDeaths,
                        "Â§7Playtime: Â§f" + (totalPlayMs / 3600000L) + "h  Â§8|  Â§7Joins: Â§f" + totalJoins),
                p -> {
                    p.sendMessage("Â§6Server Stats");
                    p.sendMessage("Â§7Unique logins: Â§f" + totalUnique + "  Â§8|  Â§7Joins: Â§f" + totalJoins);
                    p.sendMessage("Â§7Kills: Â§f" + totalKills + "  Â§8|  Â§7Deaths: Â§f" + totalDeaths);
                    p.sendMessage("Â§7Playtime: Â§f" + (totalPlayMs / 3600000L) + "h");
                    p.sendMessage("Â§7Messages: Â§f" + stats.getOrDefault(OverallStatsManager.TOTAL_MESSAGES, 0L));
                    p.sendMessage("Â§7Commands: Â§f" + stats.getOrDefault(OverallStatsManager.TOTAL_COMMANDS, 0L));
                    p.sendMessage("Â§7Graves: Â§f" + stats.getOrDefault(OverallStatsManager.TOTAL_GRAVES, 0L));
                }, null));
        return entries;
    }

    // â”€â”€ Backup & Tools â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private List<DebugEntry> backupToolsEntries() {
        List<DebugEntry> entries = new ArrayList<>();
        var lbm = plugin.getLeaderboardManager();
        var rm  = plugin.getRestartManager();
        boolean dbUp = plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isEnabled();
        entries.add(entry(Material.PAPER, "Â§fFlush All Profiles",
                List.of("Â§7Write every dirty cached profile to the database.", "Â§8Safe to run at any time."),
                p -> {
                    profiles.flushAll();
                    p.sendMessage("Â§aAll profiles flushed to DB.");
                    if (feed != null) feed.recordSystem("Manual profile flush", List.of("Â§7By Â§f" + p.getName()));
                }, null));
        entries.add(entry(Material.BOOK, "Â§aRefresh Leaderboards",
                List.of("Â§7Rebuild all leaderboard caches from the database."),
                p -> {
                    if (lbm == null) { p.sendMessage("Â§cLeaderboard manager unavailable."); return; }
                    int n = lbm.refreshNow();
                    p.sendMessage("Â§aLeaderboards refreshed â€” Â§f" + n + " entries.");
                    if (feed != null) feed.recordSystem("Leaderboards refreshed", List.of("Â§7" + n + " entries by Â§f" + p.getName()));
                }, null));
        entries.add(entry(dbUp ? Material.EMERALD : Material.REDSTONE, "Â§bDB Connection Check",
                List.of("Â§7Test that the pool can still acquire a connection."),
                p -> {
                    boolean ok = plugin.getDatabaseManager() != null && plugin.getDatabaseManager().canAcquireConnection();
                    p.sendMessage(ok ? "Â§aDB connection: Â§fOK" : "Â§cDB connection: Â§fFAILED â€” " + (plugin.getDatabaseManager() == null ? "null" : plugin.getDatabaseManager().getLastConnectError()));
                    if (feed != null) feed.recordSystem("DB check", List.of("Â§7Result: Â§f" + (ok ? "OK" : "FAILED")));
                }, null));
        entries.add(entry(Material.CLOCK, "Â§eSchedule Restart (60s)",
                List.of("Â§7Request a graceful restart in 60 seconds.", "Â§8Sends a countdown warning to all players."),
                p -> {
                    if (rm == null) { p.sendMessage("Â§cRestartManager unavailable."); return; }
                    if (rm.isRestartPending()) { p.sendMessage("Â§eA restart is already pending."); return; }
                    rm.scheduleRestart(60, "Dev cockpit restart", p);
                    if (feed != null) feed.recordSystem("Restart scheduled", List.of("Â§7By Â§f" + p.getName(), "Â§7Countdown: Â§f60s"));
                }, null));
        entries.add(entry(Material.BARRIER, "Â§cClear Debug Feed",
                List.of("Â§7Wipe all recorded debug events from memory."),
                p -> {
                    if (feed == null) { p.sendMessage("Â§cFeed unavailable."); return; }
                    feed.clear();
                    p.sendMessage("Â§aDebug feed cleared.");
                }, null));
        return entries;
    }

    /** @deprecated Delegates to systemHealthEntries(). */
    private List<DebugEntry> systemEntries() { return systemHealthEntries(); }

    private List<DebugEntry> commandEntries() {
        List<DebugEntry> entries = new ArrayList<>();
        entries.add(entry(Material.PLAYER_HEAD, "/stats", List.of("Â§7Open the new stats GUI panel.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/stats", "Open the stats GUI panel.", "/stats [player]"), null, runCommand("/stats"), suggestCommand("/stats [player]", "Â§7Click to paste: /stats [player]")));
        entries.add(entry(Material.BLAZE_POWDER, "/cosmetics", List.of("Â§7Open the Cosmetics Lounge.", "Â§8Right click opens it now. Shift-right pastes the command."), commandInfo("/cosmetics", "Open the Cosmetics Lounge.", "/cosmetics"), null, p -> new CosmeticsMenu(plugin, cosmetics, profiles, toyboxManager, ((com.darkniightz.main.JebaitedCore) plugin).getCosmeticPreviewService()).open(p), suggestCommand("/cosmetics", "Â§7Click to paste: /cosmetics")));
        entries.add(entry(Material.LEATHER_CHESTPLATE, "/wardrobe", List.of("Â§7Alias for cosmetics.", "Â§8Right click opens it now. Shift-right pastes the command."), commandInfo("/wardrobe", "Open the wardrobe directly.", "/wardrobe"), null, p -> new WardrobeMenu(plugin, cosmetics, profiles, toyboxManager, CosmeticsManager.Category.PARTICLES, ((com.darkniightz.main.JebaitedCore) plugin).getCosmeticPreviewService()).open(p), suggestCommand("/wardrobe", "Â§7Click to paste: /wardrobe")));
        entries.add(entry(Material.COMPASS, "/menu", List.of("Â§7Open the server navigator.", "Â§8Right click opens it now. Shift-right pastes the command."), commandInfo("/menu", "Open the server navigator.", "/menu"), null, p -> new com.darkniightz.core.hub.ServersMenu(plugin).open(p), suggestCommand("/menu", "Â§7Click to paste: /menu")));
        entries.add(entry(Material.ENDER_EYE, "/navigator", List.of("Â§7Alias for menu.", "Â§8Right click opens it now. Shift-right pastes the command."), commandInfo("/navigator", "Open the server navigator.", "/navigator"), null, p -> new com.darkniightz.core.hub.ServersMenu(plugin).open(p), suggestCommand("/navigator", "Â§7Click to paste: /navigator")));
        entries.add(entry(Material.GRASS_BLOCK, "/hub", List.of("Â§7Return to Hub spawn from any world.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/hub", "Return to Hub world spawn.", "/hub"), null, runCommand("/hub"), suggestCommand("/hub", "Â§7Click to paste: /hub")));
        entries.add(entry(Material.OAK_SAPLING, "/smp", List.of("Â§7Go to SMP with return-location support.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/smp", "Travel to SMP using remembered return location when present.", "/smp"), null, runCommand("/smp"), suggestCommand("/smp", "Â§7Click to paste: /smp")));
        entries.add(entry(Material.CARTOGRAPHY_TABLE, "/worldstatus", List.of("Â§7Check world routing diagnostics.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/worldstatus", "Show hub/smp routing and world load status.", "/worldstatus"), null, runCommand("/worldstatus"), suggestCommand("/worldstatus", "Â§7Click to paste: /worldstatus")));
        entries.add(entry(Material.NETHER_STAR, "/event", List.of("Â§7Control and inspect event runtime.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/event", "Manage events and queues.", "/event <status|list|start|stop|complete|setup>"), null, runCommand("/event status"), suggestCommand("/event status", "Â§7Click to paste: /event status")));
        entries.add(entry(Material.NETHER_STAR, "/jebaited", List.of("Â§7Adaptive help and command list.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/jebaited", "Show the adaptive help and command list.", "/jebaited"), null, runCommand("/jebaited"), suggestCommand("/jebaited", "Â§7Click to paste: /jebaited")));
        entries.add(entry(Material.PURPLE_DYE, "/devmode", List.of("Â§7Toggle devmode for allowed UUIDs.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/devmode", "Toggle devmode for allowed UUIDs.", "/devmode on"), null, runCommand("/devmode"), suggestCommand("/devmode", "Â§7Click to paste: /devmode")));
        entries.add(entry(Material.ANVIL, "/jreload", List.of("Â§7Reload config and refresh caches.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/jreload", "Reload config and refresh caches.", "/jreload"), null, p -> { plugin.reloadCore(); if (feed != null) feed.recordSystem("Core reloaded from command list", List.of("Â§7Triggered by Â§f" + p.getName() + "Â§7.")); }, suggestCommand("/jreload", "Â§7Click to paste: /jreload")));
        entries.add(entry(Material.IRON_SWORD, "/rank", List.of("Â§7View or edit ranks.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/rank", "View or edit ranks.", "/rank get <player>"), null, runCommand("/rank"), suggestCommand("/rank get <player>", "Â§7Click to paste: /rank get <player>")));
        entries.add(entry(Material.GOLDEN_SWORD, "/setrank", List.of("Â§7Set a player's rank.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/setrank", "Set a player's rank.", "/setrank <player> <group>"), null, runCommand("/setrank"), suggestCommand("/setrank <player> <group>", "Â§7Click to paste: /setrank <player> <group>")));
        entries.add(entry(Material.EMERALD, "/coins", List.of("Â§7View or manage Cosmetic Coins.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/coins", "View or manage Cosmetic Coins.", "/coins [player]"), null, runCommand("/coins"), suggestCommand("/coins [player]", "Â§7Click to paste: /coins [player]")));
        entries.add(entry(Material.GOLD_NUGGET, "/balance", List.of("Â§7View money balance.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/balance", "View your balance or inspect another player.", "/balance [player]"), null, runCommand("/balance"), suggestCommand("/balance [player]", "Â§7Click to paste: /balance [player]")));
        entries.add(entry(Material.GOLD_INGOT, "/pay", List.of("Â§7Send money to an online player.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/pay", "Transfer money to another player.", "/pay <player> <amount>"), null, runCommand("/pay"), suggestCommand("/pay <player> <amount>", "Â§7Click to paste: /pay <player> <amount>")));
        entries.add(entry(Material.CHEST, "/balancetop", List.of("Â§7Show richest players.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/balancetop", "Show top balances.", "/balancetop [limit]"), null, runCommand("/balancetop"), suggestCommand("/balancetop [limit]", "Â§7Click to paste: /balancetop [limit]")));
        entries.add(entry(Material.BEACON, "/eco", List.of("Â§7Admin balance controls.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/eco", "Admin give/take/set money.", "/eco <give|take|set> <player> <amount>"), null, runCommand("/eco"), suggestCommand("/eco <give|take|set> <player> <amount>", "Â§7Click to paste: /eco <give|take|set> <player> <amount>")));
        entries.add(entry(Material.RED_BED, "/sethome /home /homes", List.of("Â§7Manage personal homes.", "Â§8Right click runs /homes. Shift-right pastes /sethome <name>."), commandInfo("/homes", "List and use player homes.", "/sethome [name] | /home [name] | /delhome <name>"), null, runCommand("/homes"), suggestCommand("/sethome <name>", "Â§7Click to paste: /sethome <name>")));
        entries.add(entry(Material.NAME_TAG, "/nick /whois", List.of("Â§7Nickname and profile diagnostics.", "Â§8Right click runs /nick. Shift-right pastes /whois."), commandInfo("/nick", "Set nickname or inspect player diagnostics.", "/nick <name|off> | /whois <player>"), null, runCommand("/nick"), suggestCommand("/whois <player>", "Â§7Click to paste: /whois <player>")));
        entries.add(entry(MaterialCompat.resolve(Material.COMPASS, "SPYGLASS", "COMPASS"), "/near", List.of("Â§7List nearby players in your world.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/near", "Scan nearby players by radius.", "/near [radius]"), null, runCommand("/near"), suggestCommand("/near [radius]", "Â§7Click to paste: /near [radius]")));
        entries.add(entry(Material.BOOK, "/rules", List.of("Â§7Show current server rules.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/rules", "Show server rules from config.", "/rules"), null, runCommand("/rules"), suggestCommand("/rules", "Â§7Click to paste: /rules")));
        entries.add(entry(Material.ENDER_PEARL, "/rtp", List.of("Â§7Random teleport in SMP.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/rtp", "Teleport to random safe coordinates.", "/rtp"), null, runCommand("/rtp"), suggestCommand("/rtp", "Â§7Click to paste: /rtp")));
        entries.add(entry(Material.PAPER, "/message /reply", List.of("Â§7Private messaging commands.", "Â§8Right click runs /message. Shift-right pastes /reply."), commandInfo("/message", "Send and reply to private messages.", "/message <player> <message> | /reply <message>"), null, runCommand("/message"), suggestCommand("/reply <message>", "Â§7Click to paste: /reply <message>")));
        entries.add(entry(MaterialCompat.resolve(Material.COMPASS, "LODESTONE", "COMPASS"), "/warp /warps", List.of("Â§7Use public warps.", "Â§8Right click runs /warps. Shift-right pastes /warp <name>."), commandInfo("/warps", "List and use public warps.", "/warp <name>"), null, runCommand("/warps"), suggestCommand("/warp <name>", "Â§7Click to paste: /warp <name>")));
        entries.add(entry(Material.STRUCTURE_BLOCK, "/setwarp /delwarp", List.of("Â§7Admin warp management.", "Â§8Right click runs /setwarp. Shift-right pastes usage."), commandInfo("/setwarp", "Create or delete public warps.", "/setwarp <name> [cost] | /delwarp <name>"), null, runCommand("/setwarp"), suggestCommand("/setwarp <name> [cost]", "Â§7Click to paste: /setwarp <name> [cost]")));
        entries.add(entry(Material.SLIME_BALL, "/spawn", List.of("Â§7Teleport to spawn.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/spawn", "Teleport to spawn.", "/spawn"), null, runCommand("/spawn"), suggestCommand("/spawn", "Â§7Click to paste: /spawn")));
        entries.add(entry(Material.BEDROCK, "/setspawn", List.of("Â§7Set the configured spawn.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/setspawn", "Set the configured spawn.", "/setspawn"), null, runCommand("/setspawn"), suggestCommand("/setspawn", "Â§7Click to paste: /setspawn")));
        entries.add(entry(MaterialCompat.resolve(Material.BOOK, "WRITABLE_BOOK", "BOOK_AND_QUILL", "BOOK"), "/generatepassword", List.of("Â§7Provision a web panel login.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/generatepassword", "Provision a web panel login.", "/generatepassword"), null, runCommand("/generatepassword"), suggestCommand("/generatepassword", "Â§7Click to paste: /generatepassword")));
        entries.add(entry(Material.IRON_DOOR, "/kick", List.of("Â§7Kick a player.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/kick", "Kick a player.", "/kick <player> <reason>"), null, runCommand("/kick"), suggestCommand("/kick <player> <reason>", "Â§7Click to paste: /kick <player> <reason>")));
        entries.add(entry(Material.PAPER, "/warn", List.of("Â§7Warn a player.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/warn", "Warn a player.", "/warn <player> <reason>"), null, runCommand("/warn"), suggestCommand("/warn <player> <reason>", "Â§7Click to paste: /warn <player> <reason>")));
        entries.add(entry(Material.BLAZE_ROD, "/mute /tempmute", List.of("Â§7Mute a player.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/tempmute", "Mute a player.", "/tempmute <player> <duration> <reason>"), null, runCommand("/tempmute"), suggestCommand("/tempmute <player> <duration> <reason>", "Â§7Click to paste: /tempmute <player> <duration> <reason>")));
        entries.add(entry(Material.LAVA_BUCKET, "/ban /tempban", List.of("Â§7Ban a player.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/tempban", "Ban a player.", "/tempban <player> <duration> <reason>"), null, runCommand("/tempban"), suggestCommand("/tempban <player> <duration> <reason>", "Â§7Click to paste: /tempban <player> <duration> <reason>")));
        entries.add(entry(Material.BARRIER, "/unban /unmute", List.of("Â§7Undo punishments.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/unban", "Undo punishments.", "/unban <player> or /unmute <player>"), null, runCommand("/unban"), suggestCommand("/unban <player> or /unmute <player>", "Â§7Click to paste: /unban <player> or /unmute <player>")));
        entries.add(entry(Material.ICE, "/freeze", List.of("Â§7Freeze a player.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/freeze", "Freeze a player.", "/freeze <player>"), null, runCommand("/freeze"), suggestCommand("/freeze <player>", "Â§7Click to paste: /freeze <player>")));
        entries.add(entry(Material.ENDER_PEARL, "/vanish", List.of("Â§7Toggle staff vanish.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/vanish", "Toggle staff vanish.", "/vanish"), null, runCommand("/vanish"), suggestCommand("/vanish", "Â§7Click to paste: /vanish")));
        entries.add(entry(MaterialCompat.resolve(Material.BOOK, "WRITABLE_BOOK", "BOOK_AND_QUILL", "BOOK"), "/staffchat", List.of("Â§7Toggle staff chat or send a message.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/staffchat", "Toggle staff chat or send a message.", "/staffchat <message>"), null, runCommand("/staffchat"), suggestCommand("/staffchat <message>", "Â§7Click to paste: /staffchat <message>")));
        entries.add(entry(Material.GLASS, "/clearchat", List.of("Â§7Clear public chat.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/clearchat", "Clear public chat.", "/clearchat"), null, runCommand("/clearchat"), suggestCommand("/clearchat", "Â§7Click to paste: /clearchat")));
        entries.add(entry(Material.CLOCK, "/slowmode", List.of("Â§7Set chat slowmode.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/slowmode", "Set chat slowmode.", "/slowmode <seconds>"), null, runCommand("/slowmode"), suggestCommand("/slowmode <seconds>", "Â§7Click to paste: /slowmode <seconds>")));
        entries.add(entry(Material.PAPER, "/history", List.of("Â§7View moderation history.", "Â§8Right click runs it. Shift-right pastes the command."), commandInfo("/history", "View moderation history.", "/history <player>"), null, runCommand("/history"), suggestCommand("/history <player>", "Â§7Click to paste: /history <player>")));
        return entries;
    }

    private List<DebugEntry> listenerEntries() {
        List<DebugEntry> entries = new ArrayList<>();
        entries.add(entry(Material.NOTE_BLOCK, "Â§bChatListener", List.of("Â§7Chat formatting and staff chat routing."), infoAction("ChatListener"), null));
        entries.add(entry(Material.NAME_TAG, "Â§aJoinListener", List.of("Â§7Join MOTD and offline rank sync."), infoAction("JoinListener"), null));
        entries.add(entry(Material.ENDER_PEARL, "Â§bWorldChangeListener", List.of("Â§7Hub/SMP world transitions, respawn rules, and return-location capture."), infoAction("WorldChangeListener"), null));
        entries.add(entry(Material.BOOKSHELF, "Â§eMenuListener", List.of("Â§7Inventory routing and menu clicks."), infoAction("MenuListener"), null));
        entries.add(entry(Material.COMMAND_BLOCK, "Â§dHotbarNavigatorListener", List.of("Â§7Hotbar compass and cosmetics slot."), infoAction("HotbarNavigatorListener"), null));
        entries.add(entry(Material.SHIELD, "Â§cHubProtectionListener", List.of("Â§7Hub damage, hunger, and build protection."), infoAction("HubProtectionListener"), null));
        entries.add(entry(Material.IRON_TRAPDOOR, "Â§fModerationListener", List.of("Â§7Freeze, vanish, mute, and slowmode enforcement."), infoAction("ModerationListener"), null));
        entries.add(entry(Material.PAPER, "Â§6CommandTrackingListener", List.of("Â§7Counts command usage for stats."), infoAction("CommandTrackingListener"), null));
        entries.add(entry(Material.REPEATER, "Â§aStatsTrackingListener", List.of("Â§7Tracks kills, deaths, mobs, bosses, and periodic playtime flush."), infoAction("StatsTrackingListener"), null));
        entries.add(entry(Material.CHAIN_COMMAND_BLOCK, "Â§dEventModeChatListener", List.of("Â§7Chat-game answer routing and event completion trigger."), infoAction("EventModeChatListener"), null));
        entries.add(entry(Material.IRON_AXE, "Â§dEventModeCombatListener", List.of("Â§7Elimination event death/respawn and keep-inventory hooks."), infoAction("EventModeCombatListener"), null));
        entries.add(entry(MaterialCompat.resolve(Material.PAPER, "FIREWORK_ROCKET", "FIREWORK", "PAPER"), "Â§dToyboxListener", List.of("Â§7Handles right-click gadget use."), infoAction("ToyboxListener"), null));
        entries.add(entry(Material.NETHER_STAR, "Â§bServerListMotdListener", List.of("Â§7Server-list MOTD rendering."), infoAction("ServerListMotdListener"), null));
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
        entries.add(entry(Material.BLAZE_POWDER, "Â§6Preview Particles", List.of("Â§7Preview your current particle effect."), p -> {
            PlayerProfile prof = profiles.getOrCreate(p, ranks.getDefaultGroup());
            if (prof != null && prof.getEquippedParticles() != null) {
                cosmeticsEngine.previewParticle(p, prof.getEquippedParticles());
                if (feed != null) {
                    feed.recordPreview(p, "Particle preview", List.of("Â§7Particle key: Â§f" + prof.getEquippedParticles()));
                }
            } else {
                p.sendMessage("Â§7No particle equipped.");
            }
        }, null));
        entries.add(entry(Material.FEATHER, "Â§ePreview Trails", List.of("Â§7Preview your current trail effect."), p -> {
            PlayerProfile prof = profiles.getOrCreate(p, ranks.getDefaultGroup());
            if (prof != null && prof.getEquippedTrail() != null) {
                cosmeticsEngine.previewTrail(p, prof.getEquippedTrail());
                if (feed != null) {
                    feed.recordPreview(p, "Trail preview", List.of("Â§7Trail key: Â§f" + prof.getEquippedTrail()));
                }
            } else {
                p.sendMessage("Â§7No trail equipped.");
            }
        }, null));
        entries.add(entry(MaterialCompat.resolve(Material.PAPER, "FIREWORK_ROCKET", "FIREWORK", "PAPER"), "Â§dPreview Toybox", List.of("Â§7Trigger the active gadget without saving."), p -> {
            PlayerProfile prof = profiles.getOrCreate(p, ranks.getDefaultGroup());
            if (prof != null && prof.getEquippedGadget() != null) {
                toyboxManager.preview(p, prof.getEquippedGadget());
                if (feed != null) {
                    feed.recordPreview(p, "Toybox preview", List.of("Â§7Gadget key: Â§f" + prof.getEquippedGadget()));
                }
            } else {
                p.sendMessage("Â§7No gadget equipped.");
            }
        }, null));
        entries.add(entry(MaterialCompat.resolve(Material.COMPASS, "SPYGLASS", "COMPASS"), "Â§fPreview Mode", List.of("Â§7Toggle preview-only behavior for cosmetics."), p -> {
            boolean enabled = debugState.togglePreviewMode(p.getUniqueId());
            p.sendMessage("Â§aPreview mode is now Â§e" + (enabled ? "ON" : "OFF") + "Â§a.");
            if (feed != null) {
                feed.recordPreview(p, "Preview mode toggled", List.of("Â§7Now: Â§f" + (enabled ? "ON" : "OFF")));
            }
        }, null));
        return entries;
    }

    private List<DebugEntry> eventEntries() {
        List<DebugEntry> entries = new ArrayList<>();
        if (feed == null) {
            entries.add(entry(Material.BARRIER, "Â§cFeed unavailable", List.of("Â§7The debug feed manager is not active."), null, null));
            return entries;
        }

        List<DebugFeedManager.DebugEvent> snapshot = feed.snapshot();
        if (snapshot.isEmpty()) {
            entries.add(entry(Material.BOOK, "Â§7No recent events", List.of("Â§7Play with commands, toys, or joins to populate this feed."), null, null));
            return entries;
        }

        for (DebugFeedManager.DebugEvent event : snapshot) {
            List<String> lore = new ArrayList<>();
            lore.add("Â§8" + feed.formatTime(event.timestamp) + "  Â·  " + categoryColor(event.category) + event.category.name().toLowerCase(Locale.ROOT));
            lore.add(" ");
            lore.addAll(event.details);
            lore.add("Â§7Click to view in chat.");
            entries.add(entry(event.icon == null ? Material.PAPER : event.icon, event.title, lore, player -> {
                player.sendMessage("Â§dÂ§lDebug EventÂ§7: Â§f" + event.title);
                player.sendMessage("Â§7Category: Â§f" + event.category.name().toLowerCase(Locale.ROOT));
                player.sendMessage("Â§7Time: Â§f" + feed.formatTime(event.timestamp));
                for (String line : event.details) {
                    player.sendMessage(line);
                }
            }, null));
        }
        return entries;
    }

    private List<DebugEntry> filteredEventEntries(Player viewer) {
        if (feed == null) {
            return List.of(entry(Material.BARRIER, "Â§cFeed unavailable", List.of("Â§7The debug feed manager is not active."), null, null));
        }

        DebugFeedManager.Category filter = debugState.getFeedFilter(viewer.getUniqueId());
        List<DebugEntry> entries = new ArrayList<>();
        for (DebugFeedManager.DebugEvent event : feed.snapshot()) {
            if (filter != null && event.category != filter) continue;
            List<String> lore = new ArrayList<>();
            lore.add("Â§8" + feed.formatTime(event.timestamp) + "  Â·  " + categoryColor(event.category) + event.category.name().toLowerCase(Locale.ROOT));
            lore.add(" ");
            lore.addAll(event.details);
            lore.add("Â§7Click to view in chat.");
            entries.add(entry(event.icon == null ? Material.PAPER : event.icon, event.title, lore, player -> {
                player.sendMessage("Â§dÂ§lDebug EventÂ§7: Â§f" + event.title);
                player.sendMessage("Â§7Category: Â§f" + event.category.name().toLowerCase(Locale.ROOT));
                player.sendMessage("Â§7Time: Â§f" + feed.formatTime(event.timestamp));
                for (String line : event.details) {
                    player.sendMessage(line);
                }
            }, null));
        }

        if (entries.isEmpty()) {
            String filterName = filter == null ? "all" : filter.name().toLowerCase(Locale.ROOT);
            entries.add(entry(Material.BOOK, "Â§7No matching events", List.of("Â§7Filter: Â§f" + filterName, "Â§7Trigger some commands or actions to populate this feed."), null, null));
        }
        return entries;
    }

    private static String categoryColor(DebugFeedManager.Category cat) {
        return switch (cat) {
            case SYSTEM     -> "Â§b";
            case COMMAND    -> "Â§e";
            case LISTENER   -> "Â§6";
            case JOIN       -> "Â§a";
            case MODERATION -> "Â§c";
            case COSMETIC, GADGET -> "Â§d";
            case PREVIEW    -> "Â§7";
            case EVENT      -> "Â§5";
        };
    }

    /** @deprecated Delegates to systemHealthEntries(). */
    @SuppressWarnings("unused")
    private List<DebugEntry> healthEntries() { return systemHealthEntries(); }

    private final class DebugFeedMenu extends BaseMenu {
        private final int[] slots = {19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
        private int page = 0;

        private DebugFeedMenu() {
            super(DebugMenu.this.plugin, "Â§5Â§lLive Debug Feed", 54);
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
                if (entry.leftAction != null) lore.add("Â§7Left click for details.");
                if (entry.rightAction != null) lore.add("Â§7Right click to run it.");
                if (entry.shiftAction != null) lore.add("Â§7Shift click for a secondary action.");
                if (entry.shiftRightAction != null) lore.add("Â§7Shift-right click to paste a template.");
                inv.setItem(slots[i], new ItemBuilder(entry.icon).name(entry.title).lore(lore).build());
            }

            inv.setItem(4, new ItemBuilder(Material.CLOCK)
                    .name("Â§dÂ§lLive Feed")
                    .lore(List.of(
                            "Â§7Filter: Â§f" + (filter == null ? "all" : filter.name().toLowerCase(Locale.ROOT)),
                            "Â§7Entries: Â§f" + entries.size(),
                            "Â§8Use the chips below to refine what you see."
                    ))
                    .glow(true)
                    .build());

            inv.setItem(9, filterButton(Material.BOOK, "Â§fAll", null, filter));
            inv.setItem(10, filterButton(Material.COMMAND_BLOCK, "Â§bSystem", DebugFeedManager.Category.SYSTEM, filter));
            inv.setItem(11, filterButton(MaterialCompat.resolve(Material.BOOK, "WRITABLE_BOOK", "BOOK_AND_QUILL", "BOOK"), "Â§eCommands", DebugFeedManager.Category.COMMAND, filter));
            inv.setItem(12, filterButton(Material.LEVER, "Â§6Listeners", DebugFeedManager.Category.LISTENER, filter));
            inv.setItem(13, filterButton(Material.NAME_TAG, "Â§aJoin", DebugFeedManager.Category.JOIN, filter));
            inv.setItem(14, filterButton(Material.IRON_SWORD, "Â§cModeration", DebugFeedManager.Category.MODERATION, filter));
            inv.setItem(15, filterButton(Material.BLAZE_POWDER, "Â§dCosmetics", DebugFeedManager.Category.COSMETIC, filter));
            inv.setItem(16, filterButton(MaterialCompat.resolve(Material.PAPER, "FIREWORK_ROCKET", "FIREWORK", "PAPER"), "Â§dGadgets", DebugFeedManager.Category.GADGET, filter));
            inv.setItem(17, filterButton(MaterialCompat.resolve(Material.COMPASS, "SPYGLASS", "COMPASS"), "Â§fPreview", DebugFeedManager.Category.PREVIEW, filter));

            if (page > 0) {
                inv.setItem(45, new ItemBuilder(Material.ARROW).name("Â§ePrevious Page").lore(List.of("Â§7Go back one page.")).build());
            }
            if (page < pageCount(entries.size()) - 1) {
                inv.setItem(53, new ItemBuilder(Material.ARROW).name("Â§aNext Page").lore(List.of("Â§7Go forward one page.")).build());
            }
            inv.setItem(49, new ItemBuilder(Material.BARRIER).name("Â§cBack").lore(List.of("Â§7Return to the debug cockpit.")).build());
            inv.setItem(46, new ItemBuilder(Material.TNT).name("Â§cClear Feed").lore(List.of("Â§7Remove all recorded events from memory.")).build());
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
                who.sendMessage("Â§aDebug feed cleared.");
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
                    ? List.of("Â§aSelected", "Â§7Click to change the live feed filter.")
                    : List.of("Â§7Click to filter the live feed.");
            return new ItemBuilder(material).name(name).lore(lore).glow(selected).build();
        }

        private int pageCount(int total) {
            return Math.max(1, (int) Math.ceil(total / (double) slots.length));
        }
    }

    private List<DebugEntry> actionEntries() {
        List<DebugEntry> entries = new ArrayList<>();
        entries.add(entry(Material.ANVIL, "Â§bReload Core", List.of("Â§7Run the full config/cache reload."), p -> {
            plugin.reloadCore();
            if (feed != null) {
                feed.recordSystem("Core reloaded from action page", List.of("Â§7Triggered by Â§f" + p.getName() + "Â§7."));
            }
        }, null));
        entries.add(entry(Material.REDSTONE_TORCH, "Â§aRefresh Toybox", List.of("Â§7Re-give the active gadget item."), p -> {
            toyboxManager.refresh(p);
            if (feed != null) {
                feed.recordGadget(p, "Toybox refreshed", List.of("Â§7Active gadget item was re-issued."));
            }
        }, null));
        entries.add(entry(Material.COMPASS, "Â§eTeleport Spawn", List.of("Â§7Go to the configured spawn."), p -> spawnManager.teleportToSpawn(p), null));
        entries.add(entry(Material.REDSTONE_BLOCK, "Â§cBroadcast Now", List.of("Â§7Fire the next broadcaster line."), p -> {
            broadcasterManager.broadcastNow();
            if (feed != null) {
                feed.recordSystem("Broadcast fired", List.of("Â§7Triggered by Â§f" + p.getName() + "Â§7."));
            }
        }, null));
        entries.add(entry(Material.DRAGON_EGG, "Â§dBossBar Now", List.of("Â§7Rotate the bossbar immediately."), p -> {
            bossBarManager.rotateNow();
            if (feed != null) {
                feed.recordSystem("Bossbar rotated", List.of("Â§7Current title: Â§f" + bossBarManager.getCurrentTitle()));
            }
        }, null));
        entries.add(entry(Material.PAPER, "Â§fFlush Profiles", List.of("Â§7Save all cached player profiles."), p -> {
            profiles.flushAll();
            if (feed != null) {
                feed.recordSystem("Profiles flushed", List.of("Â§7Triggered by Â§f" + p.getName() + "Â§7."));
            }
        }, null));
        entries.add(entry(Material.BARRIER, "Â§cDisable Preview", List.of("Â§7Turn preview mode off for this player."), p -> {
            if (debugState.isPreviewMode(p.getUniqueId())) {
                debugState.togglePreviewMode(p.getUniqueId());
            }
            if (feed != null) {
                feed.recordPreview(p, "Preview mode cleared", List.of("Â§7Debug session preview state disabled."));
            }
        }, null));
        return entries;
    }

    private List<DebugEntry> spawnEntries() {
        List<DebugEntry> entries = new ArrayList<>();
        entries.add(entry(Material.COMPASS, "Â§aTeleport to Spawn", List.of("Â§7Send yourself to this world's spawn configuration."), p -> spawnManager.teleportToSpawn(p), null));
        entries.add(entry(Material.OAK_DOOR, "Â§eSet Spawn Here (This World)", List.of("Â§7Save your current location as spawn for this world."), p -> {
            if (p.getWorld() == null) {
                p.sendMessage("Â§cWorld unavailable.");
                return;
            }
            spawnManager.setSpawnForWorld(p.getWorld().getName(), p.getLocation());
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("teleport.spawn.set-message", "&aSpawn set at your location!")) + " Â§7(" + p.getWorld().getName() + ")");
        }, null));
        entries.add(entry(Material.PAPER, "Â§fShow Spawn (This World)", List.of("Â§7Print this world's configured spawn to chat."), p -> {
            if (p.getWorld() == null) {
                p.sendMessage("Â§cWorld unavailable.");
                return;
            }
            var loc = spawnManager.getSpawnForWorld(p.getWorld().getName());
            p.sendMessage("Â§7Configured spawn: Â§f" + (loc == null ? "none" : loc.getWorld().getName() + " " + Math.round(loc.getX()) + ", " + Math.round(loc.getY()) + ", " + Math.round(loc.getZ())));
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
        lore.add("Â§7Type: Â§f" + type);
        lore.add("Â§7Price: Â§6" + cosmetic.price + " coins");
        lore.add("Â§7Left click: Â§fpreview (temporary)");
        lore.add("Â§7Shift-left: Â§fequip and save");
        return entry(cosmetic.icon, cosmetic.name, lore, player -> previewOrEquip(player, cosmetic, false), player -> previewOrEquip(player, cosmetic, true));
    }

    private void previewOrEquip(Player player, CosmeticsManager.Cosmetic cosmetic, boolean forceEquip) {
        if (cosmetic.category == CosmeticsManager.Category.PARTICLES) {
            if (forceEquip || !debugState.isPreviewMode(player.getUniqueId())) {
                equipParticle(player, cosmetic);
            } else {
                cosmeticsEngine.previewParticle(player, cosmetic.key);
                if (feed != null) {
                    feed.recordPreview(player, "Particle preview", List.of("Â§7Particle key: Â§f" + cosmetic.key));
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
                    feed.recordPreview(player, "Trail preview", List.of("Â§7Trail key: Â§f" + cosmetic.key));
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
                feed.recordPreview(player, "Toybox preview", List.of("Â§7Gadget key: Â§f" + cosmetic.key));
            }
        }
    }

    private void equipParticle(Player player, CosmeticsManager.Cosmetic cosmetic) {
        PlayerProfile prof = profiles.getOrCreate(player, ranks.getDefaultGroup());
        if (prof == null) return;
        prof.setEquippedParticles(cosmetic.key);
        prof.setParticleActivatedAt(System.currentTimeMillis());
        profiles.save(player.getUniqueId());
        player.sendMessage("Â§aEquipped preview particle: Â§e" + cosmetic.name);
        if (feed != null) {
            feed.recordCosmetic(player, "Equipped particle", List.of("Â§7Key: Â§f" + cosmetic.key, "Â§7Name: Â§f" + cosmetic.name));
        }
    }

    private void equipTrail(Player player, CosmeticsManager.Cosmetic cosmetic) {
        PlayerProfile prof = profiles.getOrCreate(player, ranks.getDefaultGroup());
        if (prof == null) return;
        prof.setEquippedTrail(cosmetic.key);
        prof.setTrailActivatedAt(System.currentTimeMillis());
        profiles.save(player.getUniqueId());
        player.sendMessage("Â§aEquipped preview trail: Â§e" + cosmetic.name);
        if (feed != null) {
            feed.recordCosmetic(player, "Equipped trail", List.of("Â§7Key: Â§f" + cosmetic.key, "Â§7Name: Â§f" + cosmetic.name));
        }
    }

    private void equipGadget(Player player, CosmeticsManager.Cosmetic cosmetic) {
        PlayerProfile prof = profiles.getOrCreate(player, ranks.getDefaultGroup());
        if (prof == null) return;
        prof.setEquippedGadget(cosmetic.key);
        profiles.save(player.getUniqueId());
        toyboxManager.refresh(player);
        player.sendMessage("Â§aEquipped preview gadget: Â§e" + cosmetic.name);
        if (feed != null) {
            feed.recordGadget(player, "Equipped gadget", List.of("Â§7Key: Â§f" + cosmetic.key, "Â§7Name: Â§f" + cosmetic.name));
        }
    }

    private void equipTag(Player player, CosmeticsManager.Cosmetic cosmetic) {
        PlayerProfile prof = profiles.getOrCreate(player, ranks.getDefaultGroup());
        if (prof == null) return;
        prof.setActiveTag(cosmetic.key);
        profiles.save(player.getUniqueId());
        plugin.refreshPlayerPresentation(player);
        player.sendMessage("Â§aEquipped preview tag: Â§e" + cosmetic.name);
        if (feed != null) {
            feed.recordCosmetic(player, "Equipped tag", List.of("Â§7Key: Â§f" + cosmetic.key, "Â§7Name: Â§f" + cosmetic.name));
        }
    }

    private Consumer<Player> infoAction(String name) {
        return player -> player.sendMessage("Â§7Listener: Â§f" + name + " Â§8- registered and active if the plugin is enabled.");
    }

    private Consumer<Player> commandInfo(String command, String whatItDoes, String usage) {
        return player -> {
            player.sendMessage("Â§dÂ§lCommandÂ§7: Â§f" + command);
            player.sendMessage("Â§7What it does: Â§f" + whatItDoes);
            player.sendMessage("Â§7Usage: Â§f" + usage);
            player.sendMessage("Â§8Left click explains it, right click runs it, shift-right click pastes a template.");
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
                lore.add("Â§7Left click for info/preview.");
                lore.add("Â§7Shift-left for the persistent/equip action.");
                inv.setItem(slots[i], new ItemBuilder(entry.icon).name(entry.title).lore(lore).build());
            }

            inv.setItem(4, new ItemBuilder(Material.NETHER_STAR)
                    .name("Â§dÂ§lPage " + (page + 1) + "Â§7/Â§f" + pageCount())
                    .lore(List.of("Â§7Browse the debug entries.", "Â§8Use arrows to page through."))
                    .glow(true)
                    .build());

            if (page > 0) {
                inv.setItem(45, new ItemBuilder(Material.ARROW).name("Â§ePrevious Page").lore(List.of("Â§7Go back one page.")).build());
            }
            if (page < pageCount() - 1) {
                inv.setItem(53, new ItemBuilder(Material.ARROW).name("Â§aNext Page").lore(List.of("Â§7Go forward one page.")).build());
            }
            inv.setItem(49, new ItemBuilder(Material.BARRIER).name("Â§cBack").lore(List.of("Â§7Return to the debug cockpit.")).build());
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
                    who.sendMessage("Â§7" + ChatColor.stripColor(entry.title));
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

