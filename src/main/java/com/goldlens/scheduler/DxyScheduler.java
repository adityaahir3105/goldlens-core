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
public class DxyScheduler {

    private static final Logger log = LoggerFactory.getLogger(DxyScheduler.class);

    private static final String INDICATOR_CODE = "US_DOLLAR_INDEX";
    private static final String INDICATOR_NAME = "US Dollar Index (DXY)";
    private static final String INDICATOR_UNIT = "index";
    private static final String FRED_SERIES_ID = "DTWEXBGS";
    private static final String SOURCE = "FRED";

    private final FredClient fredClient;
    private final IndicatorService indicatorService;
    private final IndicatorValueService indicatorValueService;
    private final SignalEngineService signalEngineService;

    public DxyScheduler(FredClient fredClient,
                        IndicatorService indicatorService,
                        IndicatorValueService indicatorValueService,
                        SignalEngineService signalEngineService) {
        this.fredClient = fredClient;
        this.indicatorService = indicatorService;
        this.indicatorValueService = indicatorValueService;
        this.signalEngineService = signalEngineService;
    }

    // Daily at 06:05 UTC - runs after RealYieldScheduler; FRED data updates once per day
    @Scheduled(cron = "0 5 6 * * *")
    public void ingestDxy() {
        log.info("Starting US Dollar Index ingestion");

        Indicator indicator = indicatorService.findOrCreate(
                INDICATOR_CODE,
                INDICATOR_NAME,
                INDICATOR_UNIT
        );

        fredClient.fetchLatestObservation(FRED_SERIES_ID)
                .ifPresentOrElse(
                        observation -> processObservation(indicator, observation),
                        () -> log.warn("No valid observation received from FRED for DXY")
                );
    }

    private void processObservation(Indicator indicator, FredClient.FredObservation observation) {
        log.info("Fetched DXY observation: date={}, value={}", observation.date(), observation.value());

        boolean exists = indicatorValueService.existsByIndicatorAndDate(indicator, observation.date());

        if (exists) {
            log.info("Skipping insert: DXY value already exists for date {}", observation.date());
            return;
        }

        IndicatorValue value = IndicatorValue.builder()
                .indicator(indicator)
                .value(observation.value())
                .date(observation.date())
                .source(SOURCE)
                .build();

        indicatorValueService.save(value);
        log.info("Inserted new DXY value for date {}", observation.date());

        // Compute signal after ingestion
        signalEngineService.computeAndStoreSignal(indicator, observation.date());
    }
}
