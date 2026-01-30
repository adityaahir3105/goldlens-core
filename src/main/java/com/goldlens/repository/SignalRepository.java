package com.goldlens.repository;

import com.goldlens.domain.Indicator;
import com.goldlens.domain.Signal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface SignalRepository extends JpaRepository<Signal, Long> {

    Optional<Signal> findTopByIndicatorOrderByAsOfDateDesc(Indicator indicator);

    boolean existsByIndicatorAndAsOfDate(Indicator indicator, LocalDate asOfDate);
}
