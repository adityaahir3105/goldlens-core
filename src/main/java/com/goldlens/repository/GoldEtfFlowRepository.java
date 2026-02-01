package com.goldlens.repository;

import com.goldlens.domain.GoldEtfFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface GoldEtfFlowRepository extends JpaRepository<GoldEtfFlow, Long> {

    Optional<GoldEtfFlow> findByDateAndRegion(LocalDate date, String region);

    boolean existsByDateAndRegion(LocalDate date, String region);

    @Query("SELECT g FROM GoldEtfFlow g WHERE g.date >= :startDate ORDER BY g.date ASC, g.region ASC")
    List<GoldEtfFlow> findByDateAfterOrderByDateAsc(@Param("startDate") LocalDate startDate);

    @Query("SELECT g FROM GoldEtfFlow g ORDER BY g.date ASC, g.region ASC")
    List<GoldEtfFlow> findAllOrderByDateAsc();

    long count();
}
