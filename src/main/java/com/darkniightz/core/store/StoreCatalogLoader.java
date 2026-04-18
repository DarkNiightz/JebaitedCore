package com.darkniightz.core.store;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class StoreCatalogLoader {
    private StoreCatalogLoader() {}

    public static Map<String, StorePackage> load(JavaPlugin plugin) {
        if (!plugin.getConfig().getBoolean("store.enabled", false)) {
            return Map.of();
        }
        List<Map<?, ?>> raw = plugin.getConfig().getMapList("store.packages");
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, StorePackage> out = new LinkedHashMap<>();
        for (Map<?, ?> m : raw) {
            if (m == null) {
                continue;
            }
            Object idObj = m.get("id");
            if (idObj == null) {
                continue;
            }
            String id = String.valueOf(idObj).trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty()) {
                continue;
            }
            String name = str(m.get("display_name"), id);
            String desc = str(m.get("description"), "");
            int cents = numInt(m.get("amount_cents"), 0);
            String currency = str(m.get("currency"), "usd");
            String minRank = str(m.get("min_rank_to_purchase"), "");

            String donor = null;
            Integer coins = null;
            Double eco = null;
            String primary = null;
            Object grants = m.get("grants");
            if (grants instanceof Map<?, ?> g) {
                donor = emptyToNull(str(g.get("donor_rank"), ""));
                if (g.get("cosmetic_coins") != null) {
                    coins = Math.max(0, numInt(g.get("cosmetic_coins"), 0));
                }
                if (g.get("economy") != null) {
                    eco = numDouble(g.get("economy"), 0);
                }
                primary = emptyToNull(str(g.get("primary_rank"), ""));
            }
            StorePackage pkg =
                    new StorePackage(id, name, desc, cents, currency, minRank, donor, coins, eco, primary);
            if (!pkg.hasAnyGrant()) {
                plugin.getLogger().warning("Store: package " + id + " has no grants — skipped.");
                continue;
            }
            out.put(id, pkg);
        }
        return Collections.unmodifiableMap(out);
    }

    private static String str(Object o, String d) {
        return o == null ? d : String.valueOf(o);
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim().toLowerCase(Locale.ROOT);
    }

    private static int numInt(Object o, int d) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return d;
        }
    }

    private static double numDouble(Object o, double d) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return d;
        }
    }
}
