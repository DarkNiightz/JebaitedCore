package com.darkniightz.core.shop;

/**
 * One priced item in {@code server_shop_prices} (runtime cache).
 */
public record ShopPriceRow(
        String itemKey,
        String category,
        String displayName,
        double buyPrice,
        double sellPrice,
        int maxStack,
        int sortOrder,
        boolean enabled
) {
    public org.bukkit.Material material() {
        org.bukkit.Material m = org.bukkit.Material.matchMaterial(itemKey);
        if (m == null && itemKey != null && itemKey.contains(":")) {
            m = org.bukkit.Material.matchMaterial(itemKey.substring(itemKey.indexOf(':') + 1));
        }
        return m;
    }
}
