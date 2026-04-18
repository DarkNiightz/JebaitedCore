package com.darkniightz.core.gui;

import com.darkniightz.core.Messages;
import com.darkniightz.core.store.StorePackage;
import com.darkniightz.core.store.StoreService;
import com.darkniightz.main.JebaitedCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.UUID;
import java.util.List;
import java.util.Locale;

/**
 * Stripe-backed donate / supporter packages (YAML catalog).
 */
public final class DonateMenu extends BaseMenu {

    private static final int[] GRID = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final JebaitedCore plugin;
    private final StoreService store;

    public DonateMenu(JebaitedCore plugin, StoreService store) {
        super(plugin, plugin.getConfig().getString("store.menu_title", "§8Support the server"), 54);
        this.plugin = plugin;
        this.store = store;
    }

    @Override
    protected void populate(Player viewer) {
        for (int r = 0; r < 54; r++) {
            if (r < 9 || r > 44) {
                inventory.setItem(r, filler(Material.GRAY_STAINED_GLASS_PANE, " "));
            } else {
                boolean slot = false;
                for (int g : GRID) {
                    if (g == r) {
                        slot = true;
                        break;
                    }
                }
                if (!slot) {
                    inventory.setItem(r, filler(Material.BLACK_STAINED_GLASS_PANE, " "));
                }
            }
        }

        List<StorePackage> visible = new ArrayList<>();
        for (StorePackage p : store.catalog().values()) {
            if (store.canViewPackage(viewer, p)) {
                visible.add(p);
            }
        }

        int n = Math.min(visible.size(), GRID.length);
        for (int i = 0; i < n; i++) {
            StorePackage pkg = visible.get(i);
            ItemStack icon = new ItemStack(Material.GOLD_INGOT);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§6§l" + pkg.displayName());
                List<String> lore = new ArrayList<>();
                lore.add("§7" + formatMoney(pkg));
                lore.add("");
                for (String line : wrapDescription(pkg.description())) {
                    lore.add("§8" + line);
                }
                lore.add("");
                lore.add("§eClick §7to open Stripe checkout in chat");
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            inventory.setItem(GRID[i], icon);
        }

        inventory.setItem(
                49,
                new ItemBuilder(Material.BOOK)
                        .name("§7How it works")
                        .lore(
                                List.of(
                                        "§8Pay securely via Stripe.",
                                        "§8Rewards apply when payment completes.",
                                        "§8Use §f/link §8to connect Discord perks."))
                        .build());
    }

    private static List<String> wrapDescription(String desc) {
        if (desc == null || desc.isBlank()) {
            return List.of("§8—");
        }
        List<String> out = new ArrayList<>();
        String[] words = desc.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            if (line.length() + w.length() > 32 && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder();
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(w);
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }

    private static String formatMoney(StorePackage pkg) {
        String sym = switch (pkg.currency().toLowerCase(Locale.ROOT)) {
            case "usd" -> "$";
            case "eur" -> "€";
            case "gbp" -> "£";
            default -> pkg.currency().toUpperCase(Locale.ROOT) + " ";
        };
        double amt = pkg.amountCents() / 100.0;
        return sym + String.format(Locale.US, "%.2f", amt);
    }

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
        if (slot == 49) {
            return true;
        }
        int idx = -1;
        for (int i = 0; i < GRID.length; i++) {
            if (GRID[i] == slot) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            return true;
        }
        List<StorePackage> visible = new ArrayList<>();
        for (StorePackage p : store.catalog().values()) {
            if (store.canViewPackage(who, p)) {
                visible.add(p);
            }
        }
        if (idx >= visible.size()) {
            return true;
        }
        StorePackage pkg = visible.get(idx);
        UUID uuid = who.getUniqueId();
        who.sendMessage(Messages.prefixed("§7Creating a secure checkout session…"));
        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            String url = store.beginCheckoutUuid(uuid, pkg.id());
                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                Player online = Bukkit.getPlayer(uuid);
                                                if (online == null || !online.isOnline()) {
                                                    return;
                                                }
                                                online.closeInventory();
                                                if (url == null || url.isBlank()) {
                                                    online.sendMessage(
                                                            Messages.prefixed(
                                                                    "§cCheckout could not be started. Ask staff or try again later."));
                                                    return;
                                                }
                                                Component link =
                                                        Component.text("Open secure checkout", NamedTextColor.GREEN, TextDecoration.BOLD)
                                                                .hoverEvent(
                                                                        HoverEvent.showText(
                                                                                Component.text("Opens Stripe in your browser", NamedTextColor.GRAY)))
                                                                .clickEvent(ClickEvent.openUrl(url));
                                                LegacyComponentSerializer leg = LegacyComponentSerializer.legacySection();
                                                online.sendMessage(
                                                        leg.deserialize(Messages.prefix())
                                                                .append(Component.text(" [ ", NamedTextColor.DARK_GRAY))
                                                                .append(link)
                                                                .append(Component.text(" ]", NamedTextColor.DARK_GRAY)));
                                                online.sendMessage(
                                                        Messages.prefixed(
                                                                "§7Click the green link. You can return to Minecraft after paying."));
                                            });
                        });
        return true;
    }

    private static ItemStack filler(Material m, String name) {
        return new ItemBuilder(m).name(name).build();
    }
}
