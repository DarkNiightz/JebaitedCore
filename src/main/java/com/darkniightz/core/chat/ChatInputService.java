package com.darkniightz.core.chat;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Lightweight one-shot chat input capture.
 *
 * <p>Usage: call {@link #prompt} to register a pending callback, then hook
 * {@link #intercept} at the top of {@link ChatListener#onAsyncChat}.  The
 * callback always runs on the main thread.
 *
 * <p>Pending inputs are automatically discarded when the player quits
 * (call {@link #cancel} from a quit listener, or let the next prompt
 * overwrite the previous one).
 */
public final class ChatInputService {

    private ChatInputService() {}

    private static final ConcurrentHashMap<UUID, PendingInput> pending = new ConcurrentHashMap<>();

    /** Stores the plugin and callback for a pending input. */
    private record PendingInput(Plugin plugin, Consumer<String> callback) {}

    /**
     * Prompts the player with a coloured message and registers a one-shot
     * callback that will be invoked on the <em>main thread</em> with the raw
     * text the player types next.
     *
     * @param player         the player to prompt
     * @param legacyMessage  the prompt message (legacy § colour codes)
     * @param plugin         used to schedule the callback back on main thread
     * @param callback       invoked on main thread with the typed text
     */
    public static void prompt(Player player, String legacyMessage, Plugin plugin,
                               Consumer<String> callback) {
        pending.put(player.getUniqueId(), new PendingInput(plugin, callback));
        player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(legacyMessage));
    }

    /**
     * Called from {@link ChatListener} (async) when a player sends a message.
     * If a pending input exists for this player, the message is consumed and
     * the callback scheduled on the main thread.
     *
     * @return {@code true} if the message was consumed and should be cancelled
     */
    public static boolean intercept(Player player, String rawText) {
        PendingInput pi = pending.remove(player.getUniqueId());
        if (pi == null) return false;
        Bukkit.getScheduler().runTask(pi.plugin(), () -> pi.callback().accept(rawText));
        return true;
    }

    /** Removes any pending input for the given UUID (e.g. on player quit). */
    public static void cancel(UUID uuid) {
        pending.remove(uuid);
    }
}
