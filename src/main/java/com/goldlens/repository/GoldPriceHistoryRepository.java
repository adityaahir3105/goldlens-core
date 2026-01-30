package com.goldlens.repository;

import com.goldlens.domain.GoldPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface GoldPriceHistoryRepository extends JpaRepository<GoldPriceHistory, Long> {

    boolean existsByDate(LocalDate date);

    Optional<GoldPriceHistory> findTopByOrderByDateDesc();

    @Query("SELECT g FROM GoldPriceHistory g WHERE g.date >= :sinceDate ORDER BY g.date ASC")
    List<GoldPriceHistory> findByDateGreaterThanEqualOrderByDateAsc(@Param("sinceDate") LocalDate sinceDate);

    long count();
}
