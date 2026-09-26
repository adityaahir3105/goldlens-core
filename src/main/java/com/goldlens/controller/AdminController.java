package com.goldlens.controller;

import com.goldlens.domain.GoldEtfFlow;
import com.goldlens.service.GoldEtfExcelImporter;
import com.goldlens.service.GoldEtfFlowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final GoldEtfExcelImporter excelImporter;
    private final GoldEtfFlowService etfFlowService;
    private final com.goldlens.service.HistoricalBackfillService historicalBackfillService;

    private final ExecutorService backfillExecutor = Executors.newSingleThreadExecutor();

    @PostMapping("/backfill-macro")
    public ResponseEntity<String> backfillMacro() {
        log.info("Starting manual macro backfill (FRED & Gold Prices) — running async");
        backfillExecutor.submit(() -> {
            try {
                historicalBackfillService.runBackfillIfNeeded();
                log.info("Async macro backfill completed successfully");
            } catch (Exception e) {
                log.error("Async macro backfill failed", e);
            }
        });
        return ResponseEntity.ok("Macro backfill started in background. Check logs for progress.");
    }

    @PostMapping("/backfill-wgc")
    public ResponseEntity<String> backfillWgc(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Please select a file to upload.");
        }

        try {
            log.info("Starting manual World Gold Council backfill from uploaded file: {}", file.getOriginalFilename());
            List<GoldEtfFlow> flows = excelImporter.parseExcel(file.getInputStream());
            
            int inserted = 0;
            int skipped = 0;

            for (GoldEtfFlow flow : flows) {
                int result = etfFlowService.upsertFlow(flow);
                if (result > 0) {
                    inserted++;
                } else {
                    skipped++;
                }
            }

            String message = String.format("Successfully parsed %d records. Inserted/Updated: %d, Skipped: %d", 
                    flows.size(), inserted, skipped);
            log.info(message);
            
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            log.error("Failed to process uploaded file", e);
            return ResponseEntity.internalServerError().body("Error processing file: " + e.getMessage());
        }
    }
}
