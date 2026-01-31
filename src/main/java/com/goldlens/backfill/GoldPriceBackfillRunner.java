package com.goldlens.backfill;

import com.goldlens.client.GoldApiClient;
import com.goldlens.domain.GoldPriceHistory;
import com.goldlens.service.GoldPriceHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * TODO: TEMPORARY BACKFILL LOGIC - Remove after historical data is populated.
 * 
 * One-time backfill runner for historical gold prices using GoldAPI.
 * 
 * Activation requirements:
 *   1. Profile must be "local" or "prod"
 *   2. Config flag: gold.backfill.enabled=true
 * 
 * Behavior:
 *   - If gold_price_history already has data → skips backfill
 *   - If empty → fetches last 30 days from GoldAPI
 *   - Does NOT crash application on failure
 * 
 * After successful backfill:
 *   1. Set gold.backfill.enabled=false
 *   2. Remove this class and GoldApiClient
 */
@Component
@Profile({"local", "prod"})
@ConditionalOnProperty(name = "gold.backfill.enabled", havingValue = "true", matchIfMissing = false)
public class GoldPriceBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GoldPriceBackfillRunner.class);

    // TODO: Adjust these values if needed for backfill
    private static final int BACKFILL_DAYS = 30;
    private static final long DELAY_BETWEEN_REQUESTS_MS = 1500;

    private final GoldApiClient goldApiClient;
    private final GoldPriceHistoryService goldPriceHistoryService;

    public GoldPriceBackfillRunner(GoldApiClient goldApiClient, GoldPriceHistoryService goldPriceHistoryService) {
        this.goldApiClient = goldApiClient;
        this.goldPriceHistoryService = goldPriceHistoryService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            executeBackfill();
        } catch (Exception e) {
            // TODO: Backfill failure should NOT crash application startup
            log.error("[BACKFILL] FAILED with unexpected error - application will continue: {}", e.getMessage(), e);
        }
    }

    private void executeBackfill() {
        log.info("========================================");
        log.info("[BACKFILL] Gold price historical backfill triggered");
        log.info("========================================");

        // Check if data already exists - skip if so
        long existingCount = goldPriceHistoryService.count();
        if (existingCount > 0) {
            log.info("[BACKFILL] Gold price history already present ({} rows), skipping backfill", existingCount);
            return;
        }

        if (!goldApiClient.isConfigured()) {
            log.error("[BACKFILL] GoldAPI key not configured - aborting backfill");
            return;
        }

        log.info("[BACKFILL] Starting backfill for last {} days", BACKFILL_DAYS);

        LocalDate today = LocalDate.now();
        int inserted = 0;
        int failed = 0;

        // Backfill from (today - 30) to (today - 1) i.e. yesterday
        for (int i = BACKFILL_DAYS; i >= 1; i--) {
            LocalDate date = today.minusDays(i);

            try {
                var priceOpt = goldApiClient.fetchPriceForDate(date);

                if (priceOpt.isPresent()) {
                    var historicalPrice = priceOpt.get();
                    GoldPriceHistory history = GoldPriceHistory.builder()
                            .date(historicalPrice.date())
                            .price(historicalPrice.price())
                            .source(historicalPrice.source())
                            .build();

                    goldPriceHistoryService.save(history);
                    inserted++;
                    log.info("[BACKFILL] [{}/{}] Inserted: {} -> {} USD/oz", 
                            inserted, BACKFILL_DAYS, date, historicalPrice.price());
                } else {
                    failed++;
                    log.warn("[BACKFILL] [{}/{}] Failed to fetch price for {}", 
                            inserted + failed, BACKFILL_DAYS, date);
                }

                // Rate limiting delay between requests
                if (i > 1) {
                    Thread.sleep(DELAY_BETWEEN_REQUESTS_MS);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[BACKFILL] Interrupted - stopping backfill early");
                break;
            } catch (Exception e) {
                failed++;
                log.error("[BACKFILL] Error processing date {}: {}", date, e.getMessage());
            }
        }

        log.info("========================================");
        log.info("[BACKFILL] Completed!");
        log.info("[BACKFILL] Inserted: {}", inserted);
        log.info("[BACKFILL] Failed: {}", failed);
        log.info("[BACKFILL] Total rows in DB: {}", goldPriceHistoryService.count());
        log.info("========================================");
        
        if (inserted > 0) {
            log.warn("[BACKFILL] SUCCESS! Now set gold.backfill.enabled=false and remove backfill code.");
        }
    }
}
