package com.darkniightz.core.eventmode;

import java.util.Locale;

/** Config / command keys for chat mini-games (parallel to combat {@link EventEngine}). */
public final class ChatGameKeys {

    private ChatGameKeys() {}

    public static boolean isChatGameConfigKey(String raw) {
        if (raw == null) return false;
        return normalize(raw).startsWith("chat_");
    }

    public static String normalize(String raw) {
        if (raw == null) return "";
        String key = raw.toLowerCase(Locale.ROOT).trim();
        return switch (key) {
            case "math", "chatmath" -> "chat_math";
            case "scrabble" -> "chat_scrabble";
            case "anagram", "chat_anagram", "chat_word", "word" -> "chat_scrabble";
            case "quiz", "question" -> "chat_quiz";
            default -> key;
        };
    }
}
