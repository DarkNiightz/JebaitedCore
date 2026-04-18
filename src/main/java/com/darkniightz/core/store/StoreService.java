package com.darkniightz.core.store;

import com.darkniightz.core.Messages;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.system.EconomyManager;
import com.darkniightz.main.JebaitedCore;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Stripe Checkout + YAML catalog + fulfillment.
 */
public final class StoreService {
    private static final Set<String> DONOR_RANKS = Set.of("gold", "diamond", "legend", "grandmaster");

    private final JebaitedCore plugin;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final EconomyManager economy;
    private final StoreOrderDao orders;
    private volatile Map<String, StorePackage> catalog = Map.of();

    public StoreService(JebaitedCore plugin) {
        this.plugin = plugin;
        this.profiles = plugin.getProfileStore();
        this.ranks = plugin.getRankManager();
        this.economy = plugin.getEconomyManager();
        this.orders = new StoreOrderDao(plugin.getDatabaseManager(), plugin.getLogger());
    }

    public void reload() {
        this.catalog = StoreCatalogLoader.load(plugin);
        configureStripeKey();
    }

    private void configureStripeKey() {
        String key = System.getenv("STRIPE_SECRET_KEY");
        if (key == null || key.isBlank()) {
            key = plugin.getConfig().getString("store.stripe.secret_key", "");
        }
        if (key != null && !key.isBlank()) {
            Stripe.apiKey = key.trim();
        }
    }

    public boolean isStoreEnabled() {
        return plugin.getConfig().getBoolean("store.enabled", false)
                && plugin.getConfig().getBoolean("store.stripe.enabled", false)
                && Stripe.apiKey != null
                && !Stripe.apiKey.isBlank();
    }

    public Map<String, StorePackage> catalog() {
        return catalog;
    }

    public boolean canViewPackage(Player player, StorePackage pkg) {
        String min = pkg.minRankToPurchase();
        if (min == null || min.isBlank()) {
            return true;
        }
        PlayerProfile p = profiles.getOrCreate(player, ranks.getDefaultGroup());
        String r = p.getPrimaryRank() == null ? ranks.getDefaultGroup() : p.getPrimaryRank();
        return ranks.isAtLeast(r, min);
    }

    /**
     * @return checkout URL or null on failure
     */
    public String beginCheckout(Player player, String packageId) {
        if (!isStoreEnabled()) {
            return null;
        }
        StorePackage pkg = catalog.get(packageId.toLowerCase(Locale.ROOT));
        if (pkg == null || !canViewPackage(player, pkg)) {
            return null;
        }
        return createStripeSession(player.getUniqueId(), pkg);
    }

    /**
     * Same as {@link #beginCheckout(Player, String)} but uses only the UUID — call from async after rank checks on the main thread.
     */
    public String beginCheckoutUuid(UUID uuid, String packageId) {
        if (!isStoreEnabled()) {
            return null;
        }
        StorePackage pkg = catalog.get(packageId.toLowerCase(Locale.ROOT));
        if (pkg == null) {
            return null;
        }
        return createStripeSession(uuid, pkg);
    }

    private String createStripeSession(UUID uuid, StorePackage pkg) {
        configureStripeKey();

        String success = plugin.getConfig().getString("store.stripe.success_url", "");
        String cancel = plugin.getConfig().getString("store.stripe.cancel_url", "");
        if (success.isBlank() || cancel.isBlank()) {
            plugin.getLogger().warning("Store: configure store.stripe.success_url and cancel_url");
            return null;
        }

        profiles.getOrCreate(Bukkit.getOfflinePlayer(uuid), ranks.getDefaultGroup());

        try {
            SessionCreateParams.LineItem lineItem =
                    SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(
                                    SessionCreateParams.LineItem.PriceData.builder()
                                            .setCurrency(pkg.currency())
                                            .setUnitAmount((long) pkg.amountCents())
                                            .setProductData(
                                                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                            .setName(pkg.displayName())
                                                            .setDescription(
                                                                    pkg.description().length() > 500
                                                                            ? pkg.description().substring(0, 500)
                                                                            : pkg.description())
                                                            .build())
                                            .build())
                            .build();

            SessionCreateParams params =
                    SessionCreateParams.builder()
                            .setMode(SessionCreateParams.Mode.PAYMENT)
                            .setSuccessUrl(success)
                            .setCancelUrl(cancel)
                            .addLineItem(lineItem)
                            .putMetadata("minecraft_uuid", uuid.toString())
                            .putMetadata("package_id", pkg.id())
                            .setClientReferenceId(uuid + ":" + pkg.id())
                            .build();

            Session session = Session.create(params);
            return session.getUrl();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Stripe checkout failed", e);
            return null;
        }
    }

    public String handleStripeWebhook(byte[] payload, String sigHeader) throws SignatureVerificationException {
        String secret = System.getenv("STRIPE_WEBHOOK_SECRET");
        if (secret == null || secret.isBlank()) {
            secret = plugin.getConfig().getString("store.stripe.webhook_secret", "");
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("webhook secret not configured");
        }
        Event event = Webhook.constructEvent(new String(payload, StandardCharsets.UTF_8), sigHeader, secret.trim());
        if (!"checkout.session.completed".equals(event.getType())) {
            return "ignored";
        }
        EventDataObjectDeserializer des = event.getDataObjectDeserializer();
        if (!des.getObject().isPresent()) {
            return "no_object";
        }
        Object obj = des.getObject().get();
        if (!(obj instanceof Session session)) {
            return "not_session";
        }
        String sid = session.getId();
        Map<String, String> meta = session.getMetadata();
        String uuidStr = meta != null ? meta.get("minecraft_uuid") : null;
        String pkgId = meta != null ? meta.get("package_id") : null;
        if (uuidStr == null || pkgId == null) {
            orders.markError(sid, "missing_metadata");
            return "bad_meta";
        }
        UUID playerUuid;
        try {
            playerUuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            orders.markError(sid, "bad_uuid");
            return "bad_uuid";
        }

        StorePackage pkg = catalog.get(pkgId.toLowerCase(Locale.ROOT));
        if (pkg == null) {
            orders.markError(sid, "unknown_package");
            return "unknown_pkg";
        }

        Long total = session.getAmountTotal();
        if (total == null || total.intValue() != pkg.amountCents()) {
            orders.markError(sid, "amount_mismatch");
            return "amount_mismatch";
        }

        String cur = session.getCurrency() != null ? session.getCurrency() : "usd";
        if (!cur.equalsIgnoreCase(pkg.currency())) {
            orders.markError(sid, "currency_mismatch");
            return "currency_mismatch";
        }

        String pi = session.getPaymentIntent();
        if (!orders.tryClaimNewOrder(sid, playerUuid, pkg.id(), pkg.amountCents(), cur.toLowerCase(Locale.ROOT), pi)) {
            return "duplicate";
        }

        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            try {
                                applyGrants(playerUuid, pkg);
                                orders.markFulfilled(sid);
                                notifyPlayer(playerUuid, pkg);
                            } catch (Exception ex) {
                                plugin.getLogger().log(Level.SEVERE, "Store fulfillment failed", ex);
                                orders.markError(sid, ex.getMessage() == null ? "fulfill" : ex.getMessage());
                            }
                        });
        return "ok";
    }

    private void notifyPlayer(UUID uuid, StorePackage pkg) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline()) {
            online.sendMessage(Messages.prefixed("§aPurchase complete: §f" + pkg.displayName() + "§a. Thank you!"));
        }
    }

    private void applyGrants(UUID uuid, StorePackage pkg) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        PlayerProfile tp = profiles.getOrCreate(op, ranks.getDefaultGroup());
        if (tp == null) {
            throw new IllegalStateException("no profile");
        }

        if (pkg.donorRankGrant() != null) {
            if (!DONOR_RANKS.contains(pkg.donorRankGrant())) {
                throw new IllegalStateException("invalid donor rank in package");
            }
            if (tp.getDonorRank() != null) {
                plugin.getLogger().warning("Store: " + uuid + " already has donor " + tp.getDonorRank() + " — skipping donor grant.");
            } else {
                tp.setDonorRank(pkg.donorRankGrant());
                if ("pleb".equalsIgnoreCase(tp.getPrimaryRank())) {
                    tp.setPrimaryRank(pkg.donorRankGrant());
                }
                tp.setRankDisplayMode("donor");
            }
        }

        if (pkg.primaryRankGrant() != null) {
            String want = pkg.primaryRankGrant();
            if (!ranks.isRecognizedRank(want)) {
                plugin.getLogger().warning("Store: unknown rank in package: " + want);
            } else {
                tp.setPrimaryRank(want);
            }
        }

        if (pkg.cosmeticCoinsGrant() != null && pkg.cosmeticCoinsGrant() > 0) {
            tp.addCosmeticCoins(pkg.cosmeticCoinsGrant());
        }

        if (pkg.economyGrant() != null && pkg.economyGrant() > 0) {
            economy.addBalance(op, pkg.economyGrant());
        }

        profiles.save(uuid);
    }

}
