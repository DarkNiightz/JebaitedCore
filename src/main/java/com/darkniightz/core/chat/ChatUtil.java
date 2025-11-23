package com.darkniightz.core.chat;

import com.darkniightz.core.ranks.RankManager;

import java.util.List;

public final class ChatUtil {
    private ChatUtil() {}

    /**
     * Builds the styled player name according to the rank style.
     */
    public static String buildStyledName(String name, RankManager.RankStyle style) {
        if (style == null) return name;
        if (style.rainbow) {
            List<String> colors = style.rainbowColors;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < name.length(); i++) {
                char ch = name.charAt(i);
                String code = colors.get(i % colors.size());
                sb.append('§').append(code);
                if (style.boldEachChar) sb.append('§').append('l');
                sb.append(ch);
            }
            return sb.toString();
        } else {
            StringBuilder sb = new StringBuilder();
            String color = style.colorCode == null ? "§f" : style.colorCode;
            sb.append(color);
            if (style.bold) sb.append('§').append('l');
            sb.append(name);
            return sb.toString();
        }
    }
}
