package com.darkniightz.core.eventmode;

import java.util.Locale;

public enum EventKind {
    CHAT_MATH,
    CHAT_SCRABBLE,
    CHAT_QUIZ,
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

    public boolean isChat() {
        return this == CHAT_MATH || this == CHAT_SCRABBLE || this == CHAT_QUIZ;
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

    /** Parse a raw config/command key into an EventKind. */
    public static EventKind fromKey(String raw) {
        if (raw == null) return OTHER;
        return switch (raw.toLowerCase(Locale.ROOT).trim()) {
            case "chat_math", "math", "chatmath"         -> CHAT_MATH;
            case "chat_scrabble", "scrabble",
                 "chat_anagram", "chat_word", "anagram",
                 "word"                                  -> CHAT_SCRABBLE;
            case "chat_quiz", "quiz", "question"         -> CHAT_QUIZ;
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
