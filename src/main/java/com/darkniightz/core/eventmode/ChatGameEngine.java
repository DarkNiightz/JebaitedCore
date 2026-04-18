package com.darkniightz.core.eventmode;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Question generation and answer matching for chat mini-games. Lifecycle lives in {@link ChatGameManager}.
 */
public final class ChatGameEngine {

    private final Plugin plugin;

    public ChatGameEngine(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Sets {@link ChatGameSession#chatAnswer} and emits the opening prompt via {@code broadcast}.
     */
    public void startRound(ChatGameSession session, Consumer<String> broadcast) {
        if (session == null || broadcast == null || session.spec == null || session.spec.kind == null) {
            return;
        }
        ChatGameKind kind = session.spec.kind;
        int reward = session.spec.coinReward;
        if (kind == ChatGameKind.MATH) {
            int a = java.util.concurrent.ThreadLocalRandom.current().nextInt(4, 35);
            int b = java.util.concurrent.ThreadLocalRandom.current().nextInt(4, 35);
            session.chatAnswer = Integer.toString(a + b);
            broadcast.accept("§dMath Event: §fFirst to answer §e" + a + " + " + b + "§f wins §6" + reward + " coins.");
        } else if (kind == ChatGameKind.SCRABBLE) {
            String word = pickScrabbleWord();
            session.chatAnswer = word;
            broadcast.accept("§dScrabble Event: §fUnscramble this word: §e" + scramble(word)
                    + " §7(First correct answer wins)");
        } else if (kind == ChatGameKind.QUIZ) {
            Quiz qa = pickQuiz();
            session.chatAnswer = qa.answer;
            broadcast.accept("§dQuiz Event: §f" + qa.question + " §7(First correct answer wins)");
        }
    }

    public boolean tryAcceptAnswer(ChatGameSession session, Player player, String rawAnswer) {
        if (player == null || session == null) {
            return false;
        }
        String answer = rawAnswer == null ? "" : rawAnswer.trim();
        if (answer.isBlank() || session.chatAnswer == null) {
            return false;
        }
        String guess = normalizeAnswer(answer);
        String expected = normalizeAnswer(session.chatAnswer);
        return matchesAnswer(guess, expected);
    }

    private String pickScrabbleWord() {
        List<String> words = plugin.getConfig().getStringList("event_mode.chat.scrabble_words");
        if (words == null || words.isEmpty()) {
            words = List.of("minecraft", "diamond", "creeper", "survival", "jebaited");
        }
        return words.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(words.size()))
                .toLowerCase(Locale.ROOT).trim();
    }

    private String scramble(String word) {
        List<Character> chars = new ArrayList<>();
        for (char c : word.toCharArray()) {
            chars.add(c);
        }
        Collections.shuffle(chars);
        StringBuilder sb = new StringBuilder(chars.size());
        for (char c : chars) {
            sb.append(c);
        }
        String out = sb.toString();
        if (out.equalsIgnoreCase(word) && out.length() > 1) {
            return new StringBuilder(out).reverse().toString();
        }
        return out;
    }

    private record Quiz(String question, String answer) {}

    private Quiz pickQuiz() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("event_mode.chat.quiz");
        if (sec == null || sec.getKeys(false).isEmpty()) {
            return new Quiz("What dimension do you need Eyes of Ender for?", "end");
        }
        List<String> keys = new ArrayList<>(sec.getKeys(false));
        String pick = keys.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(keys.size()));
        String q = sec.getString(pick + ".question", "What item summons the Wither?");
        String a = sec.getString(pick + ".answer", "soul sand");
        return new Quiz(q, normalizeAnswer(a));
    }

    public static String normalizeAnswer(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9]", "");
    }

    public static boolean matchesAnswer(String guess, String expected) {
        if (guess.isBlank() || expected.isBlank()) {
            return false;
        }
        if (guess.equals(expected)) {
            return true;
        }
        if (expected.startsWith(guess) && guess.length() >= 3) {
            return true;
        }
        return guess.contains(expected);
    }
}
