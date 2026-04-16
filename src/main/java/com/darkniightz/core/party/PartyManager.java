package com.darkniightz.core.party;

import com.darkniightz.core.Messages;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory party state machine.
 * All party state is lost on restart  only aggregate stats persist via PartyStatDAO.
 */
public final class PartyManager {

    public static final String CONFIG_MAX_SIZE  = "party.max_size";
    public static final int    DEFAULT_MAX_SIZE = 6;

    /** Prefix for all party system messages. */
    static final String PARTY_TAG = "\u00a78[\u00a7d\u2694 Party\u00a78] \u00a77";

    private final Plugin       plugin;
    private final ProfileStore profiles;
    private final RankManager  ranks;

    /** uuid -> the party they belong to */
    private final Map<UUID, Party> memberIndex      = new ConcurrentHashMap<>();
    /** All active parties keyed by party id */
    private final Map<UUID, Party> parties          = new ConcurrentHashMap<>();
    /** Invite expiry: invitee -> timestamp when invite was sent */
    private final Map<UUID, Long>  inviteTimestamps = new ConcurrentHashMap<>();

    private static final long INVITE_EXPIRE_MS = 60_000L;

    public PartyManager(Plugin plugin, ProfileStore profiles, RankManager ranks) {
        this.plugin   = plugin;
        this.profiles = profiles;
        this.ranks    = ranks;
    }

    //  Queries 

    public Party  getParty(UUID memberUuid) { return memberIndex.get(memberUuid); }
    public boolean isInParty(UUID uuid)     { return memberIndex.containsKey(uuid); }
    public Collection<Party> allParties()   { return Collections.unmodifiableCollection(parties.values()); }

    //  Create / Disband 

    public Party create(Player leader) {
        if (isInParty(leader.getUniqueId())) {
            leader.sendMessage(partyMsg("cYou are already in a party."));
            return null;
        }
        Party party = new Party(leader.getUniqueId());
        parties.put(party.getId(), party);
        memberIndex.put(leader.getUniqueId(), party);
        leader.sendMessage(partyMsg("aParty created! Invite players with e/party invite <player>a."));
        PartyStatDAO.incrementAsync(plugin, leader.getUniqueId(), "parties_created", 1);
        return party;
    }

    public void disband(Player actor) {
        Party party = memberIndex.get(actor.getUniqueId());
        if (party == null) { actor.sendMessage(partyMsg("cYou are not in a party.")); return; }
        if (!party.isLeader(actor.getUniqueId())) {
            actor.sendMessage(partyMsg("cOnly the party leader can disband."));
            return;
        }
        disbandInternal(party, "cParty disbanded by the leader.");
    }

    private void disbandInternal(Party party, String reason) {
        broadcast(party, reason);
        for (UUID uuid : Set.copyOf(party.getMembers())) memberIndex.remove(uuid);
        parties.remove(party.getId());
    }

    //  Invite / Accept / Deny / Join 

    public void invite(Player inviter, Player target) {
        Party party = memberIndex.get(inviter.getUniqueId());
        if (party == null) {
            party = create(inviter);
            if (party == null) return;
        }
        if (!party.isLeader(inviter.getUniqueId())) {
            inviter.sendMessage(partyMsg("cOnly the party leader can invite players."));
            return;
        }
        int maxSize = plugin.getConfig().getInt(CONFIG_MAX_SIZE, DEFAULT_MAX_SIZE);
        if (party.size() >= maxSize) {
            inviter.sendMessage(partyMsg("cYour party is full 8(7max f" + maxSize + "8)c."));
            return;
        }
        if (party.isMember(target.getUniqueId())) {
            inviter.sendMessage(partyMsg("c" + target.getName() + " is already in your party."));
            return;
        }
        if (party.hasPendingInvite(target.getUniqueId())) {
            inviter.sendMessage(partyMsg("c" + target.getName() + " already has a pending invite."));
            return;
        }
        if (isInParty(target.getUniqueId())) {
            inviter.sendMessage(partyMsg("c" + target.getName() + " is already in another party."));
            return;
        }
        // Respect target's invite preference
        PlayerProfile targetProfile = profiles.get(target.getUniqueId());
        if (targetProfile != null && !targetProfile.isPartyInvitesEnabled()) {
            inviter.sendMessage(partyMsg("c" + target.getName() + " has party invites disabled."));
            return;
        }

        party.addInvite(target.getUniqueId());
        inviteTimestamps.put(target.getUniqueId(), System.currentTimeMillis());
        inviter.sendMessage(partyMsg("aInvite sent to e" + target.getName() + "a."));

        // Clickable invite  [Accept] [Decline] buttons
        String inviterName = inviter.getName();
        Component inviteMsg = Component.text()
                .append(LegacyComponentSerializer.legacySection().deserialize(PARTY_TAG))
                .append(LegacyComponentSerializer.legacySection().deserialize(coloredName(inviter.getUniqueId())))
                .append(Component.text(" invited you to their party!  ", NamedTextColor.GRAY))
                .append(Component.text("[Accept]", NamedTextColor.GREEN)
                        .decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/party accept " + inviterName))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to join the party!", NamedTextColor.GREEN))))
                .append(Component.text("  "))
                .append(Component.text("[Decline]", NamedTextColor.RED)
                        .decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/party deny " + inviterName))
                        .hoverEvent(HoverEvent.showText(Component.text("Decline this invite.", NamedTextColor.RED))))
                .build();
        target.sendMessage(inviteMsg);
    }

    /** Join an open party without requiring an invite. */
    public void join(Player joiner, String leaderName) {
        Player leader = Bukkit.getPlayerExact(leaderName);
        if (leader == null) {
            joiner.sendMessage(partyMsg("cPlayer e" + leaderName + " cis not online."));
            return;
        }
        Party party = memberIndex.get(leader.getUniqueId());
        if (party == null) {
            joiner.sendMessage(partyMsg("e" + leaderName + " cis not in a party."));
            return;
        }
        if (!party.isLeader(leader.getUniqueId())) {
            joiner.sendMessage(partyMsg("cUse the party leader's name to join."));
            return;
        }
        if (!party.isOpen()) {
            joiner.sendMessage(partyMsg("cThat party is invite-only."));
            return;
        }
        if (isInParty(joiner.getUniqueId())) {
            joiner.sendMessage(partyMsg("cLeave your current party first."));
            return;
        }
        int maxSize = plugin.getConfig().getInt(CONFIG_MAX_SIZE, DEFAULT_MAX_SIZE);
        if (party.size() >= maxSize) {
            joiner.sendMessage(partyMsg("cThat party is full."));
            return;
        }
        party.addMember(joiner.getUniqueId());
        memberIndex.put(joiner.getUniqueId(), party);
        broadcast(party, "e" + joiner.getName() + " 7joined the party!");
        PartyStatDAO.incrementAsync(plugin, joiner.getUniqueId(), "parties_joined", 1);
    }

    public void accept(Player joiner, String inviterName) {
        Party party = findInvitePartyFor(joiner.getUniqueId(), inviterName);
        if (party == null) {
            joiner.sendMessage(partyMsg("cNo pending party invite from e" + inviterName + "c."));
            return;
        }
        if (isExpired(joiner.getUniqueId())) {
            party.removeInvite(joiner.getUniqueId());
            inviteTimestamps.remove(joiner.getUniqueId());
            joiner.sendMessage(partyMsg("cThat invite has expired."));
            return;
        }
        if (isInParty(joiner.getUniqueId())) {
            joiner.sendMessage(partyMsg("cLeave your current party first."));
            return;
        }
        int maxSize = plugin.getConfig().getInt(CONFIG_MAX_SIZE, DEFAULT_MAX_SIZE);
        if (party.size() >= maxSize) {
            party.removeInvite(joiner.getUniqueId());
            inviteTimestamps.remove(joiner.getUniqueId());
            joiner.sendMessage(partyMsg("cThat party is now full."));
            return;
        }
        party.addMember(joiner.getUniqueId());
        inviteTimestamps.remove(joiner.getUniqueId());
        memberIndex.put(joiner.getUniqueId(), party);
        broadcast(party, "a" + joiner.getName() + " joined the party!");
        PartyStatDAO.incrementAsync(plugin, joiner.getUniqueId(), "parties_joined", 1);
    }

    public void deny(Player denier, String inviterName) {
        Party party = findInvitePartyFor(denier.getUniqueId(), inviterName);
        if (party == null) {
            denier.sendMessage(partyMsg("cNo pending invite from e" + inviterName + "c."));
            return;
        }
        party.removeInvite(denier.getUniqueId());
        inviteTimestamps.remove(denier.getUniqueId());
        denier.sendMessage(partyMsg("7Invite declined."));
        Player inviter = Bukkit.getPlayerExact(inviterName);
        if (inviter != null) inviter.sendMessage(partyMsg("e" + denier.getName() + " 7declined your invite."));
    }

    //  Leave / Kick / Transfer 

    public void leave(Player player) {
        Party party = memberIndex.get(player.getUniqueId());
        if (party == null) { player.sendMessage(partyMsg("cYou are not in a party.")); return; }
        if (party.isLeader(player.getUniqueId())) {
            disbandInternal(party, "cThe leader left  party disbanded.");
        } else {
            removeMemberInternal(party, player.getUniqueId());
            player.sendMessage(partyMsg("7You left the party."));
            broadcast(party, "e" + player.getName() + " 7left the party.");
        }
    }

    /**
     * Called when a player disconnects.
     * Promotes the next online member to leader instead of immediately disbanding.
     */
    public void handleDisconnect(UUID uuid) {
        Party party = memberIndex.get(uuid);
        if (party == null) return;
        inviteTimestamps.remove(uuid);

        if (party.isLeader(uuid)) {
            // Try to promote the next online member
            UUID nextLeader = party.getMembers().stream()
                    .filter(u -> !u.equals(uuid) && Bukkit.getPlayer(u) != null)
                    .findFirst().orElse(null);
            if (nextLeader != null) {
                party.setLeader(nextLeader);
                removeMemberInternal(party, uuid);
                // Party may have disbanded (size 1)  re-check before announcing
                Party still = memberIndex.get(nextLeader);
                if (still != null) broadcast(still, "e" + nameOf(nextLeader) + " ais now the party leader.");
            } else {
                disbandInternal(party, "cAll members disconnected  party disbanded.");
            }
        } else {
            removeMemberInternal(party, uuid);
            broadcast(party, "e" + nameOf(uuid) + " 7disconnected.");
        }
    }

    public void kick(Player leader, Player target) {
        Party party = memberIndex.get(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            leader.sendMessage(partyMsg("cOnly the party leader can kick members."));
            return;
        }
        if (!party.isMember(target.getUniqueId())) {
            leader.sendMessage(partyMsg("c" + target.getName() + " is not in your party."));
            return;
        }
        removeMemberInternal(party, target.getUniqueId());
        target.sendMessage(partyMsg("cYou were kicked from the party."));
        broadcast(party, "e" + target.getName() + " 7was kicked from the party.");
    }

    public void transfer(Player leader, Player newLeader) {
        Party party = memberIndex.get(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            leader.sendMessage(partyMsg("cOnly the current leader can transfer leadership."));
            return;
        }
        if (!party.isMember(newLeader.getUniqueId())) {
            leader.sendMessage(partyMsg("c" + newLeader.getName() + " is not in your party."));
            return;
        }
        party.setLeader(newLeader.getUniqueId());
        broadcast(party, "e" + newLeader.getName() + " ais now the party leader.");
    }

    //  Chat 

    public boolean sendChat(Player sender, String message) {
        Party party = memberIndex.get(sender.getUniqueId());
        if (party == null) return false;
        Component line = LegacyComponentSerializer.legacySection().deserialize(
                "\u00a78[\u00a7d\u2694 Party\u00a78] " + coloredName(sender.getUniqueId()) + " \u00a78\u25b8 \u00a7f" + message);
        for (UUID uuid : party.getMembers()) {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null) member.sendMessage(line);
        }
        return true;
    }

    public void toggleChat(Player player) {
        Party party = memberIndex.get(player.getUniqueId());
        if (party == null) { player.sendMessage(partyMsg("cYou are not in a party.")); return; }
        party.toggleChat(player.getUniqueId());
        boolean on = party.hasChat(player.getUniqueId());
        player.sendMessage(partyMsg(on
                ? "aParty chat 2enableda. All your messages go to the party."
                : "7Party chat fdisabled7. Messages go to public chat."));
    }

    public boolean hasChatToggle(UUID uuid) {
        Party party = memberIndex.get(uuid);
        return party != null && party.hasChat(uuid);
    }

    //  Leader settings 

    public void toggleFriendlyFire(Player leader) {
        Party party = memberIndex.get(leader.getUniqueId());
        if (party == null) { leader.sendMessage(partyMsg("cYou are not in a party.")); return; }
        if (!party.isLeader(leader.getUniqueId())) {
            leader.sendMessage(partyMsg("cOnly the party leader can change settings."));
            return;
        }
        party.toggleFriendlyFire();
        broadcast(party, party.isFriendlyFire()
                ? "c Friendly fire 4enabled cby the leader."
                : "a Friendly fire 2disabled aby the leader.");
    }

    public void toggleOpen(Player leader) {
        Party party = memberIndex.get(leader.getUniqueId());
        if (party == null) { leader.sendMessage(partyMsg("cYou are not in a party.")); return; }
        if (!party.isLeader(leader.getUniqueId())) {
            leader.sendMessage(partyMsg("cOnly the party leader can change settings."));
            return;
        }
        party.toggleOpen();
        broadcast(party, party.isOpen()
                ? "aParty is now 2open a use e/party join " + leader.getName() + " ato join."
                : "7Party is now finvite-only7.");
    }

    public void setWarp(Player leader) {
        Party party = memberIndex.get(leader.getUniqueId());
        if (party == null) { leader.sendMessage(partyMsg("cYou are not in a party.")); return; }
        if (!party.isLeader(leader.getUniqueId())) {
            leader.sendMessage(partyMsg("cOnly the party leader can set the party warp."));
            return;
        }
        party.setWarpLocation(leader.getLocation().clone());
        broadcast(party, "aParty warp set! Use e/party warp ato teleport.");
    }

    public void clearWarp(Player leader) {
        Party party = memberIndex.get(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            leader.sendMessage(partyMsg("cOnly the party leader can clear the warp."));
            return;
        }
        party.clearWarpLocation();
        broadcast(party, "7Party warp cleared.");
    }

    public void warpToParty(Player player) {
        Party party = memberIndex.get(player.getUniqueId());
        if (party == null) { player.sendMessage(partyMsg("cYou are not in a party.")); return; }
        Location warp = party.getWarpLocation();
        if (warp == null || warp.getWorld() == null) {
            player.sendMessage(partyMsg("cNo party warp is set. Leader can use e/party setwarpc."));
            return;
        }
        player.teleport(warp);
        player.sendMessage(partyMsg("aTeleported to the party warp."));
    }

    //  Broadcast 

    public void broadcast(Party party, String legacyMessage) {
        Component msg = LegacyComponentSerializer.legacySection()
                .deserialize(PARTY_TAG + legacyMessage);
        for (UUID uuid : party.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(msg);
        }
    }

    //  Helpers 

    /** Returns the rank-colored display name of a player (online or offline). */
    public String coloredName(UUID uuid) {
        PlayerProfile profile = profiles.get(uuid);
        String rankName = (profile != null && profile.getPrimaryRank() != null)
                ? profile.getPrimaryRank() : ranks.getDefaultGroup();
        RankManager.RankStyle style = ranks.getStyle(rankName);
        String color = style.rainbow ? "d" : (style.colorCode != null ? style.colorCode : "7");
        Player p = Bukkit.getPlayer(uuid);
        String name;
        if (p != null) {
            name = p.getName();
        } else {
            String offlineName = Bukkit.getOfflinePlayer(uuid).getName();
            name = offlineName != null ? offlineName : uuid.toString().substring(0, 8);
        }
        return color + name;
    }

    /** Returns a message with the party tag prefix. */
    public String partyMsg(String message) {
        return PARTY_TAG + message;
    }

    private void removeMemberInternal(Party party, UUID uuid) {
        party.removeMember(uuid);
        memberIndex.remove(uuid);
        if (party.size() <= 1) disbandInternal(party, "cNot enough members  party disbanded.");
    }

    private Party findInvitePartyFor(UUID invitee, String inviterName) {
        for (Party p : parties.values()) {
            if (!p.hasPendingInvite(invitee)) continue;
            Player inviter = Bukkit.getPlayerExact(inviterName);
            if (inviter != null && p.isLeader(inviter.getUniqueId())) return p;
            if (inviterName == null || inviterName.isBlank()) return p;
        }
        return null;
    }

    private boolean isExpired(UUID uuid) {
        Long ts = inviteTimestamps.get(uuid);
        return ts != null && System.currentTimeMillis() - ts > INVITE_EXPIRE_MS;
    }

    private static String nameOf(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) return p.getName();
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }
}