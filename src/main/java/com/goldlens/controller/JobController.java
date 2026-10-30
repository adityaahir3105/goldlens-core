package com.goldlens.controller;

import com.goldlens.scheduler.DxyScheduler;
import com.goldlens.scheduler.GoldPriceScheduler;
import com.goldlens.scheduler.GoldRiskScheduler;
import com.goldlens.scheduler.RealYieldScheduler;
import com.goldlens.service.HistoricalBackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runs the scheduled jobs on demand. Cloud Scheduler calls these (with X-Admin-Token, enforced by
 * AdminTokenFilter) in place of the in-app timers, see deploy/scheduler-setup.sh. The work runs inside
 * the request so it keeps CPU under Cloud Run's request-based billing; a failure returns 500 and
 * Cloud Scheduler retries.
 */
@RestController
@RequestMapping("/api/admin/jobs")
@RequiredArgsConstructor
@Slf4j
public class JobController {

    private final GoldPriceScheduler goldPriceScheduler;
    private final RealYieldScheduler realYieldScheduler;
    private final DxyScheduler dxyScheduler;
    private final GoldRiskScheduler goldRiskScheduler;
    private final HistoricalBackfillService historicalBackfillService;

    @PostMapping("/gold-price")
    public ResponseEntity<String> goldPrice() {
        log.info("Job trigger: gold price");
        goldPriceScheduler.persistGoldPriceSnapshot();
        return ResponseEntity.ok("gold price job done");
    }

    // Same order as the old 06:00 / 06:05 / 06:10 UTC timers: indicators first, then the risk roll-up.
    // Gap filling (formerly done on every startup) runs after the latest-value ingests, so those still
    // see the new observation and compute its signal.
    @PostMapping("/daily")
    public ResponseEntity<String> daily() {
        log.info("Job trigger: daily indicators and gold risk");
        realYieldScheduler.ingestRealYield();
        dxyScheduler.ingestDxy();
        historicalBackfillService.fillMacroGaps();
        goldRiskScheduler.computeDailyGoldRisk();
        return ResponseEntity.ok("daily jobs done");
    }
}
