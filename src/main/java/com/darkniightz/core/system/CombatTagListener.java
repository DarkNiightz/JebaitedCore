package com.darkniightz.core.system;

import com.darkniightz.main.JebaitedCore;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;

public class CombatTagListener implements Listener {
    private final JebaitedCore plugin;
    private final CombatTagManager combatTags;
    private final GraveManager graves;

    public CombatTagListener(JebaitedCore plugin, CombatTagManager combatTags) {
        this.plugin = plugin;
        this.combatTags = combatTags;
        this.graves = plugin.getGraveManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = findPlayerDamager(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;

        combatTags.tag(attacker, victim);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!combatTags.isTagged(player)) return;

        String msg = event.getMessage();
        if (msg == null || msg.isBlank()) return;
        String cmd = msg.substring(1).trim().toLowerCase(Locale.ROOT);
        if (cmd.isBlank()) return;
        String base = cmd.split("\\s+")[0];

        if (isBlocked(base)) {
            long remain = (combatTags.remainingMillis(player) + 999L) / 1000L;
            event.setCancelled(true);
            player.sendMessage(com.darkniightz.core.Messages.prefixed("§cYou are combat tagged for §f" + remain + "s§c. You cannot use §f/" + base + "§c right now."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!combatTags.isTagged(player)) return;

        java.util.UUID killer = combatTags.getLastAttacker(player.getUniqueId());
        if (graves != null) {
            graves.createCombatLogGrave(player, killer);
        }

        // Combat logging penalty: kill player on quit while tagged.
        player.setHealth(0.0D);
        combatTags.clear(player);
        String message = plugin.getConfig().getString("combatlog.quit_broadcast", "&c%player% combat logged and died.");
        if (message != null && !message.isBlank()) {
            String rendered = ChatColor.translateAlternateColorCodes('&', message.replace("%player%", player.getName()));
            plugin.getServer().broadcastMessage(rendered);
        }
    }

    private boolean isBlocked(String base) {
        return switch (base) {
            case "hub", "spawn", "smp", "home", "homes", "warp", "warps", "rtp", "tpa", "tpahere", "tpaccept", "tpdeny" -> true;
            default -> false;
        };
    }

    private Player findPlayerDamager(Entity source) {
        if (source instanceof Player player) return player;
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }
}
