package com.goldlens.controller;

import com.goldlens.domain.GoldPriceHistory;
import com.goldlens.dto.GoldPriceHistoryDto;
import com.goldlens.service.GoldPriceHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/gold/price")
public class GoldHistoryController {

    private static final Logger log = LoggerFactory.getLogger(GoldHistoryController.class);

    private static final String UNIT = "USD/oz";
    private static final String SOURCE = "GoldPricez";
    private static final int MIN_HISTORY_POINTS = 7;

    private final GoldPriceHistoryService goldPriceHistoryService;

    public GoldHistoryController(GoldPriceHistoryService goldPriceHistoryService) {
        this.goldPriceHistoryService = goldPriceHistoryService;
    }

    /**
     * Returns gold price history from DB snapshots.
     * GoldPricez does not support historical data directly - history is built over time via scheduled snapshots.
     */
    @GetMapping("/history")
    public ResponseEntity<GoldPriceHistoryDto> getHistory(
            @RequestParam(defaultValue = "30") int days) {

        int cappedDays = Math.min(Math.max(days, 1), 120);
        LocalDate sinceDate = LocalDate.now().minusDays(cappedDays);
        List<GoldPriceHistory> history = goldPriceHistoryService.findHistorySince(sinceDate);

        // Check if we have enough data points
        if (history.isEmpty()) {
            log.info("No historical data available");
            
            GoldPriceHistoryDto response = GoldPriceHistoryDto.builder()
                    .unit(UNIT)
                    .points(Collections.emptyList())
                    .historySupported(false)
                    .historicalAvailable(false)
                    .message("Historical data not available. Run backfill to populate history.")
                    .source(SOURCE)
                    .build();
            return ResponseEntity.ok(response);
        }

        if (history.size() < MIN_HISTORY_POINTS) {
            log.info("Insufficient history data: {} points (min: {})", history.size(), MIN_HISTORY_POINTS);
        }

        List<GoldPriceHistoryDto.DataPoint> points = history.stream()
                .map(h -> GoldPriceHistoryDto.DataPoint.builder()
                        .date(h.getDate())
                        .value(h.getPrice())
                        .build())
                .toList();

        GoldPriceHistoryDto response = GoldPriceHistoryDto.builder()
                .unit(UNIT)
                .points(points)
                .historySupported(true)
                .historicalAvailable(true)
                .message(null)
                .source(SOURCE)
                .build();

        return ResponseEntity.ok(response);
    }
}
