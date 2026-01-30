package com.goldlens.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoldPriceSnapshot {

    private BigDecimal price;
    private String currency;
    private String unit;
    private LocalDateTime asOf;
    private String source;
}
