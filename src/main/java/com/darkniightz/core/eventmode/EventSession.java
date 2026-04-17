package com.darkniightz.core.eventmode;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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

    /** Staff (helper+) visiting via {@code /event spectate} — not participants. */
    public final Set<UUID> spectatorVisitors = ConcurrentHashMap.newKeySet();
    public final Map<UUID, SpectatorVisitState> spectatorVisitorState = new ConcurrentHashMap<>();

    /** Arena row from {@code event_mode.arena_registry} (null = legacy DB / koth.yml only). */
    public volatile String selectedArenaKey;

    /** Live coin payout; staff may override with {@code /event setreward}. */
    public volatile int runtimeCoinReward;

    // ── CTF ───────────────────────────────────────────────────────────────────
    public final Set<UUID> ctfTeamRed  = ConcurrentHashMap.newKeySet();
    public final Set<UUID> ctfTeamBlue = ConcurrentHashMap.newKeySet();
    public final AtomicInteger ctfRedScore  = new AtomicInteger(0);
    public final AtomicInteger ctfBlueScore = new AtomicInteger(0);
    public volatile UUID ctfRedFlagCarrier;
    public volatile UUID ctfBlueFlagCarrier;
    public volatile boolean ctfRedFlagAtBase = true;
    public volatile boolean ctfBlueFlagAtBase = true;
    public volatile Location ctfRedFlagDropLocation;
    public volatile Location ctfBlueFlagDropLocation;
    public volatile long ctfRedFlagReturnAtMs;
    public volatile long ctfBlueFlagReturnAtMs;
    /** Set by {@link com.darkniightz.core.eventmode.handler.CtfHandler} when win score reached; engine finalises. */
    public volatile java.util.UUID ctfPendingWinnerUuid;

    /** Resolved arena row for this run (set at launch). */
    public volatile ArenaConfig resolvedArenaConfig;

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

    /** PostgreSQL {@code event_sessions.id}; 0 until async insert completes. */
    public volatile int persistenceSessionId;

    /** Per-player event combat stats (V006 {@code event_participants}). */
    public final Map<UUID, AtomicInteger> eventKills  = new ConcurrentHashMap<>();
    public final Map<UUID, AtomicInteger> eventDeaths = new ConcurrentHashMap<>();

    // ── Timing ───────────────────────────────────────────────────────────────
    /** Epoch-ms at which the KOTH timer (or CTF time limit) expires. */
    public volatile long endsAtMs           = 0L;
    /** Lobby countdown seconds remaining (decremented each tick). */
    public volatile int  countdownSecondsLeft = 0;

    // ── State ─────────────────────────────────────────────────────────────────
    public volatile EventState state = EventState.IDLE;

    public EventSession(EventSpec spec) {
        this.spec = spec;
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
