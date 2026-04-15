package com.darkniightz.core.commands;

import com.darkniightz.core.Messages;
import com.darkniightz.core.chat.ChatUtil;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.main.JebaitedCore;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.darkniightz.core.system.OverallStatsManager;

/**
 * Supported Paper range: 1.21.9-1.21.11.
 */
public class TradeCommand implements CommandExecutor, Listener {
    private static final double MAX_DISTANCE = 10.0;
    private static final long REQUEST_TTL_MS = 30_000L;
    private static final int COUNTDOWN_SECONDS = 5;

    private static final int[] LEFT_OFFER_SLOTS = {0, 1, 2, 9, 10, 11, 18, 19, 20};
    private static final int[] RIGHT_OFFER_SLOTS = {6, 7, 8, 15, 16, 17, 24, 25, 26};
    private static final int LEFT_MONEY_SLOT = 3;
    private static final int RIGHT_MONEY_SLOT = 23;
    private static final int LEFT_CONFIRM_SLOT = 4;
    private static final int RIGHT_CONFIRM_SLOT = 22;
    private static final long REQUEST_COOLDOWN_MS = 3_000L;
    private static final String TRADE_TITLE = "§8[Jebaited] §bTrade";

    private final JebaitedCore plugin;
    private final Map<UUID, Request> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Long> requestCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, PendingMoneyPrompt> pendingMoneyPrompts = new ConcurrentHashMap<>();
    private final Map<UUID, TradeSession> activeByPlayer = new ConcurrentHashMap<>();

    public TradeCommand(JebaitedCore plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            tell(sender, "§cOnly players can use /trade.");
            return true;
        }

        if (args.length == 0) {
            tell(player, "§eUsage: /trade <player>");
            tell(player, "§eUsage: /trade accept <player>");
            tell(player, "§eUsage: /trade deny <player>");
            return true;
        }

        if (isTradeBlockedInHub(player)) {
            tell(player, "§cTrades are disabled in the Hub.");
            play(player, Sound.ENTITY_VILLAGER_NO, 0.8f, 0.9f);
            return true;
        }

        if ("accept".equalsIgnoreCase(args[0]) || "deny".equalsIgnoreCase(args[0])) {
            if (args.length < 2) {
                tell(player, "§cSpecify a player.");
                return true;
            }
            Player other = Bukkit.getPlayerExact(args[1]);
            if (other == null) {
                tell(player, "§cPlayer not online.");
                return true;
            }
            Request req = pending.get(player.getUniqueId());
            if (req == null || !req.from.equals(other.getUniqueId()) || isExpired(req)) {
                tell(player, "§cNo valid trade request from that player.");
                return true;
            }
            pending.remove(player.getUniqueId());

            if ("deny".equalsIgnoreCase(args[0])) {
                tell(player, "§7You declined the trade request from §f" + traderName(other) + "§7.");
                tell(other, "§c" + traderName(player) + " §cdeclined your trade request.");
                play(player, Sound.UI_BUTTON_CLICK, 0.8f, 0.8f);
                play(other, Sound.ENTITY_VILLAGER_NO, 0.8f, 0.9f);
                return true;
            }

            if (isTradeBlockedInHub(other)) {
                tell(player, "§cTrades are disabled in the Hub.");
                tell(other, "§cTrades are disabled in the Hub.");
                return true;
            }
            if (!isClose(player, other)) {
                tell(player, "§cYou must stay within 10 blocks to trade.");
                tell(other, "§cTrade failed: players are too far apart.");
                return true;
            }
            if (!hasTradeRequestsEnabled(player) || !hasTradeRequestsEnabled(other)) {
                tell(player, "§cTrade failed because one player has trade requests disabled.");
                tell(other, "§cTrade failed because one player has trade requests disabled.");
                return true;
            }
            if (activeByPlayer.containsKey(player.getUniqueId()) || activeByPlayer.containsKey(other.getUniqueId())) {
                tell(player, "§cOne of you is already in a trade.");
                tell(other, "§cOne of you is already in a trade.");
                return true;
            }

            openTrade(player, other);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || target.getUniqueId().equals(player.getUniqueId())) {
            tell(player, "§cChoose a valid online player.");
            return true;
        }
        if (isTradeBlockedInHub(target)) {
            tell(player, "§cTrades are disabled in the Hub.");
            return true;
        }
        if (!isClose(player, target)) {
            tell(player, "§cYou must be within 10 blocks to trade.");
            return true;
        }
        if (!hasTradeRequestsEnabled(target)) {
            tell(player, "§cThat player has trade requests disabled.");
            return true;
        }
        if (activeByPlayer.containsKey(player.getUniqueId()) || activeByPlayer.containsKey(target.getUniqueId())) {
            tell(player, "§cOne of you is already in a trade.");
            return true;
        }

        long now = System.currentTimeMillis();
        long lastSent = requestCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastSent < REQUEST_COOLDOWN_MS) {
            tell(player, "§cWait a moment before sending another trade request.");
            return true;
        }
        requestCooldowns.put(player.getUniqueId(), now);

        pending.put(target.getUniqueId(), new Request(player.getUniqueId(), now));
        tell(player, "§aTrade request sent to §f" + traderName(target) + "§a.");
        play(player, Sound.UI_BUTTON_CLICK, 0.8f, 1.3f);
        sendTradeInvite(player, target);
        scheduleRequestExpiryNotice(player, target);
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTradeClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        TradeSession session = activeByPlayer.get(player.getUniqueId());
        if (session == null) return;
        if (!event.getView().getTopInventory().equals(session.inventory)) return;

        int raw = event.getRawSlot();
        int topSize = session.inventory.getSize();

        if (event.isShiftClick() || event.getHotbarButton() >= 0) {
            event.setCancelled(true);
            return;
        }

        if (raw >= topSize) {
            event.setCancelled(session.locked);
            return;
        }

        boolean left = session.left.getUniqueId().equals(player.getUniqueId());
        int[] ownSlots = left ? LEFT_OFFER_SLOTS : RIGHT_OFFER_SLOTS;
        int ownConfirm = left ? LEFT_CONFIRM_SLOT : RIGHT_CONFIRM_SLOT;
        int ownMoneySlot = left ? LEFT_MONEY_SLOT : RIGHT_MONEY_SLOT;

        if (session.locked && (isIn(raw, ownSlots) || raw == ownMoneySlot)) {
            event.setCancelled(true);
            tell(player, "§cTrade is locked during final countdown.");
            return;
        }

        if (raw == ownMoneySlot) {
            event.setCancelled(true);
            promptForMoney(player, session, left);
            return;
        }

        if (isIn(raw, ownSlots)) {
            resetReady(session);
            refreshButtons(session);
            event.setCancelled(false);
            return;
        }

        if (raw == ownConfirm) {
            event.setCancelled(true);
            if (!participantsOnline(session)) {
                cancelSession(session, "§cTrade cancelled because a player left.");
                return;
            }
            if (!isClose(session.left, session.right)) {
                cancelSession(session, "§cTrade cancelled: players moved too far apart.");
                return;
            }

            if (left) session.leftReady = !session.leftReady;
            else session.rightReady = !session.rightReady;

            boolean nowReady = left ? session.leftReady : session.rightReady;
            Player partner = left ? session.right : session.left;
            if (nowReady) {
                tell(player, "§aYou are marked ready. Waiting on §f" + traderName(partner) + "§a.");
                tell(partner, "§b" + traderName(player) + "§7 is now ready.");
                play(player, Sound.UI_BUTTON_CLICK, 0.8f, 1.3f);
                play(partner, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);
            } else {
                tell(player, "§eYou are no longer marked ready.");
                tell(partner, "§e" + traderName(player) + " §eis no longer ready.");
                play(player, Sound.UI_BUTTON_CLICK, 0.8f, 0.9f);
            }

            if (!session.leftReady || !session.rightReady) {
                stopCountdown(session);
                session.locked = false;
            }

            refreshButtons(session);
            if (session.leftReady && session.rightReady) {
                startCountdown(session);
            }
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTradeDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        TradeSession session = activeByPlayer.get(player.getUniqueId());
        if (session == null) return;
        if (!event.getView().getTopInventory().equals(session.inventory)) return;

        if (session.locked) {
            event.setCancelled(true);
            return;
        }

        boolean left = session.left.getUniqueId().equals(player.getUniqueId());
        int[] ownSlots = left ? LEFT_OFFER_SLOTS : RIGHT_OFFER_SLOTS;

        for (int slot : event.getRawSlots()) {
            if (slot >= session.inventory.getSize()) continue;
            if (!isIn(slot, ownSlots)) {
                event.setCancelled(true);
                return;
            }
        }

        resetReady(session);
        refreshButtons(session);
    }

    @EventHandler
    public void onTradeClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        TradeSession session = activeByPlayer.get(player.getUniqueId());
        if (session == null) return;
        if (!event.getInventory().equals(session.inventory)) return;
        if (session.closing) return;
        if (session.promptingPlayers.contains(player.getUniqueId())) return;
        cancelSession(session, "§cTrade cancelled.");
    }

    private void openTrade(Player a, Player b) {
        Inventory inv = Bukkit.createInventory(null, 27, TRADE_TITLE);
        decorate(inv);

        TradeSession session = new TradeSession(a, b, inv);
        activeByPlayer.put(a.getUniqueId(), session);
        activeByPlayer.put(b.getUniqueId(), session);

        refreshButtons(session);
        a.openInventory(inv);
        b.openInventory(inv);
        tell(a, "§aTrade started with §f" + traderName(b) + "§a. Add items, set gold, then ready up.");
        tell(b, "§aTrade started with §f" + traderName(a) + "§a. Add items, set gold, then ready up.");
        play(a, Sound.BLOCK_CHEST_OPEN, 0.9f, 1.15f);
        play(b, Sound.BLOCK_CHEST_OPEN, 0.9f, 1.15f);
        a.sendActionBar(ChatColor.AQUA + "Jebaited Trade opened with " + ChatColor.WHITE + ChatColor.stripColor(traderName(b)));
        b.sendActionBar(ChatColor.AQUA + "Jebaited Trade opened with " + ChatColor.WHITE + ChatColor.stripColor(traderName(a)));
    }

    private void decorate(Inventory inv) {
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, "§8 ");
        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (isIn(slot, LEFT_OFFER_SLOTS) || isIn(slot, RIGHT_OFFER_SLOTS)
                    || slot == LEFT_MONEY_SLOT || slot == RIGHT_MONEY_SLOT
                    || slot == LEFT_CONFIRM_SLOT || slot == RIGHT_CONFIRM_SLOT) {
                continue;
            }
            inv.setItem(slot, pane);
        }
        ItemStack badge = named(Material.NETHER_STAR, "§b§lJebaited Trade");
        var meta = badge.getItemMeta();
        if (meta != null) {
            meta.setLore(List.of(
                    "§7Offer items or gold to your partner.",
                    "§7When both players are ready, the trade locks in.",
                    "§8Fast, safe, and custom."
            ));
            badge.setItemMeta(meta);
        }
        inv.setItem(13, badge);
    }

    private void refreshButtons(TradeSession session) {
        session.inventory.setItem(LEFT_MONEY_SLOT, moneyControlItem(session.left, session.leftMoney));
        session.inventory.setItem(RIGHT_MONEY_SLOT, moneyControlItem(session.right, session.rightMoney));
        session.inventory.setItem(LEFT_CONFIRM_SLOT, readyButtonItem(session.left, session.leftReady, session.locked));
        session.inventory.setItem(RIGHT_CONFIRM_SLOT, readyButtonItem(session.right, session.rightReady, session.locked));
    }

    private void startCountdown(TradeSession session) {
        if (session.countdownTask != null) return;
        session.locked = true;
        session.countdownSeconds = COUNTDOWN_SECONDS;
        refreshButtons(session);
        tell(session.left, "§7Both players are ready. Trade locking in §f" + COUNTDOWN_SECONDS + "s§7...");
        tell(session.right, "§7Both players are ready. Trade locking in §f" + COUNTDOWN_SECONDS + "s§7...");
        play(session.left, Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1.35f);
        play(session.right, Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1.35f);
        session.countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!participantsOnline(session)) {
                cancelSession(session, "§cTrade cancelled because a player left.");
                return;
            }
            if (!isClose(session.left, session.right)) {
                cancelSession(session, "§cTrade cancelled: players moved too far apart.");
                return;
            }
            if (!session.leftReady || !session.rightReady) {
                stopCountdown(session);
                session.locked = false;
                refreshButtons(session);
                return;
            }

            session.countdownSeconds--;
            if (session.countdownSeconds > 0) {
                session.left.sendActionBar(ChatColor.GRAY + "Trade finalizing in " + ChatColor.WHITE + session.countdownSeconds + "s");
                session.right.sendActionBar(ChatColor.GRAY + "Trade finalizing in " + ChatColor.WHITE + session.countdownSeconds + "s");
                return;
            }

            stopCountdown(session);
            finalizeTrade(session);
        }, 20L, 20L);
    }

    private void stopCountdown(TradeSession session) {
        if (session.countdownTask != null) {
            session.countdownTask.cancel();
            session.countdownTask = null;
        }
    }

    private void resetReady(TradeSession session) {
        session.leftReady = false;
        session.rightReady = false;
        session.locked = false;
        stopCountdown(session);
    }

    private void finalizeTrade(TradeSession session) {
        if (!participantsOnline(session)) {
            cancelSession(session, "§cTrade cancelled because a player left.");
            return;
        }
        if (!isClose(session.left, session.right)) {
            cancelSession(session, "§cTrade cancelled: players moved too far apart.");
            return;
        }
        if (!transferMoney(session)) {
            return;
        }

        List<ItemStack> leftItems = pullItems(session.inventory, LEFT_OFFER_SLOTS);
        List<ItemStack> rightItems = pullItems(session.inventory, RIGHT_OFFER_SLOTS);

        giveItems(session.left, rightItems);
        giveItems(session.right, leftItems);

        session.closing = true;
        session.left.closeInventory();
        session.right.closeInventory();
        removeSession(session);

        tell(session.left, "§aTrade complete with §f" + traderName(session.right) + "§a.");
        tell(session.right, "§aTrade complete with §f" + traderName(session.left) + "§a.");
        play(session.left, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        play(session.right, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        if (session.leftMoney > 0) {
            tell(session.left, "§7You paid §6$" + formatShortMoney(session.leftMoney) + "§7.");
            tell(session.right, "§7You received §6$" + formatShortMoney(session.leftMoney) + "§7.");
        }
        if (session.rightMoney > 0) {
            tell(session.right, "§7You paid §6$" + formatShortMoney(session.rightMoney) + "§7.");
            tell(session.left, "§7You received §6$" + formatShortMoney(session.rightMoney) + "§7.");
        }
        if (plugin.getOverallStatsManager() != null) {
            plugin.getOverallStatsManager().increment(OverallStatsManager.TOTAL_TRADES, 1);
        }
    }

    private void cancelSession(TradeSession session, String reason) {
        List<ItemStack> leftItems = pullItems(session.inventory, LEFT_OFFER_SLOTS);
        List<ItemStack> rightItems = pullItems(session.inventory, RIGHT_OFFER_SLOTS);

        if (session.left.isOnline()) giveItems(session.left, leftItems);
        if (session.right.isOnline()) giveItems(session.right, rightItems);

        session.closing = true;
        if (session.left.isOnline()) session.left.closeInventory();
        if (session.right.isOnline()) session.right.closeInventory();
        removeSession(session);

        if (session.left.isOnline()) {
            tell(session.left, reason);
            play(session.left, Sound.ENTITY_VILLAGER_NO, 0.8f, 0.9f);
        }
        if (session.right.isOnline()) {
            tell(session.right, reason);
            play(session.right, Sound.ENTITY_VILLAGER_NO, 0.8f, 0.9f);
        }
    }

    private void removeSession(TradeSession session) {
        activeByPlayer.remove(session.left.getUniqueId());
        activeByPlayer.remove(session.right.getUniqueId());
        stopCountdown(session);
    }

    private List<ItemStack> pullItems(Inventory inv, int[] slots) {
        List<ItemStack> out = new ArrayList<>();
        for (int slot : slots) {
            ItemStack item = inv.getItem(slot);
            if (!isEmpty(item)) {
                out.add(item.clone());
            }
            inv.setItem(slot, null);
        }
        return out;
    }

    private void giveItems(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            Map<Integer, ItemStack> left = player.getInventory().addItem(item);
            for (ItemStack leftover : left.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        player.updateInventory();
    }

    @EventHandler(ignoreCancelled = true)
    public void onTradeAmountChat(AsyncChatEvent event) {
        PendingMoneyPrompt prompt = pendingMoneyPrompts.remove(event.getPlayer().getUniqueId());
        if (prompt == null) {
            return;
        }
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> handleMoneyInput(event.getPlayer(), prompt, input));
    }

    private boolean isClose(Player a, Player b) {
        if (a == null || b == null || !a.getWorld().equals(b.getWorld())) return false;
        return a.getLocation().distanceSquared(b.getLocation()) <= (MAX_DISTANCE * MAX_DISTANCE);
    }

    private boolean hasTradeRequestsEnabled(Player player) {
        if (player == null || plugin.getProfileStore() == null || plugin.getRankManager() == null) {
            return true;
        }
        var profile = plugin.getProfileStore().getOrCreate(player, plugin.getRankManager().getDefaultGroup());
        return profile == null || profile.isTradeRequestsEnabled();
    }

    private boolean isTradeBlockedInHub(Player player) {
        return player != null && plugin.getWorldManager() != null && plugin.getWorldManager().isHub(player);
    }

    private boolean isExpired(Request request) {
        return System.currentTimeMillis() - request.createdAt > REQUEST_TTL_MS;
    }

    private boolean participantsOnline(TradeSession session) {
        return session.left.isOnline() && session.right.isOnline();
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private boolean isIn(int slot, int[] slots) {
        for (int s : slots) {
            if (s == slot) return true;
        }
        return false;
    }

    private void promptForMoney(Player player, TradeSession session, boolean left) {
        if (plugin.getEconomyManager() == null) {
            tell(player, "§cEconomy offers are currently unavailable.");
            return;
        }
        session.promptingPlayers.add(player.getUniqueId());
        pendingMoneyPrompts.put(player.getUniqueId(), new PendingMoneyPrompt(session, left));
        player.closeInventory();
        tell(player, "§6Trade offer amount input");
        tell(player, "§7Type an amount in chat using §e100k§7, §e1m§7, or §e1t§7.");
        tell(player, "§7Type §ecancel§7 to keep the current offer.");
        play(player, Sound.UI_BUTTON_CLICK, 0.8f, 1.15f);
    }

    private void handleMoneyInput(Player player, PendingMoneyPrompt prompt, String input) {
        TradeSession session = prompt.session();
        session.promptingPlayers.remove(player.getUniqueId());

        if (session != activeByPlayer.get(player.getUniqueId())) {
            tell(player, "§cThat trade is no longer active.");
            return;
        }

        if (input.equalsIgnoreCase("cancel")) {
            tell(player, "§7Trade amount unchanged.");
            player.openInventory(session.inventory);
            return;
        }

        double amount = parseAmount(input);
        if (amount < 0D) {
            tell(player, "§cInvalid amount. Try values like §e25000§c, §e100k§c, §e1m§c, or §e1t§c.");
            player.openInventory(session.inventory);
            return;
        }

        if (plugin.getEconomyManager() == null) {
            tell(player, "§cEconomy offers are currently unavailable.");
            player.openInventory(session.inventory);
            return;
        }

        if (amount > plugin.getEconomyManager().getBalance(player)) {
            tell(player, "§cYou do not have that much balance to offer.");
            player.openInventory(session.inventory);
            return;
        }

        if (prompt.leftSide()) {
            session.leftMoney = amount;
        } else {
            session.rightMoney = amount;
        }
        resetReady(session);
        refreshButtons(session);
        tell(player, amount <= 0D ? "§7Money offer cleared." : "§aMoney offer set to §6$" + formatShortMoney(amount) + "§a.");
        play(player, amount <= 0D ? Sound.UI_BUTTON_CLICK : Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, amount <= 0D ? 0.9f : 1.25f);
        if (session.left.isOnline()) {
            session.left.updateInventory();
        }
        if (session.right.isOnline()) {
            session.right.updateInventory();
        }
        player.openInventory(session.inventory);
    }

    private boolean transferMoney(TradeSession session) {
        if (plugin.getEconomyManager() == null) {
            return true;
        }

        double leftMoney = Math.max(0D, session.leftMoney);
        double rightMoney = Math.max(0D, session.rightMoney);
        if (leftMoney <= 0D && rightMoney <= 0D) {
            return true;
        }

        if (plugin.getEconomyManager().getBalance(session.left) + 0.01D < leftMoney) {
            cancelSession(session, "§cTrade cancelled: " + traderName(session.left) + " §cno longer has enough balance.");
            return false;
        }
        if (plugin.getEconomyManager().getBalance(session.right) + 0.01D < rightMoney) {
            cancelSession(session, "§cTrade cancelled: " + traderName(session.right) + " §cno longer has enough balance.");
            return false;
        }

        if (leftMoney > 0D && !plugin.getEconomyManager().removeBalance(session.left, leftMoney)) {
            cancelSession(session, "§cTrade cancelled: could not collect the offered balance.");
            return false;
        }
        if (rightMoney > 0D && !plugin.getEconomyManager().removeBalance(session.right, rightMoney)) {
            if (leftMoney > 0D) {
                plugin.getEconomyManager().addBalance(session.left, leftMoney);
            }
            cancelSession(session, "§cTrade cancelled: could not collect the offered balance.");
            return false;
        }

        if (leftMoney > 0D) {
            plugin.getEconomyManager().addBalance(session.right, leftMoney);
        }
        if (rightMoney > 0D) {
            plugin.getEconomyManager().addBalance(session.left, rightMoney);
        }
        return true;
    }

    private void tell(CommandSender sender, String message) {
        if (sender != null) {
            sender.sendMessage(Messages.prefixed(message));
        }
    }

    private void tell(Player player, Component message) {
        if (player != null && message != null) {
            player.sendMessage(Component.text("[", NamedTextColor.DARK_GRAY)
                    .append(Component.text("Jebaited", NamedTextColor.AQUA, TextDecoration.BOLD))
                    .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                    .append(message));
        }
    }

    private void play(Player player, Sound sound, float volume, float pitch) {
        if (player != null && player.isOnline()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    private void sendTradeInvite(Player requester, Player target) {
        var legacy = LegacyComponentSerializer.legacySection();
        tell(target, Component.text("Trade request from ", NamedTextColor.GRAY)
                .append(legacy.deserialize(traderName(requester)))
                .append(Component.text(".", NamedTextColor.GRAY)));
        tell(target, Component.text("[ACCEPT]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/trade accept " + requester.getName()))
                .hoverEvent(HoverEvent.showText(Component.text("Accept the trade request", NamedTextColor.GREEN)))
                .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                .append(Component.text("[DENY]", NamedTextColor.RED, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/trade deny " + requester.getName()))
                        .hoverEvent(HoverEvent.showText(Component.text("Decline the trade request", NamedTextColor.RED)))));
        tell(target, Component.text("This request expires in 30 seconds.", NamedTextColor.YELLOW));
        tell(target, Component.text("Click a button above or use /trade accept " + requester.getName(), NamedTextColor.GRAY));
        play(target, Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1.25f);
    }

    private void scheduleRequestExpiryNotice(Player requester, Player target) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Request req = pending.get(target.getUniqueId());
            if (req == null || !req.from.equals(requester.getUniqueId()) || !isExpired(req)) {
                return;
            }
            pending.remove(target.getUniqueId());
            if (requester.isOnline()) {
                tell(requester, "§eYour trade request to §f" + traderName(target) + " §eexpired.");
            }
            if (target.isOnline()) {
                tell(target, "§7The trade request from §f" + traderName(requester) + " §7expired.");
            }
        }, (REQUEST_TTL_MS / 50L) + 1L);
    }

    private String traderName(Player player) {
        if (player == null) {
            return "Unknown";
        }
        if (plugin.getProfileStore() == null || plugin.getRankManager() == null) {
            return player.getName();
        }
        PlayerProfile profile = plugin.getProfileStore().getOrCreate(player, plugin.getRankManager().getDefaultGroup());
        String rank = profile == null || profile.getPrimaryRank() == null ? plugin.getRankManager().getDefaultGroup() : profile.getPrimaryRank();
        var style = plugin.getRankManager().getStyle(rank);
        String base = ChatColor.stripColor(player.getDisplayName());
        if (base == null || base.isBlank()) {
            base = player.getName();
        }
        String styled = ChatUtil.buildStyledName(base, style);
        return plugin.decorateStyledNameWithTag(profile, styled, false);
    }

    private double parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return -1D;
        }
        String normalized = raw.trim().toLowerCase().replace(",", "");
        double multiplier = 1D;
        if (normalized.endsWith("k")) {
            multiplier = 1_000D;
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("m")) {
            multiplier = 1_000_000D;
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("b")) {
            multiplier = 1_000_000_000D;
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("t")) {
            multiplier = 1_000_000_000_000D;
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        try {
            double base = Double.parseDouble(normalized);
            double result = Math.floor(base * multiplier * 100D) / 100D;
            return Double.isFinite(result) && result >= 0D ? result : -1D;
        } catch (NumberFormatException ex) {
            return -1D;
        }
    }

    private ItemStack moneyControlItem(Player owner, double amount) {
        Material material = amount > 0D ? Material.GOLD_INGOT : Material.GOLD_NUGGET;
        ItemStack item = named(material, "§6Jebaited Gold Offer §8• §f$" + formatShortMoney(amount));
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.setLore(List.of(
                    "§7Player: §f" + ChatColor.stripColor(traderName(owner)),
                    "§7Left-click to set or update the offer.",
                    "§7Supports §e100k§7, §e1m§7 and §e1t§7.",
                    amount > 0D ? "§aCurrent offer is active." : "§8No money currently offered."
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack readyButtonItem(Player owner, boolean ready, boolean locked) {
        Material material = ready ? Material.LIME_WOOL : Material.RED_WOOL;
        String title = ready
                ? (locked ? "§aLocked In • " + ChatColor.stripColor(traderName(owner)) : "§aReady • " + ChatColor.stripColor(traderName(owner)))
                : "§cNot Ready • " + ChatColor.stripColor(traderName(owner));
        ItemStack item = named(material, title);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.setLore(List.of(
                    "§7Trader: §f" + ChatColor.stripColor(traderName(owner)),
                    ready ? "§aThis side is confirmed." : "§7Click to toggle ready.",
                    locked ? "§6Trade is locked while the countdown runs." : "§8Both players must ready up to finish."
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatShortMoney(double amount) {
        double safe = Math.max(0D, amount);
        if (safe >= 1_000_000_000_000D) return String.format("%.1ft", safe / 1_000_000_000_000D);
        if (safe >= 1_000_000_000D) return String.format("%.1fb", safe / 1_000_000_000D);
        if (safe >= 1_000_000D) return String.format("%.1fm", safe / 1_000_000D);
        if (safe >= 1_000D) return String.format("%.1fk", safe / 1_000D);
        return String.format("%.0f", safe);
    }

    private ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private record Request(UUID from, long createdAt) {}

    private record PendingMoneyPrompt(TradeSession session, boolean leftSide) {}

    private static final class TradeSession {
        final Player left;
        final Player right;
        final Inventory inventory;
        boolean leftReady;
        boolean rightReady;
        boolean closing;
        boolean locked;
        int countdownSeconds;
        double leftMoney;
        double rightMoney;
        BukkitTask countdownTask;
        final java.util.Set<UUID> promptingPlayers = ConcurrentHashMap.newKeySet();

        private TradeSession(Player left, Player right, Inventory inventory) {
            this.left = left;
            this.right = right;
            this.inventory = inventory;
        }
    }
}
