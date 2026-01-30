package com.goldlens.scheduler;

import com.goldlens.client.GoldPriceClient;
import com.goldlens.domain.GoldPriceHistory;
import com.goldlens.service.GoldPriceHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class GoldPriceScheduler {

    private static final Logger log = LoggerFactory.getLogger(GoldPriceScheduler.class);

    private static final String SOURCE = "GoldAPI";

    private final GoldPriceClient goldPriceClient;
    private final GoldPriceHistoryService goldPriceHistoryService;

    public GoldPriceScheduler(GoldPriceClient goldPriceClient, GoldPriceHistoryService goldPriceHistoryService) {
        this.goldPriceClient = goldPriceClient;
        this.goldPriceHistoryService = goldPriceHistoryService;
    }

    // Every 15 minutes - gold spot price changes frequently during trading hours
    @Scheduled(cron = "0 */15 * * * *")
    public void ingestLatestGoldPrice() {
        log.info("Starting daily gold price ingestion via GoldAPI");

        if (!goldPriceClient.isConfigured()) {
            log.warn("GoldAPI not configured — skipping daily ingestion");
            return;
        }

        LocalDate today = LocalDate.now();
        if (goldPriceHistoryService.existsByDate(today)) {
            log.info("Gold price for {} already exists — skipping", today);
            return;
        }

        goldPriceClient.fetchCurrentPrice().ifPresentOrElse(
                snapshot -> {
                    GoldPriceHistory history = GoldPriceHistory.builder()
                            .date(today)
                            .price(snapshot.getPrice())
                            .source(SOURCE)
                            .build();

                    goldPriceHistoryService.save(history);
                    log.info("Inserted gold price {} for date {}", snapshot.getPrice(), today);
                },
                () -> log.warn("No gold price available from GoldAPI")
        );
    }

    /**
     * Backfills historical gold prices from GoldAPI.
     * Called on application startup via HistoricalBackfillService.
     */
    public void backfillHistory(int lookbackDays, int fetchLimit) {
        log.info("Starting gold price historical backfill via GoldAPI");

        if (!goldPriceClient.isConfigured()) {
            log.warn("GoldAPI not configured — skipping backfill");
            return;
        }

        long currentCount = goldPriceHistoryService.count();
        if (currentCount >= 30) {
            log.info("Gold price backfill skipped — sufficient history exists ({} rows)", currentCount);
            return;
        }

        LocalDate today = LocalDate.now();
        int insertedCount = 0;
        int skippedCount = 0;
        int noDataCount = 0;

        // Fetch day by day, starting from most recent
        for (int i = 1; i <= lookbackDays && insertedCount < fetchLimit; i++) {
            LocalDate date = today.minusDays(i);

            // Skip if already exists (idempotent)
            if (goldPriceHistoryService.existsByDate(date)) {
                skippedCount++;
                continue;
            }

            // Fetch price for this date
            var priceOpt = goldPriceClient.fetchPriceForDate(date);
            if (priceOpt.isEmpty()) {
                noDataCount++;
                continue;
            }

            GoldPriceHistory history = GoldPriceHistory.builder()
                    .date(priceOpt.get().date())
                    .price(priceOpt.get().price())
                    .source(SOURCE)
                    .build();

            goldPriceHistoryService.save(history);
            insertedCount++;

            // Log progress every 10 inserts
            if (insertedCount % 10 == 0) {
                log.info("Gold price backfill progress: {} rows inserted", insertedCount);
            }
        }

        long finalCount = goldPriceHistoryService.count();
        log.info("Gold price backfill completed: inserted {} rows, skipped {} duplicates, {} non-trading days, total rows now: {}",
                insertedCount, skippedCount, noDataCount, finalCount);
    }
}
