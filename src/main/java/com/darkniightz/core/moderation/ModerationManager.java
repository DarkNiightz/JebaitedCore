package com.darkniightz.core.moderation;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class ModerationManager {
    private final Plugin plugin;
    private final Set<UUID> vanished = new HashSet<>();
    private final Set<UUID> frozen = new HashSet<>();
    private final Set<UUID> staffChat = new HashSet<>();
    private final Map<UUID, Long> lastChatAt = new HashMap<>();
    private int slowmodeSeconds = 0; // 0 = off

    public ModerationManager(Plugin plugin) {
        this.plugin = plugin;
    }

    // Vanish
    public boolean toggleVanish(Player p) {
        boolean now = !vanished.contains(p.getUniqueId());
        setVanish(p, now);
        return now;
    }

    public void setVanish(Player p, boolean enable) {
        UUID id = p.getUniqueId();
        if (enable) {
            vanished.add(id);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.getUniqueId().equals(id)) other.hidePlayer(plugin, p);
            }
            p.setInvisible(true);
        } else {
            vanished.remove(id);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.getUniqueId().equals(id)) other.showPlayer(plugin, p);
            }
            p.setInvisible(false);
        }
    }

    public boolean isVanished(UUID id) { return vanished.contains(id); }
    public Set<UUID> getVanished() { return Collections.unmodifiableSet(vanished); }

    // Freeze
    public void setFrozen(UUID id, boolean frozenState) {
        if (frozenState) frozen.add(id); else frozen.remove(id);
    }
    public boolean isFrozen(UUID id) { return frozen.contains(id); }

    // Staff chat toggle
    public boolean toggleStaffChat(UUID id) {
        if (staffChat.contains(id)) { staffChat.remove(id); return false; }
        staffChat.add(id); return true;
    }
    public boolean inStaffChat(UUID id) { return staffChat.contains(id); }

    // Slowmode
    public void setSlowmodeSeconds(int seconds) { this.slowmodeSeconds = Math.max(0, seconds); }
    public int getSlowmodeSeconds() { return slowmodeSeconds; }
    public long getRemainingSlow(UUID id) {
        if (slowmodeSeconds <= 0) return 0;
        long last = lastChatAt.getOrDefault(id, 0L);
        long next = last + slowmodeSeconds * 1000L;
        long now = System.currentTimeMillis();
        return Math.max(0, next - now);
    }
    public void markChatted(UUID id) { lastChatAt.put(id, System.currentTimeMillis()); }
}
