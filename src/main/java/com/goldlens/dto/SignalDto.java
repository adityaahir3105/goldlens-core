package com.goldlens.dto;

import com.goldlens.domain.SignalType;
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
public class SignalDto {

    private String indicatorCode;
    private SignalType signalType;
    private String reason;
    private LocalDate asOfDate;
    private BigDecimal confidence;
}
