package com.darkniightz.main.listeners;

import com.darkniightz.main.Core;
import com.darkniightz.main.util.PlayerSelectorGUI;
import com.darkniightz.main.util.ReasonTimeSelector;
import com.darkniightz.main.util.RankUtil;
import com.darkniightz.main.util.PetsHandler;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InventoryManagerListener implements Listener {

    // Maps for cosmetic states and active gadget slot
    public static final Map<UUID, Boolean> doubleJumpEnabled = new HashMap<>();
    public static final Map<UUID, Boolean> particlesEnabled = new HashMap<>();
    private static final Map<UUID, ArmorStand> playerPets = new HashMap<>();
    public static final Map<UUID, Material> activeGadget = new HashMap<>();  // Track current gadget in slot 3

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.getInventory().clear();
        ItemStack cosmeticsChest = new ItemStack(Material.CHEST);
        ItemMeta meta = cosmeticsChest.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Cosmetics GUI");
        cosmeticsChest.setItemMeta(meta);
        player.getInventory().setItem(4, cosmeticsChest);

        for (int i = 0; i < 9; i++) {
            if (i != 4) {
                player.getInventory().setItem(i, new ItemStack(Material.AIR));
            }
        }

        if (player.hasPermission("core.staff")) {
            ItemStack modTools = new ItemStack(Material.NETHER_STAR);
            ItemMeta modMeta = modTools.getItemMeta();
            modMeta.setDisplayName(ChatColor.RED + "Moderation Tools");
            modTools.setItemMeta(modMeta);
            player.getInventory().setItem(6, modTools);
        }

        player.updateInventory();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null) return;

            // Prevent moving to offhand (slot -106)
            if (event.getRawSlot() == -106 || event.getHotbarButton() == -106) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Offhand swaps disabled in hub!");
                return;
            }

            // Lock cosmetics chest - loosened condition to check type and name if present
            if (clicked.getType() == Material.CHEST) {
                ItemMeta meta = clicked.getItemMeta();
                if (meta != null && meta.getDisplayName().equals(ChatColor.GOLD + "Cosmetics GUI")) {
                    event.setCancelled(true);
                    openCosmeticsGUI(player);
                    return;
                }
            }

            // Lock moderation tools - similar
            if (clicked.getType() == Material.NETHER_STAR) {
                ItemMeta meta = clicked.getItemMeta();
                if (meta != null && meta.getDisplayName().equals(ChatColor.RED + "Moderation Tools")) {
                    event.setCancelled(true);
                    openModGUI(player, event.isShiftClick());
                    return;
                }
            }

            // Handle Cosmetics GUI clicks - set to slot 3, replace previous
            if (event.getView().getTitle().equals(ChatColor.GOLD + "Cosmetics & Gadgets")) {
                event.setCancelled(true);
                UUID uuid = player.getUniqueId();
                Material previous = activeGadget.get(uuid);
                if (previous != null) {
                    player.getInventory().setItem(2, new ItemStack(Material.AIR));  // Clear slot 3
                }

                if (clicked.getType() == Material.ENCHANTED_BOOK) {
                    toggleDoubleJump(player);
                    activeGadget.put(uuid, Material.ENCHANTED_BOOK);
                    player.getInventory().setItem(2, clicked.clone());
                } else if (clicked.getType() == Material.FEATHER) {
                    toggleParticles(player);
                    activeGadget.put(uuid, Material.FEATHER);
                    player.getInventory().setItem(2, clicked.clone());
                } else if (clicked.getType() == Material.BOW) {
                    activeGadget.put(uuid, Material.BOW);
                    player.getInventory().setItem(2, clicked.clone());
                } else if (clicked.getType() == Material.EGG) {
                    PetsHandler.openPetsMenu(player);  // Open menu instead of toggle
                }
                player.updateInventory();
                return;
            }

            // Handle Moderation GUI clicks - open selectors
            if (event.getView().getTitle().equals(ChatColor.RED + "Moderation Tools")) {
                event.setCancelled(true);
                String action = null;
                if (clicked.getType() == Material.CLOCK) action = "mute";
                else if (clicked.getType() == Material.LEATHER_BOOTS) action = "kick";
                else if (clicked.getType() == Material.BARRIER) action = "ban";
                else if (clicked.getType() == Material.WRITTEN_BOOK) action = "history";

                if (action != null) {
                    player.closeInventory();
                    PlayerSelectorGUI.open(player, action);
                }
                return;
            }

            // Handle Player Selector GUI clicks - open reason/time
            if (event.getView().getTitle().contains("Select Player for")) {
                event.setCancelled(true);
                if (clicked.getType() == Material.PLAYER_HEAD) {
                    String target = clicked.getItemMeta().getDisplayName();
                    String action = event.getView().getTitle().replace(ChatColor.GRAY + "Select Player for ", "").trim();
                    player.closeInventory();
                    ReasonTimeSelector.open(player, action, target);
                }
                return;
            }

            // Handle Reason/Time GUI clicks - execute command
            if (event.getView().getTitle().contains("Reason & Time for")) {
                event.setCancelled(true);
                String[] parts = event.getView().getTitle().replace(ChatColor.GRAY + "Reason & Time for ", "").split(" ");
                String action = parts[0];
                String target = parts[1];

                String selected = clicked.getItemMeta().getDisplayName().replace(ChatColor.YELLOW + "", "").trim();
                if (clicked.getType() == Material.PAPER) {  // Reason
                    // Store reason, but for simplicity, execute with default time or add second click
                    player.performCommand(action + " " + target + " permanent " + selected);
                } else if (clicked.getType() == Material.CLOCK) {  // Time
                    player.performCommand(action + " " + target + " " + selected + " default_reason");
                }
                player.closeInventory();
                return;
            }

            // Handle Admin Sub-GUI clicks - similar prefill
            if (event.getView().getTitle().equals(ChatColor.DARK_RED + "Admin Tools")) {
                event.setCancelled(true);
                if (clicked.getType() == Material.BOOK) {
                    player.closeInventory();
                    player.chat("/setrank ");
                } else if (clicked.getType() == Material.WRITABLE_BOOK) {
                    player.closeInventory();
                    player.performCommand("core reload");
                }
                return;
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (item.getType() == Material.BOW && activeGadget.get(player.getUniqueId()) == Material.BOW) {
                event.setCancelled(true);
                fireParticleCannon(player);
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (doubleJumpEnabled.getOrDefault(uuid, false) && player.getVelocity().getY() > 0 && !player.isOnGround()) {
            player.setVelocity(player.getVelocity().add(new Vector(0, 0.5, 0)));
            player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1, 1);
        }
    }

    // Cosmetics Functions - Full Working
    private void toggleDoubleJump(Player player) {
        UUID uuid = player.getUniqueId();
        boolean enabled = !doubleJumpEnabled.getOrDefault(uuid, false);
        doubleJumpEnabled.put(uuid, enabled);
        player.sendMessage(enabled ? ChatColor.AQUA + "Double Jump enabled!" : ChatColor.RED + "Double Jump disabled!");
    }

    private void toggleParticles(Player player) {
        UUID uuid = player.getUniqueId();
        boolean enabled = !particlesEnabled.getOrDefault(uuid, false);
        particlesEnabled.put(uuid, enabled);
        player.sendMessage(enabled ? ChatColor.AQUA + "Particles enabled!" : ChatColor.RED + "Particles disabled!");
        if (enabled) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!particlesEnabled.getOrDefault(uuid, false) || !player.isOnline()) {
                        cancel();
                        return;
                    }
                    player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0.1);
                }
            }.runTaskTimer(Core.getInstance(), 0, 10);  // Every 0.5 sec
        }
    }

    private void fireParticleCannon(Player player) {
        player.sendMessage(ChatColor.AQUA + "Firing Particle Cannon!");
        Firework fw = (Firework) player.getWorld().spawnEntity(player.getEyeLocation(), EntityType.FIREWORK_ROCKET);
        FireworkMeta fwm = fw.getFireworkMeta();
        fwm.addEffect(FireworkEffect.builder().withColor(org.bukkit.Color.RED).withTrail().build());
        fwm.setPower(1);
        fw.setFireworkMeta(fwm);
        fw.setVelocity(player.getLocation().getDirection().multiply(2));
    }

    private void openCosmeticsGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.GOLD + "Cosmetics & Gadgets");
        // Double Jump
        ItemStack doubleJump = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta djMeta = doubleJump.getItemMeta();
        djMeta.setDisplayName(ChatColor.AQUA + "Double Jump (Click to Toggle)");
        doubleJump.setItemMeta(djMeta);
        gui.setItem(10, doubleJump);

        // Particles
        ItemStack particles = new ItemStack(Material.FEATHER);
        ItemMeta pMeta = particles.getItemMeta();
        pMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "Particles Trail (Click to Toggle)");
        particles.setItemMeta(pMeta);
        gui.setItem(12, particles);

        // Particle Cannon
        ItemStack cannon = new ItemStack(Material.BOW);
        ItemMeta cMeta = cannon.getItemMeta();
        cMeta.setDisplayName(ChatColor.RED + "Particle Cannon (Click to Fire)");
        cannon.setItemMeta(cMeta);
        gui.setItem(14, cannon);

        // Pets
        ItemStack pets = new ItemStack(Material.EGG);
        ItemMeta petMeta = pets.getItemMeta();
        petMeta.setDisplayName(ChatColor.GREEN + "Pet (Click to Toggle)");
        pets.setItemMeta(petMeta);
        gui.setItem(16, pets);

        // Decor glass - pleasing but unclickable (handled in click event)
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, glass);
            }
        }

        player.openInventory(gui);
    }

    private void openModGUI(Player player, boolean shiftClick) {
        if (shiftClick && RankUtil.getRankLevel(player) >= 5) {
            Inventory gui = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "Admin Tools");
            ItemStack setRank = new ItemStack(Material.BOOK);
            ItemMeta rankMeta = setRank.getItemMeta();
            rankMeta.setDisplayName(ChatColor.GREEN + "Set Rank");
            setRank.setItemMeta(rankMeta);
            gui.setItem(10, setRank);

            ItemStack reload = new ItemStack(Material.WRITABLE_BOOK);
            ItemMeta reloadMeta = reload.getItemMeta();
            reloadMeta.setDisplayName(ChatColor.BLUE + "Reload Config");
            reload.setItemMeta(reloadMeta);
            gui.setItem(12, reload);

            // Optional: Decor glass - comment out if you don't want fillers
            ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta glassMeta = glass.getItemMeta();
            glassMeta.setDisplayName(" ");
            glass.setItemMeta(glassMeta);
            for (int i = 0; i < gui.getSize(); i++) {
                if (gui.getItem(i) == null) {
                    gui.setItem(i, glass);
                }
            }

            player.openInventory(gui);
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.RED + "Moderation Tools");
        ItemStack mute = new ItemStack(Material.CLOCK);
        ItemMeta muteMeta = mute.getItemMeta();
        muteMeta.setDisplayName(ChatColor.BLUE + "Mute Player");
        mute.setItemMeta(muteMeta);
        gui.setItem(10, mute);

        ItemStack kick = new ItemStack(Material.LEATHER_BOOTS);
        ItemMeta kickMeta = kick.getItemMeta();
        kickMeta.setDisplayName(ChatColor.YELLOW + "Kick Player");
        kick.setItemMeta(kickMeta);
        gui.setItem(12, kick);

        ItemStack ban = new ItemStack(Material.BARRIER);
        ItemMeta banMeta = ban.getItemMeta();
        banMeta.setDisplayName(ChatColor.DARK_RED + "Ban Player");
        ban.setItemMeta(banMeta);
        gui.setItem(14, ban);

        ItemStack history = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta historyMeta = history.getItemMeta();
        historyMeta.setDisplayName(ChatColor.GREEN + "Player History");
        history.setItemMeta(historyMeta);
        gui.setItem(16, history);

        // Optional: Decor glass - comment out if you don't want fillers
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, glass);
            }
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        doubleJumpEnabled.remove(uuid);
        particlesEnabled.remove(uuid);
        activeGadget.remove(uuid);
        // Clean up pets if needed
    }
}