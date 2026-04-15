package com.darkniightz.core.chat;

import com.darkniightz.core.ranks.RankManager;

import java.util.List;

public final class ChatUtil {
    private ChatUtil() {}

    /**
     * Builds the styled player name according to the rank style.
     */
    public static String buildStyledName(String name, RankManager.RankStyle style) {
        String safeName = name == null ? "" : name;
        if (style == null) return safeName;

        List<String> colors = style.rainbowColors;
        if (style.rainbow && colors != null && !colors.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < safeName.length(); i++) {
                char ch = safeName.charAt(i);
                String code = colors.get(i % colors.size());
                sb.append('§').append(code);
                if (style.boldEachChar) sb.append('§').append('l');
                sb.append(ch);
            }
            return sb.toString();
        }

        StringBuilder sb = new StringBuilder();
        String color = style.colorCode == null || style.colorCode.isBlank() ? "§f" : style.colorCode;
        sb.append(color);
        if (style.bold) sb.append('§').append('l');
        sb.append(safeName);
        return sb.toString();
    }
}
