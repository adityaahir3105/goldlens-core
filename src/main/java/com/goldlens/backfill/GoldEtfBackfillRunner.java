package com.goldlens.backfill;

import com.goldlens.domain.GoldEtfFlow;
import com.goldlens.service.GoldEtfExcelImporter;
import com.goldlens.service.GoldEtfFlowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class GoldEtfBackfillRunner implements CommandLineRunner {

    private final GoldEtfExcelImporter excelImporter;
    private final GoldEtfFlowService etfFlowService;

    @Value("${gold.etf.backfill.enabled:false}")
    private boolean backfillEnabled;

    @Override
    public void run(String... args) {
        if (!backfillEnabled) {
            log.info("Gold ETF backfill is DISABLED. Set gold.etf.backfill.enabled=true to enable.");
            return;
        }

        log.info("=== Starting Gold ETF Backfill ===");

        try {
            List<GoldEtfFlow> flows = excelImporter.parseExcel();

            int processed = 0;
            int inserted = 0;
            int skipped = 0;

            for (GoldEtfFlow flow : flows) {
                processed++;
                int result = etfFlowService.upsertFlow(flow);
                if (result > 0) {
                    inserted++;
                } else {
                    skipped++;
                }

                if (processed % 100 == 0) {
                    log.info("Progress: processed={}, inserted={}, skipped={}", processed, inserted, skipped);
                }
            }

            log.info("=== Gold ETF Backfill Complete ===");
            log.info("Summary: processed={}, inserted={}, skipped={}", processed, inserted, skipped);
            log.info("Total records in database: {}", etfFlowService.count());

        } catch (Exception e) {
            log.error("Gold ETF backfill failed: {}", e.getMessage(), e);
        }
    }
}
