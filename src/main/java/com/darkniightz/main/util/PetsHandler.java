package com.darkniightz.main.util;

import com.darkniightz.main.Core;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Location;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.World;

import java.util.*;

public class PetsHandler implements Listener {

    private static final Map<UUID, Entity> playerPets = new HashMap<>();
    private static final Map<UUID, String> petTypes = new HashMap<>();  // Type for custom looks/behaviors
    private static final Map<UUID, Long> mountTimes = new HashMap<>();  // For mount delay

    private static final Set<String> VALID_PET_TYPES = Set.of("dog", "squid", "zombie", "bee", "cat", "mooshroom", "horse", "panda", "axolotl", "allay");

    public static void openPetsMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 36, ChatColor.GREEN + "Pets Menu");
        addPetItem(gui, 12, Material.BONE, ChatColor.WHITE + "Dog");
        addPetItem(gui, 13, Material.INK_SAC, ChatColor.DARK_BLUE + "Squid");
        addPetItem(gui, 14, Material.ROTTEN_FLESH, ChatColor.GREEN + "Zombie");
        addPetItem(gui, 15, Material.HONEYCOMB, ChatColor.YELLOW + "Bee");
        addPetItem(gui, 21, Material.STRING, ChatColor.GRAY + "Cat");
        addPetItem(gui, 22, Material.RED_MUSHROOM, ChatColor.RED + "Mooshroom");
        addPetItem(gui, 23, Material.SADDLE, ChatColor.LIGHT_PURPLE + "Horse");

        int rank = RankUtil.getRankLevel(player);
        if (rank >= 3) {  // Mod+
            addPetItem(gui, 24, Material.BAMBOO, ChatColor.GREEN + "Panda (Mod+)");
        }
        if (rank >= 4) {  // Sr.Mod+
            addPetItem(gui, 30, Material.BUCKET, ChatColor.AQUA + "Axolotl (Sr.Mod+)");
        }
        if (rank >= 5) {  // Admin+
            addPetItem(gui, 31, Material.AMETHYST_SHARD, ChatColor.LIGHT_PURPLE + "Allay (Admin+)");
        }

        player.openInventory(gui);
    }

    private static void addPetItem(Inventory gui, int slot, Material material, String name) {
        ItemStack pet = new ItemStack(material);
        ItemMeta meta = pet.getItemMeta();
        meta.setDisplayName(name);
        pet.setItemMeta(meta);
        gui.setItem(slot, pet);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(ChatColor.GREEN + "Pets Menu") && event.getWhoClicked() instanceof Player) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;

            String displayName = clicked.getItemMeta().getDisplayName();
            String type = ChatColor.stripColor(displayName).split("\\(")[0].trim().toLowerCase();
            if (!VALID_PET_TYPES.contains(type)) return;
            spawnPet(player, type);
            player.closeInventory();
        }
    }

    private void spawnPet(Player player, String type) {
        UUID uuid = player.getUniqueId();
        Entity oldPet = playerPets.remove(uuid);
        if (oldPet != null) oldPet.remove();

        EntityType entityType = switch (type) {
            case "dog" -> EntityType.WOLF;
            case "squid" -> EntityType.SQUID;
            case "zombie" -> EntityType.ZOMBIE;
            case "bee" -> EntityType.BEE;
            case "cat" -> EntityType.CAT;
            case "mooshroom" -> EntityType.MOOSHROOM;
            case "horse" -> EntityType.HORSE;
            case "panda" -> EntityType.PANDA;
            case "axolotl" -> EntityType.AXOLOTL;
            case "allay" -> EntityType.ALLAY;
            default -> EntityType.PIG;  // Fallback
        };

        Bukkit.getLogger().info("Spawning pet for " + player.getName() + ": type='" + type + "', entityType='" + entityType + "'");

        Location spawnLocation = player.getEyeLocation();

        Entity pet = player.getWorld().spawnEntity(spawnLocation, entityType);
        pet.setCustomName(ChatColor.AQUA + player.getName() + "'s " + type);
        pet.setCustomNameVisible(true);
        pet.setInvulnerable(true);

        // Baby for zombie
        if (type.equalsIgnoreCase("zombie")) {
            ((Zombie) pet).setBaby(true);
        }

        // Tameable mobs set owner
        if (pet instanceof Tameable) {
            ((Tameable) pet).setOwner(player);
            if (pet instanceof Sittable) {
                ((Sittable) pet).setSitting(false);
            }
        }

        // Horse-specific armor based on rank
        if (type.equalsIgnoreCase("horse")) {
            Horse horse = (Horse) pet;
            horse.setTamed(true);
            horse.setOwner(player);
            int rank = RankUtil.getRankLevel(player);
            ItemStack armor = null;
            if (rank >= 5) {
                armor = new ItemStack(Material.DIAMOND_HORSE_ARMOR);
            } else if (rank >= 4) {
                armor = new ItemStack(Material.GOLDEN_HORSE_ARMOR);
            } else if (rank >= 3) {
                armor = new ItemStack(Material.IRON_HORSE_ARMOR);
            }
            if (armor != null) {
                horse.getInventory().setArmor(armor);
            }
        }

        // For squid and axolotl outside water, keep air high
        if (type.equalsIgnoreCase("squid") || type.equalsIgnoreCase("axolotl")) {
            if (pet instanceof LivingEntity livingPet) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (pet.isDead() || !playerPets.containsKey(uuid)) {
                            cancel();
                            return;
                        }
                        livingPet.setRemainingAir(300);  // Max air to prevent suffocation
                    }
                }.runTaskTimer(Core.getInstance(), 0, 20);  // Every second
            }
        }

        playerPets.put(uuid, pet);
        petTypes.put(uuid, type);

        player.sendMessage(ChatColor.GREEN + type + " spawned!");
        startFollowTask(player, pet);
    }

    private void startFollowTask(Player player, Entity pet) {
        UUID uuid = player.getUniqueId();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !playerPets.containsKey(uuid)) {
                    if (pet != null) pet.remove();
                    cancel();
                    return;
                }
                String petType = petTypes.get(uuid);
                Location playerLoc = player.getLocation();
                double distance = pet.getLocation().distance(playerLoc);

                if (player.getVehicle() == pet) {
                    // Control while riding
                    Vector direction = player.getLocation().getDirection().normalize().multiply(0.3);
                    pet.setVelocity(direction);
                } else if (distance > 5) {
                    // Teleport closer if too far, with some randomness
                    Location target = playerLoc.add((Math.random() - 0.5) * 2, 0, (Math.random() - 0.5) * 2);
                    target.getChunk().load(true);
                    Location safeLocation = findSafeLocation(target, petType);
                    if (safeLocation != null) {
                        pet.teleport(safeLocation);
                    } else {
                        pet.teleport(target);
                    }
                }
                // Else, let it wander naturally
            }
        }.runTaskTimer(Core.getInstance(), 0, 20);  // Every 1 sec
    }

    private Location findSafeLocation(Location start, String petType) {
        World world = start.getWorld();
        int maxHeight = world.getMaxHeight();
        int minHeight = world.getMinHeight();
        Location check = start.clone();

        for (int y = 0; y <= 5; y++) {
            check.setY(start.getY() + y);
            if (check.getY() <= maxHeight && isSafeLocation(check, petType)) return check;
            check.setY(start.getY() - y);
            if (check.getY() >= minHeight && isSafeLocation(check, petType)) return check;
        }
        return null;
    }

    private boolean isSafeLocation(Location loc, String petType) {
        Block below = loc.getBlock().getRelative(0, -1, 0);
        Block at = loc.getBlock();
        Block above = loc.getBlock().getRelative(0, 1, 0);

        if (petType.equalsIgnoreCase("squid") || petType.equalsIgnoreCase("axolotl")) {
            // Prefer water but allow air since invulnerable
            return at.isPassable() && above.isPassable();
        } else if (petType.equalsIgnoreCase("bee") || petType.equalsIgnoreCase("allay")) {
            return at.isPassable() && above.isPassable();
        } else {
            return below.getType().isSolid() && !below.isLiquid() && at.isPassable() && above.isPassable();
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (clicked == playerPets.get(uuid)) {
            event.setCancelled(true);
            if (player.isSneaking()) {  // Ride on shift-click
                clicked.addPassenger(player);
                mountTimes.put(uuid, System.currentTimeMillis());
                player.sendMessage(ChatColor.AQUA + "Riding pet! Shift again to dismount.");
            }
        }
    }

    @EventHandler
    public void onVehicleExit(VehicleExitEvent event) {
        if (event.getExited() instanceof Player player) {
            Entity vehicle = event.getVehicle();
            UUID uuid = player.getUniqueId();
            if (vehicle == playerPets.get(uuid)) {
                Long mountTime = mountTimes.get(uuid);
                if (mountTime != null && System.currentTimeMillis() - mountTime < 1000) {  // 1 second delay
                    event.setCancelled(true);
                } else {
                    mountTimes.remove(uuid);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Entity pet = playerPets.remove(uuid);
        if (pet != null) pet.remove();
        petTypes.remove(uuid);
        mountTimes.remove(uuid);
    }
}