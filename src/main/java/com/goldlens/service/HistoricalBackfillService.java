package com.goldlens.service;

import com.goldlens.domain.Indicator;
import com.goldlens.domain.IndicatorValue;
import com.goldlens.scheduler.FredClient;
import com.goldlens.scheduler.GoldPriceScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class HistoricalBackfillService {

    private static final Logger log = LoggerFactory.getLogger(HistoricalBackfillService.class);

    private static final int REQUIRED_HISTORY = 30;
    private static final int FETCH_LIMIT = 500;
    private static final int LOOKBACK_DAYS = 365;

    private static final String SOURCE = "FRED";

    // Indicator code to FRED series ID mapping
    private static final Map<String, String> INDICATOR_SERIES_MAP = Map.of(
            "US_10Y_REAL_YIELD", "DFII10",
            "US_DOLLAR_INDEX", "DTWEXBGS"
    );

    // Indicator metadata for auto-creation
    private static final Map<String, IndicatorMeta> INDICATOR_META = Map.of(
            "US_10Y_REAL_YIELD", new IndicatorMeta("US 10Y Real Yield", "%"),
            "US_DOLLAR_INDEX", new IndicatorMeta("US Dollar Index (DXY)", "index")
    );

    private final FredClient fredClient;
    private final IndicatorService indicatorService;
    private final IndicatorValueService indicatorValueService;
    private final SignalEngineService signalEngineService;
    private final GoldPriceScheduler goldPriceScheduler;
    private final com.goldlens.client.GoldApiClient goldApiClient;
    private final GoldPriceHistoryService goldPriceHistoryService;

    public HistoricalBackfillService(FredClient fredClient,
                                     IndicatorService indicatorService,
                                     IndicatorValueService indicatorValueService,
                                     SignalEngineService signalEngineService,
                                     GoldPriceScheduler goldPriceScheduler,
                                     com.goldlens.client.GoldApiClient goldApiClient,
                                     GoldPriceHistoryService goldPriceHistoryService) {
        this.fredClient = fredClient;
        this.indicatorService = indicatorService;
        this.indicatorValueService = indicatorValueService;
        this.signalEngineService = signalEngineService;
        this.goldPriceScheduler = goldPriceScheduler;
        this.goldApiClient = goldApiClient;
        this.goldPriceHistoryService = goldPriceHistoryService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application ready — checking if historical backfill is needed");
        runBackfillIfNeeded();
    }

    /**
     * Runs backfill for all configured indicators if they lack sufficient history.
     * After backfill, computes signals for all indicators.
     * This method is idempotent and safe to call multiple times.
     */
    public void runBackfillIfNeeded() {
        // Backfill macro indicators from FRED
        for (String indicatorCode : INDICATOR_SERIES_MAP.keySet()) {
            backfillIndicatorIfNeeded(indicatorCode);
        }

        // Backfill gold price history using GoldAPI
        backfillGoldPriceHistory();

        // After backfill, ensure signals are computed for all indicators
        computeSignalsForAllIndicators();
    }

    private void computeSignalsForAllIndicators() {
        log.info("Computing signals for all indicators after backfill");
        LocalDate today = LocalDate.now();

        for (String indicatorCode : INDICATOR_SERIES_MAP.keySet()) {
            indicatorService.findByCode(indicatorCode).ifPresent(indicator -> {
                log.info("Computing signal for indicator {} on {}", indicatorCode, today);
                signalEngineService.computeAndStoreSignal(indicator, today);
            });
        }
    }

    /**
     * Backfills gold price history using GoldAPI.io date-specific endpoint.
     * Only fetches for dates not already in the database.
     * Skips weekends (gold markets closed).
     * Rate-limited: 1 second delay between API calls.
     */
    private void backfillGoldPriceHistory() {
        if (!goldApiClient.isConfigured()) {
            log.warn("GoldAPI not configured — skipping gold price backfill");
            return;
        }

        long existingCount = goldPriceHistoryService.count();
        log.info("Gold price history has {} existing records", existingCount);

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(LOOKBACK_DAYS);

        int inserted = 0;
        int skipped = 0;
        int errors = 0;
        int maxApiCalls = 50; // conservative limit per backfill run (free tier = ~300/month)
        int apiCallsMade = 0;

        for (LocalDate date = startDate; !date.isAfter(today) && apiCallsMade < maxApiCalls; date = date.plusDays(1)) {
            // Skip weekends — gold markets closed
            java.time.DayOfWeek dow = date.getDayOfWeek();
            if (dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY) {
                continue;
            }

            // Skip dates already in DB
            if (goldPriceHistoryService.existsByDate(date)) {
                skipped++;
                continue;
            }

            try {
                apiCallsMade++;
                var priceOpt = goldApiClient.fetchPriceForDate(date);
                if (priceOpt.isPresent()) {
                    var hp = priceOpt.get();
                    com.goldlens.domain.GoldPriceHistory history = com.goldlens.domain.GoldPriceHistory.builder()
                            .date(hp.date())
                            .price(hp.price())
                            .source("GoldAPI")
                            .build();
                    goldPriceHistoryService.save(history);
                    inserted++;
                    log.info("[gold-backfill] Saved {} for date {}", hp.price(), hp.date());
                } else {
                    errors++;
                }

                // Rate limit: 1 second between calls
                Thread.sleep(1000);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[gold-backfill] Interrupted during backfill");
                break;
            } catch (Exception e) {
                errors++;
                log.warn("[gold-backfill] Failed to fetch price for {}: {}", date, e.getMessage());
            }
        }

        log.info("[gold-backfill] Completed: inserted={}, skipped={}, errors={}, apiCalls={}",
                inserted, skipped, errors, apiCallsMade);
    }

    private void backfillIndicatorIfNeeded(String indicatorCode) {
        String seriesId = INDICATOR_SERIES_MAP.get(indicatorCode);
        IndicatorMeta meta = INDICATOR_META.get(indicatorCode);

        if (seriesId == null || meta == null) {
            log.warn("No FRED series mapping found for indicator: {}", indicatorCode);
            return;
        }

        // Ensure indicator exists
        Indicator indicator = indicatorService.findOrCreate(indicatorCode, meta.name(), meta.unit());

        // Check current history count
        long currentCount = indicatorValueService.countByIndicator(indicator);
        log.info("Indicator {} has {} existing data points", indicatorCode, currentCount);

        // Always backfill to fill any gaps (don't skip based on count alone)
        log.info("Starting backfill for {} (current count: {})", indicatorCode, currentCount);

        LocalDate startDate = LocalDate.now().minusDays(LOOKBACK_DAYS);
        List<FredClient.FredObservation> observations = fredClient.fetchHistoricalObservations(
                seriesId, startDate, FETCH_LIMIT);

        if (observations.isEmpty()) {
            log.warn("No historical observations fetched from FRED for {}", indicatorCode);
            return;
        }

        log.info("Fetched {} observations from FRED for {}", observations.size(), indicatorCode);

        int insertedCount = 0;
        int skippedCount = 0;

        for (FredClient.FredObservation obs : observations) {
            // Check if this date already exists (idempotent)
            if (indicatorValueService.existsByIndicatorAndDate(indicator, obs.date())) {
                skippedCount++;
                continue;
            }

            IndicatorValue value = IndicatorValue.builder()
                    .indicator(indicator)
                    .value(obs.value())
                    .date(obs.date())
                    .source(SOURCE)
                    .build();

            indicatorValueService.save(value);
            insertedCount++;
        }

        log.info("Backfill completed for {}: inserted {} rows, skipped {} duplicates",
                indicatorCode, insertedCount, skippedCount);
    }

    private record IndicatorMeta(String name, String unit) {}
}
