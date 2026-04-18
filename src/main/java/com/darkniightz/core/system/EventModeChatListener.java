package com.darkniightz.core.system;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * Supported Paper range: 1.21.9-1.21.11.
 */
public class EventModeChatListener implements Listener {
    private final Plugin plugin;
    private final EventModeManager eventModeManager;

    public EventModeChatListener(Plugin plugin, EventModeManager eventModeManager) {
        this.plugin = plugin;
        this.eventModeManager = eventModeManager;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message());
        if (plain == null || plain.isBlank()) {
            return;
        }

        boolean solved = eventModeManager.submitChatAnswer(player, plain);
        if (!solved) {
            return;
        }

        // Correct answer consumes chat message to keep event clear and avoid duplicated spam.
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () ->
            player.sendMessage(com.darkniightz.core.Messages.prefixed("§aCorrect! You won the chat game."))
        );
    }
}
