package com.goldlens.controller;

import com.goldlens.domain.GoldPriceHistory;
import com.goldlens.dto.GoldPriceHistoryDto;
import com.goldlens.service.GoldPriceHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/gold/price")
public class GoldHistoryController {

    private static final String UNIT = "USD/oz";

    private final GoldPriceHistoryService goldPriceHistoryService;

    public GoldHistoryController(GoldPriceHistoryService goldPriceHistoryService) {
        this.goldPriceHistoryService = goldPriceHistoryService;
    }

    @GetMapping("/history")
    public ResponseEntity<GoldPriceHistoryDto> getHistory(
            @RequestParam(defaultValue = "30") int days) {

        // Cap days defensively at 120
        int cappedDays = Math.min(Math.max(days, 1), 120);

        LocalDate sinceDate = LocalDate.now().minusDays(cappedDays);
        List<GoldPriceHistory> history = goldPriceHistoryService.findHistorySince(sinceDate);

        List<GoldPriceHistoryDto.DataPoint> points = history.stream()
                .map(h -> GoldPriceHistoryDto.DataPoint.builder()
                        .date(h.getDate())
                        .value(h.getPrice())
                        .build())
                .toList();

        GoldPriceHistoryDto response = GoldPriceHistoryDto.builder()
                .unit(UNIT)
                .points(points)
                .build();

        return ResponseEntity.ok(response);
    }
}
