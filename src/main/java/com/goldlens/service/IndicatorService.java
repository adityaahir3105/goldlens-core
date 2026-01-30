package com.goldlens.service;

import com.goldlens.domain.Indicator;
import com.goldlens.repository.IndicatorRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class IndicatorService {

    private final IndicatorRepository indicatorRepository;

    public IndicatorService(IndicatorRepository indicatorRepository) {
        this.indicatorRepository = indicatorRepository;
    }

    public List<Indicator> findAll() {
        return indicatorRepository.findAll();
    }

    public Optional<Indicator> findByCode(String code) {
        return indicatorRepository.findByCode(code);
    }

    public Indicator save(Indicator indicator) {
        return indicatorRepository.save(indicator);
    }

    /**
     * Finds an indicator by code, or creates it if it doesn't exist.
     * Ensures idempotent behavior for scheduler operations.
     */
    public Indicator findOrCreate(String code, String name, String unit) {
        return indicatorRepository.findByCode(code)
                .orElseGet(() -> {
                    Indicator newIndicator = Indicator.builder()
                            .code(code)
                            .name(name)
                            .unit(unit)
                            .active(true)
                            .build();
                    return indicatorRepository.save(newIndicator);
                });
    }
}
