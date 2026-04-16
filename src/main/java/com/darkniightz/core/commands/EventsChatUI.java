package com.darkniightz.core.commands;

import com.darkniightz.core.system.EventModeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Clickable chat-based event admin panel.
 * <p>
 * Future hook: once PlayerSettingsManager ships, check
 * {@code event.prefer_chat} to route /events (no-args) here instead of GUI.
 */
public final class EventsChatUI {
    private EventsChatUI() {}

    private static final String BAR =
            "§8§m                                                        §r";

    public static void sendAdminPanel(Player player, EventModeManager em) {
        player.sendMessage(l(BAR));
        player.sendMessage(l("  §d§lEvent Control §8— §7Admin Panel"));
        player.sendMessage(l("  " + em.getStatusLine()));
        player.sendMessage(Component.empty());

        // Start buttons — one per configured event
        List<String> keys = em.getConfiguredEventKeys();
        if (!keys.isEmpty()) {
            Component row = l("  §7Start: ");
            for (String key : keys) {
                row = row.append(
                        btn("§a[▶ §f" + key.toUpperCase() + "§a]",
                                ClickEvent.suggestCommand("/event start " + key),
                                "§7Paste §f/event start " + key + " §7into chat"));
                row = row.append(l(" "));
            }
            player.sendMessage(row);
        }

        // Control row
        Component controls = l("  §7Control: ")
                .append(btn("§c[■ Stop]",   ClickEvent.runCommand("/event stop"),   "§cStop the current event"))
                .append(l(" "))
                .append(btn("§b[✈ World]",  ClickEvent.runCommand("/event tp"),     "§7TP to event world"))
                .append(l(" "))
                .append(btn("§e[ℹ Status]", ClickEvent.runCommand("/event status"), "§7Show event status"))
                .append(l(" "))
                .append(btn("§9[≡ GUI]",    ClickEvent.runCommand("/events"),       "§7Open the events admin GUI"));
        player.sendMessage(controls);

        player.sendMessage(Component.empty());
        player.sendMessage(l("  §7§oArena Spawns"));

        // FFA
        int ffaCount = em.getArenaSpawnCount("ffa");
        Component ffaRow = l("  §6FFA §8(§7" + ffaCount + " spawn" + (ffaCount == 1 ? "" : "s") + "§8)  ")
                .append(btn("§a[⊕ Set]",  ClickEvent.runCommand("/event seteventspawn ffa"),    "§7Add your position as an FFA spawn"))
                .append(l(" "))
                .append(btn("§b[◎ View]", ClickEvent.runCommand("/event setup ffa view"),       "§7Preview FFA spawns (30s ghost blocks)"))
                .append(l(" "))
                .append(btn("§3[List]",        ClickEvent.runCommand("/event setup ffa listspawns"), "§7List all FFA spawns in chat"))
                .append(l(" "));
        if (ffaCount > 0) {
            ffaRow = ffaRow.append(btn("§e[✈ TP]", ClickEvent.runCommand("/event tp ffa"),
                    "§7TP to FFA spawn #1 (centre of the FFA map)"));
        }
        player.sendMessage(ffaRow);

        // Duels
        int duelsCount = em.getArenaSpawnCount("duels");
        Component duelsRow = l("  §5Duels §8(§7" + duelsCount + " spawn" + (duelsCount == 1 ? "" : "s") + "§8)  ")
                .append(btn("§a[⊕ Set]",  ClickEvent.runCommand("/event seteventspawn duels"),    "§7Add your position as a Duels spawn"))
                .append(l(" "))
                .append(btn("§b[◎ View]", ClickEvent.runCommand("/event setup duels view"),       "§7Preview Duels spawns (30s ghost blocks)"))
                .append(l(" "))
                .append(btn("§3[List]",        ClickEvent.runCommand("/event setup duels listspawns"), "§7List all Duels spawns in chat"))
                .append(l(" "));
        if (duelsCount > 0) {
            duelsRow = duelsRow.append(btn("§e[✈ TP]", ClickEvent.runCommand("/event tp duels"),
                    "§7TP to Duels spawn #1"));
        }
        player.sendMessage(duelsRow);

        player.sendMessage(Component.empty());
        player.sendMessage(l("  §7KOTH: §f/event setup koth pos1 §7/ §fpos2 §7to set hill corners"));
        player.sendMessage(l(BAR));
    }

    private static Component l(String legacy) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
    }

    private static Component btn(String label, ClickEvent click, String hoverLegacy) {
        return LegacyComponentSerializer.legacySection().deserialize(label)
                .clickEvent(click)
                .hoverEvent(HoverEvent.showText(
                        LegacyComponentSerializer.legacySection().deserialize(hoverLegacy)));
    }
}
