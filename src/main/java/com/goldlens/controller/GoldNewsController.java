package com.goldlens.controller;

import com.goldlens.dto.GoldNewsResponse;
import com.goldlens.service.GoldNewsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/news")
public class GoldNewsController {

    private static final Logger log = LoggerFactory.getLogger(GoldNewsController.class);

    private final GoldNewsService goldNewsService;

    public GoldNewsController(GoldNewsService goldNewsService) {
        this.goldNewsService = goldNewsService;
    }

    @GetMapping("/gold")
    public ResponseEntity<GoldNewsResponse> getGoldNews() {
        try {
            GoldNewsResponse response = goldNewsService.getGoldNews();
            log.info("[GoldNews] Returning {} articles from {}", 
                    response.getItems().size(), response.getProvider());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[GoldNews] Unexpected error: {}", e.getMessage(), e);
            // Return empty response on failure, do NOT crash
            return ResponseEntity.ok(GoldNewsResponse.builder()
                    .items(java.util.Collections.emptyList())
                    .provider("error")
                    .fetchedAt(java.time.Instant.now())
                    .build());
        }
    }
}
