package com.goldlens.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndicatorHistoryDto {

    private String indicatorCode;
    private String unit;
    private List<DataPoint> points;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DataPoint {
        private LocalDate date;
        private BigDecimal value;
    }
}
