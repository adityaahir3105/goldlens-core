package com.goldlens.service;

import com.goldlens.domain.GoldPriceHistory;
import com.goldlens.dto.GoldPriceSnapshot;
import com.goldlens.repository.GoldPriceHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class GoldPriceService {

    private static final Logger log = LoggerFactory.getLogger(GoldPriceService.class);

    private static final String CURRENCY = "USD";
    private static final String UNIT = "oz";

    private final GoldPriceHistoryRepository goldPriceHistoryRepository;

    public GoldPriceService(GoldPriceHistoryRepository goldPriceHistoryRepository) {
        this.goldPriceHistoryRepository = goldPriceHistoryRepository;
    }

    /**
     * Returns the latest gold price from the database.
     * GoldAPI is only used by schedulers for ingestion, not for serving requests.
     */
    public Optional<GoldPriceSnapshot> getLatestPrice() {
        Optional<GoldPriceHistory> latest = goldPriceHistoryRepository.findTopByOrderByDateDesc();

        if (latest.isEmpty()) {
            log.warn("No gold price data available in database");
            return Optional.empty();
        }

        GoldPriceHistory history = latest.get();
        log.info("Serving latest gold price from DB: {} on {}", history.getPrice(), history.getDate());

        GoldPriceSnapshot snapshot = GoldPriceSnapshot.builder()
                .price(history.getPrice())
                .currency(CURRENCY)
                .unit(UNIT)
                .asOf(history.getDate().atStartOfDay())
                .source(history.getSource())
                .build();

        return Optional.of(snapshot);
    }
}
