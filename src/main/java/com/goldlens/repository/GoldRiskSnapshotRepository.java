package com.goldlens.repository;

import com.goldlens.domain.GoldRiskSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface GoldRiskSnapshotRepository extends JpaRepository<GoldRiskSnapshot, Long> {

    Optional<GoldRiskSnapshot> findTopByOrderByAsOfDateDesc();

    boolean existsByAsOfDate(LocalDate asOfDate);
}
