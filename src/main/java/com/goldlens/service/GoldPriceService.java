package com.goldlens.service;

import com.goldlens.client.GoldPriceClient;
import com.goldlens.dto.GoldPriceSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class GoldPriceService {

    private static final Logger log = LoggerFactory.getLogger(GoldPriceService.class);

    private static final int CACHE_DURATION_MINUTES = 5;

    private final GoldPriceClient goldPriceClient;

    private final AtomicReference<CachedPrice> cache = new AtomicReference<>();

    public GoldPriceService(GoldPriceClient goldPriceClient) {
        this.goldPriceClient = goldPriceClient;
    }

    /**
     * Returns the current gold price, using cached value if fresh.
     * Cache expires after 5 minutes.
     */
    public Optional<GoldPriceSnapshot> getLatestPrice() {
        CachedPrice cached = cache.get();

        if (cached != null && !cached.isExpired()) {
            log.debug("Returning cached gold price from {}", cached.fetchedAt());
            return Optional.of(cached.snapshot());
        }

        log.info("Cache expired or empty — fetching fresh gold price");
        Optional<GoldPriceSnapshot> freshPrice = goldPriceClient.fetchCurrentPrice();

        freshPrice.ifPresent(snapshot -> {
            cache.set(new CachedPrice(snapshot, LocalDateTime.now()));
            log.info("Cached new gold price: {}", snapshot.getPrice());
        });

        // If fetch failed but we have stale cache, return stale data
        if (freshPrice.isEmpty() && cached != null) {
            log.warn("Fetch failed — returning stale cached price from {}", cached.fetchedAt());
            return Optional.of(cached.snapshot());
        }

        return freshPrice;
    }

    private record CachedPrice(GoldPriceSnapshot snapshot, LocalDateTime fetchedAt) {
        boolean isExpired() {
            return LocalDateTime.now().isAfter(fetchedAt.plusMinutes(CACHE_DURATION_MINUTES));
        }
    }
}
