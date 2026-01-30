package com.goldlens.repository;

import com.goldlens.domain.Indicator;
import com.goldlens.domain.IndicatorValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface IndicatorValueRepository extends JpaRepository<IndicatorValue, Long> {

    Optional<IndicatorValue> findTopByIndicatorOrderByDateDesc(Indicator indicator);

    @Query("SELECT iv FROM IndicatorValue iv WHERE iv.indicator = :indicator ORDER BY iv.date DESC LIMIT :limit")
    List<IndicatorValue> findTopNByIndicatorOrderByDateDesc(@Param("indicator") Indicator indicator, @Param("limit") int limit);

    boolean existsByIndicatorAndDate(Indicator indicator, LocalDate date);

    @Query("SELECT iv FROM IndicatorValue iv WHERE iv.indicator = :indicator AND iv.date >= :sinceDate ORDER BY iv.date ASC")
    List<IndicatorValue> findByIndicatorAndDateGreaterThanEqualOrderByDateAsc(
            @Param("indicator") Indicator indicator,
            @Param("sinceDate") LocalDate sinceDate);

    long countByIndicator(Indicator indicator);
}
