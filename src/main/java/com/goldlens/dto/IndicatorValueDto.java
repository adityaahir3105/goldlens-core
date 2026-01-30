package com.goldlens.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndicatorValueDto {

    private String indicatorCode;
    private BigDecimal value;
    private LocalDate date;
    private String source;
}
