package com.darkniightz.bot.security;

import java.time.Instant;

public final class WebhookVerifier {
    private final WebhookSigner signer;
    private final NonceStore nonceStore;
    private final long maxSkewSeconds;

    public WebhookVerifier(WebhookSigner signer, NonceStore nonceStore, long maxSkewSeconds) {
        this.signer = signer;
        this.nonceStore = nonceStore;
        this.maxSkewSeconds = maxSkewSeconds;
    }

    public boolean verify(String payload, String signature, long timestampEpochSec, String nonce) {
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - timestampEpochSec) > maxSkewSeconds) {
            return false;
        }
        if (!nonceStore.markIfNew(nonce, maxSkewSeconds)) {
            return false;
        }
        String expected = signer.sign(timestampEpochSec + "." + nonce + "." + payload);
        return expected.equalsIgnoreCase(signature);
    }
}
