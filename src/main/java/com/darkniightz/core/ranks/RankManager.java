package com.darkniightz.core.ranks;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * RankManager reads rank ladder and styles from config.yml.
 * It provides helper methods to compare seniority and fetch style data.
 */
public class RankManager {

    private final Plugin plugin;
    private final List<String> ladder; // top -> bottom order
    private final Map<String, Integer> rankIndex; // lower index = higher power
    private final String defaultGroup;

    public RankManager(Plugin plugin) {
        this.plugin = plugin;
        List<String> ld = plugin.getConfig().getStringList("ranks.ladder");
        if (ld == null || ld.isEmpty()) {
            ld = List.of("owner","developer","admin","moderator","helper","vip","friend","builder","supporter1","supporter2","supporter3");
        }
        this.ladder = new ArrayList<>(ld);
        this.rankIndex = new HashMap<>();
        for (int i = 0; i < ladder.size(); i++) {
            rankIndex.put(ladder.get(i).toLowerCase(Locale.ROOT), i);
        }
        String def = plugin.getConfig().getString("ranks.default", "friend");
        this.defaultGroup = def.toLowerCase(Locale.ROOT);
    }

    public String getDefaultGroup() {
        return defaultGroup;
    }

    public boolean isAtLeast(String subjectRank, String minimumRank) {
        String sNorm = normalizeGroup(subjectRank);
        String mNorm = normalizeGroup(minimumRank);
        if (sNorm == null || mNorm == null) return false;
        Integer s = rankIndex.get(sNorm);
        Integer m = rankIndex.get(mNorm);
        if (s == null || m == null) return false;
        return s <= m; // higher up the ladder means lower index
    }

    public RankStyle getStyle(String rank) {
        rank = normalizeGroup(rank);
        ConfigurationSection styles = plugin.getConfig().getConfigurationSection("rank_styles");
        if (styles == null) return RankStyle.defaultStyle();
        ConfigurationSection sec = styles.getConfigurationSection(rank);
        if (sec == null) return RankStyle.defaultStyle();

        // Rainbow config
        ConfigurationSection rainbow = sec.getConfigurationSection("name_rainbow");
        if (rainbow != null && rainbow.getBoolean("enabled", false)) {
            List<String> colors = rainbow.getStringList("colors");
            if (colors == null || colors.isEmpty()) {
                colors = List.of("4","c","6","e","2","a","b","3","1","9","d","5");
            }
            boolean boldEach = rainbow.getBoolean("bold_each_char", true);
            String prefix = sec.getString("prefix", "");
            return RankStyle.rainbow(prefix, colors, boldEach);
        }

        String color = sec.getString("name_color", "§f");
        boolean bold = sec.getBoolean("name_bold", false);
        String prefix = sec.getString("prefix", "");
        return RankStyle.colored(prefix, color, bold);
    }

    public List<String> getLadder() {
        return Collections.unmodifiableList(ladder);
    }

    public int indexOf(String rank) {
        String norm = normalizeGroup(rank);
        if (norm == null) return Integer.MAX_VALUE;
        Integer idx = rankIndex.get(norm);
        return idx == null ? Integer.MAX_VALUE : idx;
    }

    public boolean outranksStrict(String actorRank, String targetRank) {
        int a = indexOf(actorRank);
        int t = indexOf(targetRank);
        return a < t; // smaller index = higher rank; must be strictly higher
    }

    /**
     * Normalizes legacy/aliased rank values (including old color-code based ranks)
     * into a canonical ladder key present in rankIndex. Returns lower-case group name
     * or defaultGroup if input is null/unknown.
     */
    public String normalizeGroup(String input) {
        if (input == null || input.isBlank()) return defaultGroup;
        String s = input.trim().toLowerCase(Locale.ROOT);

        // Direct hit
        if (rankIndex.containsKey(s)) return s;

        // Known aliases from older configs
        if (s.equals("default")) return defaultGroup;
        if (s.equals("srmoderator") || s.equals("srmod")) return "moderator";

        // Color-code based legacy values: allow with or without '§' or '&'
        // Extract last color code character if present
        char code = 0;
        if (s.startsWith("§") || s.startsWith("&")) {
            if (s.length() >= 2) code = s.charAt(1);
        } else if (s.length() == 1) {
            // sometimes stored as just 'd', '5', etc.
            code = s.charAt(0);
        }
        if (code != 0) {
            switch (code) {
                case 'd':
                    return ladderContains("friend") ? "friend" : defaultGroup;
                case '5': // dark purple used previously for VIP
                case '6': // gold often used for VIP
                    return ladderContains("vip") ? "vip" : defaultGroup;
                case 'b': // aqua -> helper in new config
                    return ladderContains("helper") ? "helper" : defaultGroup;
                case 'a': // green -> moderator (approx)
                    return ladderContains("moderator") ? "moderator" : defaultGroup;
                case 'c': // red -> admin/mod
                case '4': // dark red -> admin
                    if (ladderContains("admin")) return "admin";
                    if (ladderContains("moderator")) return "moderator";
                    break;
                case '9': // blue -> builder
                    return ladderContains("builder") ? "builder" : defaultGroup;
                case 'e': // yellow -> supporter tier
                    if (ladderContains("supporter1")) return "supporter1";
                    if (ladderContains("supporter")) return "supporter"; // fallback
                    break;
            }
        }

        // As a last resort, if not recognized, fall back to default
        return defaultGroup;
    }

    private boolean ladderContains(String key) {
        return rankIndex.containsKey(key);
    }

    public static final class RankStyle {
        public final String prefix;
        public final boolean rainbow;
        public final List<String> rainbowColors; // single char color codes without '§'
        public final boolean boldEachChar;
        public final String colorCode; // with '§'
        public final boolean bold;

        private RankStyle(String prefix, boolean rainbow, List<String> rainbowColors, boolean boldEachChar, String colorCode, boolean bold) {
            this.prefix = prefix;
            this.rainbow = rainbow;
            this.rainbowColors = rainbowColors;
            this.boldEachChar = boldEachChar;
            this.colorCode = colorCode;
            this.bold = bold;
        }

        public static RankStyle rainbow(String prefix, List<String> colors, boolean boldEach) {
            return new RankStyle(prefix, true, new ArrayList<>(colors), boldEach, null, false);
        }

        public static RankStyle colored(String prefix, String colorCode, boolean bold) {
            return new RankStyle(prefix, false, List.of(), false, colorCode, bold);
        }

        public static RankStyle defaultStyle() {
            return colored("", "§f", false);
        }
    }
}
