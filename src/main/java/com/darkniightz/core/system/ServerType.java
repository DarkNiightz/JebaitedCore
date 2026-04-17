package com.darkniightz.core.system;

/**
 * Identifies which type of server this JebaitedCore instance is running on.
 * <p>
 * During single-server operation (network.enabled=false) this will always
 * return HUB or whatever is set in config — all feature gates pass through
 * because NetworkManager short-circuits when network is disabled.
 * <p>
 * When the Velocity network overhaul ships (§22), feature gates will read
 * this enum to disable hub-only features on SMP and vice-versa.
 */
public enum ServerType {

    HUB,
    SMP,
    CREATIVE,
    PVP,
    MINIGAMES,
    UNKNOWN;

    public boolean isHub()       { return this == HUB; }
    public boolean isSmp()       { return this == SMP; }
    public boolean isKnown()     { return this != UNKNOWN; }

    /**
     * Parses a config string such as "hub", "smp", "creative" etc.
     * Returns UNKNOWN for unrecognised values (and logs a warning upstream).
     */
    public static ServerType fromConfig(String value) {
        if (value == null) return UNKNOWN;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
