package com.goldlens.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
    name = "indicator_values",
    uniqueConstraints = @UniqueConstraint(columnNames = {"indicator_id", "date"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndicatorValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "indicator_id", nullable = false)
    private Indicator indicator;

    @Column(precision = 19, scale = 6)
    private BigDecimal value;

    @Column(nullable = false)
    private LocalDate date;

    private String source;
}
