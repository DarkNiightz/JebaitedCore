package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.main.JebaitedCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class GeneratePasswordCommand implements CommandExecutor {
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;
    private final SecureRandom random = new SecureRandom();
    private final Set<UUID> claimed = new HashSet<>();
    private String panelUrlCached;
    private String provisionKeyCached;

    public GeneratePasswordCommand(ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
        FileConfiguration cfg = JebaitedCore.getInstance().getConfig();
        if (cfg.isConfigurationSection("webpanel.claimed")) {
            for (String key : cfg.getConfigurationSection("webpanel.claimed").getKeys(false)) {
                try {
                    if (cfg.getBoolean("webpanel.claimed." + key, false)) {
                        claimed.add(UUID.fromString(key));
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cIn-game only.");
            return true;
        }

        PlayerProfile actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
        boolean bypass = devMode != null && devMode.isActive(p.getUniqueId());
        if (!bypass && !ranks.isAtLeast(actor.getPrimaryRank(), "moderator")) {
            sender.sendMessage(Messages.noPerm());
            return true;
        }

        FileConfiguration cfg = JebaitedCore.getInstance().getConfig();
        if (claimed.contains(p.getUniqueId())) {
            tryCreateTicket(p, cfg);
            sendAlreadyProvisioned(p, cfg.getString("webpanel.url", "http://localhost:3001"));
            return true;
        }

        String panelUrl = cfg.getString("webpanel.url", "http://localhost:3001");
        String provisionKey = cfg.getString("webpanel.provision_secret", "");
        if (provisionKey == null || provisionKey.isBlank()) {
            sender.sendMessage("§cWeb panel provision secret not set. Configure webpanel.provision_secret in config.yml");
            return true;
        }
        this.panelUrlCached = panelUrl;
        this.provisionKeyCached = provisionKey;

        // Map in-game rank to web panel role
        String role = "helper";
        if (ranks.isAtLeast(actor.getPrimaryRank(), "owner")) role = "owner";
        else if (ranks.isAtLeast(actor.getPrimaryRank(), "developer")) role = "developer";
        else if (ranks.isAtLeast(actor.getPrimaryRank(), "admin")) role = "admin";
        else if (ranks.isAtLeast(actor.getPrimaryRank(), "moderator")) role = "moderator";

        String password = generatePassword(12);
        try {
            provisionAccount(panelUrl, provisionKey, p.getName(), password, role);
            claimed.add(p.getUniqueId());
            cfg.set("webpanel.claimed." + p.getUniqueId(), true);
            JebaitedCore.getInstance().saveConfig();
            sendProvisionSuccess(p, panelUrl, p.getName(), password);
        } catch (Exception e) {
            sender.sendMessage("§cFailed to provision account: §e" + e.getMessage());
        }
        return true;
    }

    private String generatePassword(int length) {
        final String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz0123456789!@#$%^&*";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void sendProvisionSuccess(Player player, String panelUrl, String username, String password) {
        Component link = Component.text("Open Panel", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.openUrl(panelUrl))
                .hoverEvent(HoverEvent.showText(Component.text("Click to open " + panelUrl)));
        Component pass = Component.text(password, NamedTextColor.GREEN)
                .clickEvent(ClickEvent.copyToClipboard(password))
                .hoverEvent(HoverEvent.showText(Component.text("Click to copy password")));
        player.sendMessage(Component.text("Web panel account generated.", NamedTextColor.GREEN));
        player.sendMessage(Component.text("Username: ", NamedTextColor.GRAY).append(Component.text(username, NamedTextColor.AQUA)));
        player.sendMessage(Component.text("Password: ", NamedTextColor.GRAY).append(pass));
        player.sendMessage(Component.text("Link: ", NamedTextColor.GRAY).append(link));
        player.sendMessage(Component.text("On first login, change your password immediately.", NamedTextColor.YELLOW));
    }

    private void sendAlreadyProvisioned(Player player, String panelUrl) {
        Component link = Component.text("Open Panel", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.openUrl(panelUrl))
                .hoverEvent(HoverEvent.showText(Component.text("Click to open " + panelUrl)));
        Component ticket = Component.text("Forgotten password? Open a reset ticket.", NamedTextColor.GOLD)
                .clickEvent(ClickEvent.openUrl(panelUrl + "/developer"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to open ticket page")));
        player.sendMessage(Component.text("You already generated your web panel login. This command is one-time.", NamedTextColor.RED));
        player.sendMessage(Component.text("Use the panel link below:", NamedTextColor.GRAY));
        player.sendMessage(link);
        player.sendMessage(ticket);
    }

    private void provisionAccount(String baseUrl, String secret, String username, String password, String role) throws Exception {
        URL url = new URL(baseUrl + "/api/auth/provision");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("x-provision-key", secret);
        conn.setDoOutput(true);
        String payload = String.format("{\"username\":\"%s\",\"password\":\"%s\",\"role\":\"%s\"}", username, password, role);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            String err = readResponse(conn);
            throw new IllegalStateException("HTTP " + code + ": " + err);
        }
    }

    private String readResponse(HttpURLConnection conn) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void tryCreateTicket(Player player, FileConfiguration cfg) {
        String panelUrl = cfg.getString("webpanel.url", panelUrlCached != null ? panelUrlCached : "http://localhost:3001");
        String provisionKey = cfg.getString("webpanel.provision_secret", provisionKeyCached != null ? provisionKeyCached : "");
        if (provisionKey == null || provisionKey.isBlank()) return;
        PlayerProfile profile = profiles.getOrCreate(player, ranks.getDefaultGroup());
        String rank = profile != null ? profile.getPrimaryRank() : ranks.getDefaultGroup();
        try {
            URL url = new URL(panelUrl + "/api/dev/password-tickets");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-provision-key", provisionKey);
            conn.setDoOutput(true);
            String payload = String.format("{\"username\":\"%s\",\"rank\":\"%s\"}", player.getName(), rank);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            conn.getResponseCode();
        } catch (Exception ignored) {
        }
    }
}
