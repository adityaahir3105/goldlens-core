package com.goldlens.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketSnapshotDto {

    private MetricSnapshot gold;
    private MetricSnapshot realYield;
    private MetricSnapshot dxy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricSnapshot {
        private BigDecimal value;
        private BigDecimal change;
        private BigDecimal changePercent;
        private LocalDate asOfDate;
        private String source;
        private boolean fresh;
        private String status; // "fresh", "stale", "missing"
    }
}
