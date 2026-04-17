package com.darkniightz.core.moderation;

/** Helpers may only apply timed punishments up to this duration (7 days). */
public final class ModerationLimits {
    public static final long HELPER_MAX_TEMP_MS = 7L * 24 * 60 * 60 * 1000L;

    private ModerationLimits() {}
}
