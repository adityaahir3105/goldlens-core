package com.goldlens.scheduler;

import com.goldlens.service.GoldRiskAggregationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class GoldRiskScheduler {

    private static final Logger log = LoggerFactory.getLogger(GoldRiskScheduler.class);

    private final GoldRiskAggregationService goldRiskAggregationService;

    public GoldRiskScheduler(GoldRiskAggregationService goldRiskAggregationService) {
        this.goldRiskAggregationService = goldRiskAggregationService;
    }

    // Daily at 06:10 UTC - runs after indicator schedulers to aggregate fresh signals
    @Scheduled(cron = "0 10 6 * * *")
    public void computeDailyGoldRisk() {
        log.info("Starting daily gold risk aggregation");
        goldRiskAggregationService.computeAndStoreRiskSnapshot(LocalDate.now());
    }
}
