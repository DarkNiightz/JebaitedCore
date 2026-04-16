package com.darkniightz.core.gui;

import com.darkniightz.core.chat.ChatInputService;
import com.darkniightz.core.party.Party;
import com.darkniightz.core.party.PartyManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 54-slot party overview GUI.
 *
 * Layout:
 *   Row 0   glass border
 *   Row 1   member skulls (slots 10-15, max 6)
 *   Row 2   pending invite skulls (slots 19-24)
 *   Row 3   status strip: info (28), friendly fire (30), open/lock (32), warp (34)
 *   Row 4   glass separator
 *   Row 5   action bar: left(45), invite(47), close(49), chat(51), right(53)
 *
 * Member skull lore:
 *   Left-click  (leader) = kick
 *   Right-click (leader) = transfer leadership
 */
public final class PartyMenu extends BaseMenu {

    //  Title 
    private static final int SLOT_TITLE  = 4;

    //  Status strip (row 3) 
    private static final int SLOT_INFO   = 28;
    private static final int SLOT_FF     = 30;
    private static final int SLOT_LOCK   = 32;
    private static final int SLOT_WARP_S = 34;

    //  Action bar (row 5) 
    /** DISBAND for leader / LEAVE for member */
    private static final int SLOT_LEFT   = 45;
    /** INVITE (leader only) */
    private static final int SLOT_INVITE = 47;
    private static final int SLOT_CLOSE  = 49;
    private static final int SLOT_CHAT   = 51;
    /** WARP TP if warp set; SET WARP for leader if not set */
    private static final int SLOT_RIGHT  = 53;

    //  Member / pending rows 
    private static final int[] MEMBER_SLOTS  = { 10, 11, 12, 13, 14, 15 };
    private static final int[] PENDING_SLOTS = { 19, 20, 21, 22, 23, 24 };

    private final PartyManager partyManager;

    public PartyMenu(Plugin plugin, PartyManager partyManager) {
        super(plugin, "\u00a7d\u00a7l\u2694 Party", 54);
        this.partyManager = partyManager;
    }

    //  Populate 

    @Override
    protected void populate(Player viewer) {
        inventory.clear();
        fillGlass();

        Party party = partyManager.getParty(viewer.getUniqueId());

        //  No-party state: instructions + close 
        if (party == null) {
            inventory.setItem(SLOT_TITLE, new ItemBuilder(Material.BARRIER)
                    .name("\u00a7c\u00a7lNo Active Party")
                    .lore(List.of(
                            "\u00a77You are not in a party.",
                            "",
                            "\u00a7e/party create \u00a77\u2014 Create a new party",
                            "\u00a7e/party join <player> \u00a77\u2014 Join an open party",
                            "\u00a7e/party accept <player> \u00a77\u2014 Accept an invite"))
                    .build());
            inventory.setItem(SLOT_CLOSE, new ItemBuilder(Material.BARRIER)
                    .name("\u00a7cClose")
                    .lore(List.of("\u00a77Click to close this menu."))
                    .build());
            return;
        }

        boolean isLeader = party.isLeader(viewer.getUniqueId());
        String leaderName = resolveDisplayName(party.getLeader());
        int maxSize = plugin.getConfig().getInt(PartyManager.CONFIG_MAX_SIZE, PartyManager.DEFAULT_MAX_SIZE);
        long agoMin = (System.currentTimeMillis() - party.getCreatedAt()) / 60_000L;

        //  Title 
        inventory.setItem(SLOT_TITLE, new ItemBuilder(Material.NETHER_STAR)
                .name("\u00a7d\u00a7l\u2694 Party")
                .lore(List.of(
                        "\u00a77Leader: \u00a7f" + leaderName,
                        "\u00a77Members: \u00a7a" + party.size() + " \u00a78/ \u00a77" + maxSize,
                        "\u00a77Created: \u00a7f" + (agoMin < 1 ? "just now" : agoMin + "m ago")))
                .glow(true)
                .build());

        //  Member skulls (row 1) 
        List<UUID> members = new ArrayList<>(party.getMembers());
        for (int i = 0; i < members.size() && i < MEMBER_SLOTS.length; i++) {
            inventory.setItem(MEMBER_SLOTS[i], buildMemberSkull(members.get(i), party, isLeader));
        }

        //  Pending invite skulls (row 2) 
        List<UUID> pending = new ArrayList<>(party.getPendingInvites());
        for (int i = 0; i < pending.size() && i < PENDING_SLOTS.length; i++) {
            inventory.setItem(PENDING_SLOTS[i], buildPendingSkull(pending.get(i)));
        }

        //  Status strip (row 3) 
        // Info
        inventory.setItem(SLOT_INFO, new ItemBuilder(Material.BOOK)
                .name("\u00a7b\u00a7lParty Members")
                .lore(buildInfoLore(party))
                .build());

        // Friendly fire
        boolean ff = party.isFriendlyFire();
        inventory.setItem(SLOT_FF, new ItemBuilder(ff ? Material.FLINT_AND_STEEL : Material.SHIELD)
                .name(ff ? "\u00a7c\u00a7lFriendly Fire \u00a7aON" : "\u00a7a\u00a7lFriendly Fire \u00a7cOFF")
                .lore(List.of(
                        ff ? "\u00a77Members \u00a7ccan \u00a77damage each other."
                           : "\u00a77Members \u00a7acannot \u00a77damage each other.",
                        "",
                        isLeader ? "\u00a7eClick to toggle" : "\u00a78Leader only"))
                .build());

        // Open / invite-only
        boolean open = party.isOpen();
        inventory.setItem(SLOT_LOCK, new ItemBuilder(open ? Material.LIME_DYE : Material.RED_DYE)
                .name(open ? "\u00a7a\u00a7lOpen Party" : "\u00a7c\u00a7lInvite Only")
                .lore(List.of(
                        open ? "\u00a77Anyone can \u00a7e/party join " + leaderName + "\u00a77."
                             : "\u00a77Players need an invite to join.",
                        "",
                        isLeader ? "\u00a7eClick to toggle" : "\u00a78Leader only"))
                .build());

        // Warp status
        Location warp = party.getWarpLocation();
        if (warp != null) {
            inventory.setItem(SLOT_WARP_S, new ItemBuilder(Material.ENDER_PEARL)
                    .name("\u00a75\u00a7lParty Warp")
                    .lore(List.of(
                            "\u00a77World: \u00a7f" + (warp.getWorld() != null ? warp.getWorld().getName() : "?"),
                            "\u00a77XYZ: \u00a7f" + (int) warp.getX() + ", " + (int) warp.getY() + ", " + (int) warp.getZ(),
                            "",
                            "\u00a7eClick to teleport",
                            isLeader ? "\u00a78Right-click \u00a77to clear warp" : ""))
                    .build());
        } else {
            inventory.setItem(SLOT_WARP_S, new ItemBuilder(Material.COMPASS)
                    .name("\u00a77\u00a7lNo Party Warp")
                    .lore(List.of(
                            "\u00a77No warp has been set.",
                            "",
                            isLeader ? "\u00a7eClick to set warp here" : "\u00a78Leader can set with \u00a7e/party setwarp"))
                    .build());
        }

        //  Action bar (row 5) 
        // Left slot: DISBAND (leader) or LEAVE (member)
        if (isLeader) {
            inventory.setItem(SLOT_LEFT, new ItemBuilder(Material.TNT)
                    .name("\u00a7c\u00a7lDisband Party")
                    .lore(List.of(
                            "\u00a77Disbands the party and removes",
                            "\u00a77all members.",
                            "",
                            "\u00a7eClick to disband"))
                    .build());
            inventory.setItem(SLOT_INVITE, new ItemBuilder(Material.PLAYER_HEAD)
                    .name("\u00a7a\u00a7lInvite Player")
                    .lore(List.of(
                            "\u00a77Add a player to your party.",
                            "",
                            "\u00a7e/party invite <player>",
                            "\u00a77or \u00a7eclick \u00a77to type a name in chat"))
                    .build());
        } else {
            inventory.setItem(SLOT_LEFT, new ItemBuilder(Material.DARK_OAK_DOOR)
                    .name("\u00a7c\u00a7lLeave Party")
                    .lore(List.of(
                            "\u00a77Leave the current party.",
                            "",
                            "\u00a7eClick to leave"))
                    .build());
        }

        inventory.setItem(SLOT_CLOSE, new ItemBuilder(Material.BARRIER)
                .name("\u00a7cClose")
                .lore(List.of("\u00a77Click to close this menu."))
                .build());

        boolean chatOn = party.hasChat(viewer.getUniqueId());
        inventory.setItem(SLOT_CHAT, new ItemBuilder(Material.WRITABLE_BOOK)
                .name(chatOn ? "\u00a7d\u00a7lParty Chat \u00a7aON" : "\u00a77Party Chat \u00a7cOFF")
                .lore(List.of(
                        chatOn ? "\u00a77Your messages go to the party." : "\u00a77Your messages go to public chat.",
                        "",
                        "\u00a7e/p <message> \u00a77for quick party chat",
                        "\u00a7eClick to toggle"))
                .build());

        // Right slot: warp TP or set warp
        if (warp != null) {
            inventory.setItem(SLOT_RIGHT, new ItemBuilder(Material.ENDER_PEARL)
                    .name("\u00a75\u00a7lWarp to Party")
                    .lore(List.of(
                            "\u00a77Teleport to the party warp.",
                            "",
                            "\u00a7eClick to teleport"))
                    .build());
        } else if (isLeader) {
            inventory.setItem(SLOT_RIGHT, new ItemBuilder(Material.COMPASS)
                    .name("\u00a77\u00a7lSet Party Warp")
                    .lore(List.of(
                            "\u00a77Set your current location as the",
                            "\u00a77party warp point.",
                            "",
                            "\u00a7eClick to set warp"))
                    .build());
        }
    }

    //  Click handler 

    @Override
    public boolean handleClick(Player who, int slot, boolean leftClick, boolean shiftClick, boolean rightClick) {
        if (slot == SLOT_CLOSE) { MenuService.get().close(who); return true; }

        Party party = partyManager.getParty(who.getUniqueId());
        if (party == null) return true;

        boolean isLeader = party.isLeader(who.getUniqueId());

        //  Action bar 
        if (slot == SLOT_LEFT) {
            MenuService.get().close(who);
            if (isLeader) partyManager.disband(who); else partyManager.leave(who);
            return true;
        }

        if (slot == SLOT_INVITE && isLeader) {
            who.closeInventory();
            ChatInputService.prompt(who,
                    "\u00a7d  Type the player name to invite,\n\u00a7d  or type \u00a7fcancel \u00a7dto cancel:",
                    plugin,
                    input -> {
                        if ("cancel".equalsIgnoreCase(input)) return;
                        Player target = Bukkit.getPlayerExact(input);
                        if (target == null) {
                            who.sendMessage(partyManager.partyMsg("\u00a7cPlayer \u00a7e" + input + " \u00a7cis not online."));
                            return;
                        }
                        partyManager.invite(who, target);
                    });
            return true;
        }

        if (slot == SLOT_CHAT) {
            partyManager.toggleChat(who);
            populate(who);
            who.updateInventory();
            return true;
        }

        if (slot == SLOT_RIGHT) {
            MenuService.get().close(who);
            if (party.getWarpLocation() != null) partyManager.warpToParty(who);
            else if (isLeader) partyManager.setWarp(who);
            return true;
        }

        //  Status strip toggles (leader only) 
        if (slot == SLOT_FF && isLeader) {
            partyManager.toggleFriendlyFire(who);
            populate(who);
            who.updateInventory();
            return true;
        }

        if (slot == SLOT_LOCK && isLeader) {
            partyManager.toggleOpen(who);
            populate(who);
            who.updateInventory();
            return true;
        }

        // Warp strip slot: click = tp; right-click (leader) = clear warp; leader + no warp = set warp
        if (slot == SLOT_WARP_S) {
            Location warp = party.getWarpLocation();
            if (warp != null) {
                if (rightClick && isLeader) {
                    partyManager.clearWarp(who);
                    populate(who);
                    who.updateInventory();
                } else {
                    MenuService.get().close(who);
                    partyManager.warpToParty(who);
                }
            } else if (isLeader) {
                MenuService.get().close(who);
                partyManager.setWarp(who);
            }
            return true;
        }

        //  Member skull clicks (leader: left=kick, right=transfer) 
        if (isLeader) {
            List<UUID> members = new ArrayList<>(party.getMembers());
            for (int i = 0; i < MEMBER_SLOTS.length; i++) {
                if (slot != MEMBER_SLOTS[i]) continue;
                if (i >= members.size()) break;
                UUID target = members.get(i);
                if (target.equals(who.getUniqueId())) break;
                Player targetPlayer = Bukkit.getPlayer(target);
                if (targetPlayer == null) break;
                MenuService.get().close(who);
                if (rightClick) partyManager.transfer(who, targetPlayer);
                else partyManager.kick(who, targetPlayer);
                return true;
            }
        }

        return true;
    }

    //  Helpers 

    private ItemStack buildMemberSkull(UUID uuid, Party party, boolean viewerIsLeader) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        meta.setOwningPlayer(op);
        boolean online  = op.isOnline();
        boolean isLeader = party.isLeader(uuid);
        String colored  = partyManager.coloredName(uuid);
        meta.setDisplayName((isLeader ? "\u00a76\u2605 " : "") + colored);
        List<String> lore = new ArrayList<>();
        lore.add(online ? "\u00a7a\u2b24 Online" : "\u00a78\u2b24 Offline");
        if (isLeader) lore.add("\u00a76Party Leader");
        if (party.hasChat(uuid)) lore.add("\u00a7d\u2694 Party Chat active");
        if (viewerIsLeader && !isLeader) {
            lore.add("");
            lore.add("\u00a7eLeft-click \u00a77to kick");
            lore.add("\u00a7eRight-click \u00a77to transfer leadership");
        }
        meta.setLore(lore);
        skull.setItemMeta(meta);
        return skull;
    }

    private ItemStack buildPendingSkull(UUID uuid) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        meta.setOwningPlayer(op);
        String name = op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);
        meta.setDisplayName("\u00a77" + name);
        meta.setLore(List.of("\u00a77\u23f3 Invite pending...", "\u00a78Waiting for response"));
        skull.setItemMeta(meta);
        return skull;
    }

    private List<String> buildInfoLore(Party party) {
        List<String> lore = new ArrayList<>();
        lore.add("\u00a77Current members:");
        lore.add("");
        for (UUID uuid : party.getMembers()) {
            boolean online = Bukkit.getPlayer(uuid) != null;
            String col = online ? "\u00a7a" : "\u00a78";
            lore.add("  " + col + (party.isLeader(uuid) ? "\u2605 " : "\u2022 ") + resolveDisplayName(uuid));
        }
        return lore;
    }

    /** Fills all 54 slots with gray glass  content items are set on top afterwards. */
    private void fillGlass() {
        ItemStack glass = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("\u00a78 ").build();
        for (int i = 0; i < 54; i++) inventory.setItem(i, glass);
    }

    private String resolveDisplayName(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) return p.getName();
        String n = Bukkit.getOfflinePlayer(uuid).getName();
        return n != null ? n : uuid.toString().substring(0, 8);
    }
}