package com.goldlens.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
    name = "signals",
    uniqueConstraints = @UniqueConstraint(columnNames = {"indicator_id", "as_of_date"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Signal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "indicator_id", nullable = false)
    private Indicator indicator;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false)
    private SignalType signalType;

    private String reason;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(precision = 3, scale = 2)
    private BigDecimal confidence;
}
