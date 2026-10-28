package com.goldlens.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on the in-app @Scheduled timers (the default, e.g. for local runs).
 * On Cloud Run they are off (GOLDLENS_SCHEDULING_ENABLED=false): with request-based billing the
 * instance only has CPU while serving a request, so Cloud Scheduler calls /api/admin/jobs/* instead.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "goldlens.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
