package com.darkniightz.core.moderation;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class TimeUtil {
    private TimeUtil() {}

    public static long parseDurationMillis(String input) {
        if (input == null || input.isEmpty()) return -1;
        input = input.trim().toLowerCase(Locale.ROOT);
        long total = 0;
        long num = 0;
        boolean any = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isDigit(c)) {
                num = num * 10 + (c - '0');
                any = true;
            } else {
                long unit;
                switch (c) {
                    case 's' -> unit = TimeUnit.SECONDS.toMillis(1);
                    case 'm' -> unit = TimeUnit.MINUTES.toMillis(1);
                    case 'h' -> unit = TimeUnit.HOURS.toMillis(1);
                    case 'd' -> unit = TimeUnit.DAYS.toMillis(1);
                    case 'w' -> unit = TimeUnit.DAYS.toMillis(7);
                    default -> unit = -1;
                }
                if (unit == -1 || num <= 0) return -1;
                total += num * unit;
                num = 0;
            }
        }
        if (num > 0) {
            // default to seconds if trailing number without unit
            total += num * 1000L;
        }
        return any ? total : -1;
    }

    public static String formatDurationShort(long millis) {
        if (millis <= 0) return "permanent";
        long s = millis / 1000;
        long w = s / (7*24*3600); s %= 7*24*3600;
        long d = s / (24*3600); s %= 24*3600;
        long h = s / 3600; s %= 3600;
        long m = s / 60; s %= 60;
        StringBuilder sb = new StringBuilder();
        if (w>0) sb.append(w).append('w').append(' ');
        if (d>0) sb.append(d).append('d').append(' ');
        if (h>0) sb.append(h).append('h').append(' ');
        if (m>0) sb.append(m).append('m').append(' ');
        if (s>0 || sb.length()==0) sb.append(s).append('s');
        return sb.toString().trim();
    }
}
