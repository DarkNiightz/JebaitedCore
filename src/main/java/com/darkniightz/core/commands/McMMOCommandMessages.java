package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.system.McMMOIntegration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Prefixed Adventure messages for mcMMO wrapper commands (uses {@link Messages#prefix()} config).
 */
public final class McMMOCommandMessages {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private McMMOCommandMessages() {
    }

    public static void sendPrefixed(@NotNull CommandSender sender, @NotNull Component body) {
        Component prefix = LEGACY.deserialize(Messages.prefix());
        sender.sendMessage(prefix.append(body));
    }

    /** Legacy {@code §} body only (prefix is prepended as configured). */
    public static void sendPrefixedLegacy(@NotNull CommandSender sender, @NotNull String legacyBody) {
        sendPrefixed(sender, LEGACY.deserialize(legacyBody));
    }

    public static void sendNoPermission(@NotNull CommandSender sender) {
        sender.sendMessage(LEGACY.deserialize(Messages.noPerm()));
    }

    public static String formatSkillEnumName(String enumName) {
        String s = enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
        if (s.isEmpty()) {
            return enumName;
        }
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    /** Power level + per-skill levels (shared by {@code /mcstats} and {@code /inspect}). */
    public static void sendMcStatsBreakdown(@NotNull CommandSender sender, @NotNull OfflinePlayer target) {
        Integer power = McMMOIntegration.getPowerLevel(target);
        if (power == null) {
            sendPrefixedLegacy(sender, "§7No mcMMO profile data for §f" + target.getName() + "§7.");
            return;
        }
        List<McMMOIntegration.SkillLevel> levels = McMMOIntegration.collectSkillLevels(target);
        sendPrefixedLegacy(sender, "§6mcMMO §7— §f" + target.getName() + " §7| §ePower " + power);
        if (levels.isEmpty()) {
            sendPrefixedLegacy(sender, "§7(Skill breakdown unavailable.)");
            return;
        }
        String line = levels.stream()
                .map(s -> "§a" + formatSkillEnumName(s.skillName()) + " §7" + s.level())
                .collect(Collectors.joining("§8 | §r"));
        sendPrefixedLegacy(sender, line);
    }
}
