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

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final GoldEtfExcelImporter excelImporter;
    private final GoldEtfFlowService etfFlowService;

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
