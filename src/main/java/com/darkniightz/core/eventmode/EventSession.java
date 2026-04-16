package com.darkniightz.core.eventmode;

import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable live state for a single running event session.
 * Created when an event opens (OPEN state); discarded when it returns to IDLE.
 * The EventEngine owns and mutates this; handlers receive it read/write.
 */
public final class EventSession {

    public final EventSpec spec;

    // ── Participant sets ─────────────────────────────────────────────────────
    /** Players in the queue, waiting for launch. */
    public final Set<UUID> queued        = ConcurrentHashMap.newKeySet();
    /** Players currently in the active event. */
    public final Set<UUID> active        = ConcurrentHashMap.newKeySet();
    /** Players eliminated (elimination events only). */
    public final Set<UUID> eliminated    = ConcurrentHashMap.newKeySet();
    /** Players waiting for their respawn-handler to fire (pendingReturn). */
    public final Set<UUID> pendingReturn = ConcurrentHashMap.newKeySet();
    /** Non-HC eliminated players spectating the remainder. */
    public final Set<UUID> spectating    = ConcurrentHashMap.newKeySet();

    // ── Inventory snapshots ──────────────────────────────────────────────────
    /** Pre-event snapshot for active participants. */
    public final Map<UUID, InventorySnapshot> snapshots         = new ConcurrentHashMap<>();
    /** Snapshot for spectating (eliminated) players — inventory already kept on death. */
    public final Map<UUID, InventorySnapshot> spectatorSnapshots = new ConcurrentHashMap<>();

    // ── KOTH state ───────────────────────────────────────────────────────────
    /** Seconds each player has held the KOTH hill this session. */
    public final Map<UUID, Integer> kothSeconds = new ConcurrentHashMap<>();

    // ── HC loot pool ─────────────────────────────────────────────────────────
    public final List<ItemStack> hardcoreLootPool = new ArrayList<>();

    // ── Timing ───────────────────────────────────────────────────────────────
    /** Epoch-ms at which the KOTH timer (or CTF time limit) expires. */
    public volatile long endsAtMs           = 0L;
    /** Lobby countdown seconds remaining (decremented each tick). */
    public volatile int  countdownSecondsLeft = 0;

    // ── Chat events ───────────────────────────────────────────────────────────
    public volatile String chatAnswer;

    // ── State ─────────────────────────────────────────────────────────────────
    public volatile EventState state = EventState.IDLE;

    public EventSession(EventSpec spec) {
        this.spec = spec;
    }

    /** @return true if this is a chat-answer style event (Math/Scrabble/Quiz). */
    public boolean isChatEvent() {
        return spec.kind.isChat();
    }

    /** @return true if this event has a sign-up queue. */
    public boolean isSignupEvent() {
        return spec.kind.isSignup();
    }

    @Override
    public String toString() {
        return "EventSession{spec=" + spec + ", state=" + state
                + ", queued=" + queued.size() + ", active=" + active.size() + "}";
    }
}
