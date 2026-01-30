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
    private static final int FETCH_LIMIT = 100;
    private static final int LOOKBACK_DAYS = 90;

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

    public HistoricalBackfillService(FredClient fredClient,
                                     IndicatorService indicatorService,
                                     IndicatorValueService indicatorValueService,
                                     SignalEngineService signalEngineService,
                                     GoldPriceScheduler goldPriceScheduler) {
        this.fredClient = fredClient;
        this.indicatorService = indicatorService;
        this.indicatorValueService = indicatorValueService;
        this.signalEngineService = signalEngineService;
        this.goldPriceScheduler = goldPriceScheduler;
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
        // Backfill macro indicators
        for (String indicatorCode : INDICATOR_SERIES_MAP.keySet()) {
            backfillIndicatorIfNeeded(indicatorCode);
        }

        // Backfill gold price history
        goldPriceScheduler.backfillHistory(LOOKBACK_DAYS, FETCH_LIMIT);

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

        if (currentCount >= REQUIRED_HISTORY) {
            log.info("Backfill skipped for {} — sufficient history exists ({} >= {})",
                    indicatorCode, currentCount, REQUIRED_HISTORY);
            return;
        }

        // Need to backfill
        log.info("Starting backfill for {} — current count {} is below required {}",
                indicatorCode, currentCount, REQUIRED_HISTORY);

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
