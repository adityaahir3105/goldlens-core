package com.goldlens.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "gold_risk_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoldRiskSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false)
    private RiskLevel riskLevel;

    private String reason;

    @Column(name = "as_of_date", nullable = false, unique = true)
    private LocalDate asOfDate;
}
