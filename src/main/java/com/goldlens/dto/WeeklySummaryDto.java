package com.goldlens.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeeklySummaryDto {

    private LocalDate weekEnding;
    private GoldRiskSummary goldRisk;
    private List<IndicatorSignalSummary> indicators;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GoldRiskSummary {
        private String riskLevel;
        private String reason;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class IndicatorSignalSummary {
        private String code;
        private String signal;
        private java.math.BigDecimal confidence;
    }
}
