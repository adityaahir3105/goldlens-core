package com.goldlens.controller;

import com.goldlens.domain.Indicator;
import com.goldlens.domain.IndicatorValue;
import com.goldlens.dto.IndicatorDto;
import com.goldlens.dto.IndicatorHistoryDto;
import com.goldlens.dto.IndicatorValueDto;
import com.goldlens.service.IndicatorService;
import com.goldlens.service.IndicatorValueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/indicators")
public class IndicatorController {

    private final IndicatorService indicatorService;
    private final IndicatorValueService indicatorValueService;

    public IndicatorController(IndicatorService indicatorService, IndicatorValueService indicatorValueService) {
        this.indicatorService = indicatorService;
        this.indicatorValueService = indicatorValueService;
    }

    @GetMapping
    public List<IndicatorDto> listIndicators() {
        return indicatorService.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/{code}/latest")
    public ResponseEntity<IndicatorValueDto> getLatestValue(@PathVariable String code) {
        return indicatorService.findByCode(code)
                .flatMap(indicatorValueService::findLatestByIndicator)
                .map(this::toValueDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{code}/history")
    public ResponseEntity<IndicatorHistoryDto> getHistory(
            @PathVariable String code,
            @RequestParam(defaultValue = "30") int days) {

        // Cap days defensively at 120
        int cappedDays = Math.min(Math.max(days, 1), 120);

        return indicatorService.findByCode(code)
                .map(indicator -> buildHistoryResponse(indicator, cappedDays))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private IndicatorHistoryDto buildHistoryResponse(Indicator indicator, int days) {
        LocalDate sinceDate = LocalDate.now().minusDays(days);
        List<IndicatorValue> values = indicatorValueService.findHistorySince(indicator, sinceDate);

        List<IndicatorHistoryDto.DataPoint> points = values.stream()
                .map(v -> IndicatorHistoryDto.DataPoint.builder()
                        .date(v.getDate())
                        .value(v.getValue())
                        .build())
                .toList();

        return IndicatorHistoryDto.builder()
                .indicatorCode(indicator.getCode())
                .unit(indicator.getUnit())
                .points(points)
                .build();
    }

    private IndicatorDto toDto(Indicator indicator) {
        return IndicatorDto.builder()
                .code(indicator.getCode())
                .name(indicator.getName())
                .unit(indicator.getUnit())
                .active(indicator.isActive())
                .build();
    }

    private IndicatorValueDto toValueDto(IndicatorValue value) {
        return IndicatorValueDto.builder()
                .indicatorCode(value.getIndicator().getCode())
                .value(value.getValue())
                .date(value.getDate())
                .source(value.getSource())
                .build();
    }
}
