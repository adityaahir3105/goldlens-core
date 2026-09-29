package com.goldlens.service;

import com.goldlens.domain.GoldPriceHistory;
import com.goldlens.repository.GoldPriceHistoryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class GoldPriceHistoryService {

    private final GoldPriceHistoryRepository repository;

    public GoldPriceHistoryService(GoldPriceHistoryRepository repository) {
        this.repository = repository;
    }

    public boolean existsByDate(LocalDate date) {
        return repository.existsByDate(date);
    }

    public GoldPriceHistory save(GoldPriceHistory history) {
        return repository.save(history);
    }

    public List<GoldPriceHistory> findHistorySince(LocalDate sinceDate) {
        return repository.findByDateGreaterThanEqualOrderByDateAsc(sinceDate);
    }

    public Optional<GoldPriceHistory> findLatest() {
        return repository.findTopByOrderByDateDesc();
    }

    public long count() {
        return repository.count();
    }
}
