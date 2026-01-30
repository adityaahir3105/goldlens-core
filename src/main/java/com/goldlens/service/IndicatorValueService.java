package com.goldlens.service;

import com.goldlens.domain.Indicator;
import com.goldlens.domain.IndicatorValue;
import com.goldlens.repository.IndicatorValueRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class IndicatorValueService {

    private final IndicatorValueRepository indicatorValueRepository;

    public IndicatorValueService(IndicatorValueRepository indicatorValueRepository) {
        this.indicatorValueRepository = indicatorValueRepository;
    }

    public Optional<IndicatorValue> findLatestByIndicator(Indicator indicator) {
        return indicatorValueRepository.findTopByIndicatorOrderByDateDesc(indicator);
    }

    public List<IndicatorValue> findRecentByIndicator(Indicator indicator, int limit) {
        return indicatorValueRepository.findTopNByIndicatorOrderByDateDesc(indicator, limit);
    }

    public boolean existsByIndicatorAndDate(Indicator indicator, LocalDate date) {
        return indicatorValueRepository.existsByIndicatorAndDate(indicator, date);
    }

    public List<IndicatorValue> findHistorySince(Indicator indicator, LocalDate sinceDate) {
        return indicatorValueRepository.findByIndicatorAndDateGreaterThanEqualOrderByDateAsc(indicator, sinceDate);
    }

    public long countByIndicator(Indicator indicator) {
        return indicatorValueRepository.countByIndicator(indicator);
    }

    public IndicatorValue save(IndicatorValue indicatorValue) {
        return indicatorValueRepository.save(indicatorValue);
    }
}
