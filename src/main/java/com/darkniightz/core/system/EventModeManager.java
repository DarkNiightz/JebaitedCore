package com.darkniightz.core.system;

import com.darkniightz.core.eventmode.EventEngine;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Thin facade over EventEngine.
 */
public class EventModeManager {

    public record ActionResult(boolean ok, String message) {}

    private final EventEngine engine;

    public EventModeManager(Plugin plugin, BroadcasterManager broadcasterManager, BossBarManager bossBarManager) {
        this.engine = new EventEngine(plugin, broadcasterManager, bossBarManager);
    }

    public void start()  { engine.start(); }
    public void stop()   { engine.stop();  }

    public boolean isActive()       { return engine.isActive();       }
    public String  getStatusLine()  { return engine.getStatusLine();  }

    public ActionResult startEvent(String key)              { return wrap(engine.startEvent(key));            }
    public ActionResult stopEvent(String reason)            { return wrap(engine.stopEvent(reason));          }
    public ActionResult forceStart()                        { return wrap(engine.forceStart());               }
    public ActionResult completeEvent(Player w, Integer r, String reason) {
        return wrap(engine.completeEvent(w, r, reason));
    }

    public ActionResult joinQueue(Player player, boolean confirmed) { return wrap(engine.joinQueue(player, confirmed)); }
    public ActionResult leaveQueue(Player player)                   { return wrap(engine.leaveQueue(player)); }

    public boolean submitChatAnswer(Player player, String answer)   { return engine.submitChatAnswer(player, answer); }

    public void    handleParticipantDeath(Player player)            { engine.handleParticipantDeath(player);   }
    public boolean isParticipant(Player player)                     { return engine.isParticipant(player);     }
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

    private ActionResult wrap(EventEngine.ActionResult r) { return new ActionResult(r.ok(), r.message()); }
}