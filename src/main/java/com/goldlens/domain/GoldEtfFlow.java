package com.goldlens.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "gold_etf_flows", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"date", "region"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoldEtfFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 50)
    private String region;

    @Column(name = "holdings_tonnes", precision = 10, scale = 2)
    private BigDecimal holdingsTonnes;

    @Column(name = "net_flow_tonnes", precision = 10, scale = 2)
    private BigDecimal netFlowTonnes;

    @Column(length = 50)
    @Builder.Default
    private String source = "WORLD_GOLD_COUNCIL";

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
