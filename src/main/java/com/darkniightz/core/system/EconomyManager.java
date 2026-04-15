package com.darkniightz.core.system;

import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class EconomyManager {

    private final Plugin plugin;
    private final ProfileStore profiles;
    private final RankManager ranks;

    public EconomyManager(Plugin plugin, ProfileStore profiles, RankManager ranks) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.ranks = ranks;
    }

    public double getBalance(OfflinePlayer player) {
        PlayerProfile profile = profiles.getOrCreate(player, ranks.getDefaultGroup());
        return profile == null ? 0D : profile.getBalance();
    }

    public void setBalance(OfflinePlayer player, double value) {
        PlayerProfile profile = profiles.getOrCreate(player, ranks.getDefaultGroup());
        if (profile == null) return;
        profile.setBalance(round(value));
        profiles.saveDeferred(player.getUniqueId());
    }

    public void addBalance(OfflinePlayer player, double value) {
        if (value <= 0D) return;
        PlayerProfile profile = profiles.getOrCreate(player, ranks.getDefaultGroup());
        if (profile == null) return;
        profile.addBalance(round(value));
        profiles.saveDeferred(player.getUniqueId());
    }

    public boolean removeBalance(OfflinePlayer player, double value) {
        if (value <= 0D) return true;
        PlayerProfile profile = profiles.getOrCreate(player, ranks.getDefaultGroup());
        if (profile == null) return false;
        boolean ok = profile.spendBalance(round(value));
        if (ok) profiles.saveDeferred(player.getUniqueId());
        return ok;
    }

    public boolean pay(Player from, Player to, double amount) {
        if (from == null || to == null || from.getUniqueId().equals(to.getUniqueId())) return false;
        double rounded = round(amount);
        if (rounded <= 0D) return false;

        PlayerProfile sender = profiles.getOrCreate(from, ranks.getDefaultGroup());
        PlayerProfile receiver = profiles.getOrCreate(to, ranks.getDefaultGroup());
        if (sender == null || receiver == null) return false;
        if (!sender.spendBalance(rounded)) return false;

        receiver.addBalance(rounded);
        profiles.saveDeferred(from.getUniqueId());
        profiles.saveDeferred(to.getUniqueId());
        return true;
    }

    public String format(double value) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
        String symbol = plugin.getConfig().getString("economy.currency_symbol", "$");
        if (symbol == null) symbol = "$";
        return symbol + format.format(round(value));
    }

    private double round(double value) {
        return Math.round(Math.max(0D, value) * 100D) / 100D;
    }
}
