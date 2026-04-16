package com.darkniightz.core.system;

import com.darkniightz.main.JebaitedCore;
import com.darkniightz.core.gui.ItemBuilder;
import com.darkniightz.core.gui.PrivateVaultHolder;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.main.PlayerProfileDAO;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Manages multi-page private donor vaults.
 * Loads/saves from player_vaults table (V004 migration).
 */
public class PrivateVaultManager {

    private final JebaitedCore plugin;
    private final PlayerProfileDAO dao;

    // Cache: UUID -> (page -> Inventory)
    private final Map<UUID, Map<Integer, Inventory>> vaultCache = new HashMap<>();

    public PrivateVaultManager(JebaitedCore plugin) {
        this.plugin = plugin;
        this.dao = plugin.getPlayerProfileDAO();
    }

    /**
     * Opens the private vault for a player at the given page (0-based).
     */
    /**
     * Opens a target player's vault for a staff member to inspect.
     * If {@code readOnly} is true, all clicks are blocked and changes are discarded on close.
     * If false (moderator+ write access), changes save back to the target's vault.
     */
    public void openVaultForStaff(Player staff, UUID targetUUID, int requestedPage, boolean readOnly) {
        // Resolve target profile — may be offline
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerProfile targetProfile = plugin.getProfileStore().get(targetUUID);
            String donorRank = targetProfile != null ? targetProfile.getDonorRank() : null;
            // Fall back to grandmaster page count if we don't know the rank — better to show too many than too few
            int maxPages = getMaxPages(donorRank != null ? donorRank : "grandmaster");
            int page = Math.max(0, Math.min(requestedPage, maxPages - 1));

            final int fPage = page;
            final int fMaxPages = maxPages;
            final boolean fReadOnly = readOnly;

            getOrCreateVaultForStaff(targetUUID, page, maxPages, fReadOnly).thenAccept(inventory -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    staff.openInventory(inventory);
                    String targetName = Bukkit.getOfflinePlayer(targetUUID).getName();
                    if (targetName == null) targetName = targetUUID.toString().substring(0, 8);
                    staff.sendMessage("§6Inspecting vault of §e" + targetName
                            + " §8| §7Page " + (fPage + 1) + " §8/ §7" + fMaxPages
                            + (fReadOnly ? " §8[§7read-only§8]" : " §8[§ceditable§8]"));
                });
            });
        });
    }

    private CompletableFuture<Inventory> getOrCreateVaultForStaff(UUID targetUUID, int page, int maxPages, boolean readOnly) {
        return CompletableFuture.supplyAsync(() -> {
            // Load from database (bypasses the owner's live cache to avoid clobber)
            byte[] data = dao.loadVaultPage(targetUUID, page);
            String label = readOnly ? "§6Vault §8[§7View-Only§8] §7Page " + (page + 1)
                                    : "§6Vault §8[§cEditable§8] §7Page " + (page + 1);
            Inventory inv = Bukkit.createInventory(
                    new com.darkniightz.core.gui.PrivateVaultHolder(page, maxPages, targetUUID, readOnly),
                    54, label);

            if (data != null && data.length > 0) {
                try {
                    ItemStack[] items = ItemStack.deserializeItemsFromBytes(data);
                    inv.setContents(items);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to deserialize vault page " + page + " for " + targetUUID);
                }
            }

            addVaultBorder(inv, page, maxPages);
            return inv;
        });
    }

    public void openVault(Player player, int requestedPage) {
        PlayerProfile profile = plugin.getProfileStore().get(player.getUniqueId());
        if (profile == null) {
            player.sendMessage("§cYour profile is still loading...");
            return;
        }

        String donorRank = profile.getDonorRank();
        if (donorRank == null) {
            player.sendMessage("§cPrivate Vaults are a donor feature only.");
            return;
        }

        int maxPages = getMaxPages(donorRank);
        int page = Math.max(0, Math.min(requestedPage, maxPages - 1));

        getOrCreateVault(player.getUniqueId(), page, maxPages).thenAccept(inventory -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.openInventory(inventory);
                player.sendMessage("§6Private Vault §8| §7Page " + (page + 1) + " §8/ §7" + maxPages);
            });
        });
    }

    private CompletableFuture<Inventory> getOrCreateVault(UUID uuid, int page, int maxPages) {
        return CompletableFuture.supplyAsync(() -> {
            vaultCache.putIfAbsent(uuid, new HashMap<>());
            Map<Integer, Inventory> playerVaults = vaultCache.get(uuid);

            if (playerVaults.containsKey(page)) {
                return playerVaults.get(page);
            }

            // Load from database
            byte[] data = dao.loadVaultPage(uuid, page);
            Inventory inv = Bukkit.createInventory(new PrivateVaultHolder(page, maxPages), 54,
                    "§6Private Vault §8» §7Page " + (page + 1));

            if (data != null && data.length > 0) {
                try {
                    ItemStack[] items = ItemStack.deserializeItemsFromBytes(data);
                    inv.setContents(items);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to deserialize vault page " + page + " for " + uuid);
                }
            }

            addVaultBorder(inv, page, maxPages);
            playerVaults.put(page, inv);
            return inv;
        });
    }

    private void addVaultBorder(Inventory inv, int currentPage, int maxPages) {
        ItemStack glass = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 9; i++) {
            if (i != 4) inv.setItem(i + 45, glass);
        }

        if (currentPage > 0) {
            inv.setItem(45, new ItemBuilder(Material.ARROW)
                    .name("§a\u25c4 Previous Page")
                    .build());
        }

        inv.setItem(49, new ItemBuilder(Material.BARRIER)
                .name("§cClose Vault")
                .build());

        if (currentPage < maxPages - 1) {
            inv.setItem(53, new ItemBuilder(Material.ARROW)
                    .name("§a\u25ba Next Page")
                    .build());
        }
    }

    /**
     * Saves a single vault page (called on inventory close).
     */
    public void saveVaultPage(UUID uuid, int page, Inventory inventory) {
        byte[] data = serializeInventory(inventory);
        dao.saveVaultPageAsync(uuid, page, data);
    }

    /**
     * Saves all cached vaults for a player (e.g. on quit).
     */
    public void saveAllPlayerVaults(UUID uuid) {
        Map<Integer, Inventory> vaults = vaultCache.remove(uuid);
        if (vaults == null) return;

        for (Map.Entry<Integer, Inventory> entry : vaults.entrySet()) {
            saveVaultPage(uuid, entry.getKey(), entry.getValue());
        }
    }

    private byte[] serializeInventory(Inventory inv) {
        ItemStack[] contents = inv.getContents();
        return ItemStack.serializeItemsAsBytes(contents);
    }

    private int getMaxPages(String donorRank) {
        if (donorRank == null) return 1;
        return switch (donorRank.toLowerCase()) {
            case "grandmaster" -> 10;
            case "legend" -> 5;
            case "diamond" -> 3;
            case "gold" -> 1;
            default -> 1;
        };
    }

    public void clearCache(UUID uuid) {
        vaultCache.remove(uuid);
    }

    /**
     * Auto-loots a list of items into the player's vault pages.
     * Pages are loaded async (or fetched from cache), items are added on the main thread,
     * and modified pages are saved async. The overflowCallback is called on the main thread
     * with any items that didn't fit.
     *
     * @param uuid            owner UUID
     * @param effectiveRank   donor rank string (determines page count)
     * @param items           items to loot
     * @param overflowCallback called on main thread with items that didn't fit (may be empty)
     */
    public void autoLootToVault(UUID uuid, String effectiveRank, List<ItemStack> items, Consumer<List<ItemStack>> overflowCallback) {
        if (uuid == null || items == null || items.isEmpty()) {
            if (overflowCallback != null) overflowCallback.accept(new ArrayList<>());
            return;
        }

        int maxPages = getMaxPages(effectiveRank);
        List<CompletableFuture<Inventory>> futures = new ArrayList<>();
        for (int p = 0; p < maxPages; p++) {
            futures.add(getOrCreateVault(uuid, p, maxPages));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
            List<Inventory> pages = new ArrayList<>();
            for (CompletableFuture<Inventory> f : futures) {
                try { pages.add(f.join()); } catch (Exception ignored) {}
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                List<ItemStack> remaining = new ArrayList<>();
                for (ItemStack item : items) {
                    if (item != null && item.getType() != Material.AIR) remaining.add(item.clone());
                }

                for (Inventory page : pages) {
                    if (remaining.isEmpty()) break;
                    Map<Integer, ItemStack> leftover = page.addItem(remaining.toArray(new ItemStack[0]));
                    remaining = new ArrayList<>(leftover.values());
                }

                // Save modified pages async
                Map<Integer, Inventory> playerVaults = vaultCache.get(uuid);
                if (playerVaults != null) {
                    for (Map.Entry<Integer, Inventory> entry : playerVaults.entrySet()) {
                        saveVaultPage(uuid, entry.getKey(), entry.getValue());
                    }
                }

                if (overflowCallback != null) overflowCallback.accept(remaining);
            });
        });
    }

    /**
     * Returns fill percentage (0–100) across all cached vault pages.
     * Only counts usable slots (0–44 per page; bottom row is navigation).
     * Returns -1 if the vault is not cached (unknown fill).
     */
    public int getVaultFillPercent(UUID uuid, String effectiveRank) {
        Map<Integer, Inventory> playerVaults = vaultCache.get(uuid);
        if (playerVaults == null) return -1;

        int maxPages = getMaxPages(effectiveRank);
        int totalSlots = maxPages * 45;
        int filled = 0;
        for (int p = 0; p < maxPages; p++) {
            Inventory inv = playerVaults.get(p);
            if (inv == null) continue;
            for (int s = 0; s < 45; s++) {
                ItemStack item = inv.getItem(s);
                if (item != null && item.getType() != Material.AIR) filled++;
            }
        }
        return totalSlots == 0 ? 0 : (filled * 100) / totalSlots;
    }
}
