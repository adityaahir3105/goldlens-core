package com.goldlens.scheduler;

import com.goldlens.client.GoldApiClient;
import com.goldlens.domain.GoldPriceHistory;
import com.goldlens.exception.GoldApiUnavailableException;
import com.goldlens.service.GoldPriceHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Scheduler for gold price ingestion using GoldAPI.
 * 
 * Strategy: Persist daily gold price snapshots to DB.
 * Runs every 6 hours (4 req/day) to ensure we capture the daily price.
 */
@Component
public class GoldPriceScheduler {

    private static final Logger log = LoggerFactory.getLogger(GoldPriceScheduler.class);

    private static final String SOURCE = "GoldAPI";
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    private final GoldApiClient goldApiClient;
    private final GoldPriceHistoryService goldPriceHistoryService;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicReference<LocalDateTime> lastSuccessfulFetch = new AtomicReference<>();

    public GoldPriceScheduler(GoldApiClient goldApiClient, GoldPriceHistoryService goldPriceHistoryService) {
        this.goldApiClient = goldApiClient;
        this.goldPriceHistoryService = goldPriceHistoryService;
    }

    /**
     * Persists gold price snapshot to DB for history.
     * Runs every 6 hours to minimize API calls while building history.
     */
    @Scheduled(cron = "0 0 */6 * * *")
    public void persistGoldPriceSnapshot() {
        log.info("[scheduler] Starting gold price persistence job");

        if (!goldApiClient.isConfigured()) {
            log.warn("[scheduler] GoldAPI not configured — skipping");
            return;
        }

        if (consecutiveFailures.get() >= MAX_CONSECUTIVE_FAILURES) {
            log.warn("[scheduler] Skipping due to {} consecutive failures. Will reset on next success.", 
                    consecutiveFailures.get());
        }

        LocalDate today = LocalDate.now();
        if (goldPriceHistoryService.existsByDate(today)) {
            log.info("[scheduler] Gold price for {} already persisted — skipping", today);
            return;
        }

        try {
            var snapshot = goldApiClient.fetchLatestGoldPrice();

            GoldPriceHistory history = GoldPriceHistory.builder()
                    .date(today)
                    .price(snapshot.getPrice())
                    .source(SOURCE)
                    .build();

            goldPriceHistoryService.save(history);
            
            consecutiveFailures.set(0);
            lastSuccessfulFetch.set(LocalDateTime.now());
            
            log.info("[scheduler] SUCCESS: Persisted gold price {} for {} from GoldAPI", 
                    snapshot.getPrice(), today);

        } catch (GoldApiUnavailableException e) {
            int failures = consecutiveFailures.incrementAndGet();
            log.error("[scheduler] FAILED: [requestId={}] [errorType={}] [failures={}] {}",
                    e.getRequestId(), e.getErrorType(), failures, e.getMessage());
        } catch (Exception e) {
            int failures = consecutiveFailures.incrementAndGet();
            log.error("[scheduler] FAILED: [failures={}] Unexpected error: {}", failures, e.getMessage(), e);
        }
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public LocalDateTime getLastSuccessfulFetch() {
        return lastSuccessfulFetch.get();
    }
}
