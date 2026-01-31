package com.goldlens.scheduler;

import com.goldlens.client.GoldPricezClient;
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
 * Scheduler for gold price ingestion using GoldPricez API.
 * 
 * Strategy: Persist snapshots to DB to build synthetic history over time.
 * GoldPricez only supports real-time prices, not historical data.
 * 
 * Rate limit: 30-60 requests/hour. We run every 6 hours (4 req/day) for DB persistence.
 */
@Component
public class GoldPriceScheduler {

    private static final Logger log = LoggerFactory.getLogger(GoldPriceScheduler.class);

    private static final String SOURCE = "GoldPricez";
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    private final GoldPricezClient goldPricezClient;
    private final GoldPriceHistoryService goldPriceHistoryService;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicReference<LocalDateTime> lastSuccessfulFetch = new AtomicReference<>();

    public GoldPriceScheduler(GoldPricezClient goldPricezClient, GoldPriceHistoryService goldPriceHistoryService) {
        this.goldPricezClient = goldPricezClient;
        this.goldPriceHistoryService = goldPriceHistoryService;
    }

    /**
     * Persists gold price snapshot to DB for synthetic history.
     * Runs every 6 hours to minimize API calls while building history.
     */
    @Scheduled(cron = "0 0 */6 * * *")
    public void persistGoldPriceSnapshot() {
        log.info("[scheduler] Starting gold price persistence job");

        if (!goldPricezClient.isConfigured()) {
            log.warn("[scheduler] GoldPricez not configured — skipping");
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
            var snapshot = goldPricezClient.fetchLatestGoldPrice();

            GoldPriceHistory history = GoldPriceHistory.builder()
                    .date(today)
                    .price(snapshot.getPrice())
                    .source(SOURCE)
                    .build();

            goldPriceHistoryService.save(history);
            
            consecutiveFailures.set(0);
            lastSuccessfulFetch.set(LocalDateTime.now());
            
            log.info("[scheduler] SUCCESS: Persisted gold price {} for {} from GoldPricez", 
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

    /**
     * Historical backfill is NOT SUPPORTED by GoldPricez.
     * History is built over time via scheduled snapshots.
     */
    public void backfillHistory(int lookbackDays, int fetchLimit) {
        log.warn("[scheduler] Historical backfill not supported — GoldPricez provides real-time data only");
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public LocalDateTime getLastSuccessfulFetch() {
        return lastSuccessfulFetch.get();
    }
}
