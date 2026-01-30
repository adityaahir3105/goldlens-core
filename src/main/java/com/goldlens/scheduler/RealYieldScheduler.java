package com.goldlens.scheduler;

import com.goldlens.domain.Indicator;
import com.goldlens.domain.IndicatorValue;
import com.goldlens.service.IndicatorService;
import com.goldlens.service.IndicatorValueService;
import com.goldlens.service.SignalEngineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RealYieldScheduler {

    private static final Logger log = LoggerFactory.getLogger(RealYieldScheduler.class);

    private static final String INDICATOR_CODE = "US_10Y_REAL_YIELD";
    private static final String INDICATOR_NAME = "US 10Y Real Yield";
    private static final String INDICATOR_UNIT = "%";
    private static final String FRED_SERIES_ID = "DFII10";
    private static final String SOURCE = "FRED";

    private final FredClient fredClient;
    private final IndicatorService indicatorService;
    private final IndicatorValueService indicatorValueService;
    private final SignalEngineService signalEngineService;

    public RealYieldScheduler(FredClient fredClient,
                              IndicatorService indicatorService,
                              IndicatorValueService indicatorValueService,
                              SignalEngineService signalEngineService) {
        this.fredClient = fredClient;
        this.indicatorService = indicatorService;
        this.indicatorValueService = indicatorValueService;
        this.signalEngineService = signalEngineService;
    }

    // Daily at 06:00 UTC - FRED updates macro data once per day, no need for more frequent polling
    @Scheduled(cron = "0 0 6 * * *")
    public void ingestRealYield() {
        log.info("Starting US 10Y Real Yield ingestion");

        Indicator indicator = indicatorService.findOrCreate(
                INDICATOR_CODE,
                INDICATOR_NAME,
                INDICATOR_UNIT
        );

        fredClient.fetchLatestObservation(FRED_SERIES_ID)
                .ifPresentOrElse(
                        observation -> processObservation(indicator, observation),
                        () -> log.warn("No valid observation received from FRED")
                );
    }

    private void processObservation(Indicator indicator, FredClient.FredObservation observation) {
        log.info("Fetched observation: date={}, value={}", observation.date(), observation.value());

        boolean exists = indicatorValueService.existsByIndicatorAndDate(indicator, observation.date());

        if (exists) {
            log.info("Skipping insert: value already exists for date {}", observation.date());
            return;
        }

        IndicatorValue value = IndicatorValue.builder()
                .indicator(indicator)
                .value(observation.value())
                .date(observation.date())
                .source(SOURCE)
                .build();

        indicatorValueService.save(value);
        log.info("Inserted new value for date {}", observation.date());

        // Compute signal after ingestion
        signalEngineService.computeAndStoreSignal(indicator, observation.date());
    }
}
