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
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GeneratePasswordCommand implements CommandExecutor {
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;
    private final SecureRandom random = new SecureRandom();
    private final Set<UUID> claimed = ConcurrentHashMap.newKeySet();

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
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Messages.prefixed("§cIn-game only."));
            return true;
        }

        PlayerProfile actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
        boolean bypass = devMode != null && devMode.isActive(p.getUniqueId());
        if (!bypass && !ranks.isAtLeast(actor.getPrimaryRank(), "helper")) {
            sender.sendMessage(Messages.noPerm());
            return true;
        }

        FileConfiguration cfg = JebaitedCore.getInstance().getConfig();
        String publicPanelUrl = getPublicPanelUrl(cfg);
        String internalPanelUrl = getInternalPanelUrl(cfg, publicPanelUrl);

        if (claimed.contains(p.getUniqueId())) {
            Bukkit.getScheduler().runTaskAsynchronously(JebaitedCore.getInstance(), () -> tryCreateTicket(cfg, p.getName(), actor.getPrimaryRank()));
            sendAlreadyProvisioned(p, publicPanelUrl);
            return true;
        }

        String provisionKey = cfg.getString("webpanel.provision_secret", "");
        if (provisionKey == null || provisionKey.isBlank()) {
            sender.sendMessage(Messages.prefixed("§cWeb panel provision secret not set. Configure webpanel.provision_secret in config.yml"));
            return true;
        }

        String role = "helper";
        if (ranks.isAtLeast(actor.getPrimaryRank(), "owner")) role = "owner";
        else if (ranks.isAtLeast(actor.getPrimaryRank(), "developer")) role = "developer";
        else if (ranks.isAtLeast(actor.getPrimaryRank(), "admin")) role = "admin";
        else if (ranks.isAtLeast(actor.getPrimaryRank(), "srmod")) role = "srmod";
        else if (ranks.isAtLeast(actor.getPrimaryRank(), "moderator")) role = "moderator";

        boolean isLocalOrSecure = internalPanelUrl.startsWith("https://")
                || internalPanelUrl.contains("localhost")
                || internalPanelUrl.contains("127.0.0.1")
                || internalPanelUrl.contains("host.docker.internal")
                || internalPanelUrl.contains("::1");
        if (!isLocalOrSecure) {
            sender.sendMessage(Messages.prefixed("§cPanel URL must use HTTPS for non-local addresses. Update webpanel.internal_url in config.yml"));
            return true;
        }

        String password = generatePassword(12);
        UUID playerId = p.getUniqueId();
        String username = p.getName();
        final String finalInternalPanelUrl = internalPanelUrl;
        final String finalPublicPanelUrl = publicPanelUrl;
        final String finalProvisionKey = provisionKey;
        final String finalRole = role;

        Bukkit.getScheduler().runTaskAsynchronously(JebaitedCore.getInstance(), () -> {
            try {
                provisionAccount(finalInternalPanelUrl, finalProvisionKey, username, password, finalRole);
                finishProvision(playerId, finalPublicPanelUrl, username, password);
            } catch (Exception e) {
                if (shouldRetryWithDockerHost(finalInternalPanelUrl, e)) {
                    String fallbackUrl = withDockerHostInternal(finalInternalPanelUrl);
                    try {
                        provisionAccount(fallbackUrl, finalProvisionKey, username, password, finalRole);
                        finishProvision(playerId, finalPublicPanelUrl, username, password);
                        return;
                    } catch (Exception retry) {
                        sendFailure(playerId, "§cFailed to provision account via §e" + finalInternalPanelUrl + "§c and §e" + fallbackUrl + "§c: " + retry.getMessage());
                        return;
                    }
                }
                sendFailure(playerId, "§cFailed to provision account: §e" + e.getMessage());
            }
        });
        return true;
    }

    private String getPublicPanelUrl(FileConfiguration cfg) {
        return cfg.getString("webpanel.public_url", cfg.getString("webpanel.url", "http://localhost:3001"));
    }

    private String getInternalPanelUrl(FileConfiguration cfg, String publicPanelUrl) {
        String internal = cfg.getString("webpanel.internal_url", "");
        if (internal != null && !internal.isBlank()) return internal;
        return publicPanelUrl;
    }

    private boolean shouldRetryWithDockerHost(String baseUrl, Exception e) {
        Throwable cursor = e;
        while (cursor != null) {
            if (cursor instanceof ConnectException) {
                return baseUrl != null && (baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1"));
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private String withDockerHostInternal(String baseUrl) {
        if (baseUrl == null) return "http://host.docker.internal:3001";
        return baseUrl.replace("localhost", "host.docker.internal").replace("127.0.0.1", "host.docker.internal");
    }

    private String generatePassword(int length) {
        final String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz0123456789!@#$%^&*";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void finishProvision(UUID playerId, String publicPanelUrl, String username, String password) {
        Bukkit.getScheduler().runTask(JebaitedCore.getInstance(), () -> {
            claimed.add(playerId);
            FileConfiguration liveCfg = JebaitedCore.getInstance().getConfig();
            liveCfg.set("webpanel.claimed." + playerId, true);
            JebaitedCore.getInstance().saveConfig();
            Player online = Bukkit.getPlayer(playerId);
            if (online != null) {
                sendProvisionSuccess(online, publicPanelUrl, username, password);
            }
        });
    }

    private void sendFailure(UUID playerId, String message) {
        Bukkit.getScheduler().runTask(JebaitedCore.getInstance(), () -> {
            Player online = Bukkit.getPlayer(playerId);
            if (online != null) {
                online.sendMessage(message);
            }
        });
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
        String payload = String.format("{\"username\":\"%s\",\"password\":\"%s\",\"role\":\"%s\"}", username, password, role);
        var connector = JebaitedCore.getInstance().getPanelConnectorService();
        var result = connector.postJson(baseUrl + "/api/auth/provision", payload, java.util.Map.of("x-provision-key", secret));
        int code = result.statusCode();
        if (!result.sent() || code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code + ": " + result.body());
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

    private void tryCreateTicket(FileConfiguration cfg, String username, String rank) {
        String publicPanelUrl = getPublicPanelUrl(cfg);
        String panelUrl = getInternalPanelUrl(cfg, publicPanelUrl);
        String provisionKey = cfg.getString("webpanel.provision_secret", "");
        if (provisionKey == null || provisionKey.isBlank()) {
            return;
        }

        try {
            String payload = String.format("{\"username\":\"%s\",\"rank\":\"%s\"}", username, rank);
            var connector = JebaitedCore.getInstance().getPanelConnectorService();
            connector.postJson(panelUrl + "/api/dev/password-tickets", payload, java.util.Map.of("x-provision-key", provisionKey));
        } catch (Exception ignored) {
        }
    }
}
