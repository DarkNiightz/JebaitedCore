package com.darkniightz.core.store;

import java.util.Locale;

/**
 * YAML-defined purchasable package (Stripe Checkout).
 */
public final class StorePackage {
    private final String id;
    private final String displayName;
    private final String description;
    private final int amountCents;
    private final String currency;
    /** Minimum primary rank required to open/buy (ladder). Empty = no gate. */
    private final String minRankToPurchase;
    private final String donorRankGrant;
    private final Integer cosmeticCoinsGrant;
    private final Double economyGrant;
    private final String primaryRankGrant;

    public StorePackage(
            String id,
            String displayName,
            String description,
            int amountCents,
            String currency,
            String minRankToPurchase,
            String donorRankGrant,
            Integer cosmeticCoinsGrant,
            Double economyGrant,
            String primaryRankGrant) {
        this.id = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        this.displayName = displayName == null ? id : displayName;
        this.description = description == null ? "" : description;
        this.amountCents = Math.max(50, amountCents);
        this.currency = currency == null || currency.isBlank() ? "usd" : currency.trim().toLowerCase(Locale.ROOT);
        this.minRankToPurchase = minRankToPurchase == null ? "" : minRankToPurchase.trim().toLowerCase(Locale.ROOT);
        this.donorRankGrant = donorRankGrant == null || donorRankGrant.isBlank() ? null : donorRankGrant.trim().toLowerCase(Locale.ROOT);
        this.cosmeticCoinsGrant = cosmeticCoinsGrant;
        this.economyGrant = economyGrant;
        this.primaryRankGrant = primaryRankGrant == null || primaryRankGrant.isBlank() ? null : primaryRankGrant.trim().toLowerCase(Locale.ROOT);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public int amountCents() {
        return amountCents;
    }

    public String currency() {
        return currency;
    }

    public String minRankToPurchase() {
        return minRankToPurchase;
    }

    public String donorRankGrant() {
        return donorRankGrant;
    }

    public Integer cosmeticCoinsGrant() {
        return cosmeticCoinsGrant;
    }

    public Double economyGrant() {
        return economyGrant;
    }

    public String primaryRankGrant() {
        return primaryRankGrant;
    }

    public boolean hasAnyGrant() {
        return donorRankGrant != null
                || (cosmeticCoinsGrant != null && cosmeticCoinsGrant > 0)
                || (economyGrant != null && economyGrant > 0)
                || primaryRankGrant != null;
    }
}
