package com.darkniightz.core.system;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.logging.Logger;

/**
 * Central singleton for network/server-type awareness.
 * <p>
 * During single-server operation (network.enabled=false in config.yml) ALL
 * feature-gate methods return true so existing behaviour is completely
 * unchanged. When the Velocity network overhaul ships (ROADMAP §22), set
 * network.enabled=true and deploy the configured server_type — the gates
 * will then enforce hub-only vs smp-only features automatically.
 * <p>
 * Initialised early in JebaitedCore.onEnable via {@code NetworkManager.init(getConfig())}.
 * Retrieved everywhere else via {@code NetworkManager.getInstance()}.
 */
public class NetworkManager {

    private static NetworkManager instance;

    private final boolean networkEnabled;
    private final ServerType serverType;
    private final String serverId;
    private final String serverName;

    // Redis fields — inert until overhaul
    private final boolean redisEnabled;
    private final String redisHost;
    private final int redisPort;

    private NetworkManager(FileConfiguration config, Logger log) {
        this.networkEnabled = config.getBoolean("network.enabled", false);
        String typeRaw = config.getString("network.server_type", "hub");
        ServerType parsed = ServerType.fromConfig(typeRaw);
        if (parsed == ServerType.UNKNOWN) {
            log.warning("[NetworkManager] Unknown server_type '" + typeRaw + "' in config.yml — defaulting to HUB.");
            parsed = ServerType.HUB;
        }
        this.serverType = parsed;
        this.serverId   = config.getString("network.server_id",   "hub-01");
        this.serverName = config.getString("network.server_name", "Hub");

        this.redisEnabled = config.getBoolean("network.redis.enabled", false);
        this.redisHost    = config.getString("network.redis.host", "localhost");
        this.redisPort    = config.getInt("network.redis.port", 6379);

        if (networkEnabled) {
            log.info("[NetworkManager] Network mode ENABLED — server_type=" + serverType
                    + ", id=" + serverId
                    + (redisEnabled ? ", Redis=" + redisHost + ":" + redisPort : ", Redis=disabled"));
        }
        // Silence when disabled — network is opt-in, no noise on default config
    }

    public static void init(FileConfiguration config, Logger log) {
        instance = new NetworkManager(config, log);
    }

    public static NetworkManager getInstance() {
        return instance;
    }

    // -------------------------------------------------------------------------
    // Core accessors
    // -------------------------------------------------------------------------

    /** True only when network.enabled=true AND this is a HUB server. */
    public boolean isNetworkHub()  { return networkEnabled && serverType.isHub(); }

    /** True only when network.enabled=true AND this is an SMP server. */
    public boolean isNetworkSmp()  { return networkEnabled && serverType.isSmp(); }

    /** Raw server type regardless of whether the network flag is on. */
    public ServerType getServerType() { return serverType; }

    /** Whether the full Velocity network mode is active. */
    public boolean isNetworkEnabled() { return networkEnabled; }

    /**
     * True if this server should run HUB features.
     * When network is disabled, always returns true (single-server passthrough).
     */
    public boolean isHub() { return !networkEnabled || serverType.isHub(); }

    /**
     * True if this server should run SMP features.
     * When network is disabled, always returns true (single-server passthrough).
     */
    public boolean isSmp() { return !networkEnabled || serverType.isSmp(); }

    public String getServerId()   { return serverId; }
    public String getServerName() { return serverName; }
    public boolean isRedisEnabled() { return redisEnabled; }
    public String getRedisHost()  { return redisHost; }
    public int getRedisPort()     { return redisPort; }
}
