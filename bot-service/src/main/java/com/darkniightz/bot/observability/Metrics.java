package com.darkniightz.bot.observability;

import java.util.concurrent.atomic.AtomicLong;

public final class Metrics {
    private final AtomicLong webhookAccepted = new AtomicLong();
    private final AtomicLong webhookRejected = new AtomicLong();

    public void incWebhookAccepted() {
        webhookAccepted.incrementAndGet();
    }

    public void incWebhookRejected() {
        webhookRejected.incrementAndGet();
    }

    public long webhookAccepted() {
        return webhookAccepted.get();
    }

    public long webhookRejected() {
        return webhookRejected.get();
    }
}
