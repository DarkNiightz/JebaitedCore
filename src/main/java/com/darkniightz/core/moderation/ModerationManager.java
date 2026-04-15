package com.darkniightz.core.moderation;

import com.darkniightz.main.PlayerProfileDAO;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ModerationManager {
    private final Plugin plugin;
    private final PlayerProfileDAO dao;
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
    private final Set<UUID> staffChat = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastChatAt = new ConcurrentHashMap<>();
    private int slowmodeSeconds = 0; // 0 = off

    public ModerationManager(Plugin plugin, PlayerProfileDAO dao) {
        this.plugin = plugin;
        this.dao = dao;
    }

    public final void loadPersistentState() {
        if (dao == null) return;

        vanished.clear();
        vanished.addAll(dao.loadUuidStateByPrefix("vanish:"));
        frozen.clear();
        frozen.addAll(dao.loadUuidStateByPrefix("freeze:"));
        slowmodeSeconds = dao.loadSlowmodeSeconds();

        for (UUID id : vanished) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                setVanish(player, true);
            }
        }
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
        if (dao != null) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> dao.saveVanishState(id, enable));
        }
    }

    public boolean isVanished(UUID id) { return vanished.contains(id); }
    public Set<UUID> getVanished() { return Collections.unmodifiableSet(vanished); }

    // Freeze
    public void setFrozen(UUID id, boolean frozenState) {
        if (frozenState) frozen.add(id); else frozen.remove(id);
        if (dao != null) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> dao.saveFreezeState(id, frozenState));
        }
    }
    public boolean isFrozen(UUID id) { return frozen.contains(id); }

    // Staff chat toggle
    public boolean toggleStaffChat(UUID id) {
        if (staffChat.contains(id)) { staffChat.remove(id); return false; }
        staffChat.add(id); return true;
    }
    public boolean inStaffChat(UUID id) { return staffChat.contains(id); }

    // Slowmode
    public void setSlowmodeSeconds(int seconds) {
        this.slowmodeSeconds = Math.max(0, seconds);
        if (dao != null) {
            dao.saveSlowmodeSeconds(this.slowmodeSeconds);
        }
    }
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
