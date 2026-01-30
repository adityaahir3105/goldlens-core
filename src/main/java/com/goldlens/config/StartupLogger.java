package com.goldlens.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class StartupLogger {

    private static final Logger log = LoggerFactory.getLogger(StartupLogger.class);

    private final Environment environment;

    public StartupLogger(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logStartupInfo() {
        log.info("=== GoldLens Core Started ===");

        // Log active profiles
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length > 0) {
            log.info("Active profiles: {}", String.join(", ", profiles));
        } else {
            log.info("Active profiles: default");
        }

        // Log datasource URL (masked)
        String dbUrl = environment.getProperty("spring.datasource.url", "not configured");
        log.info("Database URL: {}", maskCredentials(dbUrl));

        // Log server port
        String port = environment.getProperty("server.port", "8081");
        log.info("Server port: {}", port);

        // Log API key status
        logApiKeyStatus("FRED_API_KEY", environment.getProperty("fred.api.key"));
        logApiKeyStatus("GEMINI_API_KEY", environment.getProperty("gemini.api.key"));
        logApiKeyStatus("GOLD_API_KEY", environment.getProperty("goldapi.api.key"));

        // Log scheduler status
        log.info("RealYieldScheduler enabled (cron: 0 0 6 * * * - daily 06:00 UTC)");
        log.info("DxyScheduler enabled (cron: 0 5 6 * * * - daily 06:05 UTC)");
        log.info("GoldRiskScheduler enabled (cron: 0 10 6 * * * - daily 06:10 UTC)");
        log.info("GoldPriceScheduler enabled (cron: 0 */15 * * * * - every 15 minutes)");

        log.info("=== Startup Complete ===");
    }

    private void logApiKeyStatus(String name, String value) {
        if (value != null && !value.isBlank()) {
            log.info("{}: configured ({}***)", name, value.substring(0, Math.min(4, value.length())));
        } else {
            log.warn("{}: NOT CONFIGURED", name);
        }
    }

    private String maskCredentials(String url) {
        if (url == null) {
            return "null";
        }
        // Mask password in JDBC URL if present
        return url.replaceAll("://[^:]+:[^@]+@", "://***:***@");
    }
}
