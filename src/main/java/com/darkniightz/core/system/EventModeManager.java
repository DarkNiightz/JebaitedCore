package com.darkniightz.core.system;

import com.darkniightz.core.eventmode.ChatGameManager;
import com.darkniightz.core.eventmode.EventEngine;
import com.darkniightz.core.party.PartyManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Thin facade over {@link EventEngine} (combat) and {@link ChatGameManager} (parallel chat mini-games).
 */
public class EventModeManager {

    public record ActionResult(boolean ok, String message) {}

    private final EventEngine engine;
    private final ChatGameManager chatGames;

    public EventModeManager(Plugin plugin, BroadcasterManager broadcasterManager, BossBarManager bossBarManager, PartyManager partyManager) {
        this.engine = new EventEngine(plugin, broadcasterManager, bossBarManager, partyManager);
        this.chatGames = new ChatGameManager(plugin);
    }

    public void start() {
        engine.start();
        chatGames.start();
    }

    public void stop() {
        chatGames.stop();
        engine.stop();
    }

    public void reloadChatGamesFromConfig() {
        chatGames.reloadFromConfig();
    }

    public ChatGameManager chatGames() {
        return chatGames;
    }

    public boolean isActive()       { return engine.isActive();       }
    public String  getStatusLine()  { return engine.getStatusLine();  }

    public ActionResult startEvent(String key)              { return wrap(engine.startEvent(key));            }

    public ActionResult startEvent(String key, String arenaKey) {
        return wrap(engine.startEvent(key, arenaKey));
    }

    public void reloadArenasFromConfig() {
        engine.reloadArenasFromConfig();
    }

    public List<String> listArenaKeysForKind(String eventKindKey) {
        return engine.listArenaKeysForKind(eventKindKey);
    }

    public List<String> listArenaRegistryLines() {
        return engine.listArenaRegistryLines();
    }

    public String getEventInfoSummary() {
        return engine.getEventInfoSummary();
    }

    public ActionResult setRuntimeCoinReward(int coins) {
        return wrap(engine.setRuntimeCoinReward(coins));
    }

    public ActionResult staffSpectateEnter(Player player) {
        return wrap(engine.staffSpectateEnter(player));
    }

    public ActionResult staffSpectateLeave(Player player) {
        return wrap(engine.staffSpectateLeave(player));
    }

    public void handleCtfFlagInteract(Player player, org.bukkit.block.Block block) {
        engine.handleCtfFlagInteract(player, block);
    }

    /** @return true if the vanilla pickup should be cancelled. */
    public boolean handleCtfGroundFlagPickup(Player player, org.bukkit.entity.Item item) {
        return engine.handleCtfGroundFlagPickup(player, item);
    }
    public ActionResult stopEvent(String reason)            { return wrap(engine.stopEvent(reason));          }
    public ActionResult forceStart()                        { return wrap(engine.forceStart());               }
    public ActionResult completeEvent(Player w, Integer r, String reason) {
        return wrap(engine.completeEvent(w, r, reason));
    }

    public ActionResult joinQueue(Player player, boolean confirmed) { return wrap(engine.joinQueue(player, confirmed)); }
    public ActionResult leaveQueue(Player player)                   { return wrap(engine.leaveQueue(player)); }

    public boolean submitChatAnswer(Player player, String answer)   { return chatGames.submitAnswer(player, answer); }

    public ChatGameManager.ActionResult startChatGame(String key) {
        return chatGames.startRound(key);
    }

    public ChatGameManager.ActionResult stopChatGame(String reason) {
        return chatGames.stopRound(reason);
    }

    public String getChatGameStatusLine() {
        return chatGames.getStatusLine();
    }

    public List<String> getChatGameScoreboardLines() {
        return chatGames.getChatScoreboardLines();
    }

    public List<String> getConfiguredChatGameKeys() {
        return chatGames.getConfiguredKeys();
    }

    public List<String> getConfiguredChatGameDisplayNames() {
        return chatGames.getConfiguredDisplayNames();
    }

    public void    handleParticipantFatalDamage(Player player, Player killerOrNull) {
        engine.handleParticipantFatalDamage(player, killerOrNull);
    }
    public void    handleParticipantDeath(Player player)            { engine.handleParticipantDeath(player);   }
    public boolean isParticipant(Player player)                     { return engine.isParticipant(player);     }
    public boolean isActiveEventParticipant(Player player)          { return engine.isActiveEventParticipant(player); }
    public boolean areCtfTeammates(Player a, Player b)              { return engine.areCtfTeammates(a, b); }
    public boolean isParticipantInHardcore(Player player)           { return engine.isParticipantInHardcore(player); }
    public void    collectHardcoreLoot(Player player, List<ItemStack> drops) { engine.collectHardcoreLoot(player, drops); }
    public boolean shouldKeepInventoryOnDeath(Player player)        { return engine.shouldKeepInventoryOnDeath(player); }
    public void    handleParticipantRespawn(Player player)          { engine.handleParticipantRespawn(player); }

    public ActionResult  setupKothPosition(Player player, boolean first)   { return wrap(engine.setupKothPosition(player, first)); }
    public ActionResult  setupArenaSpawn(Player player, String key)        { return wrap(engine.setupArenaSpawn(player, key)); }
    public ActionResult  clearArenaSpawns(String key)                      { return wrap(engine.clearArenaSpawns(key)); }
    public List<String>  listArenaSpawns(String key)                       { return engine.listArenaSpawns(key); }
    public ActionResult  viewArenaSpawns(Player player, String key, int s) { return wrap(engine.viewArenaSpawns(player, key, s)); }
    public Location      getFirstArenaSpawn(String key)                    { return engine.getFirstArenaSpawn(key); }
    public int           getArenaSpawnCount(String key)                    { return engine.getArenaSpawnCount(key); }

    public List<String> getConfiguredEventKeys()         { return engine.getConfiguredEventKeys();         }
    public List<String> getConfiguredEventDisplayNames() { return engine.getConfiguredEventDisplayNames(); }

    public void     grantAdminEditAccess(Player player) { engine.grantAdminEditAccess(player); }
    public boolean  canAdminEdit(Player player)         { return engine.canAdminEdit(player);  }
    public Location getAdminEditSpawn()                 { return engine.getAdminEditSpawn();   }

    public ActionResult rebuildEventWorld(boolean confirmed) { return wrap(engine.rebuildEventWorld(confirmed)); }

    public List<String> getEventScoreboardLines() { return engine.getEventScoreboardLines(); }
    public int getPendingHardcoreLootCount(Player player) {
        return player == null ? 0 : engine.getPendingHardcoreLootCount(player.getUniqueId());
    }
    public List<ItemStack> getPendingHardcoreLootPreview(Player player) {
        return player == null ? List.of() : engine.getPendingHardcoreLootPreview(player.getUniqueId());
    }
    public int claimPendingHardcoreLoot(Player player) { return engine.claimPendingHardcoreLoot(player); }

    private ActionResult wrap(EventEngine.ActionResult r) { return new ActionResult(r.ok(), r.message()); }
}
