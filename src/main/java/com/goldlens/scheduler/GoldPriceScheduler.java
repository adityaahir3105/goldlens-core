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

/**
 * Scheduler for gold price ingestion using GoldPricez API.
 * 
 * Note: GoldPricez only supports real-time prices, not historical data.
 * Historical backfill is disabled.
 * 
 * Rate limit: 30-60 requests/hour, so we run every 15 minutes (4 req/hour).
 */
@Component
public class GoldPriceScheduler {

    private static final Logger log = LoggerFactory.getLogger(GoldPriceScheduler.class);

    private static final String SOURCE = "GoldPricez";

    private final GoldPricezClient goldPricezClient;
    private final GoldPriceHistoryService goldPriceHistoryService;

    public GoldPriceScheduler(GoldPricezClient goldPricezClient, GoldPriceHistoryService goldPriceHistoryService) {
        this.goldPricezClient = goldPricezClient;
        this.goldPriceHistoryService = goldPriceHistoryService;
    }

    /**
     * Fetches latest gold price from GoldPricez and persists for analytics/history.
     * Runs every 15 minutes to stay well within rate limits (30-60 req/hour).
     */
    @Scheduled(cron = "0 */15 * * * *")
    public void ingestLatestGoldPrice() {
        log.info("Starting gold price ingestion via GoldPricez");

        if (!goldPricezClient.isConfigured()) {
            log.warn("GoldPricez not configured — skipping ingestion");
            return;
        }

        LocalDate today = LocalDate.now();
        if (goldPriceHistoryService.existsByDate(today)) {
            log.info("Gold price for {} already exists — skipping persistence", today);
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
            log.info("Inserted gold price {} for date {} from GoldPricez", snapshot.getPrice(), today);

        } catch (GoldApiUnavailableException e) {
            log.error("[requestId={}] [errorType={}] GoldPricez API error during scheduled ingestion: {}",
                    e.getRequestId(), e.getErrorType(), e.getMessage());
            // Do not crash scheduler - will retry on next scheduled run
        } catch (Exception e) {
            log.error("Unexpected error during gold price ingestion: {}", e.getMessage(), e);
            // Do not crash scheduler
        }
    }

    /**
     * Historical backfill is DISABLED for GoldPricez.
     * GoldPricez API only supports real-time prices, not historical data.
     * This method is kept for interface compatibility but does nothing.
     */
    public void backfillHistory(int lookbackDays, int fetchLimit) {
        log.warn("Gold price historical backfill is DISABLED — GoldPricez does not support historical data");
        // No-op: GoldPricez only provides real-time prices
    }
}
