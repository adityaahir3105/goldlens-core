package com.goldlens.dto;

import com.goldlens.domain.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoldRiskDto {

    private RiskLevel riskLevel;
    private String reason;
    private LocalDate asOfDate;
}
