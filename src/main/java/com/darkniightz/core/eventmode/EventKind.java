package com.darkniightz.core.eventmode;

import java.util.Locale;

public enum EventKind {
    KOTH,
    FFA,
    DUELS,
    HARDCORE,        // legacy alias → treated as HARDCORE_FFA
    HARDCORE_FFA,
    HARDCORE_DUELS,
    HARDCORE_KOTH,
    CTF,
    OTHER;

    public boolean isHardcore() {
        return this == HARDCORE || this == HARDCORE_FFA || this == HARDCORE_DUELS || this == HARDCORE_KOTH;
    }

    public boolean isElimination() {
        return this == FFA || this == DUELS || this == HARDCORE || this == HARDCORE_FFA || this == HARDCORE_DUELS;
    }

    public boolean isKoth() {
        return this == KOTH || this == HARDCORE_KOTH;
    }

    /** Event kinds that use a sign-up queue and player teleport. */
    public boolean isSignup() {
        return this == KOTH || this == FFA || this == DUELS
                || this == HARDCORE || this == HARDCORE_FFA
                || this == HARDCORE_DUELS || this == HARDCORE_KOTH
                || this == CTF;
    }

    /** Parse a raw config/command key into an EventKind (combat only; chat games use {@link ChatGameKind}). */
    public static EventKind fromKey(String raw) {
        if (raw == null) return OTHER;
        if (ChatGameKeys.isChatGameConfigKey(raw)) {
            return OTHER;
        }
        return switch (raw.toLowerCase(Locale.ROOT).trim()) {
            case "koth"                                  -> KOTH;
            case "ffa", "lms"                            -> FFA;
            case "duels"                                 -> DUELS;
            case "hardcore"                              -> HARDCORE_FFA; // legacy alias
            case "hardcore_ffa", "hardcoreffa"           -> HARDCORE_FFA;
            case "hardcore_duels", "hardcoreduels"       -> HARDCORE_DUELS;
            case "hardcore_koth", "hardcorekoth"         -> HARDCORE_KOTH;
            case "ctf"                                   -> CTF;
            default                                      -> OTHER;
        };
    }
}
