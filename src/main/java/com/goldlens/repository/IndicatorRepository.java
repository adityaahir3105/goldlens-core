package com.goldlens.repository;

import com.goldlens.domain.Indicator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IndicatorRepository extends JpaRepository<Indicator, Long> {

    Optional<Indicator> findByCode(String code);
}
