package com.darkniightz.bot.observability;

import java.util.UUID;

public final class CorrelationId {
    private CorrelationId() {}

    public static String next() {
        return UUID.randomUUID().toString();
    }
}
