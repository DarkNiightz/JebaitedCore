package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.cosmetics.ToyboxManager;
import com.darkniightz.core.world.WorldManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LoadoutCommand implements CommandExecutor {
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final ToyboxManager toyboxManager;
    private final DevModeManager devMode;
    private final WorldManager worldManager;

    public LoadoutCommand(ProfileStore profiles, RankManager ranks, ToyboxManager toyboxManager, DevModeManager devMode, WorldManager worldManager) {
        this.profiles = profiles;
        this.ranks = ranks;
        this.toyboxManager = toyboxManager;
        this.devMode = devMode;
        this.worldManager = worldManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefixed("§cOnly players can manage loadouts."));
            return true;
        }

        if (!worldManager.requireHub(player, devMode)) {
            return true;
        }

        PlayerProfile profile = profiles.getOrCreate(player, ranks.getDefaultGroup());
        if (profile == null) {
            player.sendMessage(Messages.prefixed("§cYour profile could not be loaded."));
            return true;
        }

        if (args.length == 0 || equalsAny(args[0], "list", "show")) {
            showList(player, profile, label);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        String name = args.length >= 2 ? joinArgs(args, 1) : "";
        if (name.isBlank()) {
            player.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " <save|apply|clear|list> <name>"));
            return true;
        }

        switch (action) {
            case "save", "set" -> {
                profile.putCosmeticLoadout(name, serializeCurrent(profile));
                profiles.save(player.getUniqueId());
                player.sendMessage(Messages.prefixed("§dSaved loadout §f" + name + "§d."));
                return true;
            }
            case "apply", "use" -> {
                String raw = profile.getCosmeticLoadout(name);
                if (raw == null || raw.isBlank()) {
                    player.sendMessage(Messages.prefixed("§7No loadout named §f" + name + "§7."));
                    return true;
                }
                apply(profile, raw);
                profiles.save(player.getUniqueId());
                if (toyboxManager != null) toyboxManager.refresh(player);
                player.sendMessage(Messages.prefixed("§aApplied loadout §f" + name + "§a."));
                return true;
            }
            case "clear", "remove", "delete" -> {
                profile.removeCosmeticLoadout(name);
                profiles.save(player.getUniqueId());
                player.sendMessage(Messages.prefixed("§7Removed loadout §f" + name + "§7."));
                return true;
            }
            default -> {
                player.sendMessage(Messages.prefixed("§eUsage: §f/" + label + " <save|apply|clear|list> <name>"));
                return true;
            }
        }
    }

    private void showList(Player player, PlayerProfile profile, String label) {
        List<String> names = new ArrayList<>(profile.getCosmeticLoadouts().keySet());
        if (names.isEmpty()) {
            player.sendMessage(Messages.prefixed("§7No loadouts saved yet. Use §e/" + label + " save <name>§7 while wearing a vibe."));
            return;
        }
        player.sendMessage(Messages.prefixed("§dSaved loadouts:"));
        for (String name : names) {
            player.sendMessage(Messages.prefixed("§8- §f" + name));
        }
        player.sendMessage(Messages.prefixed("§8Use /" + label + " apply <name> to switch instantly."));
    }

    private String serializeCurrent(PlayerProfile profile) {
        return safe(profile.getEquippedParticles()) + "|" + safe(profile.getEquippedTrail()) + "|" + safe(profile.getEquippedGadget());
    }

    private void apply(PlayerProfile profile, String raw) {
        String[] parts = raw.split("\\|", -1);
        String particle = parts.length > 0 ? emptyToNull(parts[0]) : null;
        String trail = parts.length > 1 ? emptyToNull(parts[1]) : null;
        String gadget = parts.length > 2 ? emptyToNull(parts[2]) : null;
        profile.setEquippedParticles(particle);
        profile.setParticleActivatedAt(particle == null ? null : System.currentTimeMillis());
        profile.setEquippedTrail(trail);
        profile.setTrailActivatedAt(trail == null ? null : System.currentTimeMillis());
        profile.setEquippedGadget(gadget);
    }

    private boolean equalsAny(String value, String... options) {
        if (value == null) return false;
        for (String option : options) {
            if (value.equalsIgnoreCase(option)) return true;
        }
        return false;
    }

    private String joinArgs(String[] args, int start) {
        if (args == null || start >= args.length) return "";
        StringBuilder out = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (args[i] == null || args[i].isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(args[i]);
        }
        return out.toString().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
