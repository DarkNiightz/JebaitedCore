package com.darkniightz.core.eventmode;

public enum ChatGameKind {
    MATH,
    SCRABBLE,
    QUIZ;

    public static ChatGameKind fromConfigKey(String raw) {
        if (raw == null) return null;
        return switch (ChatGameKeys.normalize(raw)) {
            case "chat_math" -> MATH;
            case "chat_scrabble" -> SCRABBLE;
            case "chat_quiz" -> QUIZ;
            default -> null;
        };
    }

    public String defaultConfigKey() {
        return switch (this) {
            case MATH -> "chat_math";
            case SCRABBLE -> "chat_scrabble";
            case QUIZ -> "chat_quiz";
        };
    }

    public String shortLabel() {
        return switch (this) {
            case MATH -> "Math";
            case SCRABBLE -> "Scrabble";
            case QUIZ -> "Quiz";
        };
    }

    public String scoreboardHint() {
        return switch (this) {
            case MATH -> "Answer the sum in chat";
            case SCRABBLE -> "Unscramble the word in chat";
            case QUIZ -> "Answer the trivia in chat";
        };
    }

    public static String compactDisplayName(String configKey, String configured) {
        ChatGameKind k = fromConfigKey(configKey);
        if (k != null) {
            return k.shortLabel();
        }
        return configured == null ? configKey : configured;
    }
}
