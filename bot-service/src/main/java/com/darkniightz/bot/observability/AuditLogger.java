package com.darkniightz.bot.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AuditLogger {
    private static final Logger LOG = LoggerFactory.getLogger("JebaitedBotAudit");

    private AuditLogger() {}

    public static void info(String eventType, String correlationId, String detail) {
        LOG.info("eventType={} correlationId={} detail={}", eventType, correlationId, detail);
    }
}
