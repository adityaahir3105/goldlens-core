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
public class EtfFlowPointDto {
    private LocalDate date;
    private String region;
    private BigDecimal holdingsTonnes;
    private BigDecimal netFlowTonnes;
}
