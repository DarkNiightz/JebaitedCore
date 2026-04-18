package com.darkniightz.core.party;

import com.darkniightz.main.JebaitedCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class PartyListener implements Listener {

    private final PartyManager partyManager;
    private final Plugin       plugin;
    private final JebaitedCore core;

    /**
     * Guards against re-entrant XP sharing.
     * When we call {@code giveExp()} on a member, their {@link PlayerExpChangeEvent}
     * would also try to share XP. We add their UUID here before the call and
     * remove it immediately after  since all events fire synchronously on the
     * main thread this is safe without any synchronization.
     */
    private final Set<UUID> sharingXp = new HashSet<>();

    public PartyListener(PartyManager partyManager, Plugin plugin) {
        this.partyManager = partyManager;
        this.plugin        = plugin;
        this.core          = (JebaitedCore) plugin;
    }

    // ── XP share tier (by earner's donor rank) ────────────────────────────────
    // Non-donors get no XP sharing. Each donor tier unlocks a higher % and range.
    //   gold        →  5%,  64 blocks
    //   diamond     → 10%,  96 blocks
    //   legend      → 15%, 160 blocks
    //   grandmaster → 25%, 256 blocks
    private record XpTier(double pct, double range) {}

    private XpTier xpTier(Player earner) {
        var profile = core.getProfileStore()
                .getOrCreate(earner, core.getRankManager().getDefaultGroup());
        String donor = profile == null ? null : profile.getDonorRank();
        if (donor == null) return null;
        var ranks = core.getRankManager();
        if (ranks.isAtLeast(donor, "grandmaster")) return new XpTier(0.25, 256);
        if (ranks.isAtLeast(donor, "legend"))      return new XpTier(0.15, 160);
        if (ranks.isAtLeast(donor, "diamond"))     return new XpTier(0.10,  96);
        if (ranks.isAtLeast(donor, "gold"))        return new XpTier(0.05,  64);
        return null;
    }

    //  Lifecycle 

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        partyManager.handleDisconnect(event.getPlayer().getUniqueId());
    }

    //  Stats 

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || !partyManager.isInParty(killer.getUniqueId())) return;
        PartyStatDAO.incrementAsync(plugin, killer.getUniqueId(), "party_kills", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!partyManager.isInParty(player.getUniqueId())) return;
        PartyStatDAO.incrementAsync(plugin, player.getUniqueId(), "party_blocks_broken", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player player = event.getPlayer();
        if (!partyManager.isInParty(player.getUniqueId())) return;
        PartyStatDAO.incrementAsync(plugin, player.getUniqueId(), "party_fish_caught", 1);
    }

    //  XP sharing 

    /**
     * When a donor party member earns XP, share a percentage with each nearby
     * online party member. The earner's donor rank controls both the share
     * percentage and the maximum sharing range:
     *
     *   gold        →  5%,  64 blocks
     *   diamond     → 10%,  96 blocks
     *   legend      → 15%, 160 blocks
     *   grandmaster → 25%, 256 blocks
     *
     * Non-donors do not trigger XP sharing.
     * The earner keeps their full gain  recipients receive a bonus amount.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExpChange(PlayerExpChangeEvent event) {
        Player earner = event.getPlayer();
        if (sharingXp.contains(earner.getUniqueId())) return;

        int gain = event.getAmount();
        if (gain <= 0) return;

        Party party = partyManager.getParty(earner.getUniqueId());
        if (party == null) return;

        XpTier tier = xpTier(earner);
        if (tier == null) return; // earner has no donor rank — no sharing

        int    share     = Math.max(1, (int) (gain * tier.pct()));
        double rangesSq  = tier.range() * tier.range();
        int    sent      = 0;

        for (UUID uuid : party.getMembers()) {
            if (uuid.equals(earner.getUniqueId()) || sent >= 5) continue;
            Player member = Bukkit.getPlayer(uuid);
            if (member == null) continue;
            if (!member.getWorld().equals(earner.getWorld())) continue;
            if (member.getLocation().distanceSquared(earner.getLocation()) > rangesSq) continue;

            sharingXp.add(uuid);
            member.giveExp(share);
            sharingXp.remove(uuid);

            PartyStatDAO.incrementAsync(plugin, earner.getUniqueId(), "party_xp_shared", share);
            sent++;
        }
    }

    //  Friendly fire prevention 

    /**
     * Cancels damage between party members when friendly fire is disabled (default).
     * The attacker receives a one-line notice to avoid silent confusion.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity()  instanceof Player victim))   return;

        Party party = partyManager.getParty(attacker.getUniqueId());
        if (party == null) return;
        if (!party.isMember(victim.getUniqueId())) return;
        if (party.isFriendlyFire()) return; // FF enabled  allow

        // Running event combat: party FF must not block Duels/FFA/KOTH/etc. CTF same-team keeps
        // party/team safety (until a dedicated team friendly-fire toggle exists).
        var em = core.getEventModeManager();
        if (em != null
                && em.isActiveEventParticipant(attacker)
                && em.isActiveEventParticipant(victim)
                && !em.areCtfTeammates(attacker, victim)) {
            return;
        }

        event.setCancelled(true);
        attacker.sendMessage(partyManager.partyMsg("\u00a7cFriendly fire is disabled."));
    }
}